package com.contentops.ai.agent.a2a;

import com.contentops.ai.domain.event.A2AMessage;

/**
 * A2A 消息处理函数式接口。
 *
 * <p>Agent 通过 {@link A2ABroker#subscribe(String, java.util.function.Consumer)} 注册订阅时,
 * 传入本接口实例 (或 lambda) 处理接收到的 {@link A2AMessage}。</p>
 *
 * <p>使用 {@code @FunctionalInterface} 标注, 可直接以 lambda / 方法引用方式实现。</p>
 */
@FunctionalInterface
public interface A2AMessageHandler {

    /**
     * 处理一条 A2A 消息。
     *
     * @param message 接收到的 A2A 消息 (不为 null)
     */
    void handle(A2AMessage message);
}
