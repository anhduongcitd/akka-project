package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.agents.domain.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PlannerAgent.
 *
 * Tests the planning logic with mocked LLM responses.
 */
public class PlannerAgentTest extends TestKitSupport {

    private final TestModelProvider plannerModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(PlannerAgent.class, plannerModel);
    }

    @Test
    public void shouldCreateSequentialPlanForFraudCheck() {
        // Given: A query requiring fraud analysis
        var request = new PlannerAgent.PlanningRequest(
            "Check if transaction txn_123 is fraudulent",
            "cust_456",
            "txn_123"
        );

        // Expected plan: Fraud analyst followed by payment assistant
        var expectedPlan = new ExecutionPlan(
            List.of(
                PlanStep.required("fraud-analyst", "Analyze transaction txn_123 for fraud indicators", 1),
                PlanStep.required("payment-assistant", "Suggest recovery actions for txn_123", 2)
            ),
            "SEQUENTIAL",
            "Need fraud check before suggesting any recovery actions"
        );

        plannerModel.fixedResponse(JsonSupport.encodeToString(expectedPlan));

        // When: Creating plan
        var result = componentClient
            .forAgent()
            .inSession("test-session-1")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Plan should be sequential with fraud-analyst first
        assertThat(result).isNotNull();
        assertThat(result.steps()).hasSize(2);
        assertThat(result.strategy()).isEqualTo("SEQUENTIAL");
        assertThat(result.isSequential()).isTrue();
        assertThat(result.steps().get(0).agentId()).isEqualTo("fraud-analyst");
        assertThat(result.steps().get(1).agentId()).isEqualTo("payment-assistant");
    }

    @Test
    public void shouldCreateParallelPlanForMultipleQueries() {
        // Given: A query with independent questions
        var request = new PlannerAgent.PlanningRequest(
            "What is my payment status and am I eligible for refund?",
            "cust_789",
            "txn_abc"
        );

        // Expected plan: Customer support and payment assistant in parallel
        var expectedPlan = new ExecutionPlan(
            List.of(
                PlanStep.required("customer-support", "Check payment status for txn_abc", 1),
                PlanStep.required("customer-support", "Check refund eligibility for txn_abc", 1)
            ),
            "PARALLEL",
            "Independent queries can run in parallel"
        );

        plannerModel.fixedResponse(JsonSupport.encodeToString(expectedPlan));

        // When: Creating plan
        var result = componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Plan should be parallel
        assertThat(result).isNotNull();
        assertThat(result.strategy()).isEqualTo("PARALLEL");
        assertThat(result.isParallel()).isTrue();
        assertThat(result.steps()).hasSize(2);
    }

    @Test
    public void shouldCreateSingleAgentPlanForSimpleQuery() {
        // Given: Simple payment status query
        var request = new PlannerAgent.PlanningRequest(
            "Where is my payment?",
            "cust_101",
            "txn_xyz"
        );

        // Expected plan: Single customer support agent
        var expectedPlan = new ExecutionPlan(
            List.of(
                PlanStep.required("customer-support", "Check payment status for txn_xyz", 1)
            ),
            "SEQUENTIAL",
            "Simple query needs only customer support agent"
        );

        plannerModel.fixedResponse(JsonSupport.encodeToString(expectedPlan));

        // When: Creating plan
        var result = componentClient
            .forAgent()
            .inSession("test-session-3")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Plan should have single step
        assertThat(result).isNotNull();
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).agentId()).isEqualTo("customer-support");
        assertThat(result.steps().get(0).required()).isTrue();
    }

    @Test
    public void shouldHandlePlanWithOptionalSteps() {
        // Given: Query with optional enhancement
        var request = new PlannerAgent.PlanningRequest(
            "Why did my payment fail?",
            "cust_202",
            "txn_fail123"
        );

        // Expected plan: Required payment assistant, optional fraud check
        var expectedPlan = new ExecutionPlan(
            List.of(
                PlanStep.required("payment-assistant", "Analyze failure for txn_fail123", 1),
                PlanStep.optional("fraud-analyst", "Check for suspicious patterns", 2)
            ),
            "SEQUENTIAL",
            "Analyze failure first, optionally check fraud"
        );

        plannerModel.fixedResponse(JsonSupport.encodeToString(expectedPlan));

        // When: Creating plan
        var result = componentClient
            .forAgent()
            .inSession("test-session-4")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Plan should have required and optional steps
        assertThat(result).isNotNull();
        assertThat(result.steps()).hasSize(2);
        assertThat(result.getRequiredSteps()).hasSize(1);
        assertThat(result.getOptionalSteps()).hasSize(1);
        assertThat(result.steps().get(0).required()).isTrue();
        assertThat(result.steps().get(1).required()).isFalse();
    }

    @Test
    public void shouldUseFallbackPlanOnModelFailure() {
        // Given: Planning request
        var request = new PlannerAgent.PlanningRequest(
            "Help me with my payment",
            "cust_303",
            null
        );

        // Simulate model failure
        plannerModel.fixedResponse("INVALID_JSON{broken");

        // When: Creating plan (should fall back)
        var result = componentClient
            .forAgent()
            .inSession("test-session-5")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Should return fallback plan routing to customer-support
        assertThat(result).isNotNull();
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).agentId()).isEqualTo("customer-support");
        assertThat(result.reasoning()).contains("Fallback plan");
    }

    @Test
    public void shouldCreateHybridPlanForComplexScenario() {
        // Given: Complex query with dependencies
        var request = new PlannerAgent.PlanningRequest(
            "Check if this is fraud and if safe then process refund",
            "cust_404",
            "txn_hybrid"
        );

        // Expected plan: Sequential fraud check, then parallel support + assistant
        var expectedPlan = new ExecutionPlan(
            List.of(
                PlanStep.required("fraud-analyst", "Check fraud for txn_hybrid", 1),
                PlanStep.required("customer-support", "Check refund eligibility", 2),
                PlanStep.required("payment-assistant", "Suggest refund process", 2)
            ),
            "HYBRID",
            "Fraud check first (sequential), then support + assistant in parallel"
        );

        plannerModel.fixedResponse(JsonSupport.encodeToString(expectedPlan));

        // When: Creating plan
        var result = componentClient
            .forAgent()
            .inSession("test-session-6")
            .method(PlannerAgent::createPlan)
            .invoke(request);

        // Then: Plan should be hybrid
        assertThat(result).isNotNull();
        assertThat(result.strategy()).isEqualTo("HYBRID");
        assertThat(result.isHybrid()).isTrue();
        assertThat(result.steps()).hasSize(3);
    }
}
