package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.publish.PublishAgent;
import com.contentops.ai.api.dto.PublishRequest;
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
 * 多平台发布 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishAgent publishAgent;

    /**
     * 多平台发布。
     *
     * @param request  发布请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @PostMapping("/multi-platform")
    public ApiResponse<AgentResponse> multiPlatform(@Valid @RequestBody PublishRequest request,
                                                    @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("content", request.getContent());
        params.put("platforms", request.getPlatforms());

        AgentRequest agentRequest = AgentRequest.builder()
                .query("publish:" + request.getPlatforms() + ":" + Math.abs((long) request.getContent().hashCode()))
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = publishAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
