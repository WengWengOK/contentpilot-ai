package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.content.ContentCreationAgent;
import com.contentops.ai.api.dto.ContentCreateRequest;
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
import java.util.List;
import java.util.Map;

/**
 * 内容创作 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentCreationAgent contentCreationAgent;

    /**
     * 生成内容大纲。
     *
     * @param request  内容创作请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @PostMapping("/create")
    public ApiResponse<AgentResponse> create(@Valid @RequestBody ContentCreateRequest request,
                                             @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topic", request.getTopic());
        params.put("keywords", request.getKeywords() == null ? List.of() : request.getKeywords());
        params.put("platform", request.getPlatform());

        AgentRequest agentRequest = AgentRequest.builder()
                .query("content:" + request.getTopic() + ":" + request.getKeywords())
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = contentCreationAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
