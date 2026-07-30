package com.contentops.ai.capability.validation;

/**
 * 结构化输出校验/修复失败异常。
 *
 * <p>当 LLM 输出无法解析为 JSON、不符合 JSON Schema 且 LLM 自动修复（最多 1 次）后仍不合法时抛出。</p>
 */
public class StructuredOutputException extends RuntimeException {

    public StructuredOutputException(String message) {
        super(message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
