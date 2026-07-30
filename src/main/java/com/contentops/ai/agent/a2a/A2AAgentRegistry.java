package com.contentops.ai.agent.a2a;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A Agent 注册表。
 *
 * <p>管理平台内已注册的 Agent 信息 (名称、描述、能力列表), 供 Agent 发现 / 路由使用。
 * 当前采用内存存储 (ConcurrentHashMap), 进程级可见; 如需跨实例共享可后续替换为 Redis。</p>
 *
 * <p>对齐系统设计文档 §4.8 A2A 协议模块: Agent 在启动时调用 {@link #register} 完成注册,
 * 编排层通过 {@link #findAgent} / {@link #listAgents} 发现可用 Agent 并构造 A2A 消息。</p>
 */
@Slf4j
@Component
public class A2AAgentRegistry {

    /** agentName -> AgentInfo */
    private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();

    /**
     * 注册 (或更新) 一个 Agent。
     *
     * @param agentName    Agent 唯一标识 (如 {@code analysis-agent})
     * @param description  Agent 描述
     * @param capabilities 能力列表 (如 {@code [sql, llm]})
     */
    public void register(String agentName, String description, List<String> capabilities) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName 不能为空");
        }
        AgentInfo info = new AgentInfo(
                agentName,
                description,
                capabilities == null ? List.of() : List.copyOf(capabilities),
                LocalDateTime.now());
        agents.put(agentName, info);
        log.info("Agent 已注册: name={}, capabilities={}", agentName, info.capabilities());
    }

    /**
     * 列出所有已注册的 Agent。
     *
     * @return 不可变的 AgentInfo 列表
     */
    public List<AgentInfo> listAgents() {
        return List.copyOf(agents.values());
    }

    /**
     * 按名称查找 Agent。
     *
     * @param agentName Agent 标识
     * @return AgentInfo (存在时) 或 empty
     */
    public Optional<AgentInfo> findAgent(String agentName) {
        return Optional.ofNullable(agents.get(agentName));
    }

    /**
     * 注销一个 Agent。
     *
     * @param agentName Agent 标识
     */
    public void unregister(String agentName) {
        AgentInfo removed = agents.remove(agentName);
        if (removed != null) {
            log.info("Agent 已注销: name={}", agentName);
        }
    }

    /**
     * Agent 元信息记录。
     *
     * @param agentName    Agent 唯一标识
     * @param description  Agent 描述
     * @param capabilities 能力列表 (不可变)
     * @param registeredAt 注册时间
     */
    public record AgentInfo(String agentName, String description,
                            List<String> capabilities, LocalDateTime registeredAt) {
    }
}
