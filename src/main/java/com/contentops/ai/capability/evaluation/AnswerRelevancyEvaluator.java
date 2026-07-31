package com.contentops.ai.capability.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回答相关性(Answer Relevancy)评估器。
 * <p>
 * 评估流程:
 * <ol>
 *   <li>使用 ChatClient 从 answer 反向生成若干可能对应的原始问题</li>
 *   <li>使用 EmbeddingModel 计算生成问题与原始 query 的向量相似度(余弦)</li>
 *   <li>返回平均相似度, 范围 [0,1]</li>
 * </ol>
 * 衡量 answer 是否聚焦于回答 query, 而非跑题或冗余。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerRelevancyEvaluator {

    private static final String GENERATE_PROMPT =
            "根据以下回答，生成3个该回答可能对应的原始问题，以JSON数组形式返回(仅返回数组，不要额外解释):\n回答: ";

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    /** 复用单一 ChatClient 实例, 避免每次评估重复 build 带来的对象创建开销 */
    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 评估回答相关性。
     *
     * @param query  原始问题
     * @param answer 待评估的回答
     * @return 生成问题与 query 的平均余弦相似度, 失败时返回 0.0
     */
    public double evaluate(String query, String answer) {
        if (query == null || query.isBlank() || answer == null || answer.isBlank()) {
            return 0.0;
        }
        try {
            List<String> questions = generateQuestions(answer);
            if (questions.isEmpty()) {
                return 0.0;
            }
            // 批量向量化: query + 所有问题一次性 embed, 避免 N+1 次 EmbeddingModel 调用
            // Spring AI 1.0.0 GA: EmbeddingModel.embed(List<String>) 返回 List<float[]>
            List<String> allTexts = new ArrayList<>(questions.size() + 1);
            allTexts.add(query);
            allTexts.addAll(questions);
            List<float[]> embeddings = embeddingModel.embed(allTexts);
            if (embeddings == null || embeddings.isEmpty()
                    || embeddings.get(0) == null || embeddings.get(0).length == 0) {
                log.warn("Empty embedding for query, answer relevancy returns 0.0");
                return 0.0;
            }
            float[] queryVector = embeddings.get(0);

            double sum = 0.0;
            int count = 0;
            for (int i = 1; i < embeddings.size(); i++) {
                float[] qv = embeddings.get(i);
                double sim = cosineSimilarity(queryVector, qv);
                if (sim > 0) {
                    sum += sim;
                    count++;
                }
            }
            return count == 0 ? 0.0 : sum / count;
        } catch (Exception e) {
            log.warn("Answer relevancy evaluation failed: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    private List<String> generateQuestions(String answer) {
        try {
            // 使用拼接而非 String.format, 避免 answer 中的 '%' 触发 IllegalFormatConversionException
            String prompt = GENERATE_PROMPT + answer;
            String content = chatClient.prompt().user(prompt).call().content();
            String json = FaithfulnessEvaluator.extractJsonArray(content);
            if (json == null) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to generate questions from answer: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double av = a[i];
            double bv = b[i];
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
