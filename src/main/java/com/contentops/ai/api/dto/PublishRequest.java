package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 多平台发布请求 DTO (POST /api/v1/publish/multi-platform)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishRequest {

    /** 待发布内容 */
    @NotBlank(message = "发布内容不能为空")
    private String content;

    /** 目标平台列表 */
    @NotEmpty(message = "目标平台不能为空")
    private List<String> platforms;
}
