package com.contentops.ai.capability.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TenantInterceptor} 单元测试.
 *
 * <p>使用 {@link MockHttpServletRequest} / {@link MockHttpServletResponse} 直接驱动拦截器,
 * 不依赖 Spring 上下文。覆盖租户上下文注入、JWT 解析、缺失租户 401 拒绝、
 * 上下文清理与 userId 注入等场景。</p>
 */
class TenantInterceptorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TenantInterceptor interceptor;

    private final Object handler = new Object();

    @BeforeEach
    void setUp() {
        interceptor = new TenantInterceptor(objectMapper);
        // 确保每个用例起始时上下文是干净的
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        // 防止 ThreadLocal 在用例间泄漏
        TenantContext.clear();
    }

    @Test
    @DisplayName("X-Tenant-Id头存在时注入TenantContext并放行")
    void preHandle_tenantHeaderPresent_injectsTenantContextAndReturnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "1001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("X-Tenant-Id头缺失且无有效JWT时返回401")
    void preHandle_tenantMissing_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/topics");
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":401").contains("Missing tenant context");
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("JWT Bearer token的tenant_id claim解析租户")
    void preHandle_jwtBearerToken_parsesTenantId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildJwt(Map.of("tenant_id", 2002, "sub", "user-x")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(2002L);
    }

    @Test
    @DisplayName("JWT Bearer token的tenantId(驼峰)claim解析租户")
    void preHandle_jwtBearerTokenCamelCaseClaim_parsesTenantId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildJwt(Map.of("tenantId", 3003)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(3003L);
    }

    @Test
    @DisplayName("X-Tenant-Id头优先于JWT解析")
    void preHandle_tenantHeaderTakesPriorityOverJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "1001");
        // JWT 中 tenant_id 与 header 不一致, 验证以 header 为准
        request.addHeader("Authorization", "Bearer " + buildJwt(Map.of("tenant_id", 9999)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("JWT缺少tenant_id claim时返回401")
    void preHandle_jwtWithoutTenantClaim_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + buildJwt(Map.of("sub", "user-x")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("Authorization头非Bearer前缀时返回401")
    void preHandle_nonBearerAuthorization_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("JWT结构不合法(段数不足)时返回401")
    void preHandle_malformedJwt_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 只有一段, 不是合法 JWT
        request.addHeader("Authorization", "Bearer not-a-valid-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("X-Tenant-Id非数字且无有效JWT时返回401")
    void preHandle_nonNumericTenantHeader_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("X-User-Id头存在时注入userId")
    void preHandle_userIdHeader_injectsUserId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "1001");
        request.addHeader("X-User-Id", "5001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getUserId()).isEqualTo(5001L);
        assertThat(TenantContext.getTenantId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("X-User-Id头缺失时userId保持为null但不影响放行")
    void preHandle_userIdHeaderAbsent_userIdIsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "1001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("X-User-Id非数字时被忽略, userId保持为null")
    void preHandle_nonNumericUserIdHeader_isIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "1001");
        request.addHeader("X-User-Id", "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(TenantContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion清除TenantContext(tenantId与userId)")
    void afterCompletion_clearsTenantContext() {
        TenantContext.setTenantId(9999L);
        TenantContext.setUserId(8888L);
        assertThat(TenantContext.getTenantId()).isEqualTo(9999L);
        assertThat(TenantContext.getUserId()).isEqualTo(8888L);

        interceptor.afterCompletion(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                handler,
                null);

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getUserId()).isNull();
    }

    @Test
    @DisplayName("afterCompletion在上下文为空时调用不会抛异常")
    void afterCompletion_emptyContext_isSafe() {
        // 上下文已为空, clear() 应为幂等操作
        TenantContext.clear();
        interceptor.afterCompletion(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                handler,
                new RuntimeException("boom"));
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getUserId()).isNull();
    }

    /**
     * 构造一个不校验签名的简化 JWT: header.payload.signature,
     * payload 为给定 claims 的 Base64-URL 编码 (与生产代码 {@code parseTenantFromJwt} 解析方式对齐)。
     */
    private String buildJwt(Map<String, Object> claims) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        String headerB64 = base64Url(objectMapper.writeValueAsBytes(header));
        String payloadB64 = base64Url(objectMapper.writeValueAsBytes(claims));
        return headerB64 + "." + payloadB64 + ".signature";
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }
}
