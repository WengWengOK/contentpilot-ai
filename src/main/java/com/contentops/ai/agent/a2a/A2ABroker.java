package com.contentops.ai.agent.a2a;

import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.domain.event.A2AMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A2A (Agent-to-Agent) 消息总线核心。
 *
 * <p>基于 Redis Pub/Sub 实现 Agent 间解耦通信 (对齐系统设计文档 §4.8 / §9.3):
 * <ul>
 *   <li>{@link #publish(A2AMessage)}: 将消息序列化为 JSON, 发布到 Redis 频道 {@code a2a:{toAgent}}。</li>
 *   <li>{@link #subscribe(String, Consumer)}: 为指定 Agent 订阅其频道, 收到消息后反序列化并回调 handler。</li>
 * </ul>
 * </p>
 *
 * <p>使用 Spring Data Redis 的 {@link RedisMessageListenerContainer} 管理订阅监听器,
 * 在 {@link #init()} 中初始化容器, {@link #destroy()} 中销毁。
 * Redis Pub/Sub 为实时投递、无持久化, 适合 Agent 实时协作; 如需持久化可平滑迁移到 Redis Streams。</p>
 *
 * <p>线程模型: 每个 Agent 订阅由独立线程消费 (SimpleAsyncTaskExecutor), handler 内部应自行保证线程安全。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class A2ABroker {

    /** A2A 消息频道前缀: a2a:{toAgent} */
    public static final String CHANNEL_PREFIX = "a2a:";

    /**
     * 平台内置 Agent 名称常量 (A2A 频道标识)。
     *
     * <p>注意: 此处为 A2A 通信使用的 agent 标识 (如 {@code analysis-agent}),
     * 与 {@code AiConstants.AgentType} 的 type 值 (如 {@code analysis}, 数据库 agent_type 列) 不同。</p>
     */
    public static final String TOPIC_PLANNING_AGENT   = "topic-planning-agent";
    public static final String CONTENT_CREATION_AGENT = "content-creation-agent";
    public static final String IMAGE_DESIGN_AGENT     = "image-design-agent";
    public static final String PUBLISH_AGENT          = "publish-agent";
    public static final String ANALYSIS_AGENT         = "analysis-agent";
    public static final String OPTIMIZE_AGENT         = "optimize-agent";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** agentName -> 监听器 (便于后续取消订阅, 避免重复订阅) */
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    private RedisMessageListenerContainer listenerContainer;

    /**
     * 初始化 Redis 订阅监听容器。
     *
     * <p>容器非 Spring 自动装配 Bean, 此处手动创建并启动生命周期。
     * {@link #subscribe} 在容器启动后动态注册监听器。</p>
     */
    @PostConstruct
    public void init() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        Objects.requireNonNull(connectionFactory, "RedisConnectionFactory 未初始化");

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.setBeanName("a2aMessageListenerContainer");
        // 订阅为长阻塞操作, 使用独立线程池避免阻塞容器调度线程
        listenerContainer.setTaskExecutor(new SimpleAsyncTaskExecutor("a2a-sub-"));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        log.info("A2A 消息总线监听容器已启动");
    }

    /**
     * 发布 A2A 消息到 Redis 频道 {@code a2a:{toAgent}}。
     *
     * <p>自动补全 messageId / timestamp / tenantId (缺失时), 其中 tenantId 取自当前
     * {@link TenantContext}, 实现跨租户消息路由隔离。</p>
     *
     * @param message A2A 消息 (toAgent 必填)
     */
    public void publish(A2AMessage message) {
        if (message == null || message.getToAgent() == null || message.getToAgent().isBlank()) {
            log.warn("A2A 消息或目标 Agent 为空, 忽略发布: message={}", message);
            return;
        }
        try {
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }
            if (message.getTimestamp() == null) {
                message.setTimestamp(LocalDateTime.now());
            }
            if (message.getTenantId() == null) {
                message.setTenantId(TenantContext.getTenantId());
            }
            String channel = CHANNEL_PREFIX + message.getToAgent();
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
            log.debug("A2A 消息已发布: channel={}, messageId={}, from={}, type={}",
                    channel, message.getMessageId(), message.getFromAgent(), message.getMessageType());
        } catch (Exception e) {
            log.error("A2A 消息发布失败, toAgent={}: {}", message.getToAgent(), e.getMessage(), e);
        }
    }

    /**
     * 订阅指定 Agent 的 A2A 消息。
     *
     * <p>同一 agentName 重复订阅会先移除旧监听器, 避免重复消费。
     * 收到消息后在监听线程内反序列化并回调 handler, handler 异常会被捕获并记录, 不影响后续消息处理。</p>
     *
     * @param agentName 订阅的 Agent 标识 (频道 {@code a2a:{agentName}})
     * @param handler   消息处理器 (通常为 {@link A2AMessageHandler} 的 lambda)
     */
    public void subscribe(String agentName, Consumer<A2AMessage> handler) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName 不能为空");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler 不能为空");
        }
        if (listenerContainer == null) {
            throw new IllegalStateException("A2A 监听容器尚未初始化");
        }

        // 若已存在旧订阅, 先移除避免重复消费
        if (listeners.containsKey(agentName)) {
            unsubscribe(agentName);
        }

        MessageListener listener = (Message message, byte[] pattern) -> {
            try {
                String body = new String(message.getBody(), StandardCharsets.UTF_8);
                A2AMessage a2a = objectMapper.readValue(body, A2AMessage.class);
                log.debug("A2A 消息已接收: agent={}, messageId={}, from={}, type={}",
                        agentName, a2a.getMessageId(), a2a.getFromAgent(), a2a.getMessageType());
                handler.accept(a2a);
            } catch (Exception e) {
                log.error("A2A 消息处理失败, agent={}: {}", agentName, e.getMessage(), e);
            }
        };

        String channel = CHANNEL_PREFIX + agentName;
        listeners.put(agentName, listener);
        listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
        log.info("A2A 订阅已注册: agent={}, channel={}", agentName, channel);
    }

    /**
     * 取消指定 Agent 的订阅。
     *
     * @param agentName Agent 标识
     */
    public void unsubscribe(String agentName) {
        MessageListener listener = listeners.remove(agentName);
        if (listener != null && listenerContainer != null && listenerContainer.isRunning()) {
            listenerContainer.removeMessageListener(listener);
            log.info("A2A 订阅已移除: agent={}", agentName);
        }
    }

    /**
     * 销毁监听容器, 释放 Redis 订阅连接。
     */
    @PreDestroy
    public void destroy() {
        if (listenerContainer != null) {
            try {
                listenerContainer.destroy();
                log.info("A2A 消息总线监听容器已销毁");
            } catch (Exception e) {
                log.warn("A2A 消息总线监听容器销毁异常: {}", e.getMessage());
            }
        }
    }
}
