package com.contentops.ai.common.constant;

/**
 * AI 平台通用常量定义.
 *
 * <p>对齐系统设计文档中定义的 Agent 类型、消息类型、执行状态、A2A 协议等常量。</p>
 */
public final class AiConstants {

    private AiConstants() {
    }

    /** 链路追踪请求头名称 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** 多租户请求头名称 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** 模型调用最大重试次数 */
    public static final int MAX_RETRY = 3;

    /** 模型调用默认超时 (毫秒) */
    public static final long DEFAULT_TIMEOUT_MS = 60_000L;

    /** 语义缓存集合前缀 */
    public static final String SEM_CACHE_PREFIX = "semcache:";

    /**
     * Agent 类型常量 (对应系统设计文档 §4.9 六大 Agent).
     */
    public static final class AgentType {
        public static final String TOPIC_PLANNING    = "topic_planning";
        public static final String CONTENT_CREATION  = "content_creation";
        public static final String IMAGE_DESIGN      = "image_design";
        public static final String PUBLISH           = "publish";
        public static final String ANALYSIS          = "analysis";
        public static final String OPTIMIZE          = "optimize";

        private AgentType() {
        }
    }

    /**
     * 消息类型常量 (OpenAI Chat 消息角色).
     */
    public static final class MessageType {
        public static final String SYSTEM    = "system";
        public static final String USER      = "user";
        public static final String ASSISTANT = "assistant";
        public static final String TOOL      = "tool";

        private MessageType() {
        }
    }

    /**
     * Agent 执行状态常量 (对齐 agent_execution.status).
     */
    public static final class ExecutionStatus {
        public static final String RUNNING    = "running";
        public static final String COMPLETED  = "completed";
        public static final String FAILED     = "failed";
        public static final String TIMEOUT    = "timeout";

        private ExecutionStatus() {
        }
    }

    /**
     * A2A 消息类型常量 (对齐系统设计文档 §4.8).
     */
    public static final class A2AMessageType {
        /** 委托任务给另一个 Agent */
        public static final String TASK_DELEGATION = "task_delegation";
        /** 返回任务执行结果 */
        public static final String RESULT          = "result";
        /** 查询另一个 Agent 的状态 */
        public static final String QUERY           = "query";
        /** 广播消息给所有订阅者 */
        public static final String BROADCAST       = "broadcast";

        private A2AMessageType() {
        }
    }

    /**
     * A2A (Agent-to-Agent) 协议角色常量.
     */
    public static final class A2ARole {
        public static final String USER  = "user";
        public static final String AGENT = "agent";

        private A2ARole() {
        }
    }

    /**
     * 降级链层级常量.
     */
    public static final class FallbackLayer {
        public static final String PRIMARY = "primary";
        public static final String CACHE   = "cache";
        public static final String STATIC  = "static";

        private FallbackLayer() {
        }
    }
}
