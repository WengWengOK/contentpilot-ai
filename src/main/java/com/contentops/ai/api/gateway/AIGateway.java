package com.contentops.ai.api.gateway;

import com.contentops.ai.api.dto.GatewayRequest;
import com.contentops.ai.api.dto.GatewayResponse;
import com.contentops.ai.capability.cache.SemanticCacheService;
import com.contentops.ai.capability.fallback.ChatResult;
import com.contentops.ai.capability.fallback.ModelFallbackChain;
import com.contentops.ai.capability.ratelimit.TokenLimiter;
import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 统一 AI 调用入口 (AI Gateway).
 *
 * <p>封装所有 AI 调用的公共横切逻辑, 对齐系统设计文档 §4.7:
 * <pre>
 *   语义缓存检查 → Token 限流 → 模型降级链调用 → 结果缓存
 * </pre>
 * 适用于不需要完整 Agent 编排(执行记录/检查点/RAGAS)的轻量级 AI 调用场景。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIGateway {

    private final SemanticCacheService semanticCacheService;
    private final TokenLimiter tokenLimiter;
    private final ModelFallbackChain modelFallbackChain;

    /**
     * 执行一次统一的 AI 调用。
     *
     * @param request 网关请求
     * @return 网关响应
     */
    public GatewayResponse execute(GatewayRequest request) {
        String traceId = TraceUtil.generateTraceId();
        String tenantId = TenantContext.resolveTenantCode(request.getTenantId());

        // 1. 语义缓存检查
        if (request.isUseCache()) {
            Optional<String> cached = semanticCacheService.get(request.getPrompt(), tenantId);
            if (cached.isPresent()) {
                log.info("AIGateway 缓存命中, traceId={}", traceId);
                return GatewayResponse.builder()
                        .result(cached.get())
                        .cacheHit(true)
                        .tokensUsed(0)
                        .traceId(traceId)
                        .build();
            }
        }

        // 2. Token 限流
        int estimatedTokens = estimateTokens(request.getPrompt());
        tokenLimiter.tryConsume(tenantId, estimatedTokens);

        // 3. 模型降级链调用
        ChatResult chatResult = modelFallbackChain.chatWithMeta(request.getPrompt(), tenantId);

        // 4. 结果缓存: 模板兜底文案不写入缓存, 避免污染语义缓存导致后续命中返回无意义内容
        if (request.isUseCache() && !"template".equals(chatResult.source())) {
            semanticCacheService.put(request.getPrompt(), chatResult.content(), tenantId);
        }

        log.info("AIGateway 调用完成, traceId={}, model={}, source={}, cacheHit=false",
                traceId, chatResult.modelUsed(), chatResult.source());
        return GatewayResponse.builder()
                .result(chatResult.content())
                .modelUsed(chatResult.modelUsed())
                .cacheHit(false)
                .tokensUsed(estimatedTokens)
                .traceId(traceId)
                .build();
    }

    /**
     * 估算 Token 数 (输入 ~4 字符/token + 1024 输出预留)。
     */
    private int estimateTokens(String prompt) {
        int chars = prompt == null ? 0 : prompt.length();
        return Math.max(256, chars / 4 + 1024);
    }
}
