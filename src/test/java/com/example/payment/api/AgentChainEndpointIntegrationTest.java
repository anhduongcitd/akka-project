package com.example.payment.api;

import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.domain.AgentChainConfig;
import com.example.payment.application.AgentChainEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentChainEndpoint.
 */
public class AgentChainEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreateChain() {
        // Given: Create request
        String chainId = "endpoint-chain-001";

        var request = new AgentChainEndpoint.CreateChainRequest(
            chainId,
            "Test Chain",
            "A test chain",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "{{input}}", Map.of(), "output1")
            ),
            Map.of(),
            false
        );

        // When: Creating
        var response = httpClient
            .POST("/agent-chains/")
            .withRequestBody(request)
            .responseBodyAs(String.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldGetChain() {
        // Given: Existing chain
        String chainId = "endpoint-chain-get-001";

        var config = createTestChain(chainId, "Get Chain", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // When: Getting chain
        var response = httpClient
            .GET("/agent-chains/" + chainId)
            .responseBodyAs(AgentChainEndpoint.ChainResponse.class)
            .invoke();

        // Then: Should return chain
        assertThat(response.status().isSuccess()).isTrue();
        var chain = response.body();
        assertThat(chain.chainId()).isEqualTo(chainId);
        assertThat(chain.name()).isEqualTo("Get Chain");
    }

    @Test
    public void shouldUpdateChain() {
        // Given: Existing chain
        String chainId = "endpoint-chain-update-001";

        var config = createTestChain(chainId, "Original", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // When: Updating
        var updateRequest = new AgentChainEndpoint.UpdateChainRequest(
            "Updated Name",
            "Updated description",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep("step1", "agent1", "input", Map.of(), "out1"),
                new AgentChainConfig.ChainStep("step2", "agent2", "input", Map.of(), "out2")
            ),
            Map.of(),
            true
        );

        var response = httpClient
            .PUT("/agent-chains/" + chainId)
            .withRequestBody(updateRequest)
            .responseBodyAs(String.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        // Then: Should be updated
        var getResponse = httpClient
            .GET("/agent-chains/" + chainId)
            .responseBodyAs(AgentChainEndpoint.ChainResponse.class)
            .invoke();

        assertThat(getResponse.body().name()).isEqualTo("Updated Name");
        assertThat(getResponse.body().stepCount()).isEqualTo(2);
    }

    @Test
    public void shouldDeleteChain() {
        // Given: Existing chain
        String chainId = "endpoint-chain-delete-001";

        var config = createTestChain(chainId, "To Delete", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // When: Deleting
        var response = httpClient
            .DELETE("/agent-chains/" + chainId)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldListAllChains() {
        // Given: Multiple chains
        var config1 = createTestChain("endpoint-list-001", "Chain 1", AgentChainConfig.ExecutionMode.SEQUENTIAL);
        var config2 = createTestChain("endpoint-list-002", "Chain 2", AgentChainConfig.ExecutionMode.PARALLEL);

        componentClient.forKeyValueEntity("endpoint-list-001")
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("endpoint-list-001", config1));

        componentClient.forKeyValueEntity("endpoint-list-002")
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain("endpoint-list-002", config2));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/agent-chains/")
                    .responseBodyAs(AgentChainEndpoint.ChainListResponse.class)
                    .invoke();

                assertThat(result.body().chains()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Listing all
        var response = httpClient
            .GET("/agent-chains/")
            .responseBodyAs(AgentChainEndpoint.ChainListResponse.class)
            .invoke();

        // Then: Should return all
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().chains()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void shouldSearchChains() {
        // Given: Chain with searchable name
        String searchId = "endpoint-search-001";

        var config = createTestChain(searchId, "Unique Searchable Chain", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(searchId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(searchId, config));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/agent-chains/search?query=Unique")
                    .responseBodyAs(AgentChainEndpoint.ChainListResponse.class)
                    .invoke();

                assertThat(result.body().chains())
                    .anyMatch(c -> c.chainId().equals(searchId));
            });

        // When: Searching
        var response = httpClient
            .GET("/agent-chains/search?query=Unique")
            .responseBodyAs(AgentChainEndpoint.ChainListResponse.class)
            .invoke();

        // Then: Should find chain
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().chains())
            .anyMatch(c -> c.chainId().equals(searchId));
    }

    @Test
    public void shouldExecuteChain() {
        // Given: Chain
        String chainId = "endpoint-exec-001";

        var config = createTestChain(chainId, "Execute Test", AgentChainConfig.ExecutionMode.SEQUENTIAL);

        componentClient.forKeyValueEntity(chainId)
            .method(AgentChainEntity::createChain)
            .invoke(new AgentChainEntity.CreateChain(chainId, config));

        // When: Executing
        var execRequest = new AgentChainEndpoint.ExecuteChainRequest(
            Map.of("input", "test data")
        );

        var response = httpClient
            .POST("/agent-chains/" + chainId + "/execute")
            .withRequestBody(execRequest)
            .responseBodyAs(AgentChainEndpoint.ExecutionResponse.class)
            .invoke();

        // Then: Should start execution
        assertThat(response.status().isSuccess()).isTrue();
        var execution = response.body();
        assertThat(execution.executionId()).isNotBlank();
        assertThat(execution.chainId()).isEqualTo(chainId);
        assertThat(execution.status()).isEqualTo("RUNNING");
    }

    @Test
    public void shouldGetRecentExecutions() {
        // When: Getting recent executions
        var response = httpClient
            .GET("/agent-chains/executions")
            .responseBodyAs(AgentChainEndpoint.ExecutionListResponse.class)
            .invoke();

        // Then: Should return list
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().executions()).isNotNull();
    }

    @Test
    public void shouldGetRunningExecutions() {
        // When: Getting running executions
        var response = httpClient
            .GET("/agent-chains/executions/running")
            .responseBodyAs(AgentChainEndpoint.ExecutionListResponse.class)
            .invoke();

        // Then: Should return list
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().executions()).isNotNull();
    }

    @Test
    public void shouldGetFailedExecutions() {
        // When: Getting failed executions
        var response = httpClient
            .GET("/agent-chains/executions/failed")
            .responseBodyAs(AgentChainEndpoint.ExecutionListResponse.class)
            .invoke();

        // Then: Should return list
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().executions()).isNotNull();
    }

    @Test
    public void shouldRejectInvalidChain() {
        // Given: Invalid request (no steps)
        var request = new AgentChainEndpoint.CreateChainRequest(
            "invalid-001",
            "Invalid",
            "No steps",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(), // Empty steps
            Map.of(),
            false
        );

        // When: Creating
        var response = httpClient
            .POST("/agent-chains/")
            .withRequestBody(request)
            .responseBodyAs(String.class)
            .invoke();

        // Then: Should fail
        assertThat(response.status().isError()).isTrue();
    }

    private AgentChainConfig createTestChain(String chainId, String name, AgentChainConfig.ExecutionMode mode) {
        return new AgentChainConfig(
            chainId, name, "Test description", mode,
            List.of(new AgentChainConfig.ChainStep("step1", "agent1", "{{input}}", Map.of(), "output")),
            Map.of(), false
        );
    }
}
