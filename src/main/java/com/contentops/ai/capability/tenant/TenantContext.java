package com.contentops.ai.capability.tenant;

/**
 * 多租户上下文(基于 ThreadLocal)。
 * <p>
 * 由 {@code TenantInterceptor} 在请求入口从 JWT / {@code X-Tenant-Id} 中解析 tenantId 并注入,
 * 全链路(检索 payload 过滤、数据库行级隔离、评估记录写入等)共享当前租户。
 * 同时持有当前请求的 userId (用于审计与权限判定)。
 * 请求结束时应调用 {@link #clear()} 释放 (会清除 tenantId 与 userId),
 * 防止线程复用导致的租户串号。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }

    /**
     * 解析租户编码字符串(供 Controller / Gateway 统一复用, 避免各处重复实现)。
     *
     * <p>解析顺序: 显式传入的 header tenantId -> {@link #getTenantId()} 上下文 -> "default"。
     * 与各 Controller 原有 resolveTenant 逻辑保持一致。</p>
     *
     * @param headerTenantId 请求头透传的租户标识(可为 null)
     * @return 租户编码字符串
     */
    public static String resolveTenantCode(String headerTenantId) {
        if (headerTenantId != null && !headerTenantId.isBlank()) {
            return headerTenantId;
        }
        Long ctx = getTenantId();
        return ctx != null ? String.valueOf(ctx) : "default";
    }
}
