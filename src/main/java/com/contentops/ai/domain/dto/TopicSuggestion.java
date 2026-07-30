package com.contentops.ai.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 选题建议 DTO (topic_suggestion Agent 输出).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicSuggestion {

    /** 选题标题 */
    private String title;

    /** 选题摘要 / 角度说明 */
    private String summary;

    /** 关键词列表 */
    private List<String> keywords;

    /** 所属分类 */
    private String category;

    /** 热度评分 (0.0 ~ 1.0) */
    private Double trendingScore;
}
