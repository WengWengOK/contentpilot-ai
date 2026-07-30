package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.domain.entity.AgentCheckpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 检查点服务。
 *
 * <p>对齐系统设计文档 §4.8 Checkpoint 机制: Agent 在 ReAct 循环的每个步骤持久化状态,
 * 支持断点续跑与状态恢复。底层复用 {@link AgentCheckpoint} 实体与
 * {@link AgentCheckpointRepository} 仓库。</p>
 *
 * <p>核心方法:
 * <ul>
 *   <li>{@link #saveCheckpoint}: 保存一个检查点 (按 createdAt 自动排序)。</li>
 *   <li>{@link #restore}: 恢复最新检查点的 state。</li>
 *   <li>{@link #getHistory}: 获取某次执行的全部检查点历史。</li>
 *   <li>{@link #cleanupOldCheckpoints}: 保留最新 N 条, 清理其余。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCheckpointService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存一个检查点。
     *
     * @param agentId     Agent 标识
     * @param executionId 执行 ID
     * @param state       待持久化的状态 (写入 state jsonb)
     */
    @Transactional
    public void saveCheckpoint(String agentId, String executionId, Map<String, Object> state) {
        Map<String, Object> safeState = state == null ? Map.of() : new HashMap<>(state);
        AgentCheckpoint checkpoint = AgentCheckpoint.builder()
                .agentId(agentId)
                .executionId(executionId)
                .state(safeState)
                .build();
        checkpointRepository.save(checkpoint);
        log.debug("Checkpoint 已保存: agentId={}, executionId={}, stateKeys={}",
                agentId, executionId, safeState.size());
    }

    /**
     * 恢复某次执行的最新检查点 state。
     *
     * @param executionId 执行 ID
     * @return 最新检查点的 state (存在时) 或 empty
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> restore(String executionId) {
        List<AgentCheckpoint> history = checkpointRepository.findByExecutionIdOrderByCreatedAtDesc(executionId);
        if (history.isEmpty()) {
            log.debug("未找到 executionId={} 的 checkpoint", executionId);
            return Optional.empty();
        }
        AgentCheckpoint latest = history.get(0);
        Map<String, Object> state = convertState(latest.getState());
        log.debug("已恢复 executionId={} 的最新 checkpoint, createdAt={}, stateKeys={}",
                executionId, latest.getCreatedAt(), state.size());
        return Optional.of(state);
    }

    /**
     * 获取某次执行的检查点历史 (最新在前)。
     *
     * @param executionId 执行 ID
     * @return 检查点列表 (最新在前)
     */
    @Transactional(readOnly = true)
    public List<AgentCheckpoint> getHistory(String executionId) {
        return checkpointRepository.findByExecutionIdOrderByCreatedAtDesc(executionId);
    }

    /**
     * 清理旧检查点, 仅保留最新 {@code keepLatest} 条。
     *
     * <p>策略: 取按 createdAt 倒序的第 {@code keepLatest} 条作为时间边界 cutoff,
     * 删除该 executionId 下 createdAt &lt; cutoff 的全部检查点。
     * 当历史数量不超过 keepLatest 时不做任何操作。</p>
     *
     * @param executionId 执行 ID
     * @param keepLatest  保留的最新条数
     */
    @Transactional
    public void cleanupOldCheckpoints(String executionId, int keepLatest) {
        if (keepLatest < 0) {
            throw new IllegalArgumentException("keepLatest 不能为负数: " + keepLatest);
        }
        List<AgentCheckpoint> history = checkpointRepository.findByExecutionIdOrderByCreatedAtDesc(executionId);
        if (history.size() <= keepLatest) {
            log.debug("executionId={} 的 checkpoint 数量={} <= keepLatest={}, 无需清理",
                    executionId, history.size(), keepLatest);
            return;
        }
        if (keepLatest == 0) {
            // 全部清理: cutoff 取当前时间, 删除所有 createdAt < now 的记录
            LocalDateTime cutoff = LocalDateTime.now();
            checkpointRepository.deleteByExecutionIdAndCreatedAtBefore(executionId, cutoff);
            log.info("已清理 executionId={} 的全部 checkpoint (keepLatest=0)", executionId);
            return;
        }
        // 保留最新 keepLatest 条, 第 keepLatest-1 (0-indexed) 为保留集中最旧的一条
        LocalDateTime cutoff = history.get(keepLatest - 1).getCreatedAt();
        checkpointRepository.deleteByExecutionIdAndCreatedAtBefore(executionId, cutoff);
        log.info("已清理 executionId={} 的旧 checkpoint, 保留 {}, cutoff={}",
                executionId, keepLatest, cutoff);
    }

    /**
     * 将 Hibernate JSON 反序列化产生的 Map (可能为 PersistentMap / LinkedHashMap)
     * 规整为可变、类型一致的 HashMap。
     *
     * <p>通过 ObjectMapper round-trip 规避 Hibernate 集合代理在事务外访问的异常。</p>
     *
     * @param state 原始 state (可能为 null)
     * @return 可变 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertState(Map<String, Object> state) {
        if (state == null) {
            return new HashMap<>();
        }
        try {
            String json = objectMapper.writeValueAsString(state);
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("checkpoint state 转换失败, 回退为浅拷贝: {}", e.getMessage());
            return new HashMap<>(state);
        }
    }
}
