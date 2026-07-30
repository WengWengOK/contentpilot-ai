package com.contentops.ai.capability.evaluation;

import com.contentops.ai.capability.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RAGAS 评估核心编排类。
 * <p>
 * 编排三个子评估器(Faithfulness / AnswerRelevancy / ContextPrecision),
 * 将结果记录到 PostgreSQL, 并返回 {@link RagasReport}。
 * <p>
 * 容错: 每个子评估器与持久化均独立 try-catch, 单点失败不影响整体流程,
 * 失败维度记为 0.0 并记录 warn 日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagasEvaluator {

    private final FaithfulnessEvaluator faithfulnessEvaluator;
    private final AnswerRelevancyEvaluator answerRelevancyEvaluator;
    private final ContextPrecisionEvaluator contextPrecisionEvaluator;
    private final EvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 执行 RAGAS 评估。
     *
     * @param query    原始问题
     * @param answer   生成的回答
     * @param contexts 检索到的上下文
     * @param traceId  链路追踪ID
     * @return RAGAS 评估报告
     */
    public RagasReport evaluate(String query, String answer, List<String> contexts, String traceId) {
        log.info("RAGAS evaluation start, traceId={}", traceId);

        double faithfulness = 0.0;
        double answerRelevancy = 0.0;
        double contextPrecision = 0.0;

        try {
            faithfulness = faithfulnessEvaluator.evaluate(answer, contexts);
        } catch (Exception e) {
            log.warn("Faithfulness evaluator threw, traceId={}: {}", traceId, e.getMessage(), e);
        }
        try {
            answerRelevancy = answerRelevancyEvaluator.evaluate(query, answer);
        } catch (Exception e) {
            log.warn("AnswerRelevancy evaluator threw, traceId={}: {}", traceId, e.getMessage(), e);
        }
        try {
            contextPrecision = contextPrecisionEvaluator.evaluate(query, contexts);
        } catch (Exception e) {
            log.warn("ContextPrecision evaluator threw, traceId={}: {}", traceId, e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now();
        persistEvaluation(query, answer, contexts, traceId,
                faithfulness, answerRelevancy, contextPrecision, now);

        log.info("RAGAS evaluation done, traceId={}, faithfulness={}, answerRelevancy={}, contextPrecision={}",
                traceId, faithfulness, answerRelevancy, contextPrecision);

        return RagasReport.builder()
                .faithfulness(faithfulness)
                .answerRelevancy(answerRelevancy)
                .contextPrecision(contextPrecision)
                .traceId(traceId)
                .timestamp(now)
                .build();
    }

    private void persistEvaluation(String query, String answer, List<String> contexts, String traceId,
                                   double faithfulness, double answerRelevancy,
                                   double contextPrecision, LocalDateTime now) {
        try {
            List<String> safeContexts = contexts == null ? List.of() : contexts;
            RagasEvaluation entity = RagasEvaluation.builder()
                    .tenantId(TenantContext.getTenantId())
                    .executionId(traceId)
                    .query(query)
                    .answer(answer)
                    .contexts(objectMapper.writeValueAsString(safeContexts))
                    .faithfulness(BigDecimal.valueOf(faithfulness))
                    .answerRelevancy(BigDecimal.valueOf(answerRelevancy))
                    .contextPrecision(BigDecimal.valueOf(contextPrecision))
                    .createdAt(now)
                    .build();
            evaluationRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist RAGAS evaluation, traceId={}: {}", traceId, e.getMessage(), e);
        }
    }
}
