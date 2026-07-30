package com.contentops.ai.agent.content;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.capability.retrieval.Document;
import com.contentops.ai.capability.retrieval.HybridRetriever;
import com.contentops.ai.capability.validation.StructuredOutputGuard;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ContentOutline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 内容创作 Agent.
 *
 * <p>流程: 混合检索参考资料 → 模型降级链生成内容大纲 → 结构化输出校验。
 * 输出 {@link ContentOutline} JSON, 启用 RAGAS 评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCreationAgent extends BaseAgent {

    private final HybridRetriever hybridRetriever;
    private final ModelFallbackChain modelFallbackChain;
    private final StructuredOutputGuard structuredOutputGuard;

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.CONTENT_CREATION;
    }

    @Override
    protected boolean supportsRagEvaluation() {
        return true;
    }

    @Override
    protected Object execute(AgentRequest request) {
        String tenantId = resolveTenantId(request);

        // 1. 混合检索参考资料 (RAG Context)
        List<Document> docs = hybridRetriever.retrieve(request.getQuery(), tenantId, 5);
        List<String> contexts = docs.stream()
                .map(Document::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        setRagContexts(contexts);
        log.debug("ContentCreation 检索到 {} 条参考资料, traceId={}", contexts.size(), request.getTraceId());

        // 2. 构建 prompt, 调用模型降级链生成内容大纲
        String prompt = buildPrompt(request, contexts);
        ChatResult chatResult = modelFallbackChain.chatWithMeta(prompt, tenantId);
        setModelUsed(chatResult.modelUsed());
        setAnswerText(chatResult.content());

        // 3. 结构化输出校验 (content-outline.json schema)
        ContentOutline outline = structuredOutputGuard.parseAndValidate(
                chatResult.content(), "content-outline", ContentOutline.class);
        log.info("ContentCreation 生成大纲: {}, traceId={}", outline.getTitle(), request.getTraceId());
        return outline;
    }

    /**
     * 构建内容创作 prompt。
     */
    private String buildPrompt(AgentRequest request, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的内容创作者。请根据选题与参考资料, 生成结构化的内容大纲。\n");
        sb.append("输出必须为合法 JSON, 包含字段: title, introduction, sections(List[heading,bulletPoints,order]), conclusion。\n\n");

        if (request.getParams() != null) {
            sb.append("选题: ").append(request.getParams().getOrDefault("topic", request.getQuery())).append('\n');
            sb.append("关键词: ").append(request.getParams().getOrDefault("keywords", "")).append('\n');
            sb.append("目标平台: ").append(request.getParams().getOrDefault("platform", "通用")).append('\n');
        }

        if (!contexts.isEmpty()) {
            sb.append("\n参考资料:\n");
            for (int i = 0; i < contexts.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(truncate(contexts.get(i), 400)).append('\n');
            }
        }
        return sb.toString();
    }
}
