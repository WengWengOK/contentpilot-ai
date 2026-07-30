package com.contentops.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceUtil} 链路追踪工具单元测试。
 * <p>
 * 纯单元测试, 不依赖 Spring 上下文。
 */
@DisplayName("TraceUtil 链路追踪工具测试")
class TraceUtilTest {

    @Nested
    @DisplayName("generateTraceId 生成 32 位 traceId")
    class GenerateTraceId {

        @Test
        @DisplayName("返回 32 位无连字符的 traceId")
        void should_return32CharsWithoutHyphens() {
            String traceId = TraceUtil.generateTraceId();

            assertThat(traceId).hasSize(TraceUtil.TRACE_ID_LENGTH);
            assertThat(traceId).doesNotContain("-");
        }

        @Test
        @DisplayName("仅包含小写十六进制字符(0-9, a-f)")
        void should_containOnlyLowerHexChars() {
            String traceId = TraceUtil.generateTraceId();

            assertThat(traceId).matches("^[0-9a-f]{32}$");
        }

        @Test
        @DisplayName("每次调用生成不同的 traceId")
        void should_generateDifferentTraceIds() {
            String t1 = TraceUtil.generateTraceId();
            String t2 = TraceUtil.generateTraceId();

            assertThat(t1).isNotEqualTo(t2);
        }

        @Test
        @DisplayName("生成的 traceId 通过 isValid 校验")
        void should_generateValidTraceId() {
            String traceId = TraceUtil.generateTraceId();

            assertThat(TraceUtil.isValid(traceId)).isTrue();
        }

        @Test
        @DisplayName("连续多次生成均通过校验")
        void should_allGeneratedTraceIdsBeValid() {
            for (int i = 0; i < 50; i++) {
                assertThat(TraceUtil.isValid(TraceUtil.generateTraceId())).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("shortTraceId 生成 16 位短 traceId")
    class ShortTraceId {

        @Test
        @DisplayName("返回 16 位短 traceId")
        void should_return16Chars() {
            String shortId = TraceUtil.shortTraceId();

            assertThat(shortId).hasSize(16);
        }

        @Test
        @DisplayName("仅包含小写十六进制字符")
        void should_containOnlyLowerHexChars() {
            String shortId = TraceUtil.shortTraceId();

            assertThat(shortId).matches("^[0-9a-f]{16}$");
        }

        @Test
        @DisplayName("短 traceId 不包含连字符")
        void should_notContainHyphens() {
            String shortId = TraceUtil.shortTraceId();

            assertThat(shortId).doesNotContain("-");
        }

        @Test
        @DisplayName("短 traceId(16 位) 不通过 32 位 isValid 校验")
        void should_shortIdNotPassFullValidation() {
            String shortId = TraceUtil.shortTraceId();

            assertThat(TraceUtil.isValid(shortId)).isFalse();
        }

        @Test
        @DisplayName("每次调用生成不同的短 traceId")
        void should_generateDifferentShortTraceIds() {
            String t1 = TraceUtil.shortTraceId();
            String t2 = TraceUtil.shortTraceId();

            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("isValid 校验")
    class IsValid {

        @Test
        @DisplayName("合法 32 位小写 hex 通过校验")
        void should_returnTrueForValidTraceId() {
            assertThat(TraceUtil.isValid("0123456789abcdef0123456789abcdef")).isTrue();
        }

        @Test
        @DisplayName("全 0 的 32 位字符串通过校验")
        void should_returnTrueForAllZeros() {
            assertThat(TraceUtil.isValid("00000000000000000000000000000000")).isTrue();
        }

        @Test
        @DisplayName("全 f 的 32 位字符串通过校验")
        void should_returnTrueForAllF() {
            assertThat(TraceUtil.isValid("ffffffffffffffffffffffffffffffff")).isTrue();
        }

        @Test
        @DisplayName("null 返回 false")
        void should_returnFalseForNull() {
            assertThat(TraceUtil.isValid(null)).isFalse();
        }

        @Test
        @DisplayName("长度不足 32 返回 false")
        void should_returnFalseForShortLength() {
            assertThat(TraceUtil.isValid("0123456789abcdef")).isFalse();
        }

        @Test
        @DisplayName("长度超过 32 返回 false")
        void should_returnFalseForLongLength() {
            assertThat(TraceUtil.isValid("0123456789abcdef0123456789abcdef00")).isFalse();
        }

        @Test
        @DisplayName("包含连字符返回 false")
        void should_returnFalseForHyphens() {
            assertThat(TraceUtil.isValid("01234567-89ab-cdef-0123-456789abcdef")).isFalse();
        }

        @Test
        @DisplayName("包含大写字母返回 false")
        void should_returnFalseForUpperCase() {
            assertThat(TraceUtil.isValid("0123456789ABCDEF0123456789abcdef")).isFalse();
        }

        @Test
        @DisplayName("包含非十六进制字符(g)返回 false")
        void should_returnFalseForNonHexChars() {
            assertThat(TraceUtil.isValid("0123456789abcdeg0123456789abcdef")).isFalse();
        }

        @ParameterizedTest(name = "非法 traceId [{0}] 返回 false")
        @ValueSource(strings = {
                "",
                "   ",
                "xyz",
                "0123456789abcdef0123456789abcde",
                "gggggggggggggggggggggggggggggggg",
                "0123456789ABCDEF0123456789ABCDEF"
        })
        @DisplayName("多种非法输入均返回 false")
        void should_returnFalseForVariousInvalidInputs(String input) {
            assertThat(TraceUtil.isValid(input)).isFalse();
        }
    }

    @Test
    @DisplayName("TRACE_ID_LENGTH 常量值为 32")
    void should_traceIdLengthConstantBe32() {
        assertThat(TraceUtil.TRACE_ID_LENGTH).isEqualTo(32);
    }
}
