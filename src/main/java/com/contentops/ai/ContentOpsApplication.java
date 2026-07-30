package com.contentops.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ContentOps AI 平台启动类.
 *
 * <p>基于 Spring Boot 3.2 构建, 整合 Spring AI (OpenAI 兼容协议 + Qdrant 向量库),
 * 提供多租户内容运营、Agent 编排、RAGAS 评估与可观测能力。</p>
 */
@SpringBootApplication
@EnableJpaAuditing
public class ContentOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentOpsApplication.class, args);
    }
}
