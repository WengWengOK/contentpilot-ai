package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.topic.TopicPlanningAgent;
import com.contentops.ai.api.dto.TopicSuggestRequest;
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
 * 选题策划 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/topic")
@RequiredArgsConstructor
public class TopicController {

    private final TopicPlanningAgent topicPlanningAgent;

    /**
     * 生成选题建议。
     *
     * @param request  选题请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @PostMapping("/suggest")
    public ApiResponse<AgentResponse> suggest(@Valid @RequestBody TopicSuggestRequest request,
                                              @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> keywords = request.getKeywords() == null ? List.of() : request.getKeywords();
        String platform = request.getPlatform() == null ? "" : request.getPlatform();
        params.put("keywords", keywords);
        params.put("platform", platform);
        params.put("count", request.getCount());

        AgentRequest agentRequest = AgentRequest.builder()
                .query("topic:" + keywords + ":" + platform)
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = topicPlanningAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
