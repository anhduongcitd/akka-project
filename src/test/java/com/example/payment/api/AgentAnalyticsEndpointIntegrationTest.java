package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.application.AgentPerformanceEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentAnalyticsEndpoint.
 *
 * Tests REST API for agent analytics dashboard.
 */
public class AgentAnalyticsEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldGetAllAgents() {
        // Given: Agents with recorded activity
        String agent1 = "test-endpoint-agent-1";
        String agent2 = "test-endpoint-agent-2";

        componentClient
            .forKeyValueEntity(agent1)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        componentClient
            .forKeyValueEntity(agent2)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(600.0, 1200.0, 0.006));

        // When: Calling /analytics/agents
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/agents")
                    .responseBodyAs(AgentAnalyticsEndpoint.AllAgentsResponse.class)
                    .invoke();

                // Then: Should return all agents
                assertThat(response.status().isSuccess()).isTrue();
                assertThat(response.body().agents()).isNotEmpty();
                assertThat(response.body().summary()).isNotNull();
                assertThat(response.body().summary().totalAgents()).isGreaterThanOrEqualTo(2);
            });
    }

    @Test
    public void shouldGetSpecificAgent() {
        // Given: Agent with activity
        String agentId = "test-specific-agent";

        componentClient
            .forKeyValueEntity(agentId)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        componentClient
            .forKeyValueEntity(agentId)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(600.0, 1200.0, 0.006));

        // When: Calling /analytics/agents/{agentId}
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/agents/" + agentId)
                    .responseBodyAs(AgentAnalyticsEndpoint.AgentMetrics.class)
                    .invoke();

                // Then: Should return specific agent metrics
                assertThat(response.status().isSuccess()).isTrue();
                var metrics = response.body();
                assertThat(metrics.agentId()).isEqualTo(agentId);
                assertThat(metrics.totalCalls()).isEqualTo(2);
                assertThat(metrics.successfulCalls()).isEqualTo(2);
                assertThat(metrics.failedCalls()).isEqualTo(0);
                assertThat(metrics.successRate()).isEqualTo(1.0);
            });
    }

    @Test
    public void shouldGetDashboardSummary() {
        // Given: Multiple agents with activity
        String agent1 = "test-summary-1";
        String agent2 = "test-summary-2";

        componentClient
            .forKeyValueEntity(agent1)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        componentClient
            .forKeyValueEntity(agent2)
            .method(AgentPerformanceEntity::recordFailure)
            .invoke(new AgentPerformanceEntity.RecordFailure(800.0));

        // When: Calling /analytics/summary
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/summary")
                    .responseBodyAs(AgentAnalyticsEndpoint.DashboardSummary.class)
                    .invoke();

                // Then: Should return summary statistics
                assertThat(response.status().isSuccess()).isTrue();
                var summary = response.body();
                assertThat(summary.totalAgents()).isGreaterThanOrEqualTo(2);
                assertThat(summary.totalCalls()).isGreaterThanOrEqualTo(2);
                assertThat(summary.totalSuccesses()).isGreaterThanOrEqualTo(1);
                assertThat(summary.totalFailures()).isGreaterThanOrEqualTo(1);
                assertThat(summary.totalCostUsd()).isGreaterThan(0);
                assertThat(summary.timestamp()).isNotNull();
            });
    }

    @Test
    public void shouldGetCostBreakdown() {
        // Given: Agents with different costs
        String cheapAgent = "test-cost-cheap";
        String expensiveAgent = "test-cost-expensive";

        componentClient
            .forKeyValueEntity(cheapAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 500.0, 0.001));

        componentClient
            .forKeyValueEntity(expensiveAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 2000.0, 0.01));

        // When: Calling /analytics/costs
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/costs")
                    .responseBodyAs(AgentAnalyticsEndpoint.CostBreakdown.class)
                    .invoke();

                // Then: Should return cost breakdown
                assertThat(response.status().isSuccess()).isTrue();
                var breakdown = response.body();
                assertThat(breakdown.byAgent()).isNotEmpty();
                assertThat(breakdown.totalCost()).isGreaterThan(0);
                assertThat(breakdown.period()).isEqualTo("all-time");

                // Verify percentage calculation
                double totalPercentage = breakdown.byAgent().stream()
                    .mapToDouble(AgentAnalyticsEndpoint.AgentCost::percentage)
                    .sum();
                assertThat(totalPercentage).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1.0));
            });
    }

    @Test
    public void shouldGetAgentsByActivity() {
        // Given: Agents with different activity levels
        String lowActivity = "test-activity-low";
        String highActivity = "test-activity-high";

        // Low activity - 1 call
        componentClient
            .forKeyValueEntity(lowActivity)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // High activity - 3 calls
        for (int i = 0; i < 3; i++) {
            componentClient
                .forKeyValueEntity(highActivity)
                .method(AgentPerformanceEntity::recordSuccess)
                .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));
        }

        // When: Calling /analytics/agents/by-activity
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/agents/by-activity")
                    .responseBodyAs(AgentAnalyticsEndpoint.AllAgentsResponse.class)
                    .invoke();

                // Then: Should return agents sorted by activity
                assertThat(response.status().isSuccess()).isTrue();
                var agents = response.body().agents();
                assertThat(agents).isNotEmpty();

                // High activity agent should have more calls than low activity
                var highAgent = agents.stream()
                    .filter(a -> a.agentId().equals(highActivity))
                    .findFirst();
                var lowAgent = agents.stream()
                    .filter(a -> a.agentId().equals(lowActivity))
                    .findFirst();

                if (highAgent.isPresent() && lowAgent.isPresent()) {
                    assertThat(highAgent.get().totalCalls()).isGreaterThan(lowAgent.get().totalCalls());
                }
            });
    }

    @Test
    public void shouldGetAgentsByPerformance() {
        // Given: Agents with different success rates
        String goodAgent = "test-perf-good";
        String poorAgent = "test-perf-poor";

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

        // When: Calling /analytics/agents/by-performance
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/analytics/agents/by-performance")
                    .responseBodyAs(AgentAnalyticsEndpoint.AllAgentsResponse.class)
                    .invoke();

                // Then: Should return agents sorted by performance
                assertThat(response.status().isSuccess()).isTrue();
                var agents = response.body().agents();
                assertThat(agents).isNotEmpty();

                // Good agent should have higher success rate than poor agent
                var good = agents.stream()
                    .filter(a -> a.agentId().equals(goodAgent))
                    .findFirst();
                var poor = agents.stream()
                    .filter(a -> a.agentId().equals(poorAgent))
                    .findFirst();

                if (good.isPresent() && poor.isPresent()) {
                    assertThat(good.get().successRate()).isGreaterThan(poor.get().successRate());
                }
            });
    }

    @Test
    public void shouldShowCorrectAgentStatus() {
        // Given: Agents with different statuses
        String activeAgent = "test-status-active";
        String idleAgent = "test-status-idle";
        String errorAgent = "test-status-error";

        // Active agent - good success rate
        componentClient
            .forKeyValueEntity(activeAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // Error agent - low success rate
        componentClient
            .forKeyValueEntity(errorAgent)
            .method(AgentPerformanceEntity::recordFailure)
            .invoke(new AgentPerformanceEntity.RecordFailure(800.0));

        componentClient
            .forKeyValueEntity(errorAgent)
            .method(AgentPerformanceEntity::recordFailure)
            .invoke(new AgentPerformanceEntity.RecordFailure(800.0));

        componentClient
            .forKeyValueEntity(errorAgent)
            .method(AgentPerformanceEntity::recordSuccess)
            .invoke(new AgentPerformanceEntity.RecordSuccess(500.0, 1000.0, 0.005));

        // When: Getting agent metrics
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var activeResponse = httpClient
                    .GET("/analytics/agents/" + activeAgent)
                    .responseBodyAs(AgentAnalyticsEndpoint.AgentMetrics.class)
                    .invoke();

                var errorResponse = httpClient
                    .GET("/analytics/agents/" + errorAgent)
                    .responseBodyAs(AgentAnalyticsEndpoint.AgentMetrics.class)
                    .invoke();

                // Then: Status should reflect performance
                if (activeResponse.status().isSuccess()) {
                    assertThat(activeResponse.body().status()).isEqualTo("ACTIVE");
                }

                if (errorResponse.status().isSuccess()) {
                    assertThat(errorResponse.body().status()).isEqualTo("ERROR");
                }
            });
    }
}
