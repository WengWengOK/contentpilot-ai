package com.contentops.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Agent 统一请求 DTO.
 *
 * <p>由 API 控制器层构造, 传递给 {@link BaseAgent#run}。承载查询文本、租户/用户标识、
 * 业务参数与链路追踪 ID。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRequest {

    /** 查询文本 (同时作为语义缓存的 key) */
    private String query;

    /** 租户标识 (租户编码, 用于能力层多租户隔离) */
    private String tenantId;

    /** 用户标识 */
    private String userId;

    /** 业务参数 (由各 Controller 按场景填充) */
    private Map<String, Object> params;

    /** 链路追踪 ID (为空时由 BaseAgent 自动生成) */
    private String traceId;
}
