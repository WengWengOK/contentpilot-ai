package com.contentops.ai.capability.retrieval;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HybridRetriever} 单元测试。
 *
 * <p>验证混合检索编排流程: 并行向量检索 + BM25 检索 -> RRF 融合 -> Cross-Encoder 重排,
 * 以及单路/双路检索失败时的降级策略。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("混合检索编排器 HybridRetriever 测试")
class HybridRetrieverTest {

    @Mock
    private QdrantVectorStoreService qdrantVectorStoreService;

    @Mock
    private BM25Service bm25Service;

    @Mock
    private RRFFusionStrategy rrfFusionStrategy;

    @Mock
    private CrossEncoderReranker crossEncoderReranker;

    @InjectMocks
    private HybridRetriever hybridRetriever;

    @AfterEach
    void tearDown() {
        // 释放检索线程池资源, 避免线程泄漏影响其他测试
        hybridRetriever.shutdown();
    }

    // ==================== 正常流程 ====================

    @Test
    @DisplayName("正常并行检索+融合+重排流程: 两路均返回结果, 经RRF融合和重排后返回topK")
    void retrieve_正常流程_应返回重排后的topK结果() {
        // given
        String query = "如何提升内容运营效率";
        String tenantId = "tenant-001";
        int topK = 5;

        List<Document> vectorResults = List.of(
                buildDoc("v1", "向量结果1", 0.95, Document.Source.VECTOR),
                buildDoc("v2", "向量结果2", 0.88, Document.Source.VECTOR),
                buildDoc("v3", "向量结果3", 0.80, Document.Source.VECTOR));

        List<Document> bm25Results = List.of(
                buildDoc("b1", "BM25结果1", 12.5, Document.Source.BM25),
                buildDoc("b2", "BM25结果2", 10.3, Document.Source.BM25));

        List<Document> fusedResults = List.of(
                buildDoc("v1", "向量结果1", 0.032, Document.Source.FUSED),
                buildDoc("b1", "BM25结果1", 0.030, Document.Source.FUSED),
                buildDoc("v2", "向量结果2", 0.028, Document.Source.FUSED),
                buildDoc("b2", "BM25结果2", 0.026, Document.Source.FUSED),
                buildDoc("v3", "向量结果3", 0.024, Document.Source.FUSED));

        List<Document> rerankedResults = List.of(
                buildDoc("b1", "BM25结果1", 0.95, Document.Source.RERANKED),
                buildDoc("v1", "向量结果1", 0.91, Document.Source.RERANKED),
                buildDoc("v2", "向量结果2", 0.85, Document.Source.RERANKED),
                buildDoc("b2", "BM25结果2", 0.78, Document.Source.RERANKED),
                buildDoc("v3", "向量结果3", 0.72, Document.Source.RERANKED));

        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenReturn(vectorResults);
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenReturn(bm25Results);
        when(rrfFusionStrategy.fuse(eq(vectorResults), eq(bm25Results)))
                .thenReturn(fusedResults);
        when(crossEncoderReranker.rerank(eq(query), anyList(), eq(topK)))
                .thenReturn(rerankedResults);

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(5);
        assertThat(result.get(0).getId()).isEqualTo("b1");
        assertThat(result.get(0).getSource()).isEqualTo(Document.Source.RERANKED);

        // 验证各阶段均被调用且参数正确
        verify(qdrantVectorStoreService).similaritySearch(eq(query), eq(tenantId), eq(20));
        verify(bm25Service).search(eq(query), eq(tenantId), eq(20));
        verify(rrfFusionStrategy).fuse(eq(vectorResults), eq(bm25Results));
        verify(crossEncoderReranker).rerank(eq(query), anyList(), eq(topK));
    }

    // ==================== 降级场景 ====================

    @Test
    @DisplayName("向量检索失败时降级为BM25结果: safeVectorSearch捕获异常返回空列表")
    void retrieve_向量检索失败_应降级为BM25结果() {
        // given
        String query = "内容营销策略";
        String tenantId = "tenant-002";
        int topK = 3;

        List<Document> bm25Results = List.of(
                buildDoc("b1", "BM25结果1", 12.5, Document.Source.BM25),
                buildDoc("b2", "BM25结果2", 10.3, Document.Source.BM25));

        List<Document> fusedResults = List.of(
                buildDoc("b1", "BM25结果1", 0.016, Document.Source.FUSED),
                buildDoc("b2", "BM25结果2", 0.012, Document.Source.FUSED));

        List<Document> rerankedResults = List.of(
                buildDoc("b1", "BM25结果1", 0.90, Document.Source.RERANKED),
                buildDoc("b2", "BM25结果2", 0.75, Document.Source.RERANKED));

        // 向量检索抛异常, safeVectorSearch 内部捕获并返回空列表
        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenThrow(new RuntimeException("Qdrant连接超时"));
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenReturn(bm25Results);
        // 融合时应收到 (空列表, bm25结果)
        when(rrfFusionStrategy.fuse(eq(List.of()), eq(bm25Results)))
                .thenReturn(fusedResults);
        when(crossEncoderReranker.rerank(eq(query), anyList(), eq(topK)))
                .thenReturn(rerankedResults);

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("b1");

        // 验证向量检索被调用(尽管抛异常)
        verify(qdrantVectorStoreService).similaritySearch(eq(query), eq(tenantId), eq(20));
        // 验证BM25检索被调用
        verify(bm25Service).search(eq(query), eq(tenantId), eq(20));
        // 验证融合收到的是空向量结果 + BM25结果
        verify(rrfFusionStrategy).fuse(eq(List.of()), eq(bm25Results));
        // 验证重排被调用
        verify(crossEncoderReranker).rerank(eq(query), anyList(), eq(topK));
    }

    @Test
    @DisplayName("BM25检索失败时降级为向量结果: safeBm25Search捕获异常返回空列表")
    void retrieve_BM25检索失败_应降级为向量结果() {
        // given
        String query = "用户增长分析";
        String tenantId = "tenant-003";
        int topK = 2;

        List<Document> vectorResults = List.of(
                buildDoc("v1", "向量结果1", 0.95, Document.Source.VECTOR),
                buildDoc("v2", "向量结果2", 0.88, Document.Source.VECTOR),
                buildDoc("v3", "向量结果3", 0.80, Document.Source.VECTOR));

        List<Document> fusedResults = List.of(
                buildDoc("v1", "向量结果1", 0.016, Document.Source.FUSED),
                buildDoc("v2", "向量结果2", 0.012, Document.Source.FUSED),
                buildDoc("v3", "向量结果3", 0.008, Document.Source.FUSED));

        List<Document> rerankedResults = List.of(
                buildDoc("v1", "向量结果1", 0.92, Document.Source.RERANKED),
                buildDoc("v2", "向量结果2", 0.85, Document.Source.RERANKED));

        // BM25检索抛异常, safeBm25Search 内部捕获并返回空列表
        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenReturn(vectorResults);
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenThrow(new RuntimeException("PostgreSQL连接失败"));
        // 融合时应收到 (向量结果, 空列表)
        when(rrfFusionStrategy.fuse(eq(vectorResults), eq(List.of())))
                .thenReturn(fusedResults);
        when(crossEncoderReranker.rerank(eq(query), anyList(), eq(topK)))
                .thenReturn(rerankedResults);

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("v1");

        // 验证BM25检索被调用(尽管抛异常)
        verify(bm25Service).search(eq(query), eq(tenantId), eq(20));
        // 验证向量检索被调用
        verify(qdrantVectorStoreService).similaritySearch(eq(query), eq(tenantId), eq(20));
        // 验证融合收到的是向量结果 + 空BM25结果
        verify(rrfFusionStrategy).fuse(eq(vectorResults), eq(List.of()));
        // 验证重排被调用
        verify(crossEncoderReranker).rerank(eq(query), anyList(), eq(topK));
    }

    @Test
    @DisplayName("两路检索都失败时返回空列表: 两路safe方法均捕获异常返回空, 融合和重排收到空输入")
    void retrieve_两路检索都失败_应返回空列表() {
        // given
        String query = "异常查询";
        String tenantId = "tenant-004";
        int topK = 5;

        List<Document> emptyFused = List.of();
        List<Document> emptyReranked = List.of();

        // 两路检索都抛异常
        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenThrow(new RuntimeException("Qdrant不可用"));
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenThrow(new RuntimeException("PostgreSQL不可用"));
        // 融合收到两个空列表
        when(rrfFusionStrategy.fuse(eq(List.of()), eq(List.of())))
                .thenReturn(emptyFused);
        when(crossEncoderReranker.rerank(eq(query), anyList(), eq(topK)))
                .thenReturn(emptyReranked);

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        // 验证两路检索均被调用
        verify(qdrantVectorStoreService).similaritySearch(eq(query), eq(tenantId), eq(20));
        verify(bm25Service).search(eq(query), eq(tenantId), eq(20));
        // 验证融合收到两个空列表
        verify(rrfFusionStrategy).fuse(eq(List.of()), eq(List.of()));
        // 验证重排被调用(传入空列表)
        verify(crossEncoderReranker).rerank(eq(query), anyList(), eq(topK));
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("空查询处理: 空字符串查询不中断流程, 两路服务正常调用后返回空结果")
    void retrieve_空查询_应正常流转并返回空结果() {
        // given
        String query = "";
        String tenantId = "tenant-005";
        int topK = 3;

        // 空查询时底层服务返回空结果(模拟真实BM25Service对空查询返回空的行为)
        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenReturn(List.of());
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenReturn(List.of());
        when(rrfFusionStrategy.fuse(eq(List.of()), eq(List.of())))
                .thenReturn(List.of());
        when(crossEncoderReranker.rerank(eq(query), anyList(), eq(topK)))
                .thenReturn(List.of());

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        // 验证空查询被透传到两路检索服务
        verify(qdrantVectorStoreService).similaritySearch(eq(""), eq(tenantId), eq(20));
        verify(bm25Service).search(eq(""), eq(tenantId), eq(20));
    }

    @Test
    @DisplayName("topK大于融合结果数量时: 重排候选不足CANDIDATE_TOP_K, 直接使用全部融合结果")
    void retrieve_融合结果不足候选数_应使用全部融合结果送重排() {
        // given
        String query = "少量结果的查询";
        String tenantId = "tenant-006";
        int topK = 10;

        List<Document> vectorResults = List.of(
                buildDoc("v1", "结果1", 0.9, Document.Source.VECTOR));

        List<Document> bm25Results = List.of(
                buildDoc("b1", "结果2", 5.0, Document.Source.BM25));

        List<Document> fusedResults = List.of(
                buildDoc("v1", "结果1", 0.016, Document.Source.FUSED),
                buildDoc("b1", "结果2", 0.012, Document.Source.FUSED));

        List<Document> rerankedResults = List.of(
                buildDoc("v1", "结果1", 0.90, Document.Source.RERANKED),
                buildDoc("b1", "结果2", 0.75, Document.Source.RERANKED));

        when(qdrantVectorStoreService.similaritySearch(eq(query), eq(tenantId), eq(20)))
                .thenReturn(vectorResults);
        when(bm25Service.search(eq(query), eq(tenantId), eq(20)))
                .thenReturn(bm25Results);
        when(rrfFusionStrategy.fuse(eq(vectorResults), eq(bm25Results)))
                .thenReturn(fusedResults);
        when(crossEncoderReranker.rerank(eq(query), eq(fusedResults), eq(topK)))
                .thenReturn(rerankedResults);

        // when
        List<Document> result = hybridRetriever.retrieve(query, tenantId, topK);

        // then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        // 验证重排收到的是完整融合结果(未截断, 因为fused.size() <= CANDIDATE_TOP_K)
        verify(crossEncoderReranker).rerank(eq(query), eq(fusedResults), eq(topK));
    }

    // ==================== 辅助方法 ====================

    /** 构建测试用 Document */
    private Document buildDoc(String id, String content, double score, Document.Source source) {
        return Document.builder()
                .id(id)
                .content(content)
                .score(score)
                .metadata(Map.of("tenantId", "test"))
                .source(source)
                .build();
    }
}
