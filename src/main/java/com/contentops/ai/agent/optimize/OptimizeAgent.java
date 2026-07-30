package com.contentops.ai.agent.optimize;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.agent.a2a.A2ABroker;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.event.A2AMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 优化迭代 Agent.
 *
 * <p>流程: 通过 A2A 总线接收 analysis-agent 的分析结论 → 模型降级链生成优化策略 → 返回策略建议。
 * 启动时订阅本 Agent 频道, 缓存最近一条分析结论供后续策略生成使用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizeAgent extends BaseAgent {

    private final ModelFallbackChain modelFallbackChain;
    private final A2ABroker a2aBroker;

    /** 最近接收到的 analysis-agent 分析结论 (A2A 异步推送) */
    private volatile Map<String, Object> latestAnalysis;

    /**
     * 启动时订阅本 Agent 频道, 接收 analysis-agent 发来的分析结论。
     */
    @PostConstruct
    void subscribeAnalysis() {
        a2aBroker.subscribe(A2ABroker.OPTIMIZE_AGENT, this::onAnalysisMessage);
        log.info("OptimizeAgent 已订阅 {} 频道, 接收 analysis-agent 消息", A2ABroker.OPTIMIZE_AGENT);
    }

    /**
     * 处理 A2A 消息: 缓存最近一条分析结论。
     */
    private void onAnalysisMessage(A2AMessage message) {
        try {
            if (message.getPayload() != null) {
                latestAnalysis = message.getPayload();
                log.info("OptimizeAgent 收到 analysis-agent 消息, correlationId={}, task={}",
                        message.getCorrelationId(),
                        message.getPayload().get("task"));
            }
        } catch (Exception e) {
            log.warn("处理 A2A 分析消息失败: {}", e.getMessage());
        }
    }

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.OPTIMIZE;
    }

    @Override
    protected Object execute(AgentRequest request) {
        String tenantId = resolveTenantId(request);

        // 1. 获取分析数据: 优先请求参数, 其次 A2A 缓存的最新分析结论
        Map<String, Object> analysisData = resolveAnalysisData(request);

        // 2. 模型降级链生成优化策略
        String prompt = buildOptimizePrompt(analysisData);
        ChatResult chatResult = modelFallbackChain.chatWithMeta(prompt, tenantId);
        setModelUsed(chatResult.modelUsed());
        String strategy = chatResult.content();
        setAnswerText(strategy);
        // 模型全部不可用走到模板兜底文案时, 不应缓存无意义内容
        if ("template".equals(chatResult.source())) {
            setCacheable(false);
        }

        // 3. 返回优化策略
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", strategy);
        result.put("analysisData", analysisData);
        result.put("modelUsed", chatResult.modelUsed());
        log.info("OptimizeAgent 生成优化策略, traceId={}", request.getTraceId());
        return result;
    }

    /**
     * 解析分析数据: 优先使用请求参数中的 analysisData, 其次使用 A2A 缓存的最新结论。
     * 类型不符时回退占位数据, 避免ClassCastException 中断流程。
     */
    private Map<String, Object> resolveAnalysisData(AgentRequest request) {
        if (request.getParams() != null) {
            Object val = request.getParams().get("analysisData");
            if (val instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) raw;
                return typed;
            }
        }
        if (latestAnalysis != null) {
            return latestAnalysis;
        }
        return Map.of("note", "暂无分析数据, 基于通用最佳实践生成优化策略");
    }

    /**
     * 构建优化策略 prompt。
     */
    private String buildOptimizePrompt(Map<String, Object> analysisData) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位内容运营优化专家。请根据以下数据分析结论, 生成可执行的优化策略建议(中文)。\n");
        sb.append("分析结论(JSON):\n").append(safeJson(analysisData)).append('\n');
        sb.append("\n策略需包含: 选题优化方向、内容创作改进点、发布策略调整、资源分配建议, 并给出预期效果。");
        return sb.toString();
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
