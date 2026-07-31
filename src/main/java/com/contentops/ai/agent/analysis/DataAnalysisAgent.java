package com.contentops.ai.agent.analysis;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.agent.a2a.A2ABroker;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.event.A2AMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据分析 Agent.
 *
 * <p>流程: 查询数据库统计数据 → 模型降级链生成分析报告 → 通过 A2A 总线发布给 optimize-agent。
 * 不启用 RAGAS 评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAnalysisAgent extends BaseAgent {

    private final ModelFallbackChain modelFallbackChain;
    private final A2ABroker a2aBroker;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.ANALYSIS;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object execute(AgentRequest request) {
        String tenantId = resolveTenantId(request);

        // 1. 查询数据库统计数据 (params 缺 key 或类型不符时回退默认值, 避免空指针)
        LocalDate startDate = resolveDate(request, "startDate", LocalDate.now().minusMonths(1));
        LocalDate endDate = resolveDate(request, "endDate", LocalDate.now());
        Map<String, Object> stats = queryStats(tenantId, startDate, endDate);

        // 2. 模型降级链生成分析报告
        String prompt = buildAnalysisPrompt(stats, startDate, endDate);
        ChatResult chatResult = modelFallbackChain.chatWithMeta(prompt, tenantId);
        setModelUsed(chatResult.modelUsed());
        String report = chatResult.content();
        setAnswerText(report);
        // 模型全部不可用走到模板兜底文案时, 不应缓存无意义内容
        if ("template".equals(chatResult.source())) {
            setCacheable(false);
        }

        // 3. 通过 A2A 总线发布给 optimize-agent
        a2aBroker.publish(A2AMessage.builder()
                .fromAgent(A2ABroker.ANALYSIS_AGENT)
                .toAgent(A2ABroker.OPTIMIZE_AGENT)
                .messageType(AiConstants.A2AMessageType.TASK_DELEGATION)
                .payload(Map.of(
                        "task", "generate_optimize_strategy",
                        "report", report,
                        "stats", stats))
                .correlationId(request.getTraceId())
                .build());

        // 4. 返回分析结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("report", report);
        result.put("stats", stats);
        result.put("modelUsed", chatResult.modelUsed());
        result.put("dateRange", startDate + " ~ " + endDate);
        log.info("DataAnalysis 生成报告并发布给 optimize-agent, traceId={}", request.getTraceId());
        return result;
    }

    /**
     * 从请求参数解析日期, 缺失或类型不符时回退默认值。
     * 防御 params 非空但不含指定 key 时返回 null 导致后续 NPE。
     */
    private LocalDate resolveDate(AgentRequest request, String key, LocalDate defaultValue) {
        if (request.getParams() == null) {
            return defaultValue;
        }
        Object val = request.getParams().get(key);
        if (val instanceof LocalDate ld) {
            return ld;
        }
        return defaultValue;
    }

    /**
     * 查询 agent_execution 统计数据 (按 agent_type 聚合执行次数与 Token 消耗)。
     */
    private Map<String, Object> queryStats(String tenantId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Long tenantIdLong = parseTenantIdLong(tenantId);
            String sql = "SELECT agent_type, COUNT(*) AS cnt, COALESCE(SUM(tokens_used), 0) AS tokens "
                    + "FROM agent_execution "
                    + "WHERE started_at >= ? AND started_at < ? "
                    + (tenantIdLong != null ? "AND tenant_id = ? " : "")
                    + "GROUP BY agent_type ORDER BY cnt DESC";
            List<Map<String, Object>> rows;
            if (tenantIdLong != null) {
                rows = jdbcTemplate.queryForList(sql,
                        startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), tenantIdLong);
            } else {
                rows = jdbcTemplate.queryForList(sql,
                        startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
            }
            stats.put("byAgentType", rows);
            // null 安全取值: JDBC 驱动可能返回 null 或列名大小写不一致
            stats.put("totalExecutions", rows.stream()
                    .mapToLong(r -> toLong(r.get("cnt"))).sum());
            stats.put("totalTokens", rows.stream()
                    .mapToLong(r -> toLong(r.get("tokens"))).sum());
        } catch (Exception e) {
            log.warn("查询统计数据失败, 返回空统计: {}", e.getMessage());
            stats.put("byAgentType", List.of());
            stats.put("totalExecutions", 0);
            stats.put("totalTokens", 0);
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    /**
     * 构建分析报告 prompt。
     */
    private String buildAnalysisPrompt(Map<String, Object> stats, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位数据分析师。请根据以下内容运营平台统计数据, 生成一份结构化的月度分析报告(中文)。\n");
        sb.append("统计周期: ").append(startDate).append(" ~ ").append(endDate).append('\n');
        sb.append("统计数据(JSON):\n").append(safeJson(stats)).append('\n');
        sb.append("\n报告需包含: 总体概况、各Agent执行情况、Token消耗分析、关键发现与改进建议。");
        return sb.toString();
    }

    private Long parseTenantIdLong(String tenantId) {
        try {
            return Long.parseLong(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    /**
     * 安全将 Object 转为 long, 处理 null 和不同 Number 子类。
     */
    private long toLong(Object val) {
        if (val instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
