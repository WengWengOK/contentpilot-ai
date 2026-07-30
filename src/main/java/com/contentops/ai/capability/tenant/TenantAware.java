package com.contentops.ai.capability.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要租户上下文的方法 / 类。
 *
 * <p>由 {@link TenantFilterAspect} 切面拦截: 调用标注方法时校验
 * {@link TenantContext#getTenantId()} 不为空, 缺失则抛出 403 异常,
 * 防止越权跨租户访问 (例如直接调用 Repository 写入数据但未经过拦截器)。</p>
 *
 * <p>典型用法: 标注在 Service 的写操作方法上, 或标注在需要强制租户隔离的类上。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantAware {
}
