package com.contentops.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Agent 统一响应 DTO.
 *
 * <p>由 {@link BaseAgent#run} 模板方法封装, 携带业务数据与执行元信息(模型/缓存/Token/评估)。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {

    /** 业务数据 */
    private Object data;

    /** 实际使用的模型名 */
    private String modelUsed;

    /** 是否命中语义缓存 */
    private boolean cacheHit;

    /** 本次执行消耗的 Token 数 (估算) */
    private int tokensUsed;

    /** 链路追踪 ID */
    private String traceId;

    /** RAGAS 评估指标 (仅检索增强场景填充) */
    private Map<String, Double> evaluation;

    /**
     * 构造缓存命中响应.
     *
     * @param data    缓存数据
     * @param traceId 链路追踪 ID
     * @return 缓存命中响应
     */
    public static AgentResponse cached(Object data, String traceId) {
        return AgentResponse.builder()
                .data(data)
                .cacheHit(true)
                .tokensUsed(0)
                .traceId(traceId)
                .build();
    }

    /**
     * 构造正常执行成功响应.
     *
     * @param data       业务数据
     * @param modelUsed  使用的模型名
     * @param tokensUsed 消耗 Token 数
     * @param traceId    链路追踪 ID
     * @param evaluation RAGAS 评估指标 (无评估时传 null)
     * @return 执行成功响应
     */
    public static AgentResponse success(Object data, String modelUsed, int tokensUsed,
                                        String traceId, Map<String, Double> evaluation) {
        return AgentResponse.builder()
                .data(data)
                .modelUsed(modelUsed)
                .cacheHit(false)
                .tokensUsed(tokensUsed)
                .traceId(traceId)
                .evaluation(evaluation)
                .build();
    }
}
