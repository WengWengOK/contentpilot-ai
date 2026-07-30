package com.contentops.ai.capability.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RAGAS 评估记录 JPA 实体, 对应 ragas_evaluation 表。
 */
@Entity
@Table(name = "ragas_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagasEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "execution_id")
    private String executionId;

    @Column(columnDefinition = "text")
    private String query;

    @Column(columnDefinition = "text")
    private String answer;

    @Column(columnDefinition = "jsonb")
    private String contexts;

    @Column(name = "faithfulness", precision = 4, scale = 3)
    private BigDecimal faithfulness;

    @Column(name = "answer_relevancy", precision = 4, scale = 3)
    private BigDecimal answerRelevancy;

    @Column(name = "context_precision", precision = 4, scale = 3)
    private BigDecimal contextPrecision;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
