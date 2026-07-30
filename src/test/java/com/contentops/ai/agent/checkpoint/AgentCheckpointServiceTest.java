package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.domain.entity.AgentCheckpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentCheckpointService} 单元测试.
 *
 * <p>Mock 依赖 {@link AgentCheckpointRepository} 与 {@link ObjectMapper}, 覆盖检查点保存、
 * 恢复、历史查询与清理 (含保留最新 N 条 / 全部清理 / 负数入参异常) 等场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentCheckpointServiceTest {

    @Mock
    private AgentCheckpointRepository checkpointRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AgentCheckpointService service;

    @Test
    @DisplayName("saveCheckpoint保存检查点并写入正确的agentId、executionId与state")
    void saveCheckpoint_savesCheckpointWithCorrectFields() {
        Map<String, Object> state = new HashMap<>();
        state.put("step", 1);
        state.put("input", "topic-x");

        service.saveCheckpoint("topic_planning", "exec-1", state);

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        AgentCheckpoint saved = captor.getValue();
        assertThat(saved.getAgentId()).isEqualTo("topic_planning");
        assertThat(saved.getExecutionId()).isEqualTo("exec-1");
        assertThat(saved.getState())
                .containsEntry("step", 1)
                .containsEntry("input", "topic-x");
    }

    @Test
    @DisplayName("saveCheckpoint传入null state时保存空Map避免NPE")
    void saveCheckpoint_nullState_savesEmptyMap() {
        service.saveCheckpoint("content_creation", "exec-2", null);

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("saveCheckpoint对传入state做防御性拷贝, 不持有调用方引用")
    void saveCheckpoint_stateIsDefensivelyCopied() {
        Map<String, Object> state = new HashMap<>();
        state.put("step", 1);

        service.saveCheckpoint("agent", "exec", state);
        // 调用方修改原 Map 不应影响已保存的检查点
        state.put("step", 999);

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        assertThat(captor.getValue().getState()).containsEntry("step", 1);
    }

    @Test
    @DisplayName("restore恢复最新检查点的state")
    void restore_returnsLatestCheckpointState() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put("step", 3);
        state.put("status", "running");
        AgentCheckpoint latest = checkpoint("content_creation", "exec-1", state, LocalDateTime.now());

        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(latest));
        when(objectMapper.writeValueAsString(state)).thenReturn("{\"step\":3,\"status\":\"running\"}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(state);

        Optional<Map<String, Object>> restored = service.restore("exec-1");

        assertThat(restored).isPresent();
        assertThat(restored.get())
                .containsEntry("step", 3)
                .containsEntry("status", "running");
    }

    @Test
    @DisplayName("restore取最新(createdAt倒序第一条)检查点")
    void restore_returnsTheMostRecentCheckpoint() throws Exception {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        Map<String, Object> oldState = Map.of("step", 1);
        Map<String, Object> newState = Map.of("step", 5);
        AgentCheckpoint older = checkpoint("agent", "exec-1", oldState, t1);
        AgentCheckpoint newer = checkpoint("agent", "exec-1", newState, t2);

        // 仓库返回顺序: 最新在前
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(newer, older));
        when(objectMapper.writeValueAsString(newState)).thenReturn("{\"step\":5}");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(newState);

        Optional<Map<String, Object>> restored = service.restore("exec-1");

        assertThat(restored).isPresent();
        assertThat(restored.get()).containsEntry("step", 5);
        // 只对最新一条做 state 转换
        verify(objectMapper).writeValueAsString(newState);
    }

    @Test
    @DisplayName("restore无检查点时返回empty且不调用ObjectMapper")
    void restore_noCheckpoints_returnsEmpty() throws Exception {
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-missing"))
                .thenReturn(List.of());

        Optional<Map<String, Object>> restored = service.restore("exec-missing");

        assertThat(restored).isEmpty();
        verify(objectMapper, never()).writeValueAsString(any());
        verify(objectMapper, never()).readValue(anyString(), any(TypeReference.class));
    }

    @Test
    @DisplayName("restore当state为null时返回空Map")
    void restore_nullStateInCheckpoint_returnsEmptyMap() throws Exception {
        AgentCheckpoint cp = AgentCheckpoint.builder()
                .agentId("agent")
                .executionId("exec-1")
                .state(null)
                .createdAt(LocalDateTime.now())
                .build();
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp));

        Optional<Map<String, Object>> restored = service.restore("exec-1");

        assertThat(restored).isPresent();
        assertThat(restored.get()).isEmpty();
    }

    @Test
    @DisplayName("getHistory返回检查点历史(最新在前)")
    void getHistory_returnsCheckpointHistory() {
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), LocalDateTime.now().minusMinutes(2));
        AgentCheckpoint cp2 = checkpoint("agent", "exec-1", Map.of("step", 2), LocalDateTime.now().minusMinutes(1));
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp2, cp1));

        List<AgentCheckpoint> history = service.getHistory("exec-1");

        assertThat(history).containsExactly(cp2, cp1);
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("getHistory无记录时返回空列表")
    void getHistory_noRecords_returnsEmptyList() {
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-empty"))
                .thenReturn(List.of());

        List<AgentCheckpoint> history = service.getHistory("exec-empty");

        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("cleanupOldCheckpoints保留最新N条, 删除早于cutoff的记录")
    void cleanupOldCheckpoints_keepsLatestN() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(3);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t3 = LocalDateTime.now().minusMinutes(1);
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), t1);
        AgentCheckpoint cp2 = checkpoint("agent", "exec-1", Map.of("step", 2), t2);
        AgentCheckpoint cp3 = checkpoint("agent", "exec-1", Map.of("step", 3), t3);
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp3, cp2, cp1));

        service.cleanupOldCheckpoints("exec-1", 2);

        // keepLatest=2: cutoff = history.get(1).getCreatedAt() = cp2.createdAt
        verify(checkpointRepository).deleteByExecutionIdAndCreatedAtBefore("exec-1", t2);
    }

    @Test
    @DisplayName("cleanupOldCheckpoints保留1条时cutoff为最新一条的createdAt")
    void cleanupOldCheckpoints_keepOne_cutoffIsLatestCreatedAt() {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(2);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(1);
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), t1);
        AgentCheckpoint cp2 = checkpoint("agent", "exec-1", Map.of("step", 2), t2);
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp2, cp1));

        service.cleanupOldCheckpoints("exec-1", 1);

        // keepLatest=1: cutoff = history.get(0).getCreatedAt() = cp2.createdAt
        verify(checkpointRepository).deleteByExecutionIdAndCreatedAtBefore("exec-1", t2);
    }

    @Test
    @DisplayName("cleanupOldCheckpoints历史数量不超过keepLatest时不执行删除")
    void cleanupOldCheckpoints_historyNotExceedingKeepLatest_doesNothing() {
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), LocalDateTime.now());
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp1));

        service.cleanupOldCheckpoints("exec-1", 5);

        verify(checkpointRepository, never())
                .deleteByExecutionIdAndCreatedAtBefore(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("cleanupOldCheckpoints历史数量等于keepLatest时不执行删除")
    void cleanupOldCheckpoints_historyEqualsKeepLatest_doesNothing() {
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), LocalDateTime.now().minusMinutes(1));
        AgentCheckpoint cp2 = checkpoint("agent", "exec-1", Map.of("step", 2), LocalDateTime.now());
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp2, cp1));

        service.cleanupOldCheckpoints("exec-1", 2);

        verify(checkpointRepository, never())
                .deleteByExecutionIdAndCreatedAtBefore(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("cleanupOldCheckpoints keepLatest=0全部清理, cutoff为当前时间")
    void cleanupOldCheckpoints_keepLatestZero_clearsAll() {
        LocalDateTime beforeCall = LocalDateTime.now();
        AgentCheckpoint cp1 = checkpoint("agent", "exec-1", Map.of("step", 1), LocalDateTime.now().minusMinutes(1));
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of(cp1));

        service.cleanupOldCheckpoints("exec-1", 0);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(checkpointRepository).deleteByExecutionIdAndCreatedAtBefore(eq("exec-1"), cutoffCaptor.capture());
        // keepLatest=0: cutoff = now(), 应晚于调用前的时间
        assertThat(cutoffCaptor.getValue()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    @DisplayName("cleanupOldCheckpoints keepLatest=0且无历史时不执行删除")
    void cleanupOldCheckpoints_keepLatestZero_noHistory_doesNothing() {
        when(checkpointRepository.findByExecutionIdOrderByCreatedAtDesc("exec-1"))
                .thenReturn(List.of());

        service.cleanupOldCheckpoints("exec-1", 0);

        // history.size()=0 <= keepLatest=0, 走"无需清理"分支
        verify(checkpointRepository, never())
                .deleteByExecutionIdAndCreatedAtBefore(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("cleanupOldCheckpoints负数抛出IllegalArgumentException")
    void cleanupOldCheckpoints_negativeThrowsIllegalArgument() {
        assertThatThrownBy(() -> service.cleanupOldCheckpoints("exec-1", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keepLatest")
                .hasMessageContaining("-1");
        // 参数校验在查库之前, 不应触发任何仓库交互
        verify(checkpointRepository, never())
                .findByExecutionIdOrderByCreatedAtDesc(anyString());
        verify(checkpointRepository, never())
                .deleteByExecutionIdAndCreatedAtBefore(anyString(), any(LocalDateTime.class));
    }

    /**
     * 构造一个用于测试的检查点实例。
     */
    private AgentCheckpoint checkpoint(String agentId, String executionId,
                                       Map<String, Object> state, LocalDateTime createdAt) {
        return AgentCheckpoint.builder()
                .agentId(agentId)
                .executionId(executionId)
                .state(state)
                .createdAt(createdAt)
                .build();
    }
}
