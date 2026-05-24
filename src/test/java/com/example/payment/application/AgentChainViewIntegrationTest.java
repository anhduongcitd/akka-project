package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.domain.AgentChainConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentChainView.
 */
public class AgentChainViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages(AgentChainEntity.class);
    }

    @Test
    public void shouldQueryAllChains() {
        // Given: Multiple chains
        String id1 = "view-chain-001";
        String id2 = "view-chain-002";

        var config1 = createTestChain(id1, "Chain 1", AgentChainConfig.ExecutionMode.SEQUENTIAL);
        var config2 = createTestChain(id2, "Chain 2", AgentChainConfig.ExecutionMode.PARALLEL);

        componentClient.forKeyValueEntity(id1)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(id1, config1));

        componentClient.forKeyValueEntity(id2)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(id2, config2));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getAllChains)
                    .invoke();

                assertThat(result.chains()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Querying all
        var result = componentClient.forView()
            .method(AgentChainView::getAllChains)
            .invoke();

        // Then: Should return all
        assertThat(result.chains()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void shouldQueryByExecutionMode() {
        // Given: Chains with different modes
        String seqId = "view-chain-seq-001";
        String parId = "view-chain-par-001";

        var seqConfig = createTestChain(seqId, "Sequential", AgentChainConfig.ExecutionMode.SEQUENTIAL);
        var parConfig = createTestChain(parId, "Parallel", AgentChainConfig.ExecutionMode.PARALLEL);

        componentClient.forKeyValueEntity(seqId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(seqId, seqConfig));

        componentClient.forKeyValueEntity(parId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(parId, parConfig));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getByExecutionMode)
                    .invoke("SEQUENTIAL");

                assertThat(result.chains()).isNotEmpty();
            });

        // When: Querying by mode
        var result = componentClient.forView()
            .method(AgentChainView::getByExecutionMode)
            .invoke("SEQUENTIAL");

        // Then: Should return only sequential
        assertThat(result.chains()).isNotEmpty();
        assertThat(result.chains())
            .allMatch(c -> c.executionMode().equals("SEQUENTIAL"));
    }

    @Test
    public void shouldSearchChains() {
        // Given: Chain with searchable name
        String searchId = "view-chain-search-001";

        var config = createTestChain(searchId, "Unique Search Name", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(searchId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(searchId, config));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::searchChains)
                    .invoke("%Unique%");

                assertThat(result.chains())
                    .anyMatch(c -> c.chainId().equals(searchId));
            });

        // When: Searching
        var result = componentClient.forView()
            .method(AgentChainView::searchChains)
            .invoke("%Unique%");

        // Then: Should find chain
        assertThat(result.chains())
            .anyMatch(c -> c.chainId().equals(searchId));
    }

    @Test
    public void shouldGetChainById() {
        // Given: Chain
        String chainId = "view-chain-getbyid-001";

        var config = createTestChain(chainId, "Get By ID", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getById)
                    .invoke(chainId);

                assertThat(result).isNotNull();
            });

        // When: Getting by ID
        var result = componentClient.forView()
            .method(AgentChainView::getById)
            .invoke(chainId);

        // Then: Should return chain
        assertThat(result.chainId()).isEqualTo(chainId);
        assertThat(result.name()).isEqualTo("Get By ID");
    }

    @Test
    public void shouldReflectUpdates() {
        // Given: Chain
        String updateId = "view-chain-update-001";

        var config = createTestChain(updateId, "Original", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(updateId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(updateId, config));

        // Wait for creation
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getById)
                    .invoke(updateId);

                assertThat(result.name()).isEqualTo("Original");
            });

        // When: Updating
        var updatedConfig = createTestChain(updateId, "Updated", AgentChainConfig.ExecutionMode.PARALLEL);

        componentClient.forKeyValueEntity(updateId)
            .method(AgentChainEntity::updateChain)
            .invoke(new AgentChainEntity.UpdateChain(updatedConfig));

        // Wait for update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getById)
                    .invoke(updateId);

                assertThat(result.name()).isEqualTo("Updated");
            });

        // Then: Should reflect changes
        var result = componentClient.forView()
            .method(AgentChainView::getById)
            .invoke(updateId);

        assertThat(result.name()).isEqualTo("Updated");
        assertThat(result.executionMode()).isEqualTo("PARALLEL");
    }

    @Test
    public void shouldShowStepCount() {
        // Given: Chain with multiple steps
        String chainId = "view-chain-steps-001";

        var config = new AgentChainConfig(
            chainId, "Multi-Step", "Description",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "out1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "input", Map.of(), "out2"),
                new AgentChainConfig.ChainStep("step3", "agent3", "input", Map.of(), "out3")
            ),
            Map.of(), false
        );

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getById)
                    .invoke(chainId);

                assertThat(result.stepCount()).isEqualTo(3);
            });

        // When: Querying
        var result = componentClient.forView()
            .method(AgentChainView::getById)
            .invoke(chainId);

        // Then: Should show step count
        assertThat(result.stepCount()).isEqualTo(3);
        assertThat(result.agentIds()).hasSize(1); // All same agent
    }

    @Test
    public void shouldShowUniqueAgents() {
        // Given: Chain with different agents
        String chainId = "view-chain-agents-001";

        var config = new AgentChainConfig(
            chainId, "Multi-Agent", "Description",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "out1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "input", Map.of(), "out2"),
                new AgentChainConfig.ChainStep("step3", "agent1", "input", Map.of(), "out3")
            ),
            Map.of(), false
        );

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentChainView::getById)
                    .invoke(chainId);

                assertThat(result.agentIds()).hasSize(2);
            });

        // When: Querying
        var result = componentClient.forView()
            .method(AgentChainView::getById)
            .invoke(chainId);

        // Then: Should show unique agents
        assertThat(result.agentIds()).containsExactlyInAnyOrder("agent1", "agent2");
    }

    private AgentChainConfig createTestChain(String chainId, String name, AgentChainConfig.ExecutionMode mode) {
        return new AgentChainConfig(
            chainId, name, "Test chain description", mode,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "output")),
            Map.of(), false
        );
    }
}
