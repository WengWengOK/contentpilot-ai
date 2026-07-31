package com.contentops.ai.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置.
 *
 * <p>通过 {@link Jackson2ObjectMapperBuilderCustomizer} 统一配置, Spring Boot 自动配置的
 * ObjectMapper 和 MVC 序列化均会生效 (ISO-8601 时间格式、忽略未知字段、注册 Java 8 时间模块、缩进输出)。</p>
 *
 * <p>不显式定义 {@code @Bean ObjectMapper}, 以确保 customizer 被正确应用。
 * 需要注入 ObjectMapper 的组件使用 Spring Boot 自动配置的实例即可。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToEnable(SerializationFeature.INDENT_OUTPUT);
    }
}
