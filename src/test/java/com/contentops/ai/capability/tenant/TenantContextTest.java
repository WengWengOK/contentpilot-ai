package com.contentops.ai.capability.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TenantContext} 多租户上下文单元测试。
 * <p>
 * 纯单元测试, 不依赖 Spring 上下文。基于 ThreadLocal, 需在每用例前后 clear 防止串号。
 */
@DisplayName("TenantContext 多租户上下文测试")
class TenantContextTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("tenantId 读写")
    class TenantIdAccess {

        @Test
        @DisplayName("setTenantId 后 getTenantId 返回相同值")
        void should_setAndGetTenantId() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("未设置时 getTenantId 返回 null")
        void should_returnNullWhenTenantIdNotSet() {
            assertThat(TenantContext.getTenantId()).isNull();
        }

        @Test
        @DisplayName("可覆盖已设置的 tenantId")
        void should_overrideExistingTenantId() {
            TenantContext.setTenantId(1001L);
            TenantContext.setTenantId(2002L);

            assertThat(TenantContext.getTenantId()).isEqualTo(2002L);
        }
    }

    @Nested
    @DisplayName("userId 读写")
    class UserIdAccess {

        @Test
        @DisplayName("setUserId 后 getUserId 返回相同值")
        void should_setAndGetUserId() {
            TenantContext.setUserId(2002L);

            assertThat(TenantContext.getUserId()).isEqualTo(2002L);
        }

        @Test
        @DisplayName("未设置时 getUserId 返回 null")
        void should_returnNullWhenUserIdNotSet() {
            assertThat(TenantContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("tenantId 与 userId 相互独立")
        void should_tenantIdAndUserIdBeIndependent() {
            TenantContext.setTenantId(1001L);
            TenantContext.setUserId(2002L);

            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
            assertThat(TenantContext.getUserId()).isEqualTo(2002L);
        }
    }

    @Nested
    @DisplayName("clear 清除上下文")
    class ClearContext {

        @Test
        @DisplayName("clear 后 tenantId 与 userId 均被清除")
        void should_clearAllContext() {
            TenantContext.setTenantId(1001L);
            TenantContext.setUserId(2002L);

            TenantContext.clear();

            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("clear 后可重新设置新值")
        void should_allowResetAfterClear() {
            TenantContext.setTenantId(1001L);
            TenantContext.clear();
            TenantContext.setTenantId(3003L);

            assertThat(TenantContext.getTenantId()).isEqualTo(3003L);
        }

        @Test
        @DisplayName("未设置任何值时 clear 不抛异常")
        void should_clearWhenNothingSet() {
            TenantContext.clear();

            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getUserId()).isNull();
        }

        @Test
        @DisplayName("重复调用 clear 安全")
        void should_beSafeToCallClearMultipleTimes() {
            TenantContext.setTenantId(1001L);
            TenantContext.clear();
            TenantContext.clear();

            assertThat(TenantContext.getTenantId()).isNull();
        }
    }

    @Nested
    @DisplayName("resolveTenantCode 租户编码解析")
    class ResolveTenantCode {

        @Test
        @DisplayName("headerTenantId 非空时优先返回 header 值")
        void should_preferHeaderTenantId() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.resolveTenantCode("9999")).isEqualTo("9999");
        }

        @Test
        @DisplayName("headerTenantId 为 null 时回退到上下文 tenantId")
        void should_fallbackToContextWhenHeaderNull() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.resolveTenantCode(null)).isEqualTo("1001");
        }

        @Test
        @DisplayName("headerTenantId 为空白字符串时回退到上下文 tenantId")
        void should_fallbackToContextWhenHeaderBlank() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.resolveTenantCode("   ")).isEqualTo("1001");
        }

        @Test
        @DisplayName("headerTenantId 为空字符串时回退到上下文 tenantId")
        void should_fallbackToContextWhenHeaderEmpty() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.resolveTenantCode("")).isEqualTo("1001");
        }

        @Test
        @DisplayName("header 与上下文均无值时返回 default")
        void should_returnDefaultWhenBothAbsent() {
            assertThat(TenantContext.resolveTenantCode(null)).isEqualTo("default");
        }

        @Test
        @DisplayName("header 为空字符串且上下文无值时返回 default")
        void should_returnDefaultWhenHeaderEmptyAndNoContext() {
            assertThat(TenantContext.resolveTenantCode("")).isEqualTo("default");
        }

        @Test
        @DisplayName("header 优先级高于上下文, 即使上下文已设置")
        void should_headerOverrideContext() {
            TenantContext.setTenantId(1001L);

            assertThat(TenantContext.resolveTenantCode("header-tenant")).isEqualTo("header-tenant");
        }

        @Test
        @DisplayName("上下文 tenantId 为 null 且 header 为空白时返回 default")
        void should_returnDefaultWhenContextNullAndHeaderBlank() {
            assertThat(TenantContext.resolveTenantCode("  ")).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("多线程隔离性")
    class ThreadIsolation {

        @Test
        @DisplayName("不同线程设置的 tenantId 互不影响")
        void should_isolateTenantIdBetweenThreads() throws InterruptedException {
            TenantContext.setTenantId(1001L);
            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);

            AtomicReference<Long> childInitial = new AtomicReference<>();
            CountDownLatch childSet = new CountDownLatch(1);
            CountDownLatch mainCheck = new CountDownLatch(1);

            Thread child = new Thread(() -> {
                // 子线程初始应为 null (与主线程隔离)
                childInitial.set(TenantContext.getTenantId());
                TenantContext.setTenantId(2002L);
                childSet.countDown();
                try {
                    mainCheck.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                TenantContext.clear();
            });

            child.start();
            assertThat(childSet.await(2, TimeUnit.SECONDS)).isTrue();

            // 子线程设置后, 主线程仍为自己的值
            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
            // 子线程初始看到 null (隔离)
            assertThat(childInitial.get()).isNull();

            mainCheck.countDown();
            child.join(3000);

            // 主线程值不受子线程影响
            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("子线程 clear 不影响主线程上下文")
        void should_childClearNotAffectMainThread() throws InterruptedException {
            TenantContext.setTenantId(1001L);
            TenantContext.setUserId(2002L);

            Thread child = new Thread(() -> {
                TenantContext.setTenantId(9999L);
                TenantContext.clear();
            });
            child.start();
            child.join(3000);

            assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
            assertThat(TenantContext.getUserId()).isEqualTo(2002L);
        }

        @Test
        @DisplayName("子线程设置的 userId 不影响主线程")
        void should_isolateUserIdBetweenThreads() throws InterruptedException {
            TenantContext.setUserId(1001L);

            AtomicReference<Long> childUserId = new AtomicReference<>();
            Thread child = new Thread(() -> {
                TenantContext.setUserId(8888L);
                childUserId.set(TenantContext.getUserId());
                TenantContext.clear();
            });
            child.start();
            child.join(3000);

            assertThat(childUserId.get()).isEqualTo(8888L);
            assertThat(TenantContext.getUserId()).isEqualTo(1001L);
        }
    }
}
