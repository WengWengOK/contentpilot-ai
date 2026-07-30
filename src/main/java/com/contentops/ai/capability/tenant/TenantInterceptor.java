package com.contentops.ai.capability.tenant;

import com.contentops.ai.common.constant.AiConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 多租户拦截器。
 *
 * <p>在请求入口从 {@code X-Tenant-Id} 头 (或 {@code Authorization: Bearer <jwt>} 的 payload)
 * 解析 tenantId 并注入 {@link TenantContext}, 全链路共享当前租户。
 * 请求结束 (afterCompletion) 清除上下文, 防止线程池复用导致的租户泄漏。</p>
 *
 * <p>缺少租户信息的请求直接返回 401。登录接口 ({@code /api/v1/auth/**}) 由
 * {@code WebMvcConfig} 排除, 不经过本拦截器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long tenantId = resolveTenantId(request);
        if (tenantId == null) {
            log.warn("请求缺少租户信息, uri={}, method={}", request.getRequestURI(), request.getMethod());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":401,\"message\":\"Missing tenant context\"}");
            return false;
        }

        // 注入 TenantContext (tenantId 必填, userId 可选)
        TenantContext.setTenantId(tenantId);
        Long userId = parseLong(request.getHeader(USER_ID_HEADER));
        if (userId != null) {
            TenantContext.setUserId(userId);
        }

        log.debug("租户上下文已注入: tenantId={}, userId={}, uri={}",
                tenantId, userId, request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 防止线程池复用导致的租户串号: 每次请求结束必须清理
        TenantContext.clear();
    }

    /**
     * 解析 tenantId: 优先 {@code X-Tenant-Id} 头, 其次从 Bearer JWT payload 解析。
     */
    private Long resolveTenantId(HttpServletRequest request) {
        Long fromHeader = parseLong(request.getHeader(AiConstants.TENANT_HEADER));
        if (fromHeader != null) {
            return fromHeader;
        }
        return parseTenantFromJwt(request.getHeader(AUTH_HEADER));
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 简化版 JWT 解析: 提取 payload 段 Base64-URL 解码后读取 {@code tenant_id} / {@code tenantId} claim。
     *
     * <p>不校验签名 (认证由网关 / 认证服务负责, 此处仅做透传解析用于租户路由)。</p>
     */
    private Long parseTenantFromJwt(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode node = objectMapper.readTree(payload);
            JsonNode tenantNode = node.has("tenant_id") ? node.get("tenant_id") : node.get("tenantId");
            if (tenantNode == null || tenantNode.isNull()) {
                return null;
            }
            return tenantNode.asLong();
        } catch (Exception e) {
            log.debug("JWT tenantId 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
