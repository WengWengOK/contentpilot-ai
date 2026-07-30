package com.contentops.ai.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * A2A (Agent-to-Agent) 协议消息.
 *
 * <p>用于平台内部 Agent 之间、以及与外部 Agent 服务之间的结构化通信。
 * 字段对齐系统设计文档 §4.8 定义的消息格式:
 * messageId / fromAgent / toAgent / messageType / payload / correlationId / timestamp。</p>
 *
 * <pre>
 * {
 *   "messageId": "uuid-v4",
 *   "fromAgent": "analysis-agent",
 *   "toAgent": "optimize-agent",
 *   "messageType": "task_delegation",
 *   "payload": { "task": "adjust_topic_strategy", "data": { ... } },
 *   "correlationId": "execution-trace-id",
 *   "timestamp": "2026-07-30T10:00:00Z"
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class A2AMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一 ID (uuid-v4) */
    private String messageId;

    /** 租户 ID (多租户路由用) */
    private Long tenantId;

    /** 发送方 Agent 标识 */
    private String fromAgent;

    /** 接收方 Agent 标识 */
    private String toAgent;

    /** 消息类型: task_delegation / result / query / broadcast */
    private String messageType;

    /** 消息负载 (任务名、数据等) */
    private Map<String, Object> payload;

    /** 关联 ID (与 agent_execution.trace_id 对齐, 用于链路串联) */
    private String correlationId;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 消息时间戳 */
    private LocalDateTime timestamp;
}
