package com.contentops.ai.agent.image;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.common.constant.AiConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配图设计 Agent.
 *
 * <p>流程: 模型降级链生成图片 prompt → 调用 DALL-E API 生成图片 → 失败降级返回默认图 URL。
 * 不依赖检索, 不启用 RAGAS 评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDesignAgent extends BaseAgent {

    private final ModelFallbackChain modelFallbackChain;
    private final RestTemplate restTemplate;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${spring.ai.openai.api-key:sk-placeholder}")
    private String openaiApiKey;

    @Value("${contentops.image.dalle-model:dall-e-3}")
    private String dalleModel;

    @Value("${contentops.image.default-url:https://placehold.co/600x400?text=Default+Image}")
    private String defaultImageUrl;

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.IMAGE_DESIGN;
    }

    /**
     * 图片生成结果(DALL-E URL)具有时效性, 缓存后可能返回已失效链接;
     * 且 LLM 生成的是图片 prompt 而非最终图片, 缓存语义不一致, 因此禁用语义缓存。
     */
    @Override
    protected boolean supportsSemanticCache() {
        return false;
    }

    @Override
    protected Object execute(AgentRequest request) {
        String tenantId = resolveTenantId(request);

        // 1. 模型降级链生成图片 prompt
        String prompt = buildPrompt(request);
        ChatResult chatResult = modelFallbackChain.chatWithMeta(prompt, tenantId);
        setModelUsed(chatResult.modelUsed());
        String imagePrompt = chatResult.content();
        setAnswerText(imagePrompt);

        // 2. 调用 DALL-E API 生成图片 (失败降级默认图)
        String imageUrl = generateImage(imagePrompt);

        // 3. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageUrl", imageUrl);
        result.put("prompt", imagePrompt);
        result.put("modelUsed", chatResult.modelUsed());
        result.put("degraded", defaultImageUrl.equals(imageUrl));
        log.info("ImageDesign 生成图片, url={}, traceId={}", imageUrl, request.getTraceId());
        return result;
    }

    /**
     * 调用 DALL-E 兼容接口生成图片, 失败时返回默认图 URL。
     */
    private String generateImage(String prompt) {
        try {
            String url = openaiBaseUrl + "/v1/images/generations";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            Map<String, Object> body = Map.of(
                    "model", dalleModel,
                    "prompt", prompt == null ? "" : prompt,
                    "n", 1,
                    "size", "1024x1024");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode imageUrlNode = root.path("data").path(0).path("url");
                if (!imageUrlNode.isMissingNode() && !imageUrlNode.asText().isBlank()) {
                    return imageUrlNode.asText();
                }
            }
            log.warn("DALL-E 返回非预期响应, 降级默认图: {}", resp.getStatusCode());
        } catch (Exception e) {
            log.warn("DALL-E 调用失败, 降级默认图: {}", e.getMessage());
        }
        return defaultImageUrl;
    }

    /**
     * 构建图片 prompt 生成请求。
     */
    private String buildPrompt(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位AI绘画prompt工程师。请将以下描述转化为一段高质量的DALL-E英文绘画prompt(只输出prompt文本)。\n");
        sb.append("描述: ").append(request.getQuery()).append('\n');
        if (request.getParams() != null) {
            sb.append("风格: ").append(request.getParams().getOrDefault("style", "realistic")).append('\n');
        }
        return sb.toString();
    }
}
