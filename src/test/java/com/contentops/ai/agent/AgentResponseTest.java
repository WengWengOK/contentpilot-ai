package com.contentops.ai.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentResponse} Agent 响应 DTO 单元测试。
 * <p>
 * 纯单元测试, 不依赖 Spring 上下文。验证 cached 与 success 工厂方法的字段语义。
 */
@DisplayName("AgentResponse 响应 DTO 测试")
class AgentResponseTest {

    @Nested
    @DisplayName("cached 缓存命中响应")
    class Cached {

        @Test
        @DisplayName("cached 方法创建缓存命中响应, cacheHit 为 true")
        void should_createCachedResponseWithCacheHitTrue() {
            Object data = "缓存数据";

            AgentResponse response = AgentResponse.cached(data, "trace-001");

            assertThat(response.getData()).isEqualTo("缓存数据");
            assertThat(response.isCacheHit()).isTrue();
            assertThat(response.getTraceId()).isEqualTo("trace-001");
        }

        @Test
        @DisplayName("缓存命中响应 tokensUsed 为 0")
        void should_haveZeroTokensForCachedResponse() {
            AgentResponse response = AgentResponse.cached("数据", "trace-002");

            assertThat(response.getTokensUsed()).isZero();
        }

        @Test
        @DisplayName("缓存命中响应 modelUsed 与 evaluation 为 null")
        void should_haveNullModelAndEvaluationForCached() {
            AgentResponse response = AgentResponse.cached("数据", "trace-003");

            assertThat(response.getModelUsed()).isNull();
            assertThat(response.getEvaluation()).isNull();
        }

        @Test
        @DisplayName("缓存命中响应 data 为 null 时仍可创建")
        void should_allowNullDataForCached() {
            AgentResponse response = AgentResponse.cached(null, "trace-004");

            assertThat(response.getData()).isNull();
            assertThat(response.isCacheHit()).isTrue();
            assertThat(response.getTraceId()).isEqualTo("trace-004");
        }

        @Test
        @DisplayName("缓存命中响应 traceId 为 null 时仍可创建")
        void should_allowNullTraceIdForCached() {
            AgentResponse response = AgentResponse.cached("数据", null);

            assertThat(response.getTraceId()).isNull();
            assertThat(response.isCacheHit()).isTrue();
        }

        @Test
        @DisplayName("缓存命中响应 data 可为任意对象类型")
        void should_acceptAnyDataTypeForCached() {
            Map<String, Object> complexData = Map.of("title", "标题", "count", 3);

            AgentResponse response = AgentResponse.cached(complexData, "trace-005");

            assertThat(response.getData()).isSameAs(complexData);
        }
    }

    @Nested
    @DisplayName("success 成功响应")
    class Success {

        @Test
        @DisplayName("success 方法创建成功响应, cacheHit 为 false")
        void should_createSuccessResponseWithCacheHitFalse() {
            Map<String, Double> evaluation = Map.of("faithfulness", 0.95, "relevancy", 0.88);

            AgentResponse response = AgentResponse.success("结果", "gpt-4", 1024, "trace-006", evaluation);

            assertThat(response.getData()).isEqualTo("结果");
            assertThat(response.getModelUsed()).isEqualTo("gpt-4");
            assertThat(response.isCacheHit()).isFalse();
            assertThat(response.getTokensUsed()).isEqualTo(1024);
            assertThat(response.getTraceId()).isEqualTo("trace-006");
            assertThat(response.getEvaluation())
                    .containsEntry("faithfulness", 0.95)
                    .containsEntry("relevancy", 0.88);
        }

        @Test
        @DisplayName("无评估指标时 evaluation 为 null")
        void should_allowNullEvaluation() {
            AgentResponse response = AgentResponse.success("结果", "gpt-4", 512, "trace-007", null);

            assertThat(response.getEvaluation()).isNull();
            assertThat(response.isCacheHit()).isFalse();
            assertThat(response.getTokensUsed()).isEqualTo(512);
        }

        @Test
        @DisplayName("tokensUsed 为 0 的成功响应")
        void should_allowZeroTokens() {
            AgentResponse response = AgentResponse.success("结果", "gpt-4", 0, "trace-008", null);

            assertThat(response.getTokensUsed()).isZero();
        }

        @Test
        @DisplayName("tokensUsed 为负数时不做校验(透传)")
        void should_allowNegativeTokens() {
            AgentResponse response = AgentResponse.success("结果", "gpt-4", -1, "trace-009", null);

            assertThat(response.getTokensUsed()).isEqualTo(-1);
        }

        @Test
        @DisplayName("data 可为任意对象类型")
        void should_acceptAnyObjectType() {
            Map<String, Object> complexData = Map.of("title", "标题", "sections", 3);

            AgentResponse response = AgentResponse.success(complexData, "gpt-4", 100, "trace-010", null);

            assertThat(response.getData()).isSameAs(complexData);
        }

        @Test
        @DisplayName("evaluation 包含多个 RAGAS 指标")
        void should_carryMultipleEvaluationMetrics() {
            Map<String, Double> evaluation = Map.of(
                    "faithfulness", 0.92,
                    "answerRelevancy", 0.88,
                    "contextPrecision", 0.95);

            AgentResponse response = AgentResponse.success("结果", "gpt-4", 100, "trace-011", evaluation);

            assertThat(response.getEvaluation()).hasSize(3);
            assertThat(response.getEvaluation())
                    .containsKeys("faithfulness", "answerRelevancy", "contextPrecision");
        }
    }

    @Nested
    @DisplayName("字段语义对比")
    class FieldSemantics {

        @Test
        @DisplayName("cached 与 success 的 cacheHit 互斥")
        void should_cachedAndSuccessHaveOppositeCacheHit() {
            AgentResponse cached = AgentResponse.cached("数据", "trace");
            AgentResponse success = AgentResponse.success("数据", "gpt-4", 100, "trace", null);

            assertThat(cached.isCacheHit()).isTrue();
            assertThat(success.isCacheHit()).isFalse();
        }

        @Test
        @DisplayName("traceId 在 cached 与 success 中均被正确设置")
        void should_traceIdSetInBoth() {
            AgentResponse cached = AgentResponse.cached("数据", "trace-cached");
            AgentResponse success = AgentResponse.success("数据", "gpt-4", 100, "trace-success", null);

            assertThat(cached.getTraceId()).isEqualTo("trace-cached");
            assertThat(success.getTraceId()).isEqualTo("trace-success");
        }

        @Test
        @DisplayName("cached 不携带 modelUsed, success 携带 modelUsed")
        void should_cachedNotCarryModelWhileSuccessDoes() {
            AgentResponse cached = AgentResponse.cached("数据", "trace");
            AgentResponse success = AgentResponse.success("数据", "gpt-4", 100, "trace", null);

            assertThat(cached.getModelUsed()).isNull();
            assertThat(success.getModelUsed()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("cached 的 tokensUsed 为 0, success 透传实际 token 数")
        void should_cachedZeroTokensWhileSuccessCarriesActual() {
            AgentResponse cached = AgentResponse.cached("数据", "trace");
            AgentResponse success = AgentResponse.success("数据", "gpt-4", 2048, "trace", null);

            assertThat(cached.getTokensUsed()).isZero();
            assertThat(success.getTokensUsed()).isEqualTo(2048);
        }
    }
}
