package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 数据分析请求 DTO (GET /api/v1/analysis/monthly)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRequest {

    /** 统计起始日期 (含) */
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    /** 统计结束日期 (含) */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
