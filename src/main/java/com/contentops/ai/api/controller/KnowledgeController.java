package com.contentops.ai.api.controller;

import com.contentops.ai.api.dto.KnowledgeUploadRequest;
import com.contentops.ai.capability.retrieval.Document;
import com.contentops.ai.capability.retrieval.HybridRetriever;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import com.contentops.ai.domain.entity.KnowledgeDoc;
import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.KnowledgeDocRepository;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库 API 控制器.
 *
 * <p>上传知识文档: 落库 PostgreSQL + 向量化存入 Qdrant;
 * 搜索知识库: 使用 {@link HybridRetriever} 混合检索。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocRepository knowledgeDocRepository;
    private final TenantRepository tenantRepository;
    private final EmbeddingModel embeddingModel;
    private final QdrantVectorStoreService qdrantVectorStoreService;
    private final HybridRetriever hybridRetriever;

    @Value("${contentops.qdrant.collection:knowledge_vectors}")
    private String collection;

    /**
     * 上传知识文档 (保存到 PG + 向量化存入 Qdrant)。
     *
     * @param request  上传请求
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@Valid @RequestBody KnowledgeUploadRequest request,
                                                   @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        String tenantCode = TenantContext.resolveTenantCode(tenantId);
        Long tenantIdLong = resolveTenantIdLong(tenantCode);
        if (tenantIdLong == null) {
            return ApiResponse.fail(400, "租户不存在: " + tenantCode);
        }

        // 1. 落库 PostgreSQL
        KnowledgeDoc doc = KnowledgeDoc.builder()
                .tenantId(tenantIdLong)
                .title(request.getTitle())
                .content(request.getContent())
                .docType(request.getDocType())
                .build();
        KnowledgeDoc saved = knowledgeDocRepository.save(doc);

        // 2. 向量化并写入 Qdrant
        String vectorId = UUID.randomUUID().toString();
        boolean vectorized = false;
        try {
            // Spring AI 1.0.0 GA: EmbeddingModel.embed(String) 直接返回 float[]
            float[] vector = embeddingModel.embed(request.getContent());
            if (vector == null || vector.length == 0) {
                log.warn("知识文档向量化返回空向量, docId={}", saved.getId());
            } else {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("tenant_id", tenantCode);
                payload.put("doc_id", saved.getId());
                payload.put("title", saved.getTitle());
                payload.put("content", saved.getContent());
                qdrantVectorStoreService.upsert(vectorId, vector, collection, payload);

                saved.setVectorId(vectorId);
                knowledgeDocRepository.save(saved);
                vectorized = true;
            }
        } catch (Exception e) {
            log.warn("知识文档向量化失败, docId={}: {}", saved.getId(), e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("title", saved.getTitle());
        result.put("vectorId", vectorId);
        result.put("vectorized", vectorized);
        return ApiResponse.ok(result);
    }

    /**
     * 搜索知识库 (混合检索)。
     *
     * @param query    查询文本
     * @param topK     返回数量 (非法值归一化为默认 5, 避免负数触发 DB/检索异常)
     * @param tenantId 租户标识 (请求头 X-Tenant-Id)
     * @return 检索结果列表
     */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam String query,
                                                         @RequestParam(defaultValue = "5") int topK,
                                                         @RequestHeader(value = AiConstants.TENANT_HEADER, required = false) String tenantId) {
        // 防御性归一化: topK<=0 会导致 BM25 LIMIT ? 与 rerank limit 异常, 此处统一兜底
        int safeTopK = topK <= 0 ? 5 : topK;
        String tenantCode = TenantContext.resolveTenantCode(tenantId);
        List<Document> docs = hybridRetriever.retrieve(query, tenantCode, safeTopK);

        List<Map<String, Object>> result = docs.stream()
                .map(this::toResultMap)
                .toList();
        return ApiResponse.ok(result);
    }

    private Map<String, Object> toResultMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", doc.getId());
        map.put("content", doc.getContent());
        map.put("score", doc.getScore());
        map.put("metadata", doc.getMetadata());
        map.put("source", doc.getSource());
        return map;
    }

    private Long resolveTenantIdLong(String tenantCode) {
        Long ctx = TenantContext.getTenantId();
        if (ctx != null) {
            return ctx;
        }
        try {
            return tenantRepository.findByTenantCode(tenantCode)
                    .map(Tenant::getId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("解析租户ID失败, tenantCode={}: {}", tenantCode, e.getMessage(), e);
            return null;
        }
    }
}
