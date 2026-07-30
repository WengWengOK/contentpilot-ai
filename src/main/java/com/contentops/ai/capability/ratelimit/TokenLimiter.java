package com.contentops.ai.capability.ratelimit;

import com.contentops.ai.common.exception.QuotaExceededException;
import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Token 级限流器。
 *
 * <p>按租户按天控制 Token 消耗量，防止单个租户耗尽共享配额。核心思路：
 * <pre>
 * key = "quota:{tenantId}:{yyyyMMdd}"
 * remaining = Redis.DECRBY(key, tokens)   // 原子操作
 * if remaining &lt; 0:
 *     Redis.INCRBY(key, tokens)            // 回滚
 *     throw QuotaExceededException
 * key 不存在时初始化为租户的 daily_quota
 * </pre>
 * </p>
 *
 * <p>初始化 + 扣减 + 回滚封装在一段 Lua 脚本中保证原子性，避免并发下的竞态。
 * 之所以用 Token 级而非请求级限流：一个长 prompt（5000 tokens）与短 prompt（100 tokens）
 * 资源消耗相差 50 倍，请求级限流无法反映真实成本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenLimiter {

    /** key 前缀 */
    public static final String KEY_PREFIX = "quota:";

    /** 租户未配置时的默认每日配额 */
    public static final int DEFAULT_DAILY_QUOTA = 100_000;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /**
     * 原子执行：key 不存在则初始化为 daily_quota -> DECRBY -> 不足则 INCRBY 回滚。
     * <p>返回值约定：成功返回扣减后的剩余配额（&gt;=0）；失败返回 -(本次扣减前剩余 + 1)（&lt;0）。</p>
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT;

    static {
        String lua = """
                if redis.call("exists", KEYS[1]) == 0 then
                    redis.call("set", KEYS[1], tonumber(ARGV[2]))
                end
                local after = redis.call("decrby", KEYS[1], tonumber(ARGV[1]))
                if after < 0 then
                    redis.call("incrby", KEYS[1], tonumber(ARGV[1]))
                    local pre = after + tonumber(ARGV[1])
                    return -(pre + 1)
                end
                return after
                """;
        CONSUME_SCRIPT = new DefaultRedisScript<>();
        CONSUME_SCRIPT.setScriptText(lua);
        CONSUME_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;
    private final TenantRepository tenantRepository;

    /**
     * 检查并扣减 Token 配额。
     *
     * @param tenantId 租户标识
     * @param tokens   本次请求消耗的 Token 数
     * @throws QuotaExceededException 配额不足时抛出（已回滚扣减）
     */
    public void tryConsume(String tenantId, int tokens) {
        if (tokens <= 0) {
            return;
        }
        String key = quotaKey(tenantId);
        int dailyQuota = resolveDailyQuota(tenantId);

        Long result = stringRedisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key),
                String.valueOf(tokens),
                String.valueOf(dailyQuota));

        if (result == null) {
            throw new IllegalStateException("Token限流脚本返回null, tenantId=" + tenantId);
        }
        if (result < 0) {
            // 失败：还原本次扣减前的剩余配额
            long preConsume = -result - 1;
            log.warn("租户[{}]Token配额不足, 剩余={}, 请求={}", tenantId, preConsume, tokens);
            throw new QuotaExceededException(tenantId, preConsume, tokens);
        }
        log.debug("租户[{}]扣减Token成功, 消耗={}, 剩余={}", tenantId, tokens, result);
    }

    /**
     * 查询剩余配额。key 不存在时返回租户的 daily_quota（视为满配额）。
     */
    public long getRemainingQuota(String tenantId) {
        String key = quotaKey(tenantId);
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null) {
            return resolveDailyQuota(tenantId);
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            log.warn("配额key值非法, key={}, value={}, 回退为daily_quota", key, val);
            return resolveDailyQuota(tenantId);
        }
    }

    /**
     * 构建限流 key：quota:{tenantId}:{yyyyMMdd}。
     */
    static String quotaKey(String tenantId) {
        return KEY_PREFIX + (tenantId == null ? "default" : tenantId)
                + ":" + LocalDate.now().format(DATE_FMT);
    }

    /**
     * 解析租户每日配额；租户不存在或未配置时使用默认值。
     */
    private int resolveDailyQuota(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_DAILY_QUOTA;
        }
        try {
            return tenantRepository.findByTenantCode(tenantId)
                    .map(Tenant::getDailyQuota)
                    .filter(q -> q != null && q > 0)
                    .orElseGet(() -> {
                        log.warn("租户[{}]未配置daily_quota, 使用默认值{}", tenantId, DEFAULT_DAILY_QUOTA);
                        return DEFAULT_DAILY_QUOTA;
                    });
        } catch (Exception e) {
            log.warn("查询租户[{}]配额异常, 使用默认值{}: {}", tenantId, DEFAULT_DAILY_QUOTA, e.getMessage());
            return DEFAULT_DAILY_QUOTA;
        }
    }
}
