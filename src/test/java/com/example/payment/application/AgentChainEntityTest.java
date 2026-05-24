package com.example.payment.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.AgentChainConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentChainEntity.
 */
public class AgentChainEntityTest {

    @Test
    public void shouldCreateChain() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("chain-001", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-001",
            "Test Chain",
            "A test chain",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1")
            ),
            Map.of(),
            false
        );

        // When: Creating chain
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-001", config));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.chainId()).isEqualTo("chain-001");
        assertThat(state.name()).isEqualTo("Test Chain");
        assertThat(state.getStepCount()).isEqualTo(1);
    }

    @Test
    public void shouldRejectDuplicateChain() {
        // Given: Existing chain
        var testKit = KeyValueEntityTestKit.of("chain-002", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-002", "Chain", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1")),
            Map.of(), false
        );

        testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-002", config));

        // When: Creating duplicate
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-002", config));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already exists");
    }

    @Test
    public void shouldUpdateChain() {
        // Given: Existing chain
        var testKit = KeyValueEntityTestKit.of("chain-003", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-003", "Original", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1")),
            Map.of(), false
        );

        testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-003", config));

        // When: Updating chain
        var updatedConfig = new AgentChainConfig(
            "chain-003", "Updated", "New description",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "input", Map.of(), "output2")
            ),
            Map.of(), true
        );

        var response = testKit.method(AgentChainEntity::updateChain)
            .invoke(new AgentChainEntity.UpdateChain(updatedConfig));

        // Then: Should update
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.name()).isEqualTo("Updated");
        assertThat(state.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.PARALLEL);
        assertThat(state.getStepCount()).isEqualTo(2);
    }

    @Test
    public void shouldGetChain() {
        // Given: Chain
        var testKit = KeyValueEntityTestKit.of("chain-004", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-004", "My Chain", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1")),
            Map.of(), false
        );

        testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-004", config));

        // When: Getting chain
        var response = testKit.method(AgentChainEntity::getChain)
            .invoke();

        // Then: Should return chain
        assertThat(response.isReply()).isTrue();
        var chain = response.getReply();
        assertThat(chain.name()).isEqualTo("My Chain");
    }

    @Test
    public void shouldDeleteChain() {
        // Given: Chain
        var testKit = KeyValueEntityTestKit.of("chain-005", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-005", "Chain", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output1")),
            Map.of(), false
        );

        testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-005", config));

        // When: Deleting
        var response = testKit.method(AgentChainEntity::deleteChain)
            .invoke();

        // Then: Should delete
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void shouldRejectInvalidChain() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("chain-006", AgentChainEntity::new);

        // Invalid: no steps
        var config = new AgentChainConfig(
            "chain-006", "Invalid", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(),
            Map.of(), false
        );

        // When: Creating invalid chain
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-006", config));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("Invalid");
    }

    @Test
    public void shouldHandleSequentialChain() {
        // Given: Sequential chain
        var testKit = KeyValueEntityTestKit.of("chain-seq-001", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-seq-001", "Sequential", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "{{input}}", Map.of(), "step1_out"),
                new AgentChainConfig.ChainStep("step2", "agent2", "{{step1_out}}", Map.of(), "step2_out"),
                new AgentChainConfig.ChainStep("step3", "agent3", "{{step2_out}}", Map.of(), "final_out")
            ),
            Map.of(), false
        );

        // When: Creating
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-seq-001", config));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState().getStepCount()).isEqualTo(3);
    }

    @Test
    public void shouldHandleParallelChain() {
        // Given: Parallel chain
        var testKit = KeyValueEntityTestKit.of("chain-par-001", AgentChainEntity::new);

        var config = new AgentChainConfig(
            "chain-par-001", "Parallel", "Description",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "{{input}}", Map.of(), "result1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "{{input}}", Map.of(), "result2"),
                new AgentChainConfig.ChainStep("step3", "agent3", "{{input}}", Map.of(), "result3")
            ),
            Map.of(), false
        );

        // When: Creating
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-par-001", config));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState().executionMode())
            .isEqualTo(AgentChainConfig.ExecutionMode.PARALLEL);
    }

    @Test
    public void shouldHandleConditionalChain() {
        // Given: Conditional chain
        var testKit = KeyValueEntityTestKit.of("chain-cond-001", AgentChainEntity::new);

        var condition = new AgentChainConfig.Condition(
            "approval",
            AgentChainConfig.Condition.ConditionOperator.EQUALS,
            "approved"
        );

        var config = new AgentChainConfig(
            "chain-cond-001", "Conditional", "Description",
            AgentChainConfig.ExecutionMode.CONDITIONAL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "{{input}}", Map.of(), "result1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "{{result1}}", Map.of(), "result2", condition)
            ),
            Map.of(), false
        );

        // When: Creating
        var response = testKit.method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("chain-cond-001", config));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState().getStep("step2").condition()).isNotNull();
    }
}
