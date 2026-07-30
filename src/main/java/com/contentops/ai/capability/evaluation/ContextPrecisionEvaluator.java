package com.contentops.ai.capability.evaluation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上下文精确率(Context Precision)评估器。
 * <p>
 * 评估流程:
 * <ol>
 *   <li>对每个 context 判断是否对回答 query 有用(使用 ChatClient)</li>
 *   <li>返回 useful / total 比例, 范围 [0,1]</li>
 * </ol>
 * 衡量检索召回的上下文中"有效信息"的占比, 占比越高说明检索越精准、噪声越少。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextPrecisionEvaluator {

    private static final String PROMPT =
            "请判断以下上下文是否对回答该问题有用。只回答true或false，不要解释。\n问题: ";

    private final ChatClient.Builder chatClientBuilder;

    /** 复用单一 ChatClient 实例, 避免每次评估重复 build 带来的对象创建开销 */
    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 评估上下文精确率。
     *
     * @param query    原始问题
     * @param contexts 检索到的上下文列表
     * @return useful contexts / total contexts, 失败时返回 0.0
     */
    public double evaluate(String query, List<String> contexts) {
        if (query == null || query.isBlank() || contexts == null || contexts.isEmpty()) {
            return 0.0;
        }
        try {
            int useful = 0;
            for (String context : contexts) {
                if (isUseful(query, context)) {
                    useful++;
                }
            }
            return (double) useful / contexts.size();
        } catch (Exception e) {
            log.warn("Context precision evaluation failed: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    private boolean isUseful(String query, String context) {
        try {
            // 使用拼接而非 String.format, 避免 query/context 中的 '%' 触发 IllegalFormatConversionException
            String prompt = PROMPT + query + "\n上下文: " + context;
            String content = chatClient.prompt().user(prompt).call().content();
            return FaithfulnessEvaluator.parseBoolean(content);
        } catch (Exception e) {
            log.warn("Failed to judge context usefulness: {}", e.getMessage(), e);
            return false;
        }
    }
}
