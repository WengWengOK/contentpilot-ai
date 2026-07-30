package com.contentops.ai.agent.topic;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.capability.retrieval.Document;
import com.contentops.ai.capability.retrieval.HybridRetriever;
import com.contentops.ai.capability.validation.StructuredOutputGuard;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.TopicSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 选题策划 Agent.
 *
 * <p>流程: 混合检索历史选题 → 模型降级链生成选题建议 → 结构化输出校验。
 * 输出 {@link TopicSuggestion} JSON, 启用 RAGAS 评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicPlanningAgent extends BaseAgent {

    private final HybridRetriever hybridRetriever;
    private final ModelFallbackChain modelFallbackChain;
    private final StructuredOutputGuard structuredOutputGuard;

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.TOPIC_PLANNING;
    }

    @Override
    protected boolean supportsRagEvaluation() {
        return true;
    }

    @Override
    protected Object execute(AgentRequest request) {
        String tenantId = resolveTenantId(request);

        // 1. 混合检索历史选题 / 参考资料
        List<Document> docs = hybridRetriever.retrieve(request.getQuery(), tenantId, 5);
        List<String> contexts = docs.stream()
                .map(Document::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        setRagContexts(contexts);
        log.debug("TopicPlanning 检索到 {} 条参考文档, traceId={}", contexts.size(), request.getTraceId());

        // 2. 构建 prompt, 调用模型降级链生成选题建议
        String prompt = buildPrompt(request, contexts);
        ChatResult chatResult = modelFallbackChain.chatWithMeta(prompt, tenantId);
        setModelUsed(chatResult.modelUsed());
        setAnswerText(chatResult.content());

        // 3. 结构化输出校验 (topic-suggestion.json schema)
        TopicSuggestion suggestion = structuredOutputGuard.parseAndValidate(
                chatResult.content(), "topic-suggestion", TopicSuggestion.class);
        log.info("TopicPlanning 生成选题: {}, traceId={}", suggestion.getTitle(), request.getTraceId());
        return suggestion;
    }

    /**
     * 构建选题策划 prompt。
     */
    private String buildPrompt(AgentRequest request, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深的MCN内容选题策划师。请根据关键词与历史选题参考, 生成一个高质量的选题建议。\n");
        sb.append("输出必须为合法 JSON, 包含字段: title, summary, keywords(List), category, trendingScore(0-1)。\n\n");

        if (request.getParams() != null) {
            sb.append("关键词: ").append(request.getParams().getOrDefault("keywords", "")).append('\n');
            sb.append("目标平台: ").append(request.getParams().getOrDefault("platform", "通用")).append('\n');
            sb.append("需求数量: ").append(request.getParams().getOrDefault("count", 1)).append('\n');
        }
        sb.append("用户意图: ").append(request.getQuery()).append('\n');

        if (!contexts.isEmpty()) {
            sb.append("\n历史选题参考:\n");
            for (int i = 0; i < contexts.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(truncate(contexts.get(i), 300)).append('\n');
            }
        }
        return sb.toString();
    }
}
