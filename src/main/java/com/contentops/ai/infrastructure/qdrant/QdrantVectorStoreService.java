package com.contentops.ai.infrastructure.qdrant;

import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储服务抽象。
 *
 * <p>能力层（语义缓存）复用 retrieval 模块提供的实现：在指定 collection 上做
 * 向量近邻检索、点写入与按过期时间清理。</p>
 *
 * <p>说明：本接口属于 retrieval / infrastructure 模块的契约，此处提供接口定义
 * 以保证 {@code SemanticCacheService} / {@code CacheEvictionJob} 可独立编译；
 * 具体基于 Qdrant Java Client 的实现由 retrieval 模块提供。</p>
 */
public interface QdrantVectorStoreService {

    /**
     * 在指定 collection 中按 tenant_id 过滤做向量近邻检索。
     *
     * @param vector          查询向量
     * @param collection      Qdrant collection 名称（如 semantic_cache）
     * @param tenantId        租户标识（用于 payload 过滤）
     * @param topK            返回条数上限
     * @param scoreThreshold  相似度下限，低于该值的结果不返回
     * @return 命中结果列表（按分数降序）
     */
    List<VectorSearchHit> search(float[] vector, String collection, String tenantId,
                                 int topK, double scoreThreshold);

    /**
     * 写入/更新一个向量点。
     *
     * @param pointId     点 ID（建议 UUID）
     * @param vector      向量
     * @param collection  目标 collection
     * @param payload     元数据（如 tenant_id / query / answer / expires_at）
     */
    void upsert(String pointId, float[] vector, String collection, Map<String, Object> payload);

    /**
     * 删除指定 collection 中 expires_at 早于 {@code expiresBeforeEpochMillis} 的过期缓存条目。
     *
     * @param collection                目标 collection
     * @param expiresBeforeEpochMillis  过期时间阈值（epoch 毫秒）
     * @return 实际删除的条目数
     */
    long deleteExpired(String collection, long expiresBeforeEpochMillis);
}
