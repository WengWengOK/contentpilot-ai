package com.contentops.ai.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * RAGAS 生成质量评估记录实体.
 *
 * <p>对齐系统设计文档 §5.1 ragas_evaluation 表结构。</p>
 */
@Entity
@Table(name = "ragas_evaluation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagasEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "execution_id", length = 64)
    private String executionId;

    @Column(nullable = false, columnDefinition = "text")
    private String query;

    @Column(columnDefinition = "text")
    private String answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> contexts;

    @Column(name = "faithfulness", precision = 4, scale = 3)
    private BigDecimal faithfulness;

    @Column(name = "answer_relevancy", precision = 4, scale = 3)
    private BigDecimal answerRelevancy;

    @Column(name = "context_precision", precision = 4, scale = 3)
    private BigDecimal contextPrecision;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
