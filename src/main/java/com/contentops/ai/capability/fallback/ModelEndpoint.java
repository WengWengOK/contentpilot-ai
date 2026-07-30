package com.contentops.ai.capability.fallback;

/**
 * 模型端点封装。
 *
 * <p>描述降级链中单个模型端点的连接信息与调用策略：
 * 主模型 GPT-4o -> DeepSeek -> Qwen-Plus，每个端点拥有独立的超时与重试策略。</p>
 *
 * <p>使用 record + 手写 Builder：
 * 既能作为不可变值对象，又支持 {@code ModelEndpoint.builder()...build()} 链式构造，
 * 同时兼容 Spring Boot {@code Binder} 对 record 的构造器绑定（从 application.yml 读取）。</p>
 *
 * @param name            端点名称（同时作为熔断器名称）
 * @param baseUrl         OpenAI 兼容 API 根地址
 * @param apiKey          API Key
 * @param model           模型名（如 gpt-4o / deepseek-chat / qwen-plus）
 * @param timeoutSeconds  单次调用超时（秒）
 * @param maxRetries      失败后最大重试次数（总尝试次数 = maxRetries + 1）
 * @param priority        优先级，数值越小越优先
 */
public record ModelEndpoint(
        String name,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        int maxRetries,
        int priority
) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 链式构建器。
     */
    public static final class Builder {
        private String name;
        private String baseUrl;
        private String apiKey;
        private String model;
        private int timeoutSeconds = 30;
        private int maxRetries = 1;
        private int priority = 0;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public ModelEndpoint build() {
            return new ModelEndpoint(name, baseUrl, apiKey, model, timeoutSeconds, maxRetries, priority);
        }
    }
}
