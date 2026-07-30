package com.contentops.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI Gateway 统一调用响应 DTO。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayResponse {

    /** 调用结果文本 */
    private String result;

    /** 实际使用的模型名 */
    private String modelUsed;

    /** 是否命中语义缓存 */
    private boolean cacheHit;

    /** 消耗 Token 数 (估算) */
    private int tokensUsed;

    /** 链路追踪 ID */
    private String traceId;
}
