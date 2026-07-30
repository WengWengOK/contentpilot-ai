package com.contentops.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * 策略优化请求 DTO (POST /api/v1/optimize/strategy)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizeRequest {

    /** 分析数据 (由 DataAnalysisAgent 产出, 或外部传入) */
    private Map<String, Object> analysisData;
}
