package com.contentops.ai.capability.fallback;

/**
 * 带元信息的降级链调用结果。
 *
 * @param content   返回内容
 * @param modelUsed 实际使用的模型名（缓存/模板兜底时为对应标识）
 * @param degraded  是否发生了降级（非首选模型命中，或走缓存/模板兜底）
 * @param source    结果来源：model / cache / template
 */
public record ChatResult(
        String content,
        String modelUsed,
        boolean degraded,
        String source
) {

    /** 模型端点命中。degraded 表示是否非首选模型。 */
    public static ChatResult ofModel(String content, String modelUsed, boolean degraded) {
        return new ChatResult(content, modelUsed, degraded, "model");
    }

    /** 语义缓存兜底命中。 */
    public static ChatResult ofCache(String content, String modelUsed) {
        return new ChatResult(content, modelUsed, true, "cache");
    }

    /** 模板兜底文案。 */
    public static ChatResult ofTemplate(String content) {
        return new ChatResult(content, "none", true, "template");
    }
}
