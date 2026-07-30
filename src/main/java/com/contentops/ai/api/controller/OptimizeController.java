package com.contentops.ai.api.controller;

import com.contentops.ai.agent.AgentRequest;
import com.contentops.ai.agent.AgentResponse;
import com.contentops.ai.agent.optimize.OptimizeAgent;
import com.contentops.ai.api.dto.OptimizeRequest;
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
 * 优化迭代 API 控制器。
 */
@RestController
@RequestMapping("/api/v1/optimize")
@RequiredArgsConstructor
public class OptimizeController {

    private final OptimizeAgent optimizeAgent;

    /**
     * 生成优化策略。
     *
     * @param request  优化请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return Agent 响应
     */
    @PostMapping("/strategy")
    public ApiResponse<AgentResponse> strategy(@Valid @RequestBody OptimizeRequest request,
                                               @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("analysisData", request.getAnalysisData() == null ? Map.of() : request.getAnalysisData());

        AgentRequest agentRequest = AgentRequest.builder()
                .query("optimize:" + (request.getAnalysisData() == null ? "" : request.getAnalysisData().hashCode()))
                .tenantId(TenantContext.resolveTenantCode(tenantId))
                .params(params)
                .build();

        AgentResponse response = optimizeAgent.run(agentRequest);
        return ApiResponse.ok(response, response.getTraceId());
    }
}
