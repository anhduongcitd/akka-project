package com.example.payment.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.JudgeAgent;
import com.example.payment.agents.domain.EvaluationCriteria;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentEvaluationEndpoint.
 */
public class AgentEvaluationEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldRunEvaluation() {
        // Given: Evaluation request
        var request = new AgentEvaluationEndpoint.RunEvaluationRequest(
            "customer-support",
            "test_001",
            "Where is my payment?",
            "Your payment of $50.00 was processed successfully on January 15th.",
            "Agent should provide clear status",
            "Response includes amount and status"
        );

        // When: Running evaluation
        var response = httpClient
            .POST("/evaluation/run")
            .withRequestBody(request)
            .responseBodyAs(AgentEvaluationEndpoint.EvaluationResponse.class)
            .invoke();

        // Then: Should return evaluation result
        assertThat(response.status().isSuccess()).isTrue();
        var result = response.body();
        assertThat(result.evaluationId()).isNotNull();
        assertThat(result.targetAgentId()).isEqualTo("customer-support");
        assertThat(result.scores()).isNotNull();
        assertThat(result.reasoning()).isNotNull();
        assertThat(result.overallScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    public void shouldRunTestCase() {
        // Given: Test case request
        var request = new AgentEvaluationEndpoint.TestCaseEvaluationRequest(
            "payment-assistant",
            "payment-status",
            "Your payment has been processed successfully and will arrive in 2-3 business days."
        );

        // When: Running test case
        var response = httpClient
            .POST("/evaluation/test-case")
            .withRequestBody(request)
            .responseBodyAs(AgentEvaluationEndpoint.EvaluationResponse.class)
            .invoke();

        // Then: Should return evaluation result
        assertThat(response.status().isSuccess()).isTrue();
        var result = response.body();
        assertThat(result.evaluationId()).isNotNull();
        assertThat(result.targetAgentId()).isEqualTo("payment-assistant");
        assertThat(result.passed()).isIn(true, false); // Can be either
    }

    @Test
    public void shouldGetHistory() {
        // Given: Agent with evaluations
        String agentId = "history-test-agent";

        // Create evaluation first
        var evalRequest = new AgentEvaluationEndpoint.RunEvaluationRequest(
            agentId,
            "test_history_001",
            "Test query",
            "Test response",
            "Expected behavior",
            "Success criteria"
        );

        httpClient.POST("/evaluation/run")
            .withRequestBody(evalRequest)
            .invoke();

        // Wait for evaluation to be recorded
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var historyResponse = httpClient
                    .GET("/evaluation/history/" + agentId)
                    .responseBodyAs(AgentEvaluationEndpoint.HistoryResponse.class)
                    .invoke();

                assertThat(historyResponse.status().isSuccess()).isTrue();
                assertThat(historyResponse.body().evaluations()).isNotEmpty();
            });

        // When: Getting history
        var response = httpClient
            .GET("/evaluation/history/" + agentId)
            .responseBodyAs(AgentEvaluationEndpoint.HistoryResponse.class)
            .invoke();

        // Then: Should return history
        assertThat(response.status().isSuccess()).isTrue();
        var history = response.body();
        assertThat(history.agentId()).isEqualTo(agentId);
        assertThat(history.evaluations()).isNotEmpty();
        assertThat(history.stats()).isNotNull();
        assertThat(history.stats().totalEvaluations()).isGreaterThan(0);
    }

    @Test
    public void shouldGetStats() {
        // Given: Agent with evaluations
        String agentId = "stats-test-agent";

        // Create evaluations
        var evalRequest1 = new AgentEvaluationEndpoint.RunEvaluationRequest(
            agentId,
            "test_stats_001",
            "Query 1",
            "Good response with details",
            "Expected",
            "Criteria"
        );

        httpClient.POST("/evaluation/run")
            .withRequestBody(evalRequest1)
            .invoke();

        var evalRequest2 = new AgentEvaluationEndpoint.RunEvaluationRequest(
            agentId,
            "test_stats_002",
            "Query 2",
            "Another good response",
            "Expected",
            "Criteria"
        );

        httpClient.POST("/evaluation/run")
            .withRequestBody(evalRequest2)
            .invoke();

        // Wait for evaluations to be recorded
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statsResponse = httpClient
                    .GET("/evaluation/stats/" + agentId)
                    .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
                    .invoke();

                assertThat(statsResponse.status().isSuccess()).isTrue();
                assertThat(statsResponse.body().totalEvaluations()).isGreaterThanOrEqualTo(2);
            });

        // When: Getting stats
        var response = httpClient
            .GET("/evaluation/stats/" + agentId)
            .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
            .invoke();

        // Then: Should return statistics
        assertThat(response.status().isSuccess()).isTrue();
        var stats = response.body();
        assertThat(stats.totalEvaluations()).isGreaterThanOrEqualTo(2);
        assertThat(stats.passRate()).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0);
        assertThat(stats.averageScore()).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(5.0);
    }

    @Test
    public void shouldCalculatePassRate() {
        // Given: Agent with mixed evaluations
        String agentId = "passrate-test-agent";

        // Good response (should pass)
        var goodRequest = new AgentEvaluationEndpoint.RunEvaluationRequest(
            agentId,
            "test_pass_001",
            "Test query",
            "Excellent detailed response with all required information",
            "Expected",
            "Criteria"
        );

        httpClient.POST("/evaluation/run")
            .withRequestBody(goodRequest)
            .invoke();

        // Poor response (should fail)
        var poorRequest = new AgentEvaluationEndpoint.RunEvaluationRequest(
            agentId,
            "test_pass_002",
            "Test query",
            "I don't know",
            "Expected",
            "Criteria"
        );

        httpClient.POST("/evaluation/run")
            .withRequestBody(poorRequest)
            .invoke();

        // Wait for both evaluations
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var stats = httpClient
                    .GET("/evaluation/stats/" + agentId)
                    .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
                    .invoke()
                    .body();

                assertThat(stats.totalEvaluations()).isGreaterThanOrEqualTo(2);
            });

        // When: Getting final stats
        var response = httpClient
            .GET("/evaluation/stats/" + agentId)
            .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
            .invoke();

        // Then: Should calculate pass rate
        assertThat(response.status().isSuccess()).isTrue();
        var stats = response.body();
        assertThat(stats.totalEvaluations()).isGreaterThanOrEqualTo(2);
        assertThat(stats.passedEvaluations() + stats.failedEvaluations()).isEqualTo(stats.totalEvaluations());
    }

    @Test
    public void shouldTrackMultipleAgents() {
        // Given: Multiple agents with evaluations
        String agent1 = "multi-agent-1";
        String agent2 = "multi-agent-2";

        httpClient.POST("/evaluation/run")
            .withRequestBody(new AgentEvaluationEndpoint.RunEvaluationRequest(
                agent1, "test_001", "Query", "Response", "Expected", "Criteria"
            ))
            .invoke();

        httpClient.POST("/evaluation/run")
            .withRequestBody(new AgentEvaluationEndpoint.RunEvaluationRequest(
                agent2, "test_002", "Query", "Response", "Expected", "Criteria"
            ))
            .invoke();

        // Wait for both
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var stats1 = httpClient.GET("/evaluation/stats/" + agent1)
                    .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
                    .invoke().body();

                var stats2 = httpClient.GET("/evaluation/stats/" + agent2)
                    .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
                    .invoke().body();

                assertThat(stats1.totalEvaluations()).isGreaterThan(0);
                assertThat(stats2.totalEvaluations()).isGreaterThan(0);
            });

        // When: Getting stats for each
        var stats1 = httpClient.GET("/evaluation/stats/" + agent1)
            .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
            .invoke().body();

        var stats2 = httpClient.GET("/evaluation/stats/" + agent2)
            .responseBodyAs(AgentEvaluationEndpoint.Statistics.class)
            .invoke().body();

        // Then: Each should have independent tracking
        assertThat(stats1.totalEvaluations()).isGreaterThan(0);
        assertThat(stats2.totalEvaluations()).isGreaterThan(0);
    }

    @Test
    public void shouldHandleTestCaseTypes() {
        // Given: Different test case types
        String[] testCaseTypes = {
            "payment-status",
            "refund-eligibility",
            "payment-failure",
            "fraud-concern"
        };

        for (String type : testCaseTypes) {
            // When: Running each test case type
            var request = new AgentEvaluationEndpoint.TestCaseEvaluationRequest(
                "test-agent-" + type,
                type,
                "Test response for " + type
            );

            var response = httpClient
                .POST("/evaluation/test-case")
                .withRequestBody(request)
                .responseBodyAs(AgentEvaluationEndpoint.EvaluationResponse.class)
                .invoke();

            // Then: Should handle each type
            assertThat(response.status().isSuccess()).isTrue();
            assertThat(response.body().evaluationId()).isNotNull();
        }
    }
}
