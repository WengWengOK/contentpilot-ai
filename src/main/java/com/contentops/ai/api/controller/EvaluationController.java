package com.contentops.ai.api.controller;

import com.contentops.ai.capability.evaluation.EvaluationRepository;
import com.contentops.ai.capability.evaluation.RagasEvaluation;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估报告 API 控制器.
 *
 * <p>查询 RAGAS 评估记录 (从 ragas_evaluation 表按租户与时间范围查询)。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationRepository evaluationRepository;
    private final TenantRepository tenantRepository;

    /**
     * 查询评估报告。
     *
     * @param startDate 起始日期 (含)
     * @param endDate   结束日期 (含)
     * @param tenantId  租户标识 (请求头 X-Tenant-Id)
     * @return 评估记录列表
     */
    @GetMapping("/report")
    public ApiResponse<List<RagasEvaluation>> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        Long tenantIdLong = resolveTenantIdLong(tenantId);
        if (tenantIdLong == null) {
            return ApiResponse.ok(List.of());
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        List<RagasEvaluation> reports = evaluationRepository
                .findByTenantIdAndCreatedAtBetween(tenantIdLong, start, end);
        return ApiResponse.ok(reports);
    }

    private Long resolveTenantIdLong(String tenantId) {
        Long ctx = TenantContext.getTenantId();
        if (ctx != null) {
            return ctx;
        }
        String code = TenantContext.resolveTenantCode(tenantId);
        try {
            return tenantRepository.findByTenantCode(code)
                    .map(Tenant::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("解析租户ID失败, tenantCode={}: {}", code, e.getMessage(), e);
            return null;
        }
    }
}
