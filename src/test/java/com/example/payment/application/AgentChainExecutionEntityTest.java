package com.example.payment.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.AgentChainExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentChainExecutionEntity.
 */
public class AgentChainExecutionEntityTest {

    @Test
    public void shouldStartExecution() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("exec-001", AgentChainExecutionEntity::new);

        // When: Starting execution
        var response = testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-001",
                "chain-001",
                Map.of("input", "test")
            ));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.executionId()).isEqualTo("exec-001");
        assertThat(state.chainId()).isEqualTo("chain-001");
        assertThat(state.status()).isEqualTo(AgentChainExecution.ExecutionStatus.RUNNING);
    }

    @Test
    public void shouldRejectDuplicateExecution() {
        // Given: Existing execution
        var testKit = KeyValueEntityTestKit.of("exec-002", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-002", "chain-001", Map.of()
            ));

        // When: Starting duplicate
        var response = testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-002", "chain-001", Map.of()
            ));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already exists");
    }

    @Test
    public void shouldAddStepResult() {
        // Given: Running execution
        var testKit = KeyValueEntityTestKit.of("exec-003", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-003", "chain-001", Map.of("input", "test")
            ));

        // When: Adding step result
        var stepResult = AgentChainExecution.StepResult.success(
            "step1", "agent1", "input", "output", Instant.now(), Instant.now()
        );

        var response = testKit.method(AgentChainExecutionEntity::addStepResult)
            .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

        // Then: Should add result
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.stepResults()).hasSize(1);
        assertThat(state.getCompletedStepCount()).isEqualTo(1);
    }

    @Test
    public void shouldCompleteExecution() {
        // Given: Execution with steps
        var testKit = KeyValueEntityTestKit.of("exec-004", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-004", "chain-001", Map.of()
            ));

        // When: Completing
        var response = testKit.method(AgentChainExecutionEntity::completeExecution)
            .invoke(new AgentChainExecutionEntity.CompleteExecution("Final output"));

        // Then: Should complete
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(AgentChainExecution.ExecutionStatus.COMPLETED);
        assertThat(state.finalOutput()).isEqualTo("Final output");
        assertThat(state.completedAt()).isNotNull();
    }

    @Test
    public void shouldFailExecution() {
        // Given: Running execution
        var testKit = KeyValueEntityTestKit.of("exec-005", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-005", "chain-001", Map.of()
            ));

        // When: Failing
        var response = testKit.method(AgentChainExecutionEntity::failExecution)
            .invoke(new AgentChainExecutionEntity.FailExecution("Error occurred"));

        // Then: Should fail
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(AgentChainExecution.ExecutionStatus.FAILED);
        assertThat(state.errorMessage()).isEqualTo("Error occurred");
        assertThat(state.isFailed()).isTrue();
    }

    @Test
    public void shouldCancelExecution() {
        // Given: Running execution
        var testKit = KeyValueEntityTestKit.of("exec-006", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-006", "chain-001", Map.of()
            ));

        // When: Cancelling
        var response = testKit.method(AgentChainExecutionEntity::cancelExecution)
            .invoke();

        // Then: Should cancel
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(AgentChainExecution.ExecutionStatus.CANCELLED);
        assertThat(state.isComplete()).isTrue();
    }

    @Test
    public void shouldUpdateContext() {
        // Given: Execution
        var testKit = KeyValueEntityTestKit.of("exec-007", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-007", "chain-001", Map.of("initial", "value")
            ));

        // When: Updating context
        var response = testKit.method(AgentChainExecutionEntity::updateContext)
            .invoke(new AgentChainExecutionEntity.UpdateContext("newKey", "newValue"));

        // Then: Should update
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.getContextValue("newKey")).isEqualTo("newValue");
        assertThat(state.getContextValue("initial")).isEqualTo("value");
    }

    @Test
    public void shouldGetExecution() {
        // Given: Execution
        var testKit = KeyValueEntityTestKit.of("exec-008", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-008", "chain-001", Map.of()
            ));

        // When: Getting execution
        var response = testKit.method(AgentChainExecutionEntity::getExecution)
            .invoke();

        // Then: Should return execution
        assertThat(response.isReply()).isTrue();
        var execution = response.getReply();
        assertThat(execution.executionId()).isEqualTo("exec-008");
    }

    @Test
    public void shouldDeleteExecution() {
        // Given: Execution
        var testKit = KeyValueEntityTestKit.of("exec-009", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-009", "chain-001", Map.of()
            ));

        // When: Deleting
        var response = testKit.method(AgentChainExecutionEntity::deleteExecution)
            .invoke();

        // Then: Should delete
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void shouldTrackMultipleSteps() {
        // Given: Execution
        var testKit = KeyValueEntityTestKit.of("exec-010", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-010", "chain-001", Map.of()
            ));

        // When: Adding multiple steps
        var step1 = AgentChainExecution.StepResult.success(
            "step1", "agent1", "input1", "output1", Instant.now(), Instant.now()
        );
        var step2 = AgentChainExecution.StepResult.success(
            "step2", "agent2", "input2", "output2", Instant.now(), Instant.now()
        );
        var step3 = AgentChainExecution.StepResult.failure(
            "step3", "agent3", "input3", "error", Instant.now(), Instant.now()
        );

        testKit.method(AgentChainExecutionEntity::addStepResult)
            .invoke(new AgentChainExecutionEntity.AddStepResult(step1));
        testKit.method(AgentChainExecutionEntity::addStepResult)
            .invoke(new AgentChainExecutionEntity.AddStepResult(step2));
        testKit.method(AgentChainExecutionEntity::addStepResult)
            .invoke(new AgentChainExecutionEntity.AddStepResult(step3));

        // Then: Should track all steps
        var state = testKit.getState();
        assertThat(state.stepResults()).hasSize(3);
        assertThat(state.getCompletedStepCount()).isEqualTo(2);
        assertThat(state.getFailedStepCount()).isEqualTo(1);
    }

    @Test
    public void shouldRejectOperationsOnCompletedExecution() {
        // Given: Completed execution
        var testKit = KeyValueEntityTestKit.of("exec-011", AgentChainExecutionEntity::new);

        testKit.method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                "exec-011", "chain-001", Map.of()
            ));

        testKit.method(AgentChainExecutionEntity::completeExecution)
            .invoke(new AgentChainExecutionEntity.CompleteExecution("Done"));

        // When: Adding step after completion
        var stepResult = AgentChainExecution.StepResult.success(
            "step1", "agent1", "input", "output", Instant.now(), Instant.now()
        );

        var response = testKit.method(AgentChainExecutionEntity::addStepResult)
            .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already completed");
    }
}
