package com.contentops.ai.capability.fallback;

import com.contentops.ai.capability.cache.SemanticCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 模型降级链核心。
 *
 * <p>遍历降级链（按 priority 升序）依次调用各模型端点，每个端点调用通过
 * {@link CircuitBreakerManager} 包装。全部端点失败后尝试语义缓存兜底，
 * 缓存未命中再返回模板兜底文案。每级降级均通过 @Slf4j 记录日志。</p>
 *
 * <p>降级层级：
 * <pre>
 * Level 1: 主模型（如 GPT-4o）  ↓ 失败/熔断
 * Level 2: 备用模型（如 DeepSeek） ↓ 失败/熔断
 * Level 3: 末级模型（如 Qwen-Plus） ↓ 失败/熔断
 * Level 4: 语义缓存兜底           ↓ 未命中
 * Level 5: 模板兜底文案
 * </pre>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelFallbackChain {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    /** 模板兜底文案 */
    private static final String FALLBACK_TEMPLATE =
            "抱歉，当前AI服务暂时不可用，已为您返回兜底内容。请稍后重试或联系管理员。";

    private final ObjectProvider<List<ModelEndpoint>> modelEndpointsProvider;
    private final CircuitBreakerManager circuitBreakerManager;
    /** 语义缓存服务可选（retrieval/基础设施未就绪时可能缺失） */
    private final ObjectProvider<SemanticCacheService> semanticCacheServiceProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 按端点名称缓存带独立超时的 RestTemplate */
    private final ConcurrentMap<String, RestTemplate> endpointRestTemplates = new ConcurrentHashMap<>();

    /** 排序后的端点快照（懒加载） */
    private volatile List<ModelEndpoint> sortedEndpoints;

    /**
     * 简化调用，仅返回文本内容。
     */
    public String chat(String prompt, String tenantId) {
        return chatWithMeta(prompt, tenantId).content();
    }

    /**
     * 带元信息的调用：返回结果 + 使用的模型名 + 是否降级。
     */
    public ChatResult chatWithMeta(String prompt, String tenantId) {
        List<ModelEndpoint> endpoints = endpoints();
        log.info("降级链调用开始, 租户={}, prompt长度={}, 候选模型数={}",
                tenantId, prompt == null ? 0 : prompt.length(), endpoints.size());

        String lastTriedModel = "n/a";
        for (int i = 0; i < endpoints.size(); i++) {
            ModelEndpoint endpoint = endpoints.get(i);
            lastTriedModel = endpoint.model();
            try {
                String content = circuitBreakerManager.executeWithCircuitBreaker(
                        endpoint.name(), () -> callEndpoint(endpoint, prompt));
                if (content != null && !content.isBlank()) {
                    boolean degraded = i > 0;
                    log.info("模型[{}]调用成功{}", endpoint.name(), degraded ? "(经过降级)" : "");
                    return ChatResult.ofModel(content, endpoint.model(), degraded);
                }
                log.warn("模型[{}]返回空内容, 降级到下一级", endpoint.name());
            } catch (CallNotPermittedException e) {
                log.warn("模型[{}]熔断器开启, 降级到下一级", endpoint.name());
            } catch (Exception e) {
                log.warn("模型[{}]调用失败, 降级到下一级: {}", endpoint.name(), e.getMessage());
            }
        }

        // 所有模型端点失败 -> 语义缓存兜底
        SemanticCacheService cache = semanticCacheServiceProvider.getIfAvailable();
        if (cache != null) {
            log.info("所有模型端点不可用, 尝试语义缓存兜底, 租户={}", tenantId);
            try {
                var cached = cache.get(prompt, tenantId);
                if (cached.isPresent()) {
                    log.info("语义缓存命中, 返回缓存结果, 租户={}", tenantId);
                    return ChatResult.ofCache(cached.get(), lastTriedModel);
                }
                log.warn("语义缓存未命中, 租户={}", tenantId);
            } catch (Exception e) {
                log.warn("语义缓存兜底异常, 租户={}: {}", tenantId, e.getMessage());
            }
        }

        // 缓存未命中 -> 模板兜底
        log.warn("降级链全部失败, 返回模板兜底文案, 租户={}", tenantId);
        return ChatResult.ofTemplate(FALLBACK_TEMPLATE);
    }

    /**
     * 调用单个 OpenAI 兼容端点，内部按 {@code maxRetries} 重试。
     * <p>仅对网络/传输类异常重试; 响应体解析失败属于确定性错误, 重试无意义且浪费配额, 直接抛出。</p>
     */
    private String callEndpoint(ModelEndpoint endpoint, String prompt) {
        RestTemplate rt = restTemplateFor(endpoint);
        String url = endpoint.baseUrl() + CHAT_COMPLETIONS_PATH;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(endpoint.apiKey());

        Map<String, Object> body = Map.of(
                "model", endpoint.model(),
                "messages", List.of(Map.of("role", "user", "content", prompt == null ? "" : prompt)),
                "temperature", 0.7
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Exception lastError = null;
        // 总尝试次数 = maxRetries + 1
        for (int attempt = 0; attempt <= endpoint.maxRetries(); attempt++) {
            try {
                ResponseEntity<String> resp = rt.postForEntity(url, entity, String.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    return extractContent(resp.getBody());
                }
                log.warn("模型[{}]返回非2xx状态: {}", endpoint.name(), resp.getStatusCode());
            } catch (ModelResponseException e) {
                // 确定性解析失败, 重试无意义, 直接抛出避免浪费配额
                throw e;
            } catch (Exception e) {
                lastError = e;
                log.warn("模型[{}]第{}次尝试失败: {}", endpoint.name(), attempt + 1, e.getMessage());
            }
        }
        throw new ModelResponseException("模型[" + endpoint.name() + "]全部重试失败", lastError);
    }

    /** 从 OpenAI 兼容响应中提取 choices[0].message.content */
    private String extractContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new ModelResponseException("模型响应缺少choices[0].message.content: " + body);
            }
            return content.asText();
        } catch (ModelResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelResponseException("解析模型响应JSON失败: " + e.getMessage(), e);
        }
    }

    /** 按端点构建带独立超时的 RestTemplate（缓存复用） */
    private RestTemplate restTemplateFor(ModelEndpoint endpoint) {
        return endpointRestTemplates.computeIfAbsent(endpoint.name(), name -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(endpoint.timeoutSeconds()));
            factory.setReadTimeout(Duration.ofSeconds(endpoint.timeoutSeconds()));
            RestTemplate rt = new RestTemplate(factory);
            rt.setMessageConverters(restTemplate.getMessageConverters());
            return rt;
        });
    }

    /** 懒加载并按 priority 排序端点列表 */
    private List<ModelEndpoint> endpoints() {
        List<ModelEndpoint> snapshot = sortedEndpoints;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = sortedEndpoints;
                if (snapshot == null) {
                    List<ModelEndpoint> provided = modelEndpointsProvider.getIfAvailable();
                    List<ModelEndpoint> list = new ArrayList<>(
                            provided != null ? provided : List.of());
                    if (list.isEmpty()) {
                        // 安全兜底：配置缺失时至少保证一个端点
                        list.add(ModelEndpoint.builder()
                                .name("gpt-4o")
                                .baseUrl("https://api.openai.com")
                                .apiKey(System.getenv().getOrDefault("OPENAI_API_KEY", "sk-placeholder"))
                                .model("gpt-4o")
                                .timeoutSeconds(30)
                                .maxRetries(3)
                                .priority(1)
                                .build());
                    }
                    list.sort(Comparator.comparingInt(ModelEndpoint::priority));
                    snapshot = List.copyOf(list);
                    sortedEndpoints = snapshot;
                }
            }
        }
        return snapshot;
    }

    /**
     * 模型响应解析异常(确定性失败)。
     * <p>用于区分传输类异常与响应体不可解析类异常: 前者可重试, 后者重试无意义。</p>
     */
    private static class ModelResponseException extends RuntimeException {
        ModelResponseException(String message) {
            super(message);
        }

        ModelResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
