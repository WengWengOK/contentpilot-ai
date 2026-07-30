package com.contentops.ai.agent.checkpoint;

import com.contentops.ai.capability.tenant.TenantContext;
import com.contentops.ai.common.constant.AiConstants;
import com.contentops.ai.common.exception.BusinessException;
import com.contentops.ai.domain.entity.AgentExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentExecutionService} 单元测试。
 *
 * <p>验证 Agent 执行记录的生命周期管理: 启动(running)、完成(completed)、
 * 失败(failed)状态流转, 以及执行不存在时抛出 BusinessException(404)。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Agent 执行记录服务 AgentExecutionService 测试")
class AgentExecutionServiceTest {

    @Mock
    private AgentExecutionRepository executionRepository;

    @InjectMocks
    private AgentExecutionService agentExecutionService;

    @AfterEach
    void tearDown() {
        // 清理 ThreadLocal, 避免影响其他测试
        TenantContext.clear();
    }

    // ==================== 启动执行 ====================

    @Test
    @DisplayName("startExecution(指定ID): 创建running状态记录, 包含executionId/traceId/agentType/input")
    void startExecution_指定ID_应创建running状态记录() {
        // given
        String executionId = "exec-test-001";
        Long tenantId = 1L;
        String agentType = AiConstants.AgentType.CONTENT_CREATION;
        Map<String, Object> input = Map.of("topic", "AI内容运营", "format", "article");
        String traceId = "trace-abc123";

        // when
        AgentExecution result = agentExecutionService.startExecution(
                executionId, tenantId, agentType, input, traceId);

        // then
        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(executionRepository).save(captor.capture());

        AgentExecution saved = captor.getValue();
        assertThat(saved.getExecutionId()).isEqualTo(executionId);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getAgentType()).isEqualTo(agentType);
        assertThat(saved.getStatus()).isEqualTo(AiConstants.ExecutionStatus.RUNNING);
        assertThat(saved.getTraceId()).isEqualTo(traceId);
        assertThat(saved.getTokensUsed()).isEqualTo(0);
        assertThat(saved.getInput()).containsEntry("topic", "AI内容运营");
        assertThat(saved.getInput()).containsEntry("format", "article");

        // 返回值与保存的实体一致
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("startExecution(自动生成ID): 自动生成executionId和traceId, tenantId从字符串解析为Long")
    void startExecution_自动生成ID_应生成executionId和traceId() {
        // given
        String agentType = AiConstants.AgentType.TOPIC_PLANNING;
        String tenantIdStr = "123";
        Map<String, Object> input = Map.of("keyword", "内容营销");

        // when
        AgentExecution result = agentExecutionService.startExecution(agentType, tenantIdStr, input);

        // then
        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(executionRepository).save(captor.capture());

        AgentExecution saved = captor.getValue();
        assertThat(saved.getExecutionId()).startsWith("exec-");
        assertThat(saved.getExecutionId()).hasSize(37); // "exec-" + 32位UUID无连字符 + 5 = 37
        assertThat(saved.getTenantId()).isEqualTo(123L);
        assertThat(saved.getAgentType()).isEqualTo(agentType);
        assertThat(saved.getStatus()).isEqualTo(AiConstants.ExecutionStatus.RUNNING);
        assertThat(saved.getTraceId()).isNotBlank();
        assertThat(saved.getTraceId()).hasSize(32); // 32位无连字符UUID
        assertThat(saved.getTokensUsed()).isEqualTo(0);
        assertThat(saved.getInput()).containsEntry("keyword", "内容营销");

        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("startExecution当input为null时写入空Map")
    void startExecution_input为null_应写入空Map() {
        // when
        agentExecutionService.startExecution("exec-001", 1L, "test_agent", null, "trace-001");

        // then
        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(executionRepository).save(captor.capture());

        AgentExecution saved = captor.getValue();
        assertThat(saved.getInput()).isNotNull();
        assertThat(saved.getInput()).isEmpty();
    }

    // ==================== 完成执行 ====================

    @Test
    @DisplayName("completeExecution: 更新状态为completed, 写入output/tokensUsed/modelUsed/completedAt")
    void completeExecution_应更新为completed状态() {
        // given
        String executionId = "exec-complete-001";
        AgentExecution existing = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(1L)
                .agentType(AiConstants.AgentType.CONTENT_CREATION)
                .status(AiConstants.ExecutionStatus.RUNNING)
                .input(Map.of("topic", "测试"))
                .tokensUsed(0)
                .traceId("trace-001")
                .build();

        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(existing));

        Map<String, Object> output = Map.of("content", "生成的内容", "wordCount", 500);

        // when
        agentExecutionService.completeExecution(executionId, output, 1200, "gpt-4o");

        // then
        verify(executionRepository).findByExecutionId(executionId);
        verify(executionRepository).save(existing);

        // 验证实体字段被正确更新
        assertThat(existing.getStatus()).isEqualTo(AiConstants.ExecutionStatus.COMPLETED);
        assertThat(existing.getOutput()).containsEntry("content", "生成的内容");
        assertThat(existing.getOutput()).containsEntry("wordCount", 500);
        assertThat(existing.getTokensUsed()).isEqualTo(1200);
        assertThat(existing.getModelUsed()).isEqualTo("gpt-4o");
        assertThat(existing.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeExecution(指定status): 可指定为completed或failed")
    void completeExecution_指定status_应更新为指定状态() {
        // given
        String executionId = "exec-status-001";
        AgentExecution existing = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(1L)
                .agentType(AiConstants.AgentType.ANALYSIS)
                .status(AiConstants.ExecutionStatus.RUNNING)
                .tokensUsed(0)
                .build();

        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(existing));

        // when - 指定 status 为 failed
        agentExecutionService.completeExecution(
                executionId, AiConstants.ExecutionStatus.FAILED, Map.of("error", "超时"), 500, "deepseek-chat");

        // then
        assertThat(existing.getStatus()).isEqualTo(AiConstants.ExecutionStatus.FAILED);
        assertThat(existing.getOutput()).containsEntry("error", "超时");
        assertThat(existing.getTokensUsed()).isEqualTo(500);
        assertThat(existing.getModelUsed()).isEqualTo("deepseek-chat");
        assertThat(existing.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeExecution当output为null时写入空Map")
    void completeExecution_output为null_应写入空Map() {
        // given
        String executionId = "exec-null-output";
        AgentExecution existing = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(1L)
                .agentType("test")
                .status(AiConstants.ExecutionStatus.RUNNING)
                .build();

        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(existing));

        // when
        agentExecutionService.completeExecution(executionId, null, 100, "gpt-4o");

        // then
        assertThat(existing.getOutput()).isNotNull();
        assertThat(existing.getOutput()).isEmpty();
    }

    // ==================== 失败执行 ====================

    @Test
    @DisplayName("failExecution: 更新状态为failed, 错误信息写入output.error, 记录completedAt")
    void failExecution_应更新为failed并写入error() {
        // given
        String executionId = "exec-fail-001";
        String errorMessage = "模型调用超时";

        AgentExecution existing = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(1L)
                .agentType(AiConstants.AgentType.CONTENT_CREATION)
                .status(AiConstants.ExecutionStatus.RUNNING)
                .input(Map.of("topic", "测试"))
                .tokensUsed(500)
                .modelUsed("gpt-4o")
                .traceId("trace-001")
                .build();

        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(existing));

        // when
        agentExecutionService.failExecution(executionId, errorMessage);

        // then
        verify(executionRepository).findByExecutionId(executionId);
        verify(executionRepository).save(existing);

        // 验证状态更新
        assertThat(existing.getStatus()).isEqualTo(AiConstants.ExecutionStatus.FAILED);
        // 验证错误信息写入 output.error
        assertThat(existing.getOutput()).containsEntry("error", errorMessage);
        // 验证 failedAt 时间戳写入
        assertThat(existing.getOutput()).containsKey("failedAt");
        assertThat(existing.getOutput().get("failedAt")).isInstanceOf(String.class);
        // 验证 completedAt 被设置
        assertThat(existing.getCompletedAt()).isNotNull();
        // 验证既有字段未被覆盖
        assertThat(existing.getTokensUsed()).isEqualTo(500);
        assertThat(existing.getModelUsed()).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("failExecution当已有output时保留既有内容并追加error字段")
    void failExecution_已有output_应保留既有内容并追加error() {
        // given
        String executionId = "exec-fail-002";
        String errorMessage = "数据库写入失败";

        Map<String, Object> existingOutput = new HashMap<>();
        existingOutput.put("partialResult", "部分生成的内容");
        existingOutput.put("progress", "50%");

        AgentExecution existing = AgentExecution.builder()
                .executionId(executionId)
                .tenantId(1L)
                .agentType("test")
                .status(AiConstants.ExecutionStatus.RUNNING)
                .output(existingOutput)
                .build();

        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.of(existing));

        // when
        agentExecutionService.failExecution(executionId, errorMessage);

        // then
        assertThat(existing.getOutput()).containsEntry("partialResult", "部分生成的内容");
        assertThat(existing.getOutput()).containsEntry("progress", "50%");
        assertThat(existing.getOutput()).containsEntry("error", errorMessage);
        assertThat(existing.getOutput()).containsKey("failedAt");
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("completeExecution当执行不存在时抛出BusinessException(404)")
    void completeExecution_执行不存在_应抛出BusinessException404() {
        // given
        String executionId = "exec-not-found-001";
        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agentExecutionService.completeExecution(
                executionId, Map.of(), 0, "gpt-4o"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent 执行不存在")
                .hasFieldOrPropertyWithValue("code", 404);

        // 验证未执行 save
        verify(executionRepository).findByExecutionId(executionId);
        verify(executionRepository, never()).save(any(AgentExecution.class));
    }

    @Test
    @DisplayName("failExecution当执行不存在时抛出BusinessException(404)")
    void failExecution_执行不存在_应抛出BusinessException404() {
        // given
        String executionId = "exec-not-found-002";
        when(executionRepository.findByExecutionId(executionId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> agentExecutionService.failExecution(executionId, "错误信息"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Agent 执行不存在")
                .hasFieldOrPropertyWithValue("code", 404);

        verify(executionRepository).findByExecutionId(executionId);
        verify(executionRepository, never()).save(any(AgentExecution.class));
    }
}
