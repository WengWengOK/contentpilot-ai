package com.contentops.ai.api.controller;

import com.contentops.ai.capability.ratelimit.TokenLimiter;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配额查询 API 控制器.
 *
 * <p>查询当前租户的 Token 配额使用情况。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final TokenLimiter tokenLimiter;
    private final TenantRepository tenantRepository;

    /**
     * 查询当前租户配额使用情况。
     *
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return 配额使用信息
     */
    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(
            @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        String tenantCode = TenantContext.resolveTenantCode(tenantId);
        long remaining = tokenLimiter.getRemainingQuota(tenantCode);
        int dailyQuota = resolveDailyQuota(tenantCode);
        long used = Math.max(0, (long) dailyQuota - remaining);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantCode);
        result.put("dailyQuota", dailyQuota);
        result.put("used", used);
        result.put("remaining", remaining);
        return ApiResponse.ok(result);
    }

    private int resolveDailyQuota(String tenantCode) {
        try {
            return tenantRepository.findByTenantCode(tenantCode)
                    .map(Tenant::getDailyQuota)
                    .filter(q -> q != null && q > 0)
                    .orElse(TokenLimiter.DEFAULT_DAILY_QUOTA);
        } catch (Exception e) {
            log.warn("解析租户配额失败, tenantCode={}: {}", tenantCode, e.getMessage(), e);
            return TokenLimiter.DEFAULT_DAILY_QUOTA;
        }
    }
}
