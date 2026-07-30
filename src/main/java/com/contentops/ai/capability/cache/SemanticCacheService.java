package com.contentops.ai.capability.cache;

import com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService;
import com.contentops.ai.infrastructure.qdrant.VectorSearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 语义缓存核心服务。
 *
 * <p>通过向量相似度判断请求是否与历史请求语义相同，命中则直接返回缓存结果，
 * 避免重复调用 LLM。底层使用 Qdrant 的 {@code semantic_cache} collection，
 * 检索/写入均通过 {@link QdrantVectorStoreService}（复用 retrieval 模块）完成。</p>
 *
 * <p>命中判定流程：
 * <ol>
 *   <li>将 query 通过 {@link EmbeddingModel} 生成 embedding</li>
 *   <li>在 Qdrant semantic_cache 中检索（filter: tenant_id, score &gt;= 阈值）</li>
 *   <li>命中 -> 检查 payload.expires_at -> 未过期则返回缓存的 answer</li>
 *   <li>未命中 -> 由调用方正常调用 LLM 后调用 {@link #put} 写入缓存</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    /** Qdrant 专用 collection */
    public static final String COLLECTION = "semantic_cache";

    /** payload 中各字段 key */
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_QUERY = "query";
    private static final String FIELD_ANSWER = "answer";
    private static final String FIELD_EXPIRES_AT = "expires_at";

    private final QdrantVectorStoreService vectorStoreService;
    private final EmbeddingModel embeddingModel;

    /** 相似度阈值，高于此值判定为命中（默认 0.92，从配置读取） */
    @Value("${contentops.cache.similarity-threshold:0.92}")
    private double similarityThreshold;

    /** 缓存 TTL，单位小时（默认 24） */
    @Value("${contentops.cache.ttl-hours:24}")
    private long ttlHours;

    /**
     * 检查缓存命中。
     *
     * @param query    用户查询
     * @param tenantId 租户标识
     * @return 命中的缓存答案；未命中或异常时返回 {@link Optional#empty()}
     */
    public Optional<String> get(String query, String tenantId) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            // Spring AI 1.0.0 GA: EmbeddingModel.embed(String) 直接返回 float[]
            float[] vector = embeddingModel.embed(query);
            if (vector == null || vector.length == 0) {
                log.warn("Empty embedding for query, semantic cache skipped, tenantId={}", tenantId);
                return Optional.empty();
            }
            List<VectorSearchHit> hits = vectorStoreService.search(
                    vector, COLLECTION, tenantId, 1, similarityThreshold);
            if (hits == null || hits.isEmpty()) {
                return Optional.empty();
            }
            VectorSearchHit top = hits.get(0);
            Map<String, Object> payload = top.payload();
            if (payload == null) {
                return Optional.empty();
            }
            // 过期检查：expires_at < now 视为过期
            long now = System.currentTimeMillis();
            Object expiresAtRaw = payload.get(FIELD_EXPIRES_AT);
            long expiresAt = toEpochMillis(expiresAtRaw);
            if (expiresAt > 0 && expiresAt < now) {
                log.debug("语义缓存命中但已过期, tenantId={}, score={}", tenantId, top.score());
                return Optional.empty();
            }
            Object answer = payload.get(FIELD_ANSWER);
            if (answer == null) {
                return Optional.empty();
            }
            log.debug("语义缓存命中, tenantId={}, score={}", tenantId, top.score());
            return Optional.of(answer.toString());
        } catch (Exception e) {
            log.warn("语义缓存读取异常, tenantId={}: {}", tenantId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 写入缓存。
     *
     * @param query    用户查询
     * @param answer   LLM 答案
     * @param tenantId 租户标识
     */
    public void put(String query, String answer, String tenantId) {
        if (query == null || query.isBlank() || answer == null) {
            return;
        }
        try {
            // Spring AI 1.0.0 GA: EmbeddingModel.embed(String) 直接返回 float[]
            float[] vector = embeddingModel.embed(query);
            if (vector == null || vector.length == 0) {
                log.warn("Empty embedding for query, semantic cache write skipped, tenantId={}", tenantId);
                return;
            }
            long expiresAt = System.currentTimeMillis() + ttlHours * 3600_000L;
            Map<String, Object> payload = Map.of(
                    FIELD_TENANT_ID, tenantId == null ? "" : tenantId,
                    FIELD_QUERY, query,
                    FIELD_ANSWER, answer,
                    FIELD_EXPIRES_AT, expiresAt
            );
            String pointId = UUID.randomUUID().toString();
            vectorStoreService.upsert(pointId, vector, COLLECTION, payload);
            log.debug("语义缓存写入, tenantId={}, pointId={}", tenantId, pointId);
        } catch (Exception e) {
            // 缓存写入失败不应影响主流程
            log.warn("语义缓存写入异常, tenantId={}: {}", tenantId, e.getMessage(), e);
        }
    }

    /** payload 中的 expires_at 可能是 Number 或 String，统一转为 epoch 毫秒 */
    private long toEpochMillis(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }
}
