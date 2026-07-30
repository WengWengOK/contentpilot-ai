package com.contentops.ai.capability.retrieval;

import com.contentops.ai.infrastructure.qdrant.VectorSearchHit;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.Range;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

/**
 * Qdrant 向量检索服务。
 * <p>
 * 同时承担两个角色:
 * <ol>
 *   <li>能力层: 提供 {@link #similaritySearch} 高级语义检索(文本 -> 向量 -> Qdrant 检索 -> Document)</li>
 *   <li>基础设施层: 实现 {@link com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService} 接口,
 *       为语义缓存({@code SemanticCacheService})提供底层向量操作(search/upsert/deleteExpired)</li>
 * </ol>
 * <p>
 * 使用 Spring AI 的 {@link EmbeddingModel} 生成 query 向量, 通过 {@link QdrantClient} 检索,
 * 并以 payload 过滤 tenant_id 实现多租户隔离。
 * <p>
 * 注意: gRPC 消息类对应 qdrant-java 1.9.x (Spring AI 1.0.0-M1 传递依赖)。
 *
 * @see com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QdrantVectorStoreService
        implements com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService {

    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;

    @org.springframework.beans.factory.annotation.Value("${contentops.qdrant.collection:knowledge_vectors}")
    private String collectionName;

    /** Qdrant 检索读操作超时(秒), 防止 gRPC 调用无限阻塞拖垮检索线程 */
    private static final long SEARCH_TIMEOUT_SECONDS = 10L;
    /** Qdrant 写操作(upsert/delete)超时(秒) */
    private static final long WRITE_TIMEOUT_SECONDS = 15L;

    // ==================== 能力层: 高级语义检索 ====================

    /**
     * 向量相似度检索(能力层入口)。
     * <p>
     * 使用 EmbeddingModel 将 query 文本转为向量, 在 Qdrant 中检索并按 tenant_id 过滤。
     *
     * @param query    查询文本
     * @param tenantId 租户ID(payload 过滤)
     * @param topK     返回数量
     * @return 检索到的文档列表(按相似度降序), 失败时返回空列表
     */
    public List<Document> similaritySearch(String query, String tenantId, int topK) {
        try {
            // Spring AI 1.0.0 GA: EmbeddingModel.embed(String) 直接返回 float[]
            float[] queryVector = embeddingModel.embed(query);
            if (queryVector == null || queryVector.length == 0) {
                log.warn("Empty embedding for query, skipping vector search");
                return Collections.emptyList();
            }

            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(toFloatList(queryVector))
                    .setLimit(topK)
                    .setWithPayload(enable(true))
                    .setFilter(Filter.newBuilder()
                            .addMust(matchKeyword("tenant_id", tenantId))
                            .build())
                    .build();

            List<ScoredPoint> points = qdrantClient.searchAsync(searchPoints)
                    .get(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (points == null) {
                return Collections.emptyList();
            }
            return points.stream().map(this::toDocument).collect(Collectors.toList());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Qdrant vector search failed for tenant {}: {}", tenantId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ==================== 基础设施层: 接口实现 ====================

    /**
     * {@inheritDoc}
     * <p>
     * 在指定 collection 中按 tenant_id 过滤做向量近邻检索, 并应用相似度阈值。
     */
    @Override
    public List<VectorSearchHit> search(float[] vector, String collection, String tenantId,
                                        int topK, double scoreThreshold) {
        try {
            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .addAllVector(toFloatList(vector))
                    .setLimit(topK)
                    .setWithPayload(enable(true))
                    .setScoreThreshold((float) scoreThreshold)
                    .setFilter(Filter.newBuilder()
                            .addMust(matchKeyword("tenant_id", tenantId))
                            .build())
                    .build();

            List<ScoredPoint> points = qdrantClient.searchAsync(searchPoints)
                    .get(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (points == null) {
                return Collections.emptyList();
            }
            return points.stream().map(this::toVectorSearchHit).collect(Collectors.toList());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Qdrant search failed for collection {}: {}", collection, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 写入/更新一个向量点, payload 中的 Java 对象通过 {@link io.qdrant.client.ValueFactory} 转为 Qdrant Value。
     */
    @Override
    public void upsert(String pointId, float[] vector, String collection, Map<String, Object> payload) {
        try {
            Map<String, Value> valueMap = new HashMap<>();
            if (payload != null) {
                payload.forEach((k, v) -> {
                    if (v != null) {
                        valueMap.put(k, toQdrantValue(v));
                    }
                });
            }

            PointStruct point = PointStruct.newBuilder()
                    .setId(id(UUID.fromString(pointId)))
                    .setVectors(vectors(toFloatList(vector)))
                    .putAllPayload(valueMap)
                    .build();

            qdrantClient.upsertAsync(collection, List.of(point))
                    .get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Qdrant upsert success, collection={}, pointId={}", collection, pointId);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Qdrant upsert failed for collection {}: {}", collection, e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 通过 Range 过滤条件删除 expires_at 早于阈值的点。
     * 注意: Qdrant delete API 不返回删除数量, 此处返回 0 作为占位。
     */
    @Override
    public long deleteExpired(String collection, long expiresBeforeEpochMillis) {
        try {
            Condition rangeCondition = Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("expires_at")
                            .setRange(Range.newBuilder()
                                    .setLt((double) expiresBeforeEpochMillis)
                                    .build())
                            .build())
                    .build();

            Filter filter = Filter.newBuilder()
                    .addMust(rangeCondition)
                    .build();

            qdrantClient.deleteAsync(collection, filter).get(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Qdrant deleteExpired completed, collection={}, cutoff={}",
                    collection, expiresBeforeEpochMillis);
            return 0L;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Qdrant deleteExpired failed for collection {}: {}",
                    collection, e.getMessage(), e);
            return 0L;
        }
    }

    // ==================== 转换辅助方法 ====================

    /**
     * 将 ScoredPoint 转为能力层 Document。
     * 兼容 content / doc_content 两种内容字段名。
     */
    private Document toDocument(ScoredPoint point) {
        Map<String, Object> metadata = payloadToMap(point.getPayloadMap());
        String content = extractContent(metadata);
        String docId = metadata.containsKey("doc_id")
                ? String.valueOf(metadata.get("doc_id"))
                : extractPointId(point);

        return Document.builder()
                .id(docId)
                .content(content)
                .score(point.getScore())
                .metadata(metadata)
                .source(Document.Source.VECTOR)
                .build();
    }

    /**
     * 将 ScoredPoint 转为基础设施层 VectorSearchHit。
     */
    private VectorSearchHit toVectorSearchHit(ScoredPoint point) {
        Map<String, Object> payload = payloadToMap(point.getPayloadMap());
        return new VectorSearchHit(extractPointId(point), point.getScore(), payload);
    }

    /**
     * 将 Qdrant payload Map 转为 Java Map, 递归处理嵌套 Struct 和 ListValue。
     * 逻辑参照 Spring AI QdrantObjectFactory。
     */
    private Map<String, Object> payloadToMap(Map<String, Value> payload) {
        Map<String, Object> map = new HashMap<>();
        if (payload != null) {
            payload.forEach((key, val) -> map.put(key, toJavaObject(val)));
        }
        return map;
    }

    /**
     * 递归将 Qdrant JsonWithInt.Value 转为 Java 对象。
     */
    private Object toJavaObject(Value value) {
        if (value == null) {
            return null;
        }
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                Map<String, Object> map = new HashMap<>();
                value.getStructValue().getFieldsMap()
                        .forEach((k, v) -> map.put(k, toJavaObject(v)));
                return map;
            case LIST_VALUE:
                return value.getListValue().getValuesList().stream()
                        .map(this::toJavaObject)
                        .collect(Collectors.toList());
            case NULL_VALUE:
            case KIND_NOT_SET:
            default:
                return null;
        }
    }

    /**
     * 将 Java 对象转为 Qdrant Value, 使用 ValueFactory 静态工厂方法。
     */
    private Value toQdrantValue(Object obj) {
        if (obj instanceof String s) {
            return value(s);
        } else if (obj instanceof Number n) {
            if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
                return value(n.longValue());
            } else {
                return value(n.doubleValue());
            }
        } else if (obj instanceof Boolean b) {
            return value(b);
        } else {
            return value(String.valueOf(obj));
        }
    }

    /**
     * 从 payload 中提取文档内容, 兼容 "content" 和 Spring AI 默认的 "doc_content" 字段名。
     */
    private String extractContent(Map<String, Object> metadata) {
        if (metadata.containsKey("content")) {
            return String.valueOf(metadata.get("content"));
        }
        if (metadata.containsKey("doc_content")) {
            return String.valueOf(metadata.get("doc_content"));
        }
        return "";
    }

    /**
     * 从 ScoredPoint 中提取点 ID, 兼容 UUID 和数字两种格式。
     */
    private String extractPointId(ScoredPoint point) {
        try {
            var pointId = point.getId();
            String uuid = pointId.getUuid();
            if (uuid != null && !uuid.isEmpty()) {
                return uuid;
            }
            return String.valueOf(pointId.getNum());
        } catch (Exception e) {
            log.debug("Failed to extract pointId from ScoredPoint: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * 将 float[] 转为 List<Float>, 供 Qdrant gRPC API 使用。
     */
    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }
}
