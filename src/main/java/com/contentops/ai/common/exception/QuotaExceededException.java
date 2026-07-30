package com.contentops.ai.common.exception;

import lombok.Getter;

/**
 * Token 配额超限异常。
 *
 * <p>当租户的 Token 消耗超过其每日配额（daily_quota）时抛出。
 * 由 {@code TokenLimiter#tryConsume} 在扣减后配额不足时抛出。
 * 继承 {@link BusinessException} 以携带 HTTP 状态码 429，由
 * {@link GlobalExceptionHandler} 统一处理。</p>
 *
 * <p>说明：该类按设计位于 {@code common.exception} 包中，
 * 此处提供实现以保证能力层模块可独立编译。若你的工程中已存在该类，可直接忽略本文件。</p>
 */
@Getter
public class QuotaExceededException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** HTTP 429 Too Many Requests */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /** 触发限流的租户标识 */
    private final String tenantId;
    /** 本次扣减前的剩余配额 */
    private final long remaining;
    /** 本次请求消耗的 Token 数 */
    private final int requested;

    public QuotaExceededException(String tenantId, long remaining, int requested) {
        super(HTTP_TOO_MANY_REQUESTS,
                String.format("租户[%s]Token配额不足: 剩余=%d, 请求=%d", tenantId, remaining, requested));
        this.tenantId = tenantId;
        this.remaining = remaining;
        this.requested = requested;
    }
}
