package com.contentops.ai.capability.tenant;

import com.contentops.ai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 租户隔离切面。
 *
 * <p>简化实现 (对齐系统设计文档 §4.7):
 * <ol>
 *   <li><b>强制校验</b>: 拦截所有标注 {@link TenantAware} 的方法, 校验
 *       {@link TenantContext#getTenantId()} 不为空, 缺失则抛出 BusinessException(403),
 *       防止绕过拦截器直接调用带租户敏感数据的方法。</li>
 *   <li><b>告警检查</b>: 拦截所有 JPA Repository 的 find / save / delete 等数据访问方法,
 *       若 TenantContext 为空则记录 debug 日志 (非阻断, 避免影响系统级启动、Flyway 迁移、
 *       定时任务等无请求上下文的内部调用)。</li>
 * </ol>
 * </p>
 *
 * <p>完整的行级隔离 (Hibernate Filter 自动附加 {@code WHERE tenant_id = ?}) 可在此基础上扩展,
 * 此处采用手动检查的方式作为简化实现。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    /** 项目内所有 Repository 接口的执行连接点 */
    private static final String REPO_POINTCUT = "execution(* com.contentops.ai..*Repository.*(..))";

    /**
     * 对 {@link TenantAware} 标注的方法强制校验租户上下文。
     */
    @Around("@annotation(tenantAware)")
    public Object enforceTenantAware(ProceedingJoinPoint pjp, TenantAware tenantAware) throws Throwable {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            log.error("租户上下文缺失, 拒绝执行 @TenantAware 方法: {}.{}()",
                    pjp.getSignature().getDeclaringType().getSimpleName(),
                    pjp.getSignature().getName());
            throw new BusinessException(403, "Tenant context required for this operation");
        }
        return pjp.proceed();
    }

    /**
     * 拦截 Repository 的 find / save / delete 等数据访问方法, 检查租户上下文 (非阻断)。
     *
     * <p>仅对数据访问类方法名做检查, 排除 {@code existsById} 等也会被 {@code exists*} 捕获。
     * 当 TenantContext 为空时仅记录日志, 不阻断调用, 以兼容系统内部无请求上下文的操作。</p>
     */
    @Around(REPO_POINTCUT)
    public Object checkRepositoryAccess(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        if (isDataAccessMethod(methodName)) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                log.debug("Repository 数据访问未携带租户上下文 (可能为系统内部调用): {}.{}()",
                        pjp.getSignature().getDeclaringType().getSimpleName(), methodName);
            }
        }
        return pjp.proceed();
    }

    private boolean isDataAccessMethod(String name) {
        return name.startsWith("find") || name.startsWith("save")
                || name.startsWith("delete") || name.startsWith("get")
                || name.startsWith("count") || name.startsWith("exists");
    }
}
