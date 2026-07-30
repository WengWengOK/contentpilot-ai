package com.contentops.ai.capability.fallback;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 降级链配置类。
 *
 * <p>从 {@code application.yml} 读取配置前缀 {@code contentops.fallback} 构建模型端点列表；
 * 若未配置则使用默认降级链：GPT-4o -> DeepSeek -> Qwen-Plus。
 * 端点按 {@code priority} 升序排列（数值越小越优先）。</p>
 *
 * <p>配置示例（application.yml）：
 * <pre>
 * contentops:
 *   fallback:
 *     endpoints:
 *       - name: gpt-4o
 *         base-url: https://api.openai.com
 *         api-key: ${OPENAI_API_KEY}
 *         model: gpt-4o
 *         timeout-seconds: 30
 *         max-retries: 3
 *         priority: 1
 * </pre>
 * </p>
 *
 * <p>同时在此开启 {@link EnableScheduling}，使语义缓存清理与配额重置定时任务生效。</p>
 */
@Configuration
@EnableScheduling
public class FallbackConfig {

    /** 配置前缀 */
    public static final String CONFIG_PREFIX = "contentops.fallback";

    /**
     * 模型降级链端点列表。
     */
    @Bean
    public List<ModelEndpoint> modelFallbackChain(Environment env) {
        Binder binder = Binder.get(env);
        List<ModelEndpoint> endpoints = binder.bind(
                        CONFIG_PREFIX + ".endpoints",
                        Bindable.listOf(ModelEndpoint.class))
                .orElseGet(List::of);

        List<ModelEndpoint> chain = new ArrayList<>(
                (endpoints == null || endpoints.isEmpty()) ? defaultChain() : endpoints);
        chain.sort(Comparator.comparingInt(ModelEndpoint::priority));
        return chain;
    }

    /**
     * 默认降级链：GPT-4o -> DeepSeek -> Qwen-Plus。
     */
    private List<ModelEndpoint> defaultChain() {
        return List.of(
                ModelEndpoint.builder()
                        .name("gpt-4o")
                        .baseUrl("https://api.openai.com")
                        .apiKey(System.getenv().getOrDefault("OPENAI_API_KEY", "sk-placeholder"))
                        .model("gpt-4o")
                        .timeoutSeconds(30)
                        .maxRetries(3)
                        .priority(1)
                        .build(),
                ModelEndpoint.builder()
                        .name("deepseek")
                        .baseUrl("https://api.deepseek.com")
                        .apiKey(System.getenv().getOrDefault("DEEPSEEK_API_KEY", "sk-placeholder"))
                        .model("deepseek-chat")
                        .timeoutSeconds(20)
                        .maxRetries(2)
                        .priority(2)
                        .build(),
                ModelEndpoint.builder()
                        .name("qwen-plus")
                        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                        .apiKey(System.getenv().getOrDefault("DASHSCOPE_API_KEY", "sk-placeholder"))
                        .model("qwen-plus")
                        .timeoutSeconds(15)
                        .maxRetries(1)
                        .priority(3)
                        .build()
        );
    }
}
