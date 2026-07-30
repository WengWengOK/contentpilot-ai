package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.domain.entity.AgentExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 执行记录 JPA 仓库。
 *
 * <p>对齐系统设计文档 §5.1 {@code agent_execution} 表, 供 {@link AgentExecutionService}
 * 启动 / 完成 / 失败执行时读写记录。</p>
 */
@Repository
public interface AgentExecutionRepository extends JpaRepository<AgentExecution, Long> {

    /**
     * 按业务执行 ID (字符串, 与 trace_id 解耦) 查询执行记录。
     *
     * @param executionId 执行 ID
     * @return 执行记录 (存在时) 或 empty
     */
    Optional<AgentExecution> findByExecutionId(String executionId);

    /**
     * 按租户与 Agent 类型查询执行记录 (用于租户级执行历史 / 审计)。
     *
     * @param tenantId 租户 ID
     * @param agentType Agent 类型 (对齐 {@code AiConstants.AgentType})
     * @return 执行记录列表
     */
    List<AgentExecution> findByTenantIdAndAgentType(Long tenantId, String agentType);
}
