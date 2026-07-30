package com.contentops.ai.capability.ratelimit;

import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 配额重置定时任务。
 *
 * <p>每天 00:00 执行（cron = "0 0 0 * * ?"），为所有活跃租户重置 Redis 中的
 * 当日配额 key（quota:{tenantId}:{yyyyMMdd}）为该租户的 daily_quota。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaResetJob {

    private static final String ACTIVE_STATUS = "active";

    private final StringRedisTemplate stringRedisTemplate;
    private final TenantRepository tenantRepository;

    /**
     * 每天 00:00 重置活跃租户配额。
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetQuota() {
        try {
            List<Tenant> tenants = tenantRepository.findByStatus(ACTIVE_STATUS);
            if (tenants == null || tenants.isEmpty()) {
                log.info("配额重置: 无活跃租户, 跳过");
                return;
            }
            int success = 0;
            for (Tenant tenant : tenants) {
                try {
                    String key = TokenLimiter.quotaKey(tenant.getTenantCode());
                    int quota = (tenant.getDailyQuota() != null && tenant.getDailyQuota() > 0)
                            ? tenant.getDailyQuota()
                            : TokenLimiter.DEFAULT_DAILY_QUOTA;
                    // 写入当日配额, 并设置 25 小时 TTL 作为兜底清理（防重置任务失败导致 key 残留）
                    stringRedisTemplate.opsForValue().set(key, String.valueOf(quota), Duration.ofHours(25));
                    success++;
                } catch (Exception e) {
                    log.warn("重置租户[{}]配额失败: {}", tenant.getTenantCode(), e.getMessage());
                }
            }
            log.info("配额重置完成, 活跃租户数={}, 成功数={}", tenants.size(), success);
        } catch (Exception e) {
            log.error("配额重置任务异常: {}", e.getMessage(), e);
        }
    }
}
