package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 图片生成请求 DTO (POST /api/v1/image/generate)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerateRequest {

    /** 图片描述 */
    @NotBlank(message = "图片描述不能为空")
    private String description;

    /** 图片风格 */
    @Builder.Default
    private String style = "realistic";
}
