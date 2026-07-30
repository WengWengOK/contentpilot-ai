package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.image.ImageDesignAgent;
import com.contentops.ai.api.dto.ImageGenerateRequest;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配图设计 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageDesignAgent imageDesignAgent;

    /**
     * 生成配图。
     *
     * @param request  图片生成请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @PostMapping("/generate")
    public ApiResponse<AgentResponse> generate(@Valid @RequestBody ImageGenerateRequest request,
                                               @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("description", request.getDescription());
        params.put("style", request.getStyle());

        AgentRequest agentRequest = AgentRequest.builder()
                .query(request.getDescription())
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = imageDesignAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
