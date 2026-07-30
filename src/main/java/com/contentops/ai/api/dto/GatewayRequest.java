package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI Gateway 统一调用请求 DTO。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayRequest {

    /** 调用 prompt */
    @NotBlank(message = "prompt不能为空")
    private String prompt;

    /** 租户标识 */
    private String tenantId;

    /** Agent 类型 */
    private String agentType;

    /** 是否启用语义缓存 */
    @Builder.Default
    private boolean useCache = true;
}
