package com.contentops.ai.infrastructure.postgres;

import com.contentops.ai.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 租户 JPA 仓库。
 *
 * <p>供 {@code TokenLimiter} 查询租户每日配额、{@code QuotaResetJob} 遍历活跃租户重置配额。</p>
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /** 按租户编码（tenantCode）查询，租户编码即限流 key 中的 tenantId。 */
    Optional<Tenant> findByTenantCode(String tenantCode);

    /** 按状态查询租户，用于每日 00:00 重置活跃租户配额。 */
    List<Tenant> findByStatus(String status);
}
