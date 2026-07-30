package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.common.exception.BusinessException;
import com.contentops.ai.common.util.TraceUtil;
import com.contentops.ai.domain.entity.AgentExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行记录服务。
 *
 * <p>封装 {@link AgentExecution} 实体的生命周期管理 (对齐系统设计文档 §4.8 / §5.1):
 * <ul>
 *   <li>{@link #startExecution(String, String, Map)}: 创建一条 running 状态的执行记录,
 *       自动生成 executionId / traceId (任务规约要求的主入口)。</li>
 *   <li>{@link #startExecution(String, Long, String, Map, String)}: 重载, 由调用方指定
 *       executionId / tenantId(Long) / traceId (供 {@code BaseAgent} 模板方法复用 traceId 作为执行 ID)。</li>
 *   <li>{@link #completeExecution(String, Map, int, String)}: 写入 output / tokensUsed / modelUsed, 置为 completed。</li>
 *   <li>{@link #completeExecution(String, String, Map, int, String)}: 重载, 显式传入 status
 *       (completed / failed), 供 {@code BaseAgent} 在成功 / 失败分支统一调用。</li>
 *   <li>{@link #failExecution}: 置为 failed, 错误信息写入 output.error (实体无独立 error_message 字段)。</li>
 * </ul>
 * </p>
 *
 * <p>tenantId 入参为字符串的主入口内部解析为 Long 落库; 解析失败时回退到
 * {@link TenantContext}, 仍无法确定则抛出 400。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExecutionService {

    /** executionId 生成前缀 */
    private static final String EXECUTION_ID_PREFIX = "exec-";

    private final AgentExecutionRepository executionRepository;

    // ==================== 启动执行 ====================

    /**
     * 启动一次 Agent 执行 (任务规约主入口)。
     *
     * <p>自动生成 executionId 与 traceId, tenantId 由字符串解析为 Long。</p>
     *
     * @param agentType Agent 类型 (对齐 {@code AiConstants.AgentType})
     * @param tenantId  租户标识 (字符串, 可为请求头透传值)
     * @param input     执行输入 (写入 input jsonb)
     * @return 已持久化的 AgentExecution (含 executionId / traceId)
     */
    @Transactional
    public AgentExecution startExecution(String agentType, String tenantId, Map<String, Object> input) {
        return startExecution(generateExecutionId(), resolveTenantId(tenantId), agentType,
                input, TraceUtil.generateTraceId());
    }

    /**
     * 启动一次 Agent 执行 (重载, 由调用方指定 executionId / traceId)。
     *
     * <p>供 {@code BaseAgent} 等编排层复用既有 traceId 作为 executionId, 保证全链路 ID 一致。</p>
     *
     * @param executionId 执行 ID
     * @param tenantId    租户 ID (Long, 已由调用方解析)
     * @param agentType   Agent 类型
     * @param input       执行输入
     * @param traceId     链路追踪 ID
     * @return 已持久化的 AgentExecution
     */
    @Transactional
    public AgentExecution startExecution(String executionId, Long tenantId, String agentType,
                                         Map<String, Object> input, String traceId) {
        AgentExecution execution = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(tenantId)
                .agentType(agentType)
                .status(AiConstants.ExecutionStatus.RUNNING)
                .input(input == null ? Map.of() : new HashMap<>(input))
                .tokensUsed(0)
                .traceId(traceId)
                .build();
        executionRepository.save(execution);
        log.info("Agent 执行已启动: executionId={}, agentType={}, tenantId={}, traceId={}",
                executionId, agentType, tenantId, traceId);
        return execution;
    }

    // ==================== 完成 / 失败执行 ====================

    /**
     * 完成一次 Agent 执行 (任务规约主入口, 状态置为 completed)。
     *
     * @param executionId 执行 ID
     * @param output      执行输出
     * @param tokensUsed  消耗的 Token 数
     * @param modelUsed   使用的模型 (如 {@code gpt-4o})
     */
    @Transactional
    public void completeExecution(String executionId, Map<String, Object> output,
                                  int tokensUsed, String modelUsed) {
        completeExecution(executionId, AiConstants.ExecutionStatus.COMPLETED, output, tokensUsed, modelUsed);
    }

    /**
     * 更新执行状态 (重载, 显式传入 status)。
     *
     * <p>供 {@code BaseAgent} 在成功 / 失败分支统一调用: 成功传 {@code COMPLETED},
     * 失败传 {@code FAILED} 并在 output 中携带 error。非 running 终态时记录 completedAt。</p>
     *
     * @param executionId 执行 ID
     * @param status      目标状态 (completed / failed)
     * @param output      执行输出 (失败时可包含 error 字段)
     * @param tokensUsed  消耗的 Token 数
     * @param modelUsed   使用的模型
     */
    @Transactional
    public void completeExecution(String executionId, String status, Map<String, Object> output,
                                  int tokensUsed, String modelUsed) {
        AgentExecution execution = executionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new BusinessException(404, "Agent 执行不存在: " + executionId));
        execution.setStatus(status);
        execution.setOutput(output == null ? Map.of() : new HashMap<>(output));
        execution.setTokensUsed(tokensUsed);
        execution.setModelUsed(modelUsed);
        if (!AiConstants.ExecutionStatus.RUNNING.equals(status)) {
            execution.setCompletedAt(LocalDateTime.now());
        }
        executionRepository.save(execution);
        log.info("Agent 执行状态更新: executionId={}, status={}, tokensUsed={}, modelUsed={}",
                executionId, status, tokensUsed, modelUsed);
    }

    /**
     * 标记一次 Agent 执行失败。
     *
     * <p>错误信息写入 output 的 {@code error} 字段 (实体无独立 error_message 列),
     * 同时保留既有 output 并记录 {@code failedAt} 时间戳, 不覆盖已计量的 tokensUsed / modelUsed。</p>
     *
     * @param executionId  执行 ID
     * @param errorMessage 错误信息
     */
    @Transactional
    public void failExecution(String executionId, String errorMessage) {
        AgentExecution execution = executionRepository.findByExecutionId(executionId)
                .orElseThrow(() -> new BusinessException(404, "Agent 执行不存在: " + executionId));
        execution.setStatus(AiConstants.ExecutionStatus.FAILED);
        Map<String, Object> output = execution.getOutput() == null
                ? new HashMap<>() : new HashMap<>(execution.getOutput());
        output.put("error", errorMessage);
        output.put("failedAt", LocalDateTime.now().toString());
        execution.setOutput(output);
        execution.setCompletedAt(LocalDateTime.now());
        executionRepository.save(execution);
        log.error("Agent 执行失败: executionId={}, error={}", executionId, errorMessage);
    }

    // ==================== 内部辅助 ====================

    /**
     * 解析 tenantId: 优先将字符串解析为 Long, 失败或为空时回退到 TenantContext。
     *
     * @param tenantId 字符串租户标识
     * @return Long 租户 ID
     * @throws BusinessException 无法确定租户时抛出 400
     */
    private Long resolveTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                return Long.parseLong(tenantId.trim());
            } catch (NumberFormatException ignored) {
                // 非数字编码, 回退到 TenantContext
            }
        }
        Long ctxTenantId = TenantContext.getTenantId();
        if (ctxTenantId != null) {
            return ctxTenantId;
        }
        throw new BusinessException(400, "无法解析 tenantId: " + tenantId);
    }

    private String generateExecutionId() {
        return EXECUTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
