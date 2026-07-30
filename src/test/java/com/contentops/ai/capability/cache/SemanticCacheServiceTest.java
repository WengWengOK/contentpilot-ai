package com.contentops.ai.capability.cache;

import com.contentops.ai.infrastructure.qdrant.QdrantVectorStoreService;
import com.contentops.ai.infrastructure.qdrant.VectorSearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 语义缓存服务单元测试.
 *
 * <p>覆盖缓存命中、缓存未命中、过期淘汰、空输入防御、异常容错等场景。</p>
 */
@DisplayName("语义缓存服务测试")
@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

    @Mock
    private QdrantVectorStoreService vectorStoreService;

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private SemanticCacheService semanticCacheService;

    private static final String TENANT_ID = "tenant-001";
    private static final String QUERY = "如何优化RAG检索质量";
    private static final String ANSWER = "可以通过混合检索、重排序和语义分块来优化";
    private static final float[] EMBEDDING = new float[]{0.1f, 0.2f, 0.3f};

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(semanticCacheService, "similarityThreshold", 0.92);
        ReflectionTestUtils.setField(semanticCacheService, "ttlHours", 24L);
    }

    @Nested
    @DisplayName("缓存读取 (get)")
    class GetTest {

        @Test
        @DisplayName("命中未过期的缓存时返回答案")
        void should_returnAnswer_when_cacheHitAndNotExpired() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            long futureExpiry = System.currentTimeMillis() + 3600_000L;
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("answer", ANSWER, "expires_at", futureExpiry));
            when(vectorStoreService.search(eq(EMBEDDING), eq("semantic_cache"),
                    eq(TENANT_ID), eq(1), eq(0.92)))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("命中但已过期的缓存返回empty")
        void should_returnEmpty_when_cacheHitButExpired() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            long pastExpiry = System.currentTimeMillis() - 3600_000L;
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("answer", ANSWER, "expires_at", pastExpiry));
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("缓存未命中返回empty")
        void should_returnEmpty_when_cacheMiss() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of());

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空查询返回empty且不调用embedding")
        void should_returnEmpty_when_queryIsBlank() {
            Optional<String> result = semanticCacheService.get("", TENANT_ID);

            assertThat(result).isEmpty();
            verifyNoInteractions(embeddingModel, vectorStoreService);
        }

        @Test
        @DisplayName("null查询返回empty")
        void should_returnEmpty_when_queryIsNull() {
            Optional<String> result = semanticCacheService.get(null, TENANT_ID);

            assertThat(result).isEmpty();
            verifyNoInteractions(embeddingModel, vectorStoreService);
        }

        @Test
        @DisplayName("embedding为空时返回empty")
        void should_returnEmpty_when_embeddingIsEmpty() {
            when(embeddingModel.embed(QUERY)).thenReturn(new float[0]);

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
            verify(vectorStoreService, never()).search(any(), anyString(), anyString(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("embedding为null时返回empty")
        void should_returnEmpty_when_embeddingIsNull() {
            when(embeddingModel.embed(QUERY)).thenReturn(null);

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
            verify(vectorStoreService, never()).search(any(), anyString(), anyString(), anyInt(), anyDouble());
        }

        @Test
        @DisplayName("payload中answer为null时返回empty")
        void should_returnEmpty_when_answerFieldIsNull() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            long futureExpiry = System.currentTimeMillis() + 3600_000L;
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("expires_at", futureExpiry));
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("payload为null时返回empty")
        void should_returnEmpty_when_payloadIsNull() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95, null);
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("expires_at为字符串类型时正确解析")
        void should_parseExpiresAt_when_itIsString() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            long futureExpiry = System.currentTimeMillis() + 3600_000L;
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("answer", ANSWER, "expires_at", String.valueOf(futureExpiry)));
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("expires_at为非法字符串时视为不过期")
        void should_treatAsNeverExpires_when_expiresAtIsInvalidString() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("answer", ANSWER, "expires_at", "invalid"));
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("无expires_at字段时视为不过期")
        void should_treatAsNeverExpires_when_noExpiresAtField() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            VectorSearchHit hit = new VectorSearchHit("point-1", 0.95,
                    Map.of("answer", ANSWER));
            when(vectorStoreService.search(any(), anyString(), anyString(), anyInt(), anyDouble()))
                    .thenReturn(List.of(hit));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(ANSWER);
        }

        @Test
        @DisplayName("检索异常时返回empty不影响主流程")
        void should_returnEmpty_when_exceptionThrown() {
            when(embeddingModel.embed(QUERY)).thenThrow(new RuntimeException("Qdrant connection refused"));

            Optional<String> result = semanticCacheService.get(QUERY, TENANT_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("缓存写入 (put)")
    class PutTest {

        @Test
        @DisplayName("正常写入缓存")
        void should_writeCache_when_normalInput() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);

            semanticCacheService.put(QUERY, ANSWER, TENANT_ID);

            verify(vectorStoreService).upsert(anyString(), eq(EMBEDDING),
                    eq("semantic_cache"), argThat(payload ->
                    TENANT_ID.equals(payload.get("tenant_id")) &&
                    QUERY.equals(payload.get("query")) &&
                    ANSWER.equals(payload.get("answer")) &&
                    payload.get("expires_at") instanceof Long));
        }

        @Test
        @DisplayName("空查询不写入缓存")
        void should_skipWrite_when_queryIsBlank() {
            semanticCacheService.put("", ANSWER, TENANT_ID);

            verifyNoInteractions(embeddingModel, vectorStoreService);
        }

        @Test
        @DisplayName("null查询不写入缓存")
        void should_skipWrite_when_queryIsNull() {
            semanticCacheService.put(null, ANSWER, TENANT_ID);

            verifyNoInteractions(embeddingModel, vectorStoreService);
        }

        @Test
        @DisplayName("null答案不写入缓存")
        void should_skipWrite_when_answerIsNull() {
            semanticCacheService.put(QUERY, null, TENANT_ID);

            verifyNoInteractions(embeddingModel, vectorStoreService);
        }

        @Test
        @DisplayName("embedding为空时不写入缓存")
        void should_skipWrite_when_embeddingIsEmpty() {
            when(embeddingModel.embed(QUERY)).thenReturn(new float[0]);

            semanticCacheService.put(QUERY, ANSWER, TENANT_ID);

            verify(vectorStoreService, never()).upsert(anyString(), any(), anyString(), anyMap());
        }

        @Test
        @DisplayName("写入异常不影响主流程")
        void should_notThrow_when_writeFails() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);
            doThrow(new RuntimeException("Qdrant write failed"))
                    .when(vectorStoreService).upsert(anyString(), any(), anyString(), anyMap());

            org.assertj.core.api.Assertions.assertThatCode(() ->
                    semanticCacheService.put(QUERY, ANSWER, TENANT_ID))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null tenantId时写入空字符串作为tenant_id")
        void should_useEmptyString_when_tenantIdIsNull() {
            when(embeddingModel.embed(QUERY)).thenReturn(EMBEDDING);

            semanticCacheService.put(QUERY, ANSWER, null);

            verify(vectorStoreService).upsert(anyString(), eq(EMBEDDING),
                    eq("semantic_cache"), argThat(payload -> "".equals(payload.get("tenant_id"))));
        }
    }
}
