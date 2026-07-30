package com.contentops.ai.capability.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RAGAS 评估记录 JPA 仓库。
 * <p>
 * save 由 JpaRepository 继承, 此处显式声明以明确返回具体类型;
 * findByTenantIdAndCreatedAtBetween 支持按租户与时间范围查询评估报告。
 */
@Repository
public interface EvaluationRepository extends JpaRepository<RagasEvaluation, Long> {

    /**
     * 保存评估记录。
     *
     * @param entity 评估记录
     * @return 保存后的实体(含主键)
     */
    RagasEvaluation save(RagasEvaluation entity);

    /**
     * 按租户与时间范围查询评估记录。
     *
     * @param tenantId 租户ID
     * @param start    起始时间(含)
     * @param end      结束时间(含)
     * @return 评估记录列表
     */
    List<RagasEvaluation> findByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime start, LocalDateTime end);
}
