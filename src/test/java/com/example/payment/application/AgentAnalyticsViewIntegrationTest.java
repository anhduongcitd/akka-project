package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentAnalyticsView.
 *
 * Tests view updates from AgentPerformanceEntity.
 */
public class AgentAnalyticsViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages(AgentPerformanceEntity.class);
    }

    @Test
    public void shouldUpdateViewWhenAgentRecordsSuccess() {
        // Given: Agent records successful call
        String agentId = "test-agent-1";

        componentClient
            .forKeyValueEntity(agentId)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // When: View is updated
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAgentMetrics)
                    .invoke(agentId);

                // Then: View contains agent metrics
                assertThat(result).isNotNull();
                assertThat(result.agentId()).isEqualTo(agentId);
                assertThat(result.totalCalls()).isEqualTo(1);
                assertThat(result.successfulCalls()).isEqualTo(1);
                assertThat(result.failedCalls()).isEqualTo(0);
                assertThat(result.successRate()).isEqualTo(1.0);
                assertThat(result.averageLatencyMs()).isEqualTo(500.0);
                assertThat(result.totalTokensUsed()).isEqualTo(1000.0);
                assertThat(result.totalCostUsd()).isEqualTo(0.005);
            });
    }

    @Test
    public void shouldUpdateViewWhenAgentRecordsFailure() {
        // Given: Agent records failed call
        String agentId = "test-agent-2";

        componentClient
            .forKeyValueEntity(agentId)
            .method(AgentPerformanceEntity::recordFailure)
            .invoke(new AgentPerformanceEntity.RecordFailure(800.0));

        // When: View is updated
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAgentMetrics)
                    .invoke(agentId);

                // Then: View shows failure
                assertThat(result).isNotNull();
                assertThat(result.totalCalls()).isEqualTo(1);
                assertThat(result.successfulCalls()).isEqualTo(0);
                assertThat(result.failedCalls()).isEqualTo(1);
                assertThat(result.successRate()).isEqualTo(0.0);
            });
    }

    @Test
    public void shouldShowMultipleAgentsInView() {
        // Given: Multiple agents with activity
        String agent1 = "test-agent-multi-1";
        String agent2 = "test-agent-multi-2";
        String agent3 = "test-agent-multi-3";

        componentClient
            .forKeyValueEntity(agent1)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(400.0, 800.0, 0.004));

        componentClient
            .forKeyValueEntity(agent2)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(600.0, 1200.0, 0.006));

        componentClient
            .forKeyValueEntity(agent3)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // When: Querying all agents
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAllAgentMetrics)
                    .invoke();

                // Then: All agents appear in view
                assertThat(result.agents()).hasSizeGreaterThanOrEqualTo(3);
                assertThat(result.agents())
                    .extracting(AgentAnalyticsView.AgentMetricsRow::agentId)
                    .contains(agent1, agent2, agent3);
            });
    }

    @Test
    public void shouldSortAgentsByActivity() {
        // Given: Agents with different call counts
        String lowActivity = "test-agent-low";
        String highActivity = "test-agent-high";

        // High activity agent - 5 calls
        for (int i = 0; i < 5; i++) {
            componentClient
                .forKeyValueEntity(highActivity)
                .method(AgentPerformanceEntity::recordSuccess)
                .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));
        }

        // Low activity agent - 1 call
        componentClient
            .forKeyValueEntity(lowActivity)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // When: Sorting by activity
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAgentsByActivity)
                    .invoke();

                // Then: High activity agent should appear before low activity
                var highActivityRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(highActivity))
                    .findFirst();

                var lowActivityRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(lowActivity))
                    .findFirst();

                assertThat(highActivityRow).isPresent();
                assertThat(lowActivityRow).isPresent();
                assertThat(highActivityRow.get().totalCalls()).isGreaterThan(lowActivityRow.get().totalCalls());
            });
    }

    @Test
    public void shouldSortAgentsByPerformance() {
        // Given: Agents with different success rates
        String goodAgent = "test-agent-good";
        String poorAgent = "test-agent-poor";

        // Good agent - 100% success
        componentClient
            .forKeyValueEntity(goodAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // Poor agent - 50% success
        componentClient
            .forKeyValueEntity(poorAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        componentClient
            .forKeyValueEntity(poorAgent)
            .method(AgentPerformanceEntity::recordFailure)
            .invoke(new AgentPerformanceEntity.RecordFailure(800.0));

        // When: Sorting by performance
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAgentsByPerformance)
                    .invoke();

                // Then: Good agent should appear before poor agent
                var goodRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(goodAgent))
                    .findFirst();

                var poorRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(poorAgent))
                    .findFirst();

                assertThat(goodRow).isPresent();
                assertThat(poorRow).isPresent();
                assertThat(goodRow.get().successRate()).isGreaterThan(poorRow.get().successRate());
            });
    }

    @Test
    public void shouldSortAgentsByCost() {
        // Given: Agents with different costs
        String cheapAgent = "test-agent-cheap";
        String expensiveAgent = "test-agent-expensive";

        // Cheap agent - $0.001
        componentClient
            .forKeyValueEntity(cheapAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 500.0, 0.001));

        // Expensive agent - $0.01
        componentClient
            .forKeyValueEntity(expensiveAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 2000.0, 0.01));

        // When: Sorting by cost
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forView()
                    .method(AgentAnalyticsView::getAgentsByCost)
                    .invoke();

                // Then: Expensive agent should appear before cheap agent
                var expensiveRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(expensiveAgent))
                    .findFirst();

                var cheapRow = result.agents().stream()
                    .filter(row -> row.agentId().equals(cheapAgent))
                    .findFirst();

                assertThat(expensiveRow).isPresent();
                assertThat(cheapRow).isPresent();
                assertThat(expensiveRow.get().totalCostUsd()).isGreaterThan(cheapRow.get().totalCostUsd());
            });
    }
}
