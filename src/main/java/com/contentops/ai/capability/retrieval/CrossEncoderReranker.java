package com.contentops.ai.capability.retrieval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cross-Encoder 重排器。
 * <p>
 * 调用本地部署的 bge-reranker-v2-m3 模型 API(POST /rerank), 入参 {query, documents},
 * 返回 scores, 按重排分数降序取 TopK。
 * <p>
 * 容错: 若 rerank 服务不可用或响应异常, 降级为直接返回原始前 TopK 个结果并记录 warn 日志,
 * 保证检索链路不被 rerank 故障中断。
 */
@Slf4j
@Service
public class CrossEncoderReranker {

    private final RestTemplate restTemplate;

    @Value("${contentops.rerank.endpoint:http://localhost:8000/rerank}")
    private String rerankEndpoint;

    public CrossEncoderReranker(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 重排文档。
     *
     * @param query     查询文本
     * @param documents 待重排文档
     * @param topK      返回数量
     * @return 按重排分数降序的 TopK 文档; 服务不可用时降级返回原始前 TopK
     */
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        // topK 非法(<=0)时直接返回空, 避免 stream().limit 负数抛 IllegalArgumentException
        int safeTopK = Math.max(0, topK);
        if (safeTopK == 0) {
            return List.of();
        }
        try {
            // 过滤掉 content 为 null 的文档, 避免向 rerank 服务发送 null 文本; 同时复用原始对象以便回填分数
            List<Document> validDocs = documents.stream()
                    .filter(d -> d != null && d.getContent() != null && !d.getContent().isBlank())
                    .toList();
            if (validDocs.isEmpty()) {
                return fallback(documents, safeTopK);
            }

            List<String> docTexts = validDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.toList());

            // Map.of 不允许 null value, query 需防御 null
            Map<String, Object> request = Map.of(
                    "query", query == null ? "" : query,
                    "documents", docTexts);

            ResponseEntity<RerankResponse> response = restTemplate.postForEntity(
                    rerankEndpoint, request, RerankResponse.class);
            RerankResponse body = response.getBody();

            if (body == null || body.scores() == null || body.scores().size() != validDocs.size()) {
                log.warn("Rerank response invalid (scores size mismatch, expected={}, actual={}), falling back to original top-{}",
                        validDocs.size(), body == null || body.scores() == null ? 0 : body.scores().size(), safeTopK);
                return fallback(documents, safeTopK);
            }

            List<Double> scores = body.scores();
            List<Document> reranked = new ArrayList<>();
            for (int i = 0; i < validDocs.size(); i++) {
                Document original = validDocs.get(i);
                reranked.add(original.toBuilder()
                        .score(scores.get(i))
                        .source(Document.Source.RERANKED)
                        .build());
            }
            reranked.sort(Comparator.comparingDouble(Document::getScore).reversed());
            return reranked.stream().limit(safeTopK).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Cross-Encoder rerank service unavailable, falling back to original top-{} results: {}",
                    safeTopK, e.getMessage(), e);
            return fallback(documents, safeTopK);
        }
    }

    private List<Document> fallback(List<Document> documents, int topK) {
        int safeTopK = Math.max(0, topK);
        return documents.stream().limit(safeTopK).collect(Collectors.toList());
    }

    /**
     * rerank 模型响应体: {"scores": [0.9, 0.8, ...]}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(List<Double> scores) {
    }
}
