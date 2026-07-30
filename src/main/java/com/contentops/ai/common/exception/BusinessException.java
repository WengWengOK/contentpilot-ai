package com.contentops.ai.common.exception;

import lombok.Getter;

/**
 * 业务异常基类, 携带业务错误码.
 *
 * <p>所有可预见的业务错误应抛出此异常或其子类, 由 {@link GlobalExceptionHandler} 统一处理。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
