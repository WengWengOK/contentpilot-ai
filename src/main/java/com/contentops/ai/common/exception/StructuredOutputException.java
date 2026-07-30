package com.contentops.ai.common.exception;

/**
 * 结构化输出异常.
 *
 * <p>当模型返回内容无法解析为目标结构化对象 (JSON Schema 校验失败 / 反序列化失败) 时抛出,
 * HTTP 状态码 422。</p>
 */
public class StructuredOutputException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public StructuredOutputException(String message) {
        super(422, message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(422, message, cause);
    }
}
