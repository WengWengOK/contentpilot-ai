package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 选题建议请求 DTO (POST /api/v1/topic/suggest)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSuggestRequest {

    /** 选题关键词列表 */
    @NotEmpty(message = "关键词不能为空")
    private List<String> keywords;

    /** 目标平台 */
    private String platform;

    /** 需求选题数量 */
    @Positive(message = "数量必须为正整数")
    @Builder.Default
    private int count = 1;
}
