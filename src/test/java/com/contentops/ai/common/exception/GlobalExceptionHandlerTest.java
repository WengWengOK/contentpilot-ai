package com.contentops.ai.common.exception;

import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.domain.dto.ApiResponse;
import com.contentops.ai.domain.dto.ApiResponse.ApiResponseBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 单元测试.
 *
 * <p>采用 MockMvc standalone 模式: 注册一个最小化的测试 Controller 用于触发各类异常,
 * 再通过 {@code setControllerAdvice(new GlobalExceptionHandler())} 接入被测的全局异常处理器,
 * 从而在无需启动完整 Spring 上下文的前提下覆盖所有异常处理分支。</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 最小化测试 Controller: 每个端点专门触发一种异常, 供 GlobalExceptionHandler 处理。
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        public String business() {
            throw new BusinessException(409, "资源冲突");
        }

        @GetMapping("/quota")
        public String quota() {
            throw new QuotaExceededException("tenant-001", 0, 500);
        }

        @PostMapping("/validate")
        public String validate(@RequestBody @jakarta.validation.Valid SampleRequest request) {
            return "ok";
        }

        @GetMapping("/notfound")
        public String notFound() throws NoHandlerFoundException {
            throw new NoHandlerFoundException(HttpMethod.GET.name(), "/test/notfound", new HttpHeaders());
        }

        @GetMapping("/error")
        public String error() {
            throw new IllegalStateException("模拟未知异常");
        }
    }

    /**
     * 用于触发 {@code @Valid} 校验失败的 DTO。
     */
    static class SampleRequest {

        @NotBlank(message = "标题不能为空")
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(new ParameterMessageInterpolator());
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("BusinessException返回对应业务状态码")
    void handleBusiness_shouldReturnMatchingStatusCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/business")
                        .header(AiConstants.TRACE_HEADER, "trace-biz-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("资源冲突"))
                .andExpect(jsonPath("$.traceId").value("trace-biz-001"))
                .andReturn();

        ApiResponse<Void> body = readBody(result);
        assertThat(body.getCode()).isEqualTo(409);
        assertThat(body.getTraceId()).isEqualTo("trace-biz-001");
    }

    @Test
    @DisplayName("QuotaExceededException返回429")
    void handleQuota_shouldReturn429() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/quota")
                        .header(AiConstants.TRACE_HEADER, "trace-quota-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.traceId").value("trace-quota-001"))
                .andReturn();

        ApiResponse<Void> body = readBody(result);
        assertThat(body.getCode()).isEqualTo(429);
        assertThat(body.getMessage()).contains("配额不足");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException返回400并包含字段错误信息")
    void handleValidation_shouldReturn400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .header(AiConstants.TRACE_HEADER, "trace-valid-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("title: 标题不能为空"))
                .andExpect(jsonPath("$.traceId").value("trace-valid-001"));
    }

    @Test
    @DisplayName("请求体缺失时触发MethodArgumentNotValid以外的校验异常仍返回400")
    void handleValidation_missingBody_shouldReturn400() throws Exception {
        // 不传 body, HttpMessageConverter 无法读取 => HttpMessageNotReadableException => 400
        mockMvc.perform(post("/test/validate")
                        .header(AiConstants.TRACE_HEADER, "trace-nobody-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.traceId").value("trace-nobody-001"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException返回400")
    void handleNotReadable_shouldReturn400() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .header(AiConstants.TRACE_HEADER, "trace-read-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-a-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误或不可读"))
                .andExpect(jsonPath("$.traceId").value("trace-read-001"));
    }

    @Test
    @DisplayName("NoHandlerFoundException返回404")
    void handleNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/test/notfound")
                        .header(AiConstants.TRACE_HEADER, "trace-nf-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.traceId").value("trace-nf-001"));
    }

    @Test
    @DisplayName("通用Exception返回500")
    void handleOther_shouldReturn500() throws Exception {
        mockMvc.perform(get("/test/error")
                        .header(AiConstants.TRACE_HEADER, "trace-err-001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("内部服务器错误"))
                .andExpect(jsonPath("$.traceId").value("trace-err-001"));
    }

    @Test
    @DisplayName("未携带X-Trace-Id时响应体中traceId为null并被忽略")
    void handleBusiness_withoutTraceHeader_traceIdAbsent() throws Exception {
        mockMvc.perform(get("/test/business")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    /**
     * 将响应体反序列化为 {@link ApiResponse} 以便用 AssertJ 做更强类型的断言。
     */
    private ApiResponse<Void> readBody(MvcResult result) throws Exception {
        ApiResponseBuilder<Void> builder = ApiResponse.builder();
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = objectMapper.readValue(result.getResponse().getContentAsByteArray(), Map.class);
        builder.code(((Number) raw.get("code")).intValue());
        if (raw.containsKey("message")) {
            builder.message((String) raw.get("message"));
        }
        if (raw.containsKey("traceId")) {
            builder.traceId((String) raw.get("traceId"));
        }
        return builder.build();
    }
}
