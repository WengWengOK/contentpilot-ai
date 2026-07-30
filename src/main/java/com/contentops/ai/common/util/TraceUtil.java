package com.contentops.ai.common.util;

import java.util.UUID;

/**
 * 链路追踪 ID 生成工具.
 */
public final class TraceUtil {

    /** 32 位标准 traceId 长度 (去连字符的 UUID) */
    public static final int TRACE_ID_LENGTH = 32;

    private TraceUtil() {
    }

    /**
     * 生成 32 位无连字符的 traceId.
     *
     * @return traceId 字符串
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 16 位短 traceId, 适用于日志前缀等场景.
     *
     * @return 16 位短 traceId
     */
    public static String shortTraceId() {
        return generateTraceId().substring(0, 16);
    }

    /**
     * 校验 traceId 是否有效.
     *
     * @param traceId 待校验的 traceId
     * @return true 表示有效
     */
    public static boolean isValid(String traceId) {
        return traceId != null && traceId.length() == TRACE_ID_LENGTH
                && traceId.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
    }
}
