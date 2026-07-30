package com.contentops.ai.capability.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 结构化输出泛型校验器。
 *
 * <p>校验 LLM 输出的 JSON 格式与字段完整性，校验失败时调用 LLM 自动修复（最多 1 次）。</p>
 *
 * <p>校验流程：
 * <ol>
 *   <li>JSON 解析：失败时尝试正则提取 JSON 片段（{@code \{.*\}} 或 {@code [.*]}）后重试</li>
 *   <li>Schema 校验：通过 networknt 校验，失败进入修复</li>
 *   <li>自动修复：把错误信息 + 原始输出 + schema 发给 LLM 让其修正，最多重试 1 次</li>
 *   <li>绑定到目标类型返回</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputGuard {

    /** 贪婪 + DOTALL，匹配最外层对象 */
    private static final Pattern OBJECT_PATTERN = Pattern.compile("(?s)\\{.*\\}");
    /** 贪婪 + DOTALL，匹配最外层数组 */
    private static final Pattern ARRAY_PATTERN = Pattern.compile("(?s)\\[.*\\]");

    private final ObjectMapper objectMapper;
    private final JsonSchemaLoader jsonSchemaLoader;
    private final ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 解析 + Schema 校验 + 自动修复，并绑定到目标类型。
     *
     * @param llmOutput  LLM 原始输出
     * @param schemaName JSON Schema 名称（对应 classpath:schemas/{schemaName}.json），如 "topic-suggestion"
     * @param targetType 目标 Java 类型
     * @param <T>        返回类型
     * @return 校验通过并绑定后的对象
     * @throws StructuredOutputException 解析/校验/修复失败
     */
    public <T> T parseAndValidate(String llmOutput, String schemaName, Class<T> targetType) {
        JsonNode node = parseJsonLenient(llmOutput);
        if (schemaName != null && !schemaName.isBlank()) {
            node = validateAndRepair(node, llmOutput, schemaName);
        }
        return bind(node, targetType);
    }

    /**
     * 无 Schema 版本：仅做 JSON 解析（含片段提取兜底）并绑定到目标类型。
     */
    public <T> T parseAndValidate(String llmOutput, Class<T> targetType) {
        JsonNode node = parseJsonLenient(llmOutput);
        return bind(node, targetType);
    }

    /**
     * 解析 JSON，失败时尝试提取 JSON 片段重试。
     */
    private JsonNode parseJsonLenient(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new StructuredOutputException("LLM输出为空, 无法解析JSON");
        }
        try {
            return objectMapper.readTree(llmOutput);
        } catch (Exception e) {
            log.warn("JSON直接解析失败, 尝试提取JSON片段: {}", e.getMessage());
            String extracted = extractJson(llmOutput);
            if (extracted == null) {
                throw new StructuredOutputException("无法从LLM输出中解析或提取JSON", e);
            }
            try {
                return objectMapper.readTree(extracted);
            } catch (Exception ex) {
                throw new StructuredOutputException("提取的JSON片段仍然无法解析", ex);
            }
        }
    }

    /**
     * Schema 校验 + 自动修复（最多 1 次）。
     */
    private JsonNode validateAndRepair(JsonNode node, String original, String schemaName) {
        JsonSchema schema = jsonSchemaLoader.load(schemaName);
        Set<ValidationMessage> errors = schema.validate(node);
        if (errors.isEmpty()) {
            return node;
        }

        log.warn("JSON Schema校验失败, 尝试LLM自动修复, schema={}, 错误数={}", schemaName, errors.size());
        String repaired;
        try {
            repaired = repair(original, errors, schemaName);
        } catch (Exception e) {
            throw new StructuredOutputException("调用LLM修复JSON失败: " + e.getMessage(), e);
        }

        JsonNode repairedNode = parseJsonLenient(repaired);
        Set<ValidationMessage> repairedErrors = schema.validate(repairedNode);
        if (!repairedErrors.isEmpty()) {
            throw new StructuredOutputException("LLM修复后仍不符合Schema, 错误: "
                    + formatErrors(repairedErrors));
        }
        log.info("JSON Schema修复成功, schema={}", schemaName);
        return repairedNode;
    }

    /**
     * 调用 LLM 修复 JSON：把 schema + 错误信息 + 原始输出交给 LLM，要求只输出合法 JSON。
     */
    private String repair(String original, Set<ValidationMessage> errors, String schemaName) {
        String schemaText = jsonSchemaLoader.loadNode(schemaName).toString();
        String errorText = formatErrors(errors);
        // 使用拼接而非 String.formatted，避免原始输出/schema 文本中的 '%' 导致格式化异常
        String prompt = "你是一个JSON修复助手。下面的LLM输出不符合给定的JSON Schema。"
                + "请修正它并只输出合法的JSON，不要包含任何解释或Markdown代码块。\n\n"
                + "JSON Schema:\n" + schemaText + "\n\n"
                + "校验错误:\n" + errorText + "\n\n"
                + "原始输出:\n" + original;
        return chatClient.prompt().user(prompt).call().content();
    }

    /** 绑定 JsonNode 到目标类型 */
    private <T> T bind(JsonNode node, Class<T> targetType) {
        try {
            return objectMapper.treeToValue(node, targetType);
        } catch (Exception e) {
            throw new StructuredOutputException(
                    "JSON绑定到目标类型失败: " + targetType.getSimpleName(), e);
        }
    }

    /** 从文本中提取最外层 JSON 片段（对象优先，其次数组） */
    private String extractJson(String text) {
        Matcher m = OBJECT_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        m = ARRAY_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private String formatErrors(Set<ValidationMessage> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.joining("; "));
    }
}
