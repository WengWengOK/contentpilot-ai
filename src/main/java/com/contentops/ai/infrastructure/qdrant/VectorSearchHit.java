package com.contentops.ai.infrastructure.qdrant;

import java.util.Map;

/**
 * 向量检索命中结果。
 *
 * @param id      点 ID
 * @param score   相似度分数
 * @param payload 点元数据
 */
public record VectorSearchHit(String id, double score, Map<String, Object> payload) {
}
