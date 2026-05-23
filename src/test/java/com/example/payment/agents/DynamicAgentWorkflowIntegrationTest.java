package com.example.payment.agents;

import akka.Done;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.agents.domain.PlanStep;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DynamicAgentWorkflow.
 *
 * Tests multi-agent orchestration with mocked LLM responses.
 */
public class DynamicAgentWorkflowIntegrationTest extends TestKitSupport {

    private final TestModelProvider plannerModel = new TestModelProvider();
    private final TestModelProvider supportModel = new TestModelProvider();
    private final TestModelProvider fraudModel = new TestModelProvider();
    private final TestModelProvider assistantModel = new TestModelProvider();
    private final TestModelProvider summarizerModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(PlannerAgent.class, plannerModel)
            .withModelProvider(CustomerSupportAgent.class, supportModel)
            .withModelProvider(FraudAnalystAgent.class, fraudModel)
            .withModelProvider(PaymentAssistantAgent.class, assistantModel)
            .withModelProvider(SummarizerAgent.class, summarizerModel);
    }

    @Test
    public void shouldOrchestrateSingleAgentWorkflow() {
        // Given: Simple query requiring only customer support
        String sessionId = "session-single-agent";

        // Mock PlannerAgent to return single-step plan
        var plan = new ExecutionPlan(
            List.of(PlanStep.required("customer-support", "Check payment status", 1)),
            "SEQUENTIAL",
            "Simple query needs only support agent"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // Mock CustomerSupportAgent response
        var supportResponse = new CustomerSupportAgent.SupportResponse(
            "Payment txn_123 was processed successfully",
            "INFORM",
            "HIGH",
            "txn_123",
            null
        );
        supportModel.fixedResponse(JsonSupport.encodeToString(supportResponse));

        // Mock SummarizerAgent response
        var summaryResponse = new SummarizerAgent.SummarizedResponse(
            "Your payment was processed successfully",
            "HIGH",
            java.util.Map.of("customer-support", "Payment confirmed"),
            "No action needed"
        );
        summarizerModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));

        // When: Starting workflow
        var request = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            "Where is my payment?",
            "cust_123",
            "txn_123"
        );

        var startResult = componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(request);

        assertThat(startResult).isEqualTo(Done.getInstance());

        // Then: Workflow should complete successfully
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forWorkflow(sessionId)
                    .method(DynamicAgentWorkflow::getResult)
                    .invoke();

                assertThat(result.answer()).isNotNull();
                assertThat(result.answer()).contains("processed successfully");
                assertThat(result.confidence()).isEqualTo("HIGH");
                assertThat(result.agentContributions()).containsKey("customer-support");
            });
    }

    @Test
    public void shouldOrchestrateSequentialMultiAgentWorkflow() {
        // Given: Query requiring fraud check then payment assistant
        String sessionId = "session-sequential";

        // Mock PlannerAgent to return sequential plan
        var plan = new ExecutionPlan(
            List.of(
                PlanStep.required("fraud-analyst", "Check fraud for txn_456", 1),
                PlanStep.required("payment-assistant", "Suggest recovery for txn_456", 2)
            ),
            "SEQUENTIAL",
            "Fraud check before recovery suggestions"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // Mock FraudAnalystAgent response
        var fraudResponse = new FraudAnalystAgent.FraudAnalysis(
            false,
            "LOW",
            0.95,
            List.of(),
            "APPROVE",
            "No fraud indicators detected"
        );
        fraudModel.fixedResponse(JsonSupport.encodeToString(fraudResponse));

        // Mock PaymentAssistantAgent response
        var assistantResponse = new PaymentAssistantAgent.FailureAnalysis(
            "CARD_EXPIRED",
            "RECOVERABLE",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("CHANGE_CARD", "Update card expiration", 1)
            ),
            "Card expired, please update"
        );
        assistantModel.fixedResponse(JsonSupport.encodeToString(assistantResponse));

        // Mock SummarizerAgent response
        var summaryResponse = new SummarizerAgent.SummarizedResponse(
            "No fraud detected, but card expired. Update your card to retry.",
            "HIGH",
            java.util.Map.of(
                "fraud-analyst", "No fraud",
                "payment-assistant", "Card expired"
            ),
            "Update card expiration date"
        );
        summarizerModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));

        // When: Starting workflow
        var request = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            "Check fraud and fix payment",
            "cust_456",
            "txn_456"
        );

        componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(request);

        // Then: Both agents should be called sequentially
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forWorkflow(sessionId)
                    .method(DynamicAgentWorkflow::getResult)
                    .invoke();

                assertThat(result.answer()).contains("No fraud");
                assertThat(result.answer()).contains("card expired");
                assertThat(result.agentContributions()).hasSize(2);
                assertThat(result.agentContributions()).containsKeys("fraud-analyst", "payment-assistant");
            });
    }

    @Test
    public void shouldHandleOptionalStepFailure() {
        // Given: Plan with required step and optional step (that will fail)
        String sessionId = "session-optional-fail";

        // Mock plan with optional fraud check
        var plan = new ExecutionPlan(
            List.of(
                PlanStep.required("customer-support", "Check payment", 1),
                PlanStep.optional("fraud-analyst", "Additional fraud check", 2)
            ),
            "SEQUENTIAL",
            "Support required, fraud optional"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // Mock successful support response
        var supportResponse = new CustomerSupportAgent.SupportResponse(
            "Payment is pending",
            "INFORM",
            "HIGH",
            "txn_789",
            null
        );
        supportModel.fixedResponse(JsonSupport.encodeToString(supportResponse));

        // Mock fraud agent failure (invalid JSON)
        fraudModel.fixedResponse("ERROR: Analysis failed");

        // Mock summarizer (should still work with partial results)
        var summaryResponse = new SummarizerAgent.SummarizedResponse(
            "Payment is pending",
            "MEDIUM",
            java.util.Map.of("customer-support", "Pending status"),
            "Wait for completion"
        );
        summarizerModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));

        // When: Starting workflow
        var request = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            "Check my payment",
            "cust_789",
            "txn_789"
        );

        componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(request);

        // Then: Workflow should complete despite optional step failure
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forWorkflow(sessionId)
                    .method(DynamicAgentWorkflow::getResult)
                    .invoke();

                assertThat(result.answer()).isNotNull();
                assertThat(result.agentContributions()).containsKey("customer-support");
                // fraud-analyst may or may not be in results depending on timing
            });
    }

    @Test
    public void shouldFailOnRequiredStepFailure() {
        // Given: Plan with required step that will fail
        String sessionId = "session-required-fail";

        // Mock plan with required customer support
        var plan = new ExecutionPlan(
            List.of(PlanStep.required("customer-support", "Critical query", 1)),
            "SEQUENTIAL",
            "Required step"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // Mock support failure
        supportModel.fixedResponse("CRITICAL_ERROR");

        // When: Starting workflow
        var request = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            "Critical query",
            "cust_999",
            null
        );

        componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(request);

        // Then: Workflow should fail with error message
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forWorkflow(sessionId)
                    .method(DynamicAgentWorkflow::getResult)
                    .invoke();

                assertThat(result.answer()).isNotNull();
                // Should contain error message from errorStep
                assertThat(result.answer()).containsAnyOf("issue", "unable", "error");
            });
    }

    @Test
    public void shouldHandleEmptyPlan() {
        // Given: Planner returns empty plan
        String sessionId = "session-empty-plan";

        // Mock empty plan
        var plan = new ExecutionPlan(
            List.of(),
            "SEQUENTIAL",
            "No agents available"
        );
        plannerModel.fixedResponse(JsonSupport.encodeToString(plan));

        // When: Starting workflow
        var request = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            "Invalid query",
            "cust_empty",
            null
        );

        componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(request);

        // Then: Workflow should complete with error
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient
                    .forWorkflow(sessionId)
                    .method(DynamicAgentWorkflow::getResult)
                    .invoke();

                assertThat(result.answer()).contains("issue");
            });
    }
}
