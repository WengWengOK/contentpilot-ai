package com.contentops.ai.capability.fallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatResult} 降级链结果单元测试。
 * <p>
 * 纯单元测试, 不依赖 Spring 上下文。ChatResult 为 record, 直接验证工厂方法语义。
 */
@DisplayName("ChatResult 降级链结果测试")
class ChatResultTest {

    @Nested
    @DisplayName("ofModel 创建模型结果")
    class OfModel {

        @Test
        @DisplayName("创建模型命中结果, source 为 model")
        void should_createModelResultWithCorrectSource() {
            ChatResult result = ChatResult.ofModel("模型内容", "gpt-4", false);

            assertThat(result.content()).isEqualTo("模型内容");
            assertThat(result.modelUsed()).isEqualTo("gpt-4");
            assertThat(result.source()).isEqualTo("model");
            assertThat(result.degraded()).isFalse();
        }

        @Test
        @DisplayName("非首选模型命中时 degraded 为 true")
        void should_markDegradedWhenNonPrimaryModel() {
            ChatResult result = ChatResult.ofModel("降级内容", "fallback-model", true);

            assertThat(result.degraded()).isTrue();
            assertThat(result.source()).isEqualTo("model");
            assertThat(result.modelUsed()).isEqualTo("fallback-model");
        }

        @Test
        @DisplayName("首选模型命中时 degraded 为 false")
        void should_markNotDegradedWhenPrimaryModel() {
            ChatResult result = ChatResult.ofModel("内容", "primary-model", false);

            assertThat(result.degraded()).isFalse();
        }

        @Test
        @DisplayName("content 为 null 时仍可创建")
        void should_allowNullContent() {
            ChatResult result = ChatResult.ofModel(null, "gpt-4", false);

            assertThat(result.content()).isNull();
            assertThat(result.modelUsed()).isEqualTo("gpt-4");
            assertThat(result.source()).isEqualTo("model");
        }

        @Test
        @DisplayName("modelUsed 为 null 时仍可创建")
        void should_allowNullModelUsed() {
            ChatResult result = ChatResult.ofModel("内容", null, false);

            assertThat(result.modelUsed()).isNull();
            assertThat(result.content()).isEqualTo("内容");
        }
    }

    @Nested
    @DisplayName("ofCache 创建缓存结果")
    class OfCache {

        @Test
        @DisplayName("创建缓存命中结果, degraded 为 true, source 为 cache")
        void should_createCacheResultWithDegradedTrue() {
            ChatResult result = ChatResult.ofCache("缓存内容", "gpt-4");

            assertThat(result.content()).isEqualTo("缓存内容");
            assertThat(result.modelUsed()).isEqualTo("gpt-4");
            assertThat(result.degraded()).isTrue();
            assertThat(result.source()).isEqualTo("cache");
        }

        @Test
        @DisplayName("缓存结果 source 始终为 cache")
        void should_alwaysHaveCacheSource() {
            ChatResult result = ChatResult.ofCache("内容", "any-model");

            assertThat(result.source()).isEqualTo("cache");
        }

        @Test
        @DisplayName("缓存结果 degraded 始终为 true, 不受 modelUsed 影响")
        void should_alwaysBeDegradedRegardlessOfModel() {
            ChatResult withModel = ChatResult.ofCache("内容", "gpt-4");
            ChatResult withoutModel = ChatResult.ofCache("内容", null);

            assertThat(withModel.degraded()).isTrue();
            assertThat(withoutModel.degraded()).isTrue();
        }

        @Test
        @DisplayName("缓存结果 modelUsed 透传调用方传入的模型名")
        void should_passThroughModelUsed() {
            ChatResult result = ChatResult.ofCache("内容", "claude-3");

            assertThat(result.modelUsed()).isEqualTo("claude-3");
        }
    }

    @Nested
    @DisplayName("ofTemplate 创建模板兜底")
    class OfTemplate {

        @Test
        @DisplayName("创建模板兜底结果, modelUsed 为 none, degraded 为 true")
        void should_createTemplateFallbackResult() {
            ChatResult result = ChatResult.ofTemplate("兜底文案");

            assertThat(result.content()).isEqualTo("兜底文案");
            assertThat(result.modelUsed()).isEqualTo("none");
            assertThat(result.degraded()).isTrue();
            assertThat(result.source()).isEqualTo("template");
        }

        @Test
        @DisplayName("模板兜底不接收 modelUsed 参数, 固定为 none")
        void should_templateAlwaysUseNoneModel() {
            ChatResult result = ChatResult.ofTemplate("默认文案");

            assertThat(result.modelUsed()).isEqualTo("none");
            assertThat(result.source()).isEqualTo("template");
        }

        @Test
        @DisplayName("模板兜底 content 为 null 时仍可创建")
        void should_allowNullContentForTemplate() {
            ChatResult result = ChatResult.ofTemplate(null);

            assertThat(result.content()).isNull();
            assertThat(result.modelUsed()).isEqualTo("none");
            assertThat(result.degraded()).isTrue();
        }
    }

    @Nested
    @DisplayName("三种来源 source 区分")
    class SourceDistinction {

        @Test
        @DisplayName("model / cache / template 三种来源互不相同")
        void should_haveDistinctSources() {
            ChatResult model = ChatResult.ofModel("内容", "gpt-4", false);
            ChatResult cache = ChatResult.ofCache("内容", "gpt-4");
            ChatResult template = ChatResult.ofTemplate("内容");

            assertThat(model.source()).isEqualTo("model");
            assertThat(cache.source()).isEqualTo("cache");
            assertThat(template.source()).isEqualTo("template");
            assertThat(model.source()).isNotEqualTo(cache.source());
            assertThat(cache.source()).isNotEqualTo(template.source());
        }

        @Test
        @DisplayName("cache 与 template 均为降级(degraded=true)")
        void should_cacheAndTemplateBeDegraded() {
            ChatResult cache = ChatResult.ofCache("内容", "gpt-4");
            ChatResult template = ChatResult.ofTemplate("内容");

            assertThat(cache.degraded()).isTrue();
            assertThat(template.degraded()).isTrue();
        }

        @Test
        @DisplayName("template 的 modelUsed 固定为 none, 区别于 model 与 cache")
        void should_templateModelUsedBeNone() {
            ChatResult model = ChatResult.ofModel("内容", "gpt-4", true);
            ChatResult cache = ChatResult.ofCache("内容", "gpt-4");
            ChatResult template = ChatResult.ofTemplate("内容");

            assertThat(template.modelUsed()).isEqualTo("none");
            assertThat(model.modelUsed()).isEqualTo("gpt-4");
            assertThat(cache.modelUsed()).isEqualTo("gpt-4");
        }
    }

    @Nested
    @DisplayName("record 相等性语义")
    class RecordSemantics {

        @Test
        @DisplayName("相同字段值的两个实例相等")
        void should_beEqualWhenFieldsSame() {
            ChatResult r1 = ChatResult.ofModel("内容", "gpt-4", false);
            ChatResult r2 = ChatResult.ofModel("内容", "gpt-4", false);

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("不同 source 的实例不相等")
        void should_notBeEqualWhenSourceDiffers() {
            ChatResult model = ChatResult.ofModel("内容", "gpt-4", true);
            ChatResult cache = ChatResult.ofCache("内容", "gpt-4");

            assertThat(model).isNotEqualTo(cache);
        }

        @Test
        @DisplayName("toString 包含全部字段")
        void should_toStringContainAllFields() {
            ChatResult result = ChatResult.ofModel("内容", "gpt-4", false);

            String str = result.toString();
            assertThat(str).contains("内容", "gpt-4", "model");
        }
    }
}
