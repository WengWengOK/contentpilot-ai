package com.contentops.ai.capability.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAGAS 评估报告数据类。
 * <p>
 * 汇聚四维评估中的三个量化指标(忠实度、回答相关性、上下文精确率),
 * 并携带 traceId 与时间戳以支持全链路追踪。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagasReport {

    /** 忠实度: answer 中的事实声明被 contexts 支持的比例, 范围 [0,1] */
    private double faithfulness;

    /** 回答相关性: answer 反向生成的问题与原始 query 的平均向量相似度, 范围 [0,1] */
    private double answerRelevancy;

    /** 上下文精确率: contexts 中对回答有用的文档比例, 范围 [0,1] */
    private double contextPrecision;

    /** 链路追踪ID */
    private String traceId;

    /** 评估时间 */
    private LocalDateTime timestamp;
}
