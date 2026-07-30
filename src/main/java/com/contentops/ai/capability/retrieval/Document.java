package com.contentops.ai.capability.retrieval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 检索结果文档模型。
 * <p>
 * 统一表达向量检索、BM25 检索、RRF 融合、Cross-Encoder 重排各阶段的产物，
 * 通过 {@link Source} 标识当前文档所处的检索阶段。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /** 文档唯一标识 */
    private String id;

    /** 文档正文内容 */
    private String content;

    /** 检索 / 融合 / 重排分数 */
    private double score;

    /** 文档元数据(来源、标题、租户等) */
    private Map<String, Object> metadata;

    /** 结果来源阶段 */
    private Source source;

    /**
     * 检索结果来源枚举。
     */
    public enum Source {
        /** 向量检索阶段 */
        VECTOR,
        /** BM25 关键词检索阶段 */
        BM25,
        /** RRF 融合后 */
        FUSED,
        /** Cross-Encoder 重排后 */
        RERANKED
    }
}
