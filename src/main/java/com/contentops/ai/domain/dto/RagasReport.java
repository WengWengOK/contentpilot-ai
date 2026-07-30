package com.contentops.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * RAGAS 评估报告 DTO.
 *
 * <p>对应 GET /api/v1/evaluation/report 接口的返回结构。
 * 四维评估指标对齐系统设计文档 §4.2 (含 context recall 维度)。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagasReport {

    /** 关联的 Agent 执行 ID (字符串业务键, 对齐 agent_execution.execution_id) */
    private String executionId;

    /** 忠实度 (生成内容是否忠于上下文) */
    private BigDecimal faithfulness;

    /** 答案相关性 */
    private BigDecimal answerRelevancy;

    /** 上下文精确率 */
    private BigDecimal contextPrecision;

    /** 上下文召回率 */
    private BigDecimal contextRecall;

    /** 综合得分 */
    private BigDecimal overallScore;

    /** 详细评估数据 (分项样本 / token 等) */
    private Map<String, Object> detail;

    /** 可读性总结 */
    private String summary;
}
