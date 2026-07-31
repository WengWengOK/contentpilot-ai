package com.contentops.ai.capability.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RRF(Reciprocal Rank Fusion) 融合策略。
 * <p>
 * RRF 只依赖各路检索的排名, 不依赖原始分数, 天然解决向量相似度与 BM25 分数量纲不一致的问题。
 * <pre>
 * 公式: score(d) = Σ_{i=1}^{n} 1 / (k + rank_i(d))
 *   n       = 检索路数(向量 + BM25 = 2)
 *   k       = 平滑参数(默认60, 控制排名靠后结果的权重衰减)
 *   rank_i  = 文档d在第i路检索中的排名(从1开始)
 * </pre>
 */
@Slf4j
@Component
public class RRFFusionStrategy {

    /** 默认平滑参数 */
    public static final int DEFAULT_K = 60;

    /**
     * 融合两路检索结果。
     *
     * @param vectorResults 向量检索结果(已按相关性降序)
     * @param bm25Results   BM25 检索结果(已按相关性降序)
     * @param k             RRF 平滑参数
     * @return 融合后按融合分数降序排列的文档
     */
    public List<Document> fuse(List<Document> vectorResults, List<Document> bm25Results, int k) {
        int safeK = k <= 0 ? DEFAULT_K : k;

        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, Document> docById = new HashMap<>();

        accumulate(vectorResults, safeK, fusedScores, docById);
        accumulate(bm25Results, safeK, fusedScores, docById);

        List<Document> fused = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    Document original = docById.get(e.getKey());
                    return original.toBuilder()
                            .score(e.getValue())
                            .source(Document.Source.FUSED)
                            .build();
                })
                .collect(Collectors.toList());

        log.debug("RRF fused {} documents (vector={}, bm25={}, k={})",
                fused.size(), sizeOf(vectorResults), sizeOf(bm25Results), safeK);
        return fused;
    }

    /** 使用默认 k=60 融合 */
    public List<Document> fuse(List<Document> vectorResults, List<Document> bm25Results) {
        return fuse(vectorResults, bm25Results, DEFAULT_K);
    }

    private void accumulate(List<Document> results, int k,
                            Map<String, Double> scores, Map<String, Document> docById) {
        if (results == null) {
            return;
        }
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            if (doc == null || doc.getId() == null) {
                continue;
            }
            int rank = i + 1;
            double contribution = 1.0 / (k + rank);
            scores.merge(doc.getId(), contribution, Double::sum);
            docById.putIfAbsent(doc.getId(), doc);
        }
    }

    private int sizeOf(List<Document> list) {
        return list == null ? 0 : list.size();
    }
}
