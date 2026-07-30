package com.contentops.ai.capability.retrieval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrossEncoderReranker} 单元测试。
 *
 * <p>验证 Cross-Encoder 重排器的核心逻辑: 正常重排排序、服务不可用降级、
 * 响应异常降级、空输入与非法 topK 的边界处理。</p>
 *
 * <p>由于 {@link CrossEncoderReranker} 构造器接收 {@link RestTemplateBuilder} 并在内部构建
 * {@link RestTemplate}(通过 @Value 注入 rerankEndpoint), 测试中通过 Mock RestTemplateBuilder
 * 链式调用替换为 Mock RestTemplate, 并用 {@link ReflectionTestUtils} 注入 endpoint 配置。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cross-Encoder 重排器 CrossEncoderReranker 测试")
class CrossEncoderRerankerTest {

    private static final String RERANK_ENDPOINT = "http://localhost:8000/rerank";

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private CrossEncoderReranker crossEncoderReranker;

    @BeforeEach
    void setUp() {
        // Mock RestTemplateBuilder 链式调用: setConnectTimeout -> setReadTimeout -> build
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class)))
                .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class)))
                .thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build())
                .thenReturn(restTemplate);

        // 手动构造(不使用 @InjectMocks, 因为需要在构造前完成 Builder Mock 链)
        crossEncoderReranker = new CrossEncoderReranker(restTemplateBuilder);

        // 注入 @Value 字段 rerankEndpoint
        ReflectionTestUtils.setField(crossEncoderReranker, "rerankEndpoint", RERANK_ENDPOINT);
    }

    // ==================== 正常重排 ====================

    @Test
    @DisplayName("正常重排: 返回按分数降序排列的topK结果, 文档source标记为RERANKED")
    void rerank_正常重排_应返回按分数降序的topK结果() {
        // given
        String query = "如何优化RAG检索效果";
        List<Document> documents = List.of(
                buildDoc("d1", "文档一内容", 0.3),
                buildDoc("d2", "文档二内容", 0.5),
                buildDoc("d3", "文档三内容", 0.7));

        // rerank 服务返回的分数(与文档顺序对应): d1=0.85, d2=0.92, d3=0.60
        CrossEncoderReranker.RerankResponse rerankResponse =
                new CrossEncoderReranker.RerankResponse(List.of(0.85, 0.92, 0.60));

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenReturn(new ResponseEntity<>(rerankResponse, HttpStatus.OK));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 2);

        // then
        assertThat(result).hasSize(2);
        // 按分数降序: d2(0.92) > d1(0.85) > d3(0.60), 取top2
        assertThat(result.get(0).getId()).isEqualTo("d2");
        assertThat(result.get(0).getScore()).isEqualTo(0.92);
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.RERANKED);
        assertThat(result.get(1).getId()).isEqualTo("d1");
        assertThat(result.get(1).getScore()).isEqualTo(0.85);
        assertThat(result.get(1).getSource()).isEqualTo(Document.Source.RERANKED);

        // 验证 HTTP 调用
        verify(restTemplate).postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class));
    }

    @Test
    @DisplayName("正常重排: topK大于文档数量时返回全部文档(按分数降序)")
    void rerank_topK大于文档数_应返回全部文档() {
        // given
        String query = "查询文本";
        List<Document> documents = List.of(
                buildDoc("d1", "文档A", 0.0),
                buildDoc("d2", "文档B", 0.0));

        CrossEncoderReranker.RerankResponse rerankResponse =
                new CrossEncoderReranker.RerankResponse(List.of(0.45, 0.88));

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenReturn(new ResponseEntity<>(rerankResponse, HttpStatus.OK));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 10);

        // then
        assertThat(result).hasSize(2);
        // 按分数降序: d2(0.88) > d1(0.45)
        assertThat(result.get(0).getId()).isEqualTo("d2");
        assertThat(result.get(0).getScore()).isEqualTo(0.88);
        assertThat(result.get(1).getId()).isEqualTo("d1");
        assertThat(result.get(1).getScore()).isEqualTo(0.45);
    }

    // ==================== 降级场景 ====================

    @Test
    @DisplayName("rerank服务不可用时降级: 抛异常后返回原始前topK个文档(不重排)")
    void rerank_服务不可用_应降级返回原始前topK() {
        // given
        String query = "查询";
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3),
                buildDoc("d2", "文档二", 0.5),
                buildDoc("d3", "文档三", 0.7));

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 2);

        // then
        assertThat(result).hasSize(2);
        // 降级返回原始前2个(保持原始顺序, 未重排)
        assertThat(result.get(0).getId()).isEqualTo("d1");
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.VECTOR);
        assertThat(result.get(1).getId()).isEqualTo("d2");
        assertThat(result.get(1).getSource()).isEqualTo(Document.Source.VECTOR);

        verify(restTemplate).postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class));
    }

    @Test
    @DisplayName("响应scores数量不匹配时降级: 返回原始前topK个文档")
    void rerank_响应scores数量不匹配_应降级返回原始前topK() {
        // given
        String query = "查询";
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3),
                buildDoc("d2", "文档二", 0.5),
                buildDoc("d3", "文档三", 0.7));

        // 返回2个分数, 但文档有3个 -> 数量不匹配
        CrossEncoderReranker.RerankResponse mismatchResponse =
                new CrossEncoderReranker.RerankResponse(List.of(0.9, 0.8));

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenReturn(new ResponseEntity<>(mismatchResponse, HttpStatus.OK));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 3);

        // then
        assertThat(result).hasSize(3);
        // 降级返回原始前3个(保持原始顺序)
        assertThat(result.get(0).getId()).isEqualTo("d1");
        assertThat(result.get(1).getId()).isEqualTo("d2");
        assertThat(result.get(2).getId()).isEqualTo("d3");
        // source 未被修改为 RERANKED
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.VECTOR);
    }

    @Test
    @DisplayName("响应body为null时降级: 返回原始前topK个文档")
    void rerank_响应body为null_应降级返回原始前topK() {
        // given
        String query = "查询";
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3),
                buildDoc("d2", "文档二", 0.5));

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("d1");
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.VECTOR);
    }

    @Test
    @DisplayName("响应scores为null时降级: 返回原始前topK个文档")
    void rerank_响应scores为null_应降级返回原始前topK() {
        // given
        String query = "查询";
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3));

        CrossEncoderReranker.RerankResponse nullScoresResponse =
                new CrossEncoderReranker.RerankResponse(null);

        when(restTemplate.postForEntity(eq(RERANK_ENDPOINT), any(), eq(CrossEncoderReranker.RerankResponse.class)))
                .thenReturn(new ResponseEntity<>(nullScoresResponse, HttpStatus.OK));

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 1);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("d1");
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.VECTOR);
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("空文档列表: 直接返回空列表, 不调用rerank服务")
    void rerank_空文档列表_应返回空列表() {
        // given
        String query = "查询";
        List<Document> documents = List.of();

        // when
        List<Document> result = crossEncoderReranker.rerank(query, documents, 5);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        // 验证未调用 rerank 服务
        verify(restTemplate, org.mockito.Mockito.never())
                .postForEntity(anyString(), any(), eq(CrossEncoderReranker.RerankResponse.class));
    }

    @Test
    @DisplayName("null文档列表: 直接返回空列表")
    void rerank_null文档列表_应返回空列表() {
        // when
        List<Document> result = crossEncoderReranker.rerank("查询", null, 5);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("topK=0: 直接返回空列表, 不调用rerank服务")
    void rerank_topK为零_应返回空列表() {
        // given
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3),
                buildDoc("d2", "文档二", 0.5));

        // when
        List<Document> result = crossEncoderReranker.rerank("查询", documents, 0);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(restTemplate, org.mockito.Mockito.never())
                .postForEntity(anyString(), any(), eq(CrossEncoderReranker.RerankResponse.class));
    }

    @Test
    @DisplayName("topK为负数: 等同于0, 返回空列表")
    void rerank_topK为负数_应返回空列表() {
        // given
        List<Document> documents = List.of(
                buildDoc("d1", "文档一", 0.3));

        // when
        List<Document> result = crossEncoderReranker.rerank("查询", documents, -5);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("所有文档content为null或空白时: validDocs为空, 降级返回原始前topK")
    void rerank_所有文档内容为空_应降级返回原始前topK() {
        // given
        List<Document> documents = List.of(
                buildDoc("d1", null, 0.3),
                buildDoc("d2", "   ", 0.5));

        // when
        List<Document> result = crossEncoderReranker.rerank("查询", documents, 2);

        // then
        assertThat(result).hasSize(2);
        // 降级返回原始前2个
        assertThat(result.get(0).getId()).isEqualTo("d1");
        assertThat(result.get(1).getId()).isEqualTo("d2");

        // 验证未调用 rerank 服务(因为validDocs为空, 直接走fallback)
        verify(restTemplate, org.mockito.Mockito.never())
                .postForEntity(anyString(), any(), eq(CrossEncoderReranker.RerankResponse.class));
    }

    // ==================== 辅助方法 ====================

    /** 构建测试用 Document */
    private Document buildDoc(String id, String content, double score) {
        return Document.builder()
                .id(id)
                .content(content)
                .score(score)
                .metadata(Map.of("tenantId", "test"))
                .source(Document.Source.VECTOR)
                .build();
    }
}
