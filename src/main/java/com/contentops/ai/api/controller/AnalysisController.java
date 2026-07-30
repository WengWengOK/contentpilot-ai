package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.analysis.DataAnalysisAgent;
import com.contentops.ai.api.dto.AnalysisRequest;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据分析 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final DataAnalysisAgent dataAnalysisAgent;

    /**
     * 月度数据分析。
     *
     * @param request  分析请求 (startDate / endDate, 由 query 参数绑定)
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @GetMapping("/monthly")
    public ApiResponse<AgentResponse> monthly(@Valid AnalysisRequest request,
                                              @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("startDate", request.getStartDate());
        params.put("endDate", request.getEndDate());

        AgentRequest agentRequest = AgentRequest.builder()
                .query("analysis:" + request.getStartDate() + ":" + request.getEndDate())
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = dataAnalysisAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
