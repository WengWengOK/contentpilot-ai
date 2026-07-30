package com.contentops.ai.capability.ratelimit;

import com.contentops.ai.common.exception.QuotaExceededException;
import com.contentops.ai.domain.entity.Tenant;
import com.contentops.ai.infrastructure.postgres.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TokenLimiter} 单元测试。
 *
 * <p>验证 Token 级限流器的核心逻辑: 配额充足时扣减成功、配额不足时抛出异常、
 * 非法 tokens 值的边界处理、剩余配额查询, 以及限流 key 格式的正确性。</p>
 *
 * <p>通过 Mock {@link StringRedisTemplate} 的 Lua 脚本执行结果模拟 Redis 侧的扣减/回滚行为,
 * Mock {@link TenantRepository} 返回租户配额配置。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Token 限流器 TokenLimiter 测试")
class TokenLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenLimiter tokenLimiter;

    // ==================== 正常扣减 ====================

    @Test
    @DisplayName("配额充足时扣减成功: Redis脚本返回正数, 不抛异常")
    void tryConsume_配额充足_应扣减成功() {
        // given
        String tenantId = "tenant-001";
        int tokens = 500;
        int dailyQuota = 100_000;

        Tenant tenant = Tenant.builder()
                .id(1L)
                .tenantCode(tenantId)
                .tenantName("测试租户")
                .dailyQuota(dailyQuota)
                .status("active")
                .build();

        when(tenantRepository.findByTenantCode(tenantId))
                .thenReturn(Optional.of(tenant));
        // Lua 脚本返回扣减后的剩余配额(正数表示成功)
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()))
                .thenReturn(99500L);

        // when & then
        assertThatCode(() -> tokenLimiter.tryConsume(tenantId, tokens))
                .doesNotThrowAnyException();

        // 验证 Redis 脚本被调用
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
        // 验证租户配额查询
        verify(tenantRepository).findByTenantCode(tenantId);
    }

    // ==================== 配额不足 ====================

    @Test
    @DisplayName("配额不足时抛出QuotaExceededException: Redis脚本返回负数, 已回滚扣减")
    void tryConsume_配额不足_应抛出QuotaExceededException() {
        // given
        String tenantId = "tenant-002";
        int tokens = 200;
        int dailyQuota = 100_000;

        Tenant tenant = Tenant.builder()
                .id(2L)
                .tenantCode(tenantId)
                .tenantName("配额不足租户")
                .dailyQuota(dailyQuota)
                .status("active")
                .build();

        when(tenantRepository.findByTenantCode(tenantId))
                .thenReturn(Optional.of(tenant));
        // Lua 脚本返回负数表示配额不足: -(preConsume + 1)
        // preConsume = -result - 1 = -(-101) - 1 = 100
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()))
                .thenReturn(-101L);

        // when & then
        assertThatThrownBy(() -> tokenLimiter.tryConsume(tenantId, tokens))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("配额不足")
                .hasFieldOrPropertyWithValue("tenantId", tenantId)
                .hasFieldOrPropertyWithValue("remaining", 100L)
                .hasFieldOrPropertyWithValue("requested", tokens);

        // 验证 Redis 脚本被调用(脚本内部已回滚)
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
    }

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("tokens=0时直接返回: 不调用Redis脚本, 不查询租户配额")
    void tryConsume_tokens为零_应直接返回() {
        // given
        String tenantId = "tenant-003";

        // when & then
        assertThatCode(() -> tokenLimiter.tryConsume(tenantId, 0))
                .doesNotThrowAnyException();

        // 验证未调用 Redis 和租户查询
        verify(stringRedisTemplate, never()).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
        verify(tenantRepository, never()).findByTenantCode(anyString());
    }

    @Test
    @DisplayName("tokens为负数时直接返回: 不调用Redis脚本")
    void tryConsume_tokens为负数_应直接返回() {
        // when & then
        assertThatCode(() -> tokenLimiter.tryConsume("tenant-004", -100))
                .doesNotThrowAnyException();

        verify(stringRedisTemplate, never()).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
    }

    @Test
    @DisplayName("租户不存在时使用默认配额DEFAULT_DAILY_QUOTA: 不抛异常, 正常执行脚本")
    void tryConsume_租户不存在_应使用默认配额() {
        // given
        String tenantId = "unknown-tenant";
        int tokens = 100;

        when(tenantRepository.findByTenantCode(tenantId))
                .thenReturn(Optional.empty());
        when(stringRedisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any()))
                .thenReturn(99900L);

        // when & then
        assertThatCode(() -> tokenLimiter.tryConsume(tenantId, tokens))
                .doesNotThrowAnyException();

        verify(tenantRepository).findByTenantCode(tenantId);
        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any());
    }

    // ==================== 查询剩余配额 ====================

    @Test
    @DisplayName("getRemainingQuota当key存在时返回Redis中的剩余配额值")
    void getRemainingQuota_key存在_应返回剩余配额() {
        // given
        String tenantId = "tenant-001";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("85000");

        // when
        long remaining = tokenLimiter.getRemainingQuota(tenantId);

        // then
        assertThat(remaining).isEqualTo(85000L);
        verify(stringRedisTemplate).opsForValue();
        verify(valueOperations).get(anyString());
    }

    @Test
    @DisplayName("getRemainingQuota当key不存在时返回租户的dailyQuota(视为满配额)")
    void getRemainingQuota_key不存在_应返回每日配额() {
        // given
        String tenantId = "tenant-002";
        int dailyQuota = 50_000;

        Tenant tenant = Tenant.builder()
                .id(2L)
                .tenantCode(tenantId)
                .dailyQuota(dailyQuota)
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(tenantRepository.findByTenantCode(tenantId))
                .thenReturn(Optional.of(tenant));

        // when
        long remaining = tokenLimiter.getRemainingQuota(tenantId);

        // then
        assertThat(remaining).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("getRemainingQuota当key值非法时回退为dailyQuota")
    void getRemainingQuota_key值非法_应回退为每日配额() {
        // given
        String tenantId = "tenant-003";
        int dailyQuota = 80_000;

        Tenant tenant = Tenant.builder()
                .id(3L)
                .tenantCode(tenantId)
                .dailyQuota(dailyQuota)
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("not-a-number");
        when(tenantRepository.findByTenantCode(tenantId))
                .thenReturn(Optional.of(tenant));

        // when
        long remaining = tokenLimiter.getRemainingQuota(tenantId);

        // then
        assertThat(remaining).isEqualTo(80_000L);
    }

    // ==================== quotaKey 格式 ====================

    @Test
    @DisplayName("quotaKey格式正确: quota:{tenantId}:{yyyyMMdd}")
    void quotaKey_应返回正确格式() {
        // given
        String tenantId = "tenant-001";
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        // when
        String key = TokenLimiter.quotaKey(tenantId);

        // then
        assertThat(key).isEqualTo("quota:" + tenantId + ":" + today);
        assertThat(key).startsWith(TokenLimiter.KEY_PREFIX);
        assertThat(key).matches("quota:tenant-001:\\d{8}");
    }

    @Test
    @DisplayName("quotaKey当tenantId为null时使用default: quota:default:{yyyyMMdd}")
    void quotaKey_tenantId为null_应使用default() {
        // when
        String key = TokenLimiter.quotaKey(null);

        // then
        assertThat(key).startsWith("quota:default:");
        assertThat(key).matches("quota:default:\\d{8}");
    }

    @Test
    @DisplayName("quotaKey当tenantId为空字符串时使用default")
    void quotaKey_tenantId为空_应使用default() {
        // when
        String key = TokenLimiter.quotaKey("");

        // then
        assertThat(key).startsWith("quota:");
        assertThat(key).matches("quota::\\d{8}");
    }
}
