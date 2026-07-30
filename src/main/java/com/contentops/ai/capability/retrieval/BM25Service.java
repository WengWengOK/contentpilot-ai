package com.contentops.ai.capability.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BM25 关键词检索服务。
 * <p>
 * 基于 PostgreSQL 的 tsvector 全文索引实现, 使用 ts_rank_cd 计算相关性分数。
 * knowledge_doc 表的 tsv 列由 {@code to_tsvector('simple', title || ' ' || content)} 生成并建 GIN 索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BM25Service {

    private final JdbcTemplate jdbcTemplate;

    /** 全文检索配置(与 knowledge_doc.tsv 生成时一致) */
    @Value("${contentops.bm25.ts-config:simple}")
    private String tsConfig;

    /** 允许的全文检索配置白名单(防御纵深: 即使配置被污染也无法注入 SQL) */
    private static final Set<String> ALLOWED_TS_CONFIGS =
            Set.of("simple", "english", "chinese_zh", "pg_catalog.simple", "pg_catalog.english");

    /**
     * BM25(全文检索)查询。
     *
     * @param query    查询文本
     * @param tenantId 租户ID(行级隔离)
     * @param topK     返回数量
     * @return 检索到的文档列表(按 ts_rank_cd 降序), 失败时返回空列表
     */
    public List<Document> search(String query, String tenantId, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Long tenantIdLong = Long.parseLong(tenantId);
            String safeTsConfig = resolveSafeTsConfig();

            // 全部参数化: tsConfig 通过 ?::regconfig 绑定并经白名单校验, query/tenantId/topK 均为占位符, 杜绝 SQL 注入
            String sql = "SELECT id, title, content, "
                    + "ts_rank_cd(tsv, plainto_tsquery(?::regconfig, ?)) AS rank "
                    + "FROM knowledge_doc "
                    + "WHERE tenant_id = ? AND tsv @@ plainto_tsquery(?::regconfig, ?) "
                    + "ORDER BY rank DESC "
                    + "LIMIT ?";

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("docId", rs.getLong("id"));
                        metadata.put("title", rs.getString("title"));
                        return Document.builder()
                                .id(String.valueOf(rs.getLong("id")))
                                .content(rs.getString("content"))
                                .score(rs.getDouble("rank"))
                                .metadata(metadata)
                                .source(Document.Source.BM25)
                                .build();
                    },
                    safeTsConfig, query, tenantIdLong, safeTsConfig, query, topK);
        } catch (NumberFormatException e) {
            log.warn("Invalid tenantId for BM25 search '{}': {}", tenantId, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("BM25 search failed for tenant {}: {}", tenantId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析并校验 tsConfig: 仅允许白名单内的配置名, 否则回退为 simple。
     * 通过 ?::regconfig 参数绑定时 PostgreSQL 会再次校验 regconfig 合法性, 实现双重防护。
     */
    private String resolveSafeTsConfig() {
        String config = tsConfig == null || tsConfig.isBlank() ? "simple" : tsConfig.trim();
        if (!ALLOWED_TS_CONFIGS.contains(config)) {
            log.warn("非法的 ts-config 值 '{}', 回退为 'simple'", config);
            return "simple";
        }
        return config;
    }
}
