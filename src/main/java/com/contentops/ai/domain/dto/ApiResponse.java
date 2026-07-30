package com.contentops.ai.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统一 API 响应结构.
 *
 * @param <T> 业务数据类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** 业务状态码, 200 表示成功 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 链路追踪 ID */
    private String traceId;

    /** 业务数据 */
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .traceId(traceId)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> fail(int code, String message, String traceId) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .traceId(traceId)
                .build();
    }

    public boolean isSuccess() {
        return code == 200;
    }
}
