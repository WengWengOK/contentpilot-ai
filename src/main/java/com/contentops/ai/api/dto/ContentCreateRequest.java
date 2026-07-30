package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 内容创作请求 DTO (POST /api/v1/content/create)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCreateRequest {

    /** 选题标题 */
    @NotBlank(message = "选题不能为空")
    private String topic;

    /** 关键词列表 */
    private List<String> keywords;

    /** 目标平台 */
    private String platform;
}
