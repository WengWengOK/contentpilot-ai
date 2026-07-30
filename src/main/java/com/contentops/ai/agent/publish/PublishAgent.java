package com.contentops.ai.agent.publish;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.BaseAgent;
import com.contentops.ai.common.constant.AiConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 排版发布 Agent.
 *
 * <p>流程: 调用多平台发布 API (此处简化为模拟) → 返回各平台发布结果。
 * 不需要 LLM 调用, 主要是 API 集成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishAgent extends BaseAgent {

    @Override
    public String getAgentType() {
        return AiConstants.AgentType.PUBLISH;
    }

    /**
     * 发布动作有副作用(真实多平台发布), 命中缓存会跳过发布返回历史 postId,
     * 造成业务错误, 因此禁用语义缓存。
     */
    @Override
    protected boolean supportsSemanticCache() {
        return false;
    }

    @Override
    protected Object execute(AgentRequest request) {
        @SuppressWarnings("unchecked")
        List<String> platforms = request.getParams() != null
                ? (List<String>) request.getParams().getOrDefault("platforms", List.of("wechat"))
                : List.of("wechat");

        String content = request.getParams() != null
                ? String.valueOf(request.getParams().getOrDefault("content", ""))
                : "";

        // 模拟多平台发布 API 调用
        List<Map<String, Object>> results = new ArrayList<>();
        for (String platform : platforms) {
            results.add(publishToPlatform(platform, content, request.getTraceId()));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("platforms", results);
        summary.put("totalCount", results.size());
        summary.put("successCount", results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count());
        log.info("Publish 完成, 平台数={}, traceId={}", results.size(), request.getTraceId());
        return summary;
    }

    /**
     * 模拟单平台发布 (生产环境替换为真实平台 API 调用)。
     */
    private Map<String, Object> publishToPlatform(String platform, String content, String traceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // TODO: 替换为真实多平台 API 集成 (微信公众号 / 小红书 / 抖音 / 知乎等)
            String postId = platform + "-" + UUID.randomUUID().toString().substring(0, 8);
            result.put("platform", platform);
            result.put("success", true);
            result.put("postId", postId);
            result.put("publishUrl", "https://" + platform + ".example.com/posts/" + postId);
            result.put("publishedAt", LocalDateTime.now().toString());
            log.debug("发布到 {} 成功, postId={}, traceId={}", platform, postId, traceId);
        } catch (Exception e) {
            result.put("platform", platform);
            result.put("success", false);
            result.put("error", e.getMessage());
            log.warn("发布到 {} 失败, traceId={}: {}", platform, traceId, e.getMessage());
        }
        return result;
    }
}
