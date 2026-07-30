package com.contentops.ai.capability.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 忠实度(Faithfulness)评估器。
 * <p>
 * 评估流程:
 * <ol>
 *   <li>使用 ChatClient 从 answer 中提取所有事实声明(factual claims)</li>
 *   <li>对每个 claim 判断是否被 contexts 支持</li>
 *   <li>返回 supported / total 比例, 范围 [0,1]</li>
 * </ol>
 * 衡量 answer 是否"编造"了 contexts 中不存在的信息(幻觉)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaithfulnessEvaluator {

    private static final String EXTRACT_PROMPT =
            "从以下回答中提取所有事实声明(factual claims)，以JSON数组形式返回(仅返回数组，不要额外解释):\n回答: ";

    private static final String VERIFY_PROMPT =
            "请判断以下事实声明是否被给定的上下文支持。只回答true或false，不要解释。\n声明: ";

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    /** 复用单一 ChatClient 实例, 避免每次评估重复 build 带来的对象创建开销 */
    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 评估忠实度。
     *
     * @param answer   待评估的回答
     * @param contexts 检索到的上下文
     * @return supported claims / total claims, 失败时返回 0.0
     */
    public double evaluate(String answer, List<String> contexts) {
        if (answer == null || answer.isBlank() || contexts == null || contexts.isEmpty()) {
            return 0.0;
        }
        try {
            List<String> claims = extractClaims(answer);
            if (claims.isEmpty()) {
                // 没有可验证的事实声明, 视为无幻觉, 忠实度为 1.0
                return 1.0;
            }
            String joinedContexts = String.join("\n---\n", contexts);
            int supported = 0;
            for (String claim : claims) {
                if (isSupported(claim, joinedContexts)) {
                    supported++;
                }
            }
            return (double) supported / claims.size();
        } catch (Exception e) {
            log.warn("Faithfulness evaluation failed: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    private List<String> extractClaims(String answer) {
        try {
            // 使用拼接而非 String.format, 避免 answer 中的 '%' 触发 IllegalFormatConversionException
            String prompt = EXTRACT_PROMPT + answer;
            String content = chatClient.prompt().user(prompt).call().content();
            String json = extractJsonArray(content);
            if (json == null) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to extract claims: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private boolean isSupported(String claim, String contexts) {
        try {
            // 使用拼接而非 String.format, 避免 claim/contexts 中的 '%' 触发格式化异常
            String prompt = VERIFY_PROMPT + claim + "\n上下文: " + contexts;
            String content = chatClient.prompt().user(prompt).call().content();
            return parseBoolean(content);
        } catch (Exception e) {
            log.warn("Failed to verify claim '{}': {}", claim, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从 LLM 输出中提取首个 JSON 数组片段。
     */
    static String extractJsonArray(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 从 LLM 输出中解析布尔值(兼容 true/yes/是/对 等表达)。
     */
    static boolean parseBoolean(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase().trim();
        return lower.contains("true") || lower.contains("yes")
                || lower.contains("是") || lower.contains("对");
    }
}
