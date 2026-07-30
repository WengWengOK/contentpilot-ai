package com.contentops.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 知识文档上传请求 DTO (POST /api/v1/knowledge/upload)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeUploadRequest {

    /** 文档标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 文档正文 */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 文档类型 */
    @Builder.Default
    private String docType = "article";
}
