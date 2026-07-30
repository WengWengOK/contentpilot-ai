package com.contentops.ai.capability.retrieval;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 混合检索核心编排类。
 * <p>
 * 编排全流程: 向量检索 + BM25 检索(并行) -> RRF 融合 -> Cross-Encoder 重排 -> 取 TopK。
 * <pre>
 * 输入: query, tenantId, topK
 *   ├── 1. 向量检索: EmbeddingModel -> Qdrant similaritySearch(topK=20, filter=tenant_id)
 *   ├── 2. BM25检索: PostgreSQL ts_vector 查询(topK=20, filter=tenant_id)
 *   ├── 3. RRF融合: score(d) = Σ 1/(k + rank_i), k=60
 *   └── 4. Cross-Encoder Rerank: bge-reranker-v2-m3 -> 取 topK
 * 输出: List&lt;Document&gt; (topK条, 按rerank分数降序)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    /** 候选召回数量(向量与BM25各召回20条) */
    private static final int CANDIDATE_TOP_K = 20;
    /** 并行检索等待超时(秒), 防止单路检索阻塞拖垮整体流程 */
    private static final long PARALLEL_TIMEOUT_SECONDS = 15L;

    /** 专用检索线程池: 检索为阻塞 I/O(gRPC/DB), 不应复用 ForkJoinPool.commonPool 避免饿死 CPU 密集任务 */
    private final ExecutorService retrievalExecutor =
            Executors.newFixedThreadPool(8, new RetrievalThreadFactory());

    private final QdrantVectorStoreService qdrantVectorStoreService;
    private final BM25Service bm25Service;
    private final RRFFusionStrategy rrfFusionStrategy;
    private final CrossEncoderReranker crossEncoderReranker;

    /**
     * 混合检索入口。
     *
     * @param query    查询文本
     * @param tenantId 租户ID
     * @param topK     最终返回数量
     * @return 重排后的 TopK 文档(按 rerank 分数降序)
     */
    public List<Document> retrieve(String query, String tenantId, int topK) {
        log.debug("Hybrid retrieve start, query='{}', tenantId={}, topK={}", query, tenantId, topK);

        // 1. 并行执行向量检索与 BM25 检索(使用专用线程池, 避免 ForkJoinPool 阻塞)
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
                () -> safeVectorSearch(query, tenantId), retrievalExecutor);
        CompletableFuture<List<Document>> bm25Future = CompletableFuture.supplyAsync(
                () -> safeBm25Search(query, tenantId), retrievalExecutor);

        List<Document> vectorResults;
        List<Document> bm25Results;
        try {
            // 带超时等待, 防止单路检索无限阻塞
            CompletableFuture.allOf(vectorFuture, bm25Future)
                    .get(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            vectorResults = vectorFuture.get();
            bm25Results = bm25Future.get();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("并行检索等待异常或超时, 降级为单路结果合并: {}", e.getMessage(), e);
            // 取消可能仍在阻塞的 future, 释放线程; mayInterruptIfRunning=true 以中断阻塞 I/O
            vectorFuture.cancel(true);
            bm25Future.cancel(true);
            // 分别尝试取已就绪的结果, 失败的路用空列表兜底(不再串行重试, 避免重复调用放大下游压力)
            vectorResults = safeJoin(vectorFuture);
            bm25Results = safeJoin(bm25Future);
        }

        log.debug("Retrieved candidates: vector={}, bm25={}", sizeOf(vectorResults), sizeOf(bm25Results));

        // 2. RRF 融合两路结果
        List<Document> fused = rrfFusionStrategy.fuse(vectorResults, bm25Results);

        // 3. 限制重排候选规模(取融合后前 CANDIDATE_TOP_K 条), 降低 rerank 成本
        List<Document> candidates = fused.size() > CANDIDATE_TOP_K
                ? fused.subList(0, CANDIDATE_TOP_K) : fused;

        // 4. Cross-Encoder 精排, 取 TopK
        List<Document> reranked = crossEncoderReranker.rerank(query, candidates, topK);

        log.debug("Hybrid retrieve done, returned {} documents", reranked.size());
        return reranked;
    }

    /** 安全 join: 已完成则取结果, 未完成/异常则返回空列表 */
    private List<Document> safeJoin(CompletableFuture<List<Document>> future) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            try {
                List<Document> r = future.getNow(List.of());
                return r == null ? List.of() : r;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private List<Document> safeVectorSearch(String query, String tenantId) {
        try {
            return qdrantVectorStoreService.similaritySearch(query, tenantId, CANDIDATE_TOP_K);
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Document> safeBm25Search(String query, String tenantId) {
        try {
            return bm25Service.search(query, tenantId, CANDIDATE_TOP_K);
        } catch (Exception e) {
            log.warn("BM25 search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private int sizeOf(List<Document> list) {
        return list == null ? 0 : list.size();
    }

    /**
     * 关闭检索线程池, 释放线程资源。
     */
    @PreDestroy
    public void shutdown() {
        retrievalExecutor.shutdown();
        try {
            if (!retrievalExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retrievalExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            retrievalExecutor.shutdownNow();
        }
    }

    /** 检索线程命名工厂, 便于线程 dump 排查 */
    private static final class RetrievalThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "hybrid-retrieval-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
