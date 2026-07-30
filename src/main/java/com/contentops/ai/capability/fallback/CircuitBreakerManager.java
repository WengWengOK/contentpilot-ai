package com.contentops.ai.capability.fallback;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 熔断器管理。
 *
 * <p>基于 Resilience4j 的 {@link CircuitBreakerRegistry}，为每个模型端点注册独立熔断器，
 * 供 {@link ModelFallbackChain} 包装每次模型调用。</p>
 *
 * <p>熔断参数（符合系统设计文档）：
 * <ul>
 *   <li>failureRateThreshold = 50%（失败率阈值）</li>
 *   <li>slowCallRateThreshold = 60%（慢调用比例阈值）</li>
 *   <li>slowCallDurationThreshold = 10s</li>
 *   <li>waitDurationInOpenState = 30s（熔断开启后等待时间）</li>
 *   <li>slidingWindowSize = 10（滑动窗口大小）</li>
 *   <li>minimumNumberOfCalls = 5（最少调用次数才计算失败率）</li>
 * </ul>
 * 熔断器处于 OPEN 状态时，调用将直接抛出 {@link CallNotPermittedException}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerManager {

    private final CircuitBreakerRegistry registry;

    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 在指定熔断器保护下执行 supplier。
     *
     * @param circuitBreakerName 熔断器名称（通常为模型端点 name）
     * @param supplier           实际业务调用
     * @param <T>                返回类型
     * @return 调用结果
     * @throws CallNotPermittedException 熔断器开启时抛出
     */
    public <T> T executeWithCircuitBreaker(String circuitBreakerName, Supplier<T> supplier) {
        CircuitBreaker circuitBreaker = breakers.computeIfAbsent(circuitBreakerName, this::register);
        return circuitBreaker.executeSupplier(supplier);
    }

    /** 熔断器是否允许调用（OPEN/HALF_OPEN 受限时返回 false）。 */
    public boolean isCallPermitted(String circuitBreakerName) {
        CircuitBreaker circuitBreaker = breakers.get(circuitBreakerName);
        return circuitBreaker == null || circuitBreaker.tryAcquirePermission();
    }

    private CircuitBreaker register(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slowCallRateThreshold(60.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreaker circuitBreaker = registry.circuitBreaker(name, config);
        log.info("已注册模型熔断器: {}", name);
        return circuitBreaker;
    }
}
