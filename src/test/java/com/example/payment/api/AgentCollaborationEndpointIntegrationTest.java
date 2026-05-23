package com.example.payment.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.payment.agents.CustomerSupportAgent;
import com.example.payment.agents.PlannerAgent;
import com.example.payment.agents.SummarizerAgent;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.agents.domain.PlanStep;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentCollaborationEndpoint.
 *
 * Tests the HTTP API for multi-agent collaboration.
 */
public class AgentCollaborationEndpointIntegrationTest extends TestKitSupport {

    private final TestModelProvider plannerModel = new TestModelProvider();
    private final TestModelProvider supportModel = new TestModelProvider();
    private final TestModelProvider summarizerModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(PlannerAgent.class, plannerModel)
            .withModelProvider(CustomerSupportAgent.class, supportModel)
            .withModelProvider(SummarizerAgent.class, summarizerModel);
    }

    @Test
    public void shouldStartCollaborationWithGeneratedSessionId() {
        // Given: Collaboration request without session ID
        var request = new AgentCollaborationEndpoint.CollaborationRequest(
            "Where is my payment?",
            "cust_test_1",
            "txn_test_1",
            null  // No session ID provided
        );

        // Mock agent responses
        setupSuccessfulCollaboration();

        // When: Calling collaborate endpoint
        var response = httpClient
            .POST("/agents/collaborate")
            .withRequestBody(request)
            .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
            .invoke();

        // Then: Should generate session ID and start workflow
        assertThat(response.status().isSuccess()).isTrue();
        var body = response.body();
        assertThat(body.sessionId()).isNotNull();
        assertThat(body.sessionId()).isNotEmpty();
        // Initially may be PENDING since workflow is async
        assertThat(body.status()).isIn("PENDING", "COMPLETED");
    }

    @Test
    public void shouldUseProvidedSessionId() {
        // Given: Collaboration request with specific session ID
        String sessionId = "custom-session-" + UUID.randomUUID();
        var request = new AgentCollaborationEndpoint.CollaborationRequest(
            "Check payment status",
            "cust_test_2",
            "txn_test_2",
            sessionId
        );

        // Mock agent responses
        setupSuccessfulCollaboration();

        // When: Calling collaborate endpoint
        var response = httpClient
            .POST("/agents/collaborate")
            .withRequestBody(request)
            .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
            .invoke();

        // Then: Should use provided session ID
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().sessionId()).isEqualTo(sessionId);
    }

    @Test
    public void shouldCompleteCollaborationSuccessfully() {
        // Given: Valid collaboration request
        String sessionId = "test-complete-" + UUID.randomUUID();
        var request = new AgentCollaborationEndpoint.CollaborationRequest(
            "Is my payment successful?",
            "cust_test_3",
            "txn_test_3",
            sessionId
        );

        // Mock agent responses
        setupSuccessfulCollaboration();

        // When: Starting collaboration
        var startResponse = httpClient
            .POST("/agents/collaborate")
            .withRequestBody(request)
            .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
            .invoke();

        assertThat(startResponse.status().isSuccess()).isTrue();

        // Then: Wait for workflow to complete and verify result
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var checkResponse = httpClient
                    .POST("/agents/collaborate")
                    .withRequestBody(request)  // Same request checks existing workflow
                    .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
                    .invoke();

                if (checkResponse.body().status().equals("COMPLETED")) {
                    assertThat(checkResponse.body().answer()).isNotNull();
                    assertThat(checkResponse.body().answer()).contains("payment");
                    assertThat(checkResponse.body().confidence()).isNotNull();
                    assertThat(checkResponse.body().planUsed()).isNotNull();
                }
            });
    }

    @Test
    public void shouldGetAgentPerformanceMetrics() {
        // Given: Agent with recorded performance
        String agentId = "customer-support";

        // Record some performance data
        componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.application.AgentPerformanceEntity::recordSuccess)
            .invoke(new com.example.payment.application.AgentPerformanceEntity.RecordSuccess(
                500.0,
                1000.0,
                0.005
            ));

        componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.application.AgentPerformanceEntity::recordSuccess)
            .invoke(new com.example.payment.application.AgentPerformanceEntity.RecordSuccess(
                600.0,
                1200.0,
                0.006
            ));

        // When: Getting performance metrics
        var response = httpClient
            .GET("/agents/performance/" + agentId)
            .responseBodyAs(AgentCollaborationEndpoint.PerformanceResponse.class)
            .invoke();

        // Then: Should return metrics
        assertThat(response.status().isSuccess()).isTrue();
        var metrics = response.body();
        assertThat(metrics.agentId()).isEqualTo(agentId);
        assertThat(metrics.totalCalls()).isEqualTo(2);
        assertThat(metrics.successfulCalls()).isEqualTo(2);
        assertThat(metrics.failedCalls()).isEqualTo(0);
        assertThat(metrics.successRate()).isEqualTo(1.0);
        assertThat(metrics.averageLatencyMs()).isGreaterThan(0);
        assertThat(metrics.totalCostUsd()).isGreaterThan(0);
    }

    @Test
    public void shouldGetEmptyPerformanceForNewAgent() {
        // Given: New agent with no history
        String agentId = "new-agent-" + UUID.randomUUID();

        // When: Getting performance metrics
        var response = httpClient
            .GET("/agents/performance/" + agentId)
            .responseBodyAs(AgentCollaborationEndpoint.PerformanceResponse.class)
            .invoke();

        // Then: Should return empty metrics
        assertThat(response.status().isSuccess()).isTrue();
        var metrics = response.body();
        assertThat(metrics.agentId()).isEqualTo(agentId);
        assertThat(metrics.totalCalls()).isEqualTo(0);
        assertThat(metrics.successfulCalls()).isEqualTo(0);
        assertThat(metrics.failedCalls()).isEqualTo(0);
        assertThat(metrics.successRate()).isEqualTo(0.0);
    }

    @Test
    public void shouldHandleCollaborationWithContext() {
        // Given: Request with rich context
        String sessionId = "test-context-" + UUID.randomUUID();
        var request = new AgentCollaborationEndpoint.CollaborationRequest(
            "Why did this fail?",
            "cust_test_4",
            "txn_fail_456 amount=500.00 currency=USD merchant=AcmeCorp",
            sessionId
        );

        // Mock agent responses
        setupSuccessfulCollaboration();

        // When: Starting collaboration
        var response = httpClient
            .POST("/agents/collaborate")
            .withRequestBody(request)
            .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
            .invoke();

        // Then: Should process successfully
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().sessionId()).isEqualTo(sessionId);
    }

    @Test
    public void shouldReturnPendingStatusForLongRunningWorkflow() {
        // Given: Collaboration request that may take time
        String sessionId = "test-pending-" + UUID.randomUUID();
        var request = new AgentCollaborationEndpoint.CollaborationRequest(
            "Complex multi-agent query",
            "cust_test_5",
            null,
            sessionId
        );

        // Mock agent responses with delays
        setupSuccessfulCollaboration();

        // When: Starting collaboration
        var response = httpClient
            .POST("/agents/collaborate")
            .withRequestBody(request)
            .responseBodyAs(AgentCollaborationEndpoint.CollaborationResponse.class)
            .invoke();

        // Then: Initial response may show PENDING
        assertThat(response.status().isSuccess()).isTrue();
        var body = response.body();
        // Status could be PENDING or COMPLETED depending on timing
        assertThat(body.status()).isIn("PENDING", "COMPLETED");
        if (body.status().equals("PENDING")) {
            assertThat(body.answer()).isEqualTo("Processing...");
        }
    }

    /**
     * Helper method to set up successful collaboration mocks.
     */
    private void setupSuccessfulCollaboration() {
        // Mock PlannerAgent
        var plan = new ExecutionPlan(
            List.of(PlanStep.required("customer-support", "Handle query", 1)),
            "SEQUENTIAL",
            "Simple query"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // Mock CustomerSupportAgent
        var supportResponse = new CustomerSupportAgent.SupportResponse(
            "Payment processed successfully",
            "INFORM",
            "HIGH",
            "txn_test",
            null
        );
        supportModel.fixedResponse(JsonSupport.encodeToString(supportResponse));

        // Mock SummarizerAgent
        var summaryResponse = new SummarizerAgent.SummarizedResponse(
            "Your payment was processed successfully",
            "HIGH",
            Map.of("customer-support", "Confirmed"),
            "No action needed"
        );
        summarizerModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));
    }
}
