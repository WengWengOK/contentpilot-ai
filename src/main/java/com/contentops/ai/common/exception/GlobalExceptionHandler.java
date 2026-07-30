package com.contentops.ai.common.exception;

import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器.
 *
 * <p>统一将异常转换为 {@link ApiResponse} 标准响应结构, 并补充 traceId 以便链路追踪。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        log.warn("Business exception | trace={} | code={} | msg={}", traceId, ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getCode())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuota(QuotaExceededException ex, HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        log.warn("Quota exceeded | trace={} | msg={}", traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed | trace={} | msg={}", traceId, msg);
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, msg, traceId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(400, "请求体格式错误或不可读", traceId));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex,
                                                            HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "资源不存在", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex, HttpServletRequest request) {
        String traceId = request.getHeader(AiConstants.TRACE_HEADER);
        log.error("Unhandled exception | trace={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "内部服务器错误", traceId));
    }
}
