package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.domain.entity.AgentCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 检查点 JPA 仓库。
 *
 * <p>对齐系统设计文档 §5.1 {@code agent_checkpoint} 表 (含 {@code idx_checkpoint_exec} 索引),
 * 供 {@link AgentCheckpointService} 保存 / 恢复 / 清理检查点。</p>
 */
@Repository
public interface AgentCheckpointRepository extends JpaRepository<AgentCheckpoint, Long> {

    /**
     * 按执行 ID 查询检查点历史, 按创建时间倒序 (最新在前)。
     *
     * @param executionId 执行 ID
     * @return 检查点列表 (最新在前)
     */
    List<AgentCheckpoint> findByExecutionIdOrderByCreatedAtDesc(String executionId);

    /**
     * 删除指定执行 ID 下、创建时间早于 {@code before} 的检查点 (用于清理旧 checkpoint)。
     *
     * <p>使用 JPQL 批量删除 (@Modifying), 调用方需在事务中执行。</p>
     *
     * @param executionId 执行 ID
     * @param before      时间边界 (不含)
     */
    @Modifying
    @Query("DELETE FROM AgentCheckpoint c WHERE c.executionId = :executionId AND c.createdAt < :before")
    void deleteByExecutionIdAndCreatedAtBefore(@Param("executionId") String executionId,
                                               @Param("before") LocalDateTime before);
}
