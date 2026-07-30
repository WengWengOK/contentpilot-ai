package com.contentops.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 内容大纲 DTO (content_outline Agent 输出).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentOutline {

    /** 文章标题 */
    private String title;

    /** 引言 / 导语 */
    private String introduction;

    /** 正文章节列表 */
    private List<Section> sections;

    /** 结语 */
    private String conclusion;

    /**
     * 大章节.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Section {

        /** 章节标题 */
        private String heading;

        /** 要点列表 */
        private List<String> bulletPoints;

        /** 章节顺序 (从 1 开始) */
        private Integer order;
    }
}
