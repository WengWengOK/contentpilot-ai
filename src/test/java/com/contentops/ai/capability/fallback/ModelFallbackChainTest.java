package com.contentops.ai.capability.fallback;

import com.contentops.ai.capability.cache.SemanticCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelFallbackChain} 单元测试。
 *
 * <p>验证模型降级链的核心逻辑: 主模型成功直接返回、主模型失败降级到备用模型、
 * 所有模型失败后语义缓存兜底、缓存未命中后模板兜底, 以及端点按 priority 排序。</p>
 *
 * <p>通过 Mock {@link CircuitBreakerManager#executeWithCircuitBreaker} 拦截实际模型调用,
 * 模拟成功/失败/熔断等场景, 验证降级链的遍历顺序与兜底策略。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("模型降级链 ModelFallbackChain 测试")
class ModelFallbackChainTest {

    @Mock
    private ObjectProvider<List<ModelEndpoint>> modelEndpointsProvider;

    @Mock
    private CircuitBreakerManager circuitBreakerManager;

    @Mock
    private ObjectProvider<SemanticCacheService> semanticCacheServiceProvider;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SemanticCacheService semanticCacheService;

    private ModelFallbackChain modelFallbackChain;

    @BeforeEach
    void setUp() {
        // 手动构造, 避免 @InjectMocks 无法区分两个 ObjectProvider 泛型 mock 的注入顺序问题
        modelFallbackChain = new ModelFallbackChain(
                modelEndpointsProvider,
                circuitBreakerManager,
                semanticCacheServiceProvider,
                restTemplate,
                objectMapper);
    }

    // ==================== 正常路径 ====================

    @Test
    @DisplayName("主模型成功: 直接返回主模型结果, 不触发降级, 不访问缓存")
    void chatWithMeta_主模型成功_应直接返回主模型结果() {
        // given
        String prompt = "请生成一篇关于AI的科普文章";
        String tenantId = "tenant-001";

        ModelEndpoint primaryEndpoint = ModelEndpoint.builder()
                .name("gpt-4o-endpoint")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-key")
                .model("gpt-4o")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(1)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(primaryEndpoint));
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class)))
                .thenReturn("这是GPT-4o生成的科普文章内容");

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("这是GPT-4o生成的科普文章内容");
        assertThat(result.modelUsed()).isEqualTo("gpt-4o");
        assertThat(result.degraded()).isFalse();
        assertThat(result.source()).isEqualTo("model");

        // 验证只调用了主模型, 未访问缓存
        verify(circuitBreakerManager).executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class));
        verify(semanticCacheServiceProvider, never()).getIfAvailable();
    }

    // ==================== 降级场景 ====================

    @Test
    @DisplayName("主模型失败降级到备用模型: 主模型抛异常后遍历到备用模型并成功返回")
    void chatWithMeta_主模型失败_应降级到备用模型() {
        // given
        String prompt = "请分析用户增长数据";
        String tenantId = "tenant-002";

        ModelEndpoint primaryEndpoint = ModelEndpoint.builder()
                .name("gpt-4o-endpoint")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-key1")
                .model("gpt-4o")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(1)
                .build();

        ModelEndpoint backupEndpoint = ModelEndpoint.builder()
                .name("deepseek-endpoint")
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-key2")
                .model("deepseek-chat")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(2)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(primaryEndpoint, backupEndpoint));
        // 主模型抛异常
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("GPT-4o调用超时"));
        // 备用模型成功
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("deepseek-endpoint"), any(Supplier.class)))
                .thenReturn("这是DeepSeek生成的分析结果");

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("这是DeepSeek生成的分析结果");
        assertThat(result.modelUsed()).isEqualTo("deepseek-chat");
        assertThat(result.degraded()).isTrue();
        assertThat(result.source()).isEqualTo("model");

        // 验证两个端点都被调用
        verify(circuitBreakerManager).executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class));
        verify(circuitBreakerManager).executeWithCircuitBreaker(eq("deepseek-endpoint"), any(Supplier.class));
        // 未访问缓存(因为备用模型成功)
        verify(semanticCacheServiceProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("所有模型失败后语义缓存兜底: 所有端点均失败, 缓存命中返回缓存结果")
    void chatWithMeta_所有模型失败_应语义缓存兜底() {
        // given
        String prompt = "请总结这篇报告";
        String tenantId = "tenant-003";

        ModelEndpoint primaryEndpoint = ModelEndpoint.builder()
                .name("gpt-4o-endpoint")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-key1")
                .model("gpt-4o")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(1)
                .build();

        ModelEndpoint backupEndpoint = ModelEndpoint.builder()
                .name("deepseek-endpoint")
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-key2")
                .model("deepseek-chat")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(2)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(primaryEndpoint, backupEndpoint));
        // 所有端点均失败
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("GPT-4o不可用"));
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("deepseek-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("DeepSeek不可用"));
        // 语义缓存命中
        when(semanticCacheServiceProvider.getIfAvailable())
                .thenReturn(semanticCacheService);
        when(semanticCacheService.get(eq(prompt), eq(tenantId)))
                .thenReturn(Optional.of("这是缓存的总结结果"));

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("这是缓存的总结结果");
        assertThat(result.modelUsed()).isEqualTo("deepseek-chat");
        assertThat(result.degraded()).isTrue();
        assertThat(result.source()).isEqualTo("cache");

        // 验证缓存被访问
        verify(semanticCacheServiceProvider).getIfAvailable();
        verify(semanticCacheService).get(eq(prompt), eq(tenantId));
    }

    @Test
    @DisplayName("所有模型+缓存都失败后模板兜底: 返回模板兜底文案")
    void chatWithMeta_所有模型和缓存都失败_应返回模板兜底() {
        // given
        String prompt = "请生成营销文案";
        String tenantId = "tenant-004";

        ModelEndpoint primaryEndpoint = ModelEndpoint.builder()
                .name("gpt-4o-endpoint")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-key1")
                .model("gpt-4o")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(1)
                .build();

        ModelEndpoint backupEndpoint = ModelEndpoint.builder()
                .name("deepseek-endpoint")
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-key2")
                .model("deepseek-chat")
                .timeoutSeconds(30)
                .maxRetries(2)
                .priority(2)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(primaryEndpoint, backupEndpoint));
        // 所有端点均失败
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("GPT-4o不可用"));
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("deepseek-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("DeepSeek不可用"));
        // 缓存未命中
        when(semanticCacheServiceProvider.getIfAvailable())
                .thenReturn(semanticCacheService);
        when(semanticCacheService.get(eq(prompt), eq(tenantId)))
                .thenReturn(Optional.empty());

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("template");
        assertThat(result.degraded()).isTrue();
        assertThat(result.content()).isNotBlank();
        assertThat(result.content()).contains("兜底");

        // 验证所有兜底层级都被尝试
        verify(circuitBreakerManager).executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class));
        verify(circuitBreakerManager).executeWithCircuitBreaker(eq("deepseek-endpoint"), any(Supplier.class));
        verify(semanticCacheService).get(eq(prompt), eq(tenantId));
    }

    @Test
    @DisplayName("缓存服务不可用时: 跳过缓存直接返回模板兜底")
    void chatWithMeta_缓存服务不可用_应跳过缓存返回模板兜底() {
        // given
        String prompt = "查询";
        String tenantId = "tenant-005";

        ModelEndpoint endpoint = ModelEndpoint.builder()
                .name("gpt-4o-endpoint")
                .baseUrl("https://api.openai.com")
                .apiKey("sk-key")
                .model("gpt-4o")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(1)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(endpoint));
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("gpt-4o-endpoint"), any(Supplier.class)))
                .thenThrow(new RuntimeException("模型不可用"));
        // 缓存服务不可用(getIfAvailable返回null)
        when(semanticCacheServiceProvider.getIfAvailable())
                .thenReturn(null);

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result.source()).isEqualTo("template");
        assertThat(result.content()).isNotBlank();
    }

    // ==================== 端点排序 ====================

    @Test
    @DisplayName("端点按priority升序排序: priority=1的端点最先被调用")
    void chatWithMeta_端点应按priority升序排序() {
        // given
        String prompt = "测试排序";
        String tenantId = "tenant-006";

        // 故意以乱序提供: priority=3, 1, 2
        ModelEndpoint epPriority3 = ModelEndpoint.builder()
                .name("ep-priority-3")
                .baseUrl("https://api.p3.com")
                .apiKey("key3")
                .model("model-p3")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(3)
                .build();

        ModelEndpoint epPriority1 = ModelEndpoint.builder()
                .name("ep-priority-1")
                .baseUrl("https://api.p1.com")
                .apiKey("key1")
                .model("model-p1")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(1)
                .build();

        ModelEndpoint epPriority2 = ModelEndpoint.builder()
                .name("ep-priority-2")
                .baseUrl("https://api.p2.com")
                .apiKey("key2")
                .model("model-p2")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(2)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(epPriority3, epPriority1, epPriority2));
        // 所有端点返回空内容, 触发全部遍历
        when(circuitBreakerManager.executeWithCircuitBreaker(anyString(), any(Supplier.class)))
                .thenReturn("");

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then - 最终走模板兜底(所有模型返回空 + 缓存未配置)
        assertThat(result.source()).isEqualTo("template");

        // 验证调用顺序: priority=1 -> priority=2 -> priority=3
        InOrder inOrder = inOrder(circuitBreakerManager);
        inOrder.verify(circuitBreakerManager).executeWithCircuitBreaker(eq("ep-priority-1"), any(Supplier.class));
        inOrder.verify(circuitBreakerManager).executeWithCircuitBreaker(eq("ep-priority-2"), any(Supplier.class));
        inOrder.verify(circuitBreakerManager).executeWithCircuitBreaker(eq("ep-priority-3"), any(Supplier.class));
    }

    @Test
    @DisplayName("端点返回空内容时降级: content为blank时继续尝试下一个端点")
    void chatWithMeta_模型返回空内容_应降级到下一个端点() {
        // given
        String prompt = "测试空内容";
        String tenantId = "tenant-007";

        ModelEndpoint primaryEndpoint = ModelEndpoint.builder()
                .name("ep-primary")
                .baseUrl("https://api.primary.com")
                .apiKey("key1")
                .model("model-primary")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(1)
                .build();

        ModelEndpoint backupEndpoint = ModelEndpoint.builder()
                .name("ep-backup")
                .baseUrl("https://api.backup.com")
                .apiKey("key2")
                .model("model-backup")
                .timeoutSeconds(30)
                .maxRetries(1)
                .priority(2)
                .build();

        when(modelEndpointsProvider.getIfAvailable())
                .thenReturn(List.of(primaryEndpoint, backupEndpoint));
        // 主模型返回空内容
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("ep-primary"), any(Supplier.class)))
                .thenReturn("");
        // 备用模型返回有效内容
        when(circuitBreakerManager.executeWithCircuitBreaker(eq("ep-backup"), any(Supplier.class)))
                .thenReturn("备用模型的有效内容");

        // when
        ChatResult result = modelFallbackChain.chatWithMeta(prompt, tenantId);

        // then
        assertThat(result.content()).isEqualTo("备用模型的有效内容");
        assertThat(result.modelUsed()).isEqualTo("model-backup");
        assertThat(result.degraded()).isTrue();
        assertThat(result.source()).isEqualTo("model");
    }
}
