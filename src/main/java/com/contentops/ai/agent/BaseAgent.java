package com.contentops.ai.agent;

import com.contentops.ai.agent.checkpoint.AgentCheckpointService;
import com.contentops.ai.agent.checkpoint.AgentExecutionService;
import com.contentops.ai.capability.cache.SemanticCacheService;
import com.contentops.ai.capability.evaluation.RagasEvaluator;
import com.contentops.ai.capability.evaluation.RagasReport;
import com.contentops.ai.capability.ratelimit.TokenLimiter;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.common.util.TraceUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 抽象基类 — 模板方法模式.
 *
 * <p>封装所有 Agent 共用的横切编排逻辑, 子类只需实现 {@link #execute} 聚焦业务。
 * {@link #run} 为模板方法, 按固定顺序编排完整流程:</p>
 *
 * <ol>
 *   <li>生成 / 复用 traceId ({@link TraceUtil})</li>
 *   <li>检查语义缓存 ({@link SemanticCacheService#get})</li>
 *   <li>缓存命中则直接返回</li>
 *   <li>检查 Token 配额 ({@link TokenLimiter#tryConsume})</li>
 *   <li>开始执行记录 ({@link AgentExecutionService#startExecution})</li>
 *   <li>保存 checkpoint ({@link AgentCheckpointService#saveCheckpoint})</li>
 *   <li>调用子类 {@link #execute}</li>
 *   <li>RAGAS 评估 (检索增强场景, {@link #supportsRagEvaluation})</li>
 *   <li>完成执行记录 ({@link AgentExecutionService#completeExecution})</li>
 *   <li>写入语义缓存 ({@link SemanticCacheService#put})</li>
 *   <li>返回 {@link AgentResponse}</li>
 * </ol>
 *
 * <p>共享依赖通过字段注入, 便于子类用 {@code @RequiredArgsConstructor} 注入各自依赖。
 * 子类在 execute 期间可通过 {@link #setModelUsed} / {@link #setTokensUsed} /
 * {@link #setRagContexts} / {@link #setAnswerText} 回传执行元信息,
 * 通过 ThreadLocal 保证多线程安全。</p>
 */
@Slf4j
@Component
public abstract class BaseAgent {

    @Autowired
    protected SemanticCacheService semanticCacheService;

    @Autowired
    protected TokenLimiter tokenLimiter;

    @Autowired
    protected AgentExecutionService executionService;

    @Autowired
    protected AgentCheckpointService checkpointService;

    @Autowired
    protected RagasEvaluator ragasEvaluator;

    @Autowired
    protected ObjectMapper objectMapper;

    // ==================== 子类需实现的抽象方法 ====================

    /**
     * 返回 Agent 类型标识 (对齐 {@link AiConstants.AgentType})。
     *
     * @return Agent 类型
     */
    public abstract String getAgentType();

    /**
     * 执行 Agent 核心业务逻辑 (由子类实现)。
     *
     * <p>子类应将受检异常包装为 RuntimeException (如 {@code BusinessException}) 抛出,
     * 以便 {@link #run} 的异常处理与全局异常处理器正确路由。</p>
     *
     * @param request Agent 请求
     * @return 业务结果
     */
    protected abstract Object execute(AgentRequest request);

    // ==================== 子类可覆盖的钩子 ====================

    /**
     * 是否启用 RAGAS 评估 (检索增强场景覆盖为 true)。
     *
     * @return 默认 false
     */
    protected boolean supportsRagEvaluation() {
        return false;
    }

    /**
     * 是否启用语义缓存。
     *
     * <p>有副作用的 Agent(如发布动作、生成临时图片 URL)应覆盖为 false:
     * <ul>
     *   <li>发布类 Agent 命中缓存会跳过真实发布, 返回历史 postId, 造成业务错误;</li>
     *   <li>图片 URL 类结果通常有时效性, 缓存后可能返回已失效链接。</li>
     * </ul>
     * 默认 true(纯文本生成场景可安全缓存)。</p>
     *
     * @return 默认 true
     */
    protected boolean supportsSemanticCache() {
        return true;
    }

    /**
     * 估算本次请求消耗的 Token 数 (用于配额扣减)。
     *
     * @param request Agent 请求
     * @return 估算 Token 数
     */
    protected int estimateTokens(AgentRequest request) {
        int chars = request.getQuery() == null ? 0 : request.getQuery().length();
        // 粗略估算: 输入 ~4 字符/token, 预留 1024 token 输出
        return Math.max(256, chars / 4 + 1024);
    }

    /**
     * 截断文本到指定长度(超出追加省略号), 公共工具方法供子类构建 prompt 复用。
     *
     * @param s   原始文本
     * @param max 最大字符数
     * @return 截断后的文本
     */
    protected static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ==================== 模板方法 ====================

    /**
     * 模板方法: 编排完整 Agent 执行流程。
     *
     * <p>子类抛出的业务异常应为 {@link RuntimeException} (如 {@code QuotaExceededException} /
     * {@code StructuredOutputException}), 由 {@code GlobalExceptionHandler} 统一处理并路由为合适的 HTTP 状态码。</p>
     *
     * @param request Agent 请求
     * @return Agent 响应
     */
    public final AgentResponse run(AgentRequest request) {
        // 1. 生成 / 复用 traceId
        String traceId = (request.getTraceId() != null && !request.getTraceId().isBlank())
                ? request.getTraceId() : TraceUtil.generateTraceId();
        request.setTraceId(traceId);
        String tenantId = resolveTenantId(request);

        try {
            // 2 & 3. 语义缓存检查, 命中直接返回 (有副作用的 Agent 跳过缓存)
            if (supportsSemanticCache()) {
                Optional<String> cached = semanticCacheService.get(request.getQuery(), tenantId);
                if (cached.isPresent()) {
                    log.info("[{}] 语义缓存命中, traceId={}", getAgentType(), traceId);
                    Object cachedData = deserializeSafely(cached.get());
                    return AgentResponse.cached(cachedData, traceId);
                }
            }

            // 4. 检查 Token 配额 (估算 tokens)
            int estimatedTokens = estimateTokens(request);
            tokenLimiter.tryConsume(tenantId, estimatedTokens);

            // 5. 开始执行记录
            Long tenantIdLong = resolveTenantIdLong(tenantId);
            executionService.startExecution(traceId, tenantIdLong, getAgentType(),
                    buildInput(request), traceId);

            // 6. 保存 checkpoint
            checkpointService.saveCheckpoint(getAgentType(), traceId, Map.of(
                    "phase", "execute",
                    "query", safeQuery(request),
                    "tenantId", tenantId));

            // 7. 调用子类 execute
            Object result = execute(request);

            // 8. RAGAS 评估 (检索增强场景)
            Map<String, Double> evaluation = Collections.emptyMap();
            if (supportsRagEvaluation()) {
                evaluation = runRagasEvaluation(request, traceId);
            }

            // 9. 完成执行记录
            String modelUsed = MODEL_USED.get();
            int tokensUsed = TOKENS_USED.get() != null ? TOKENS_USED.get() : estimatedTokens;
            executionService.completeExecution(traceId, AiConstants.ExecutionStatus.COMPLETED,
                    buildOutput(result), tokensUsed, modelUsed);

            // 10. 写入语义缓存: 仅对支持缓存的 Agent, 且未发生模板兜底降级时写入, 避免缓存污染
            if (supportsSemanticCache() && Boolean.TRUE.equals(CACHEABLE.get())) {
                String answerJson = ANSWER_TEXT.get();
                if (answerJson == null) {
                    answerJson = serializeSafely(result);
                }
                semanticCacheService.put(request.getQuery(), answerJson, tenantId);
            }

            // 11. 返回结果
            log.info("[{}] 执行完成, traceId={}, cacheHit=false, tokensUsed={}, model={}",
                    getAgentType(), traceId, tokensUsed, modelUsed);
            return AgentResponse.success(result, modelUsed, tokensUsed, traceId, evaluation);
        } catch (RuntimeException e) {
            log.error("[{}] 执行失败, traceId={}: {}", getAgentType(), traceId, e.getMessage(), e);
            String failedModel = MODEL_USED.get();
            executionService.completeExecution(traceId, AiConstants.ExecutionStatus.FAILED,
                    Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage()),
                    0, failedModel);
            throw e;
        } finally {
            // 清理 ThreadLocal, 防止线程复用导致的串号
            MODEL_USED.remove();
            TOKENS_USED.remove();
            RAG_CONTEXTS.remove();
            ANSWER_TEXT.remove();
            CACHEABLE.remove();
        }
    }

    // ==================== 子类回传执行元信息的 ThreadLocal 钩子 ====================

    /** 当前执行使用的模型名 */
    private static final ThreadLocal<String> MODEL_USED = new ThreadLocal<>();
    /** 当前执行消耗的 Token 数 */
    private static final ThreadLocal<Integer> TOKENS_USED = new ThreadLocal<>();
    /** 当前执行检索到的上下文 (RAGAS 评估用) */
    private static final ThreadLocal<List<String>> RAG_CONTEXTS = new ThreadLocal<>();
    /** 当前执行生成的答案文本 (RAGAS 评估 / 缓存写入用) */
    private static final ThreadLocal<String> ANSWER_TEXT = new ThreadLocal<>();
    /** 当前执行结果是否可写入语义缓存 (模板兜底等降级结果应置为 false, 避免缓存污染) */
    private static final ThreadLocal<Boolean> CACHEABLE = ThreadLocal.withInitial(() -> Boolean.TRUE);

    /**
     * 子类在 execute 中调用: 设置实际使用的模型名。
     *
     * @param modelUsed 模型名
     */
    protected void setModelUsed(String modelUsed) {
        MODEL_USED.set(modelUsed);
    }

    /**
     * 子类在 execute 中调用: 设置实际消耗的 Token 数。
     *
     * @param tokensUsed Token 数
     */
    protected void setTokensUsed(int tokensUsed) {
        TOKENS_USED.set(tokensUsed);
    }

    /**
     * 子类在 execute 中调用: 设置 RAGAS 评估用的检索上下文。
     *
     * @param contexts 上下文列表
     */
    protected void setRagContexts(List<String> contexts) {
        RAG_CONTEXTS.set(contexts);
    }

    /**
     * 子类在 execute 中调用: 设置 RAGAS 评估答案文本 (同时作为缓存写入值)。
     *
     * @param answer 答案文本 (通常为 LLM 原始输出)
     */
    protected void setAnswerText(String answer) {
        ANSWER_TEXT.set(answer);
    }

    /**
     * 子类在 execute 中调用: 标记本次结果不可缓存(如模型降级链走到模板兜底文案,
     * 缓存兜底文案会污染语义缓存, 后续命中返回无意义内容)。
     *
     * @param cacheable 是否可缓存
     */
    protected void setCacheable(boolean cacheable) {
        CACHEABLE.set(cacheable);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 执行 RAGAS 评估, 返回三维指标 map。
     */
    private Map<String, Double> runRagasEvaluation(AgentRequest request, String traceId) {
        List<String> contexts = RAG_CONTEXTS.get();
        if (contexts == null || contexts.isEmpty()) {
            return Collections.emptyMap();
        }
        String answer = ANSWER_TEXT.get();
        if (answer == null) {
            answer = "";
        }
        try {
            RagasReport report = ragasEvaluator.evaluate(
                    request.getQuery(), answer, contexts, traceId);
            Map<String, Double> evaluation = new LinkedHashMap<>();
            evaluation.put("faithfulness", report.getFaithfulness());
            evaluation.put("answerRelevancy", report.getAnswerRelevancy());
            evaluation.put("contextPrecision", report.getContextPrecision());
            log.info("[{}] RAGAS评估完成, traceId={}, faithfulness={}, answerRelevancy={}, contextPrecision={}",
                    getAgentType(), traceId, report.getFaithfulness(),
                    report.getAnswerRelevancy(), report.getContextPrecision());
            return evaluation;
        } catch (Exception e) {
            log.warn("[{}] RAGAS评估失败, traceId={}: {}", getAgentType(), traceId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 解析租户标识 (String), 用于能力层多租户隔离。子类可直接复用。
     *
     * @param request Agent 请求
     * @return 租户标识字符串
     */
    protected String resolveTenantId(AgentRequest request) {
        if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
            return request.getTenantId();
        }
        Long ctx = TenantContext.getTenantId();
        return ctx != null ? String.valueOf(ctx) : "default";
    }

    /**
     * 解析租户 ID (Long), 用于执行记录持久化 (tenant_id 外键)。
     * 优先取 TenantContext, 其次尝试解析字符串租户标识。
     */
    private Long resolveTenantIdLong(String tenantId) {
        Long ctx = TenantContext.getTenantId();
        if (ctx != null) {
            return ctx;
        }
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeQuery(AgentRequest request) {
        String q = request.getQuery();
        if (q == null) {
            return "";
        }
        return q.length() > 500 ? q.substring(0, 500) : q;
    }

    private Map<String, Object> buildInput(AgentRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", request.getQuery());
        input.put("agentType", getAgentType());
        if (request.getParams() != null) {
            input.put("params", request.getParams());
        }
        return input;
    }

    private Map<String, Object> buildOutput(Object result) {
        if (result == null) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(result,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of("result", String.valueOf(result));
        }
    }

    /**
     * 将结果序列化为 JSON 字符串 (缓存写入用), 失败时回退为 toString。
     */
    private String serializeSafely(Object result) {
        if (result == null) {
            return "null";
        }
        if (result instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[{}] 序列化结果失败, 回退toString: {}", getAgentType(), e.getMessage());
            return String.valueOf(result);
        }
    }

    /**
     * 将缓存命中字符串反序列化为对象 (保持 JSON 结构一致), 失败时原样返回字符串。
     */
    private Object deserializeSafely(String cached) {
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, Object.class);
        } catch (Exception e) {
            return cached;
        }
    }
}
