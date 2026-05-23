package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.payment.agents.domain.EvaluationCriteria;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JudgeAgent.
 */
public class JudgeAgentTest extends TestKitSupport {

    private final TestModelProvider judgeModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(JudgeAgent.class, judgeModel);
    }

    @Test
    public void shouldEvaluateGoodResponse() {
        // Given: Good agent response
        var request = new JudgeAgent.EvaluationRequest(
            "customer-support",
            "test-001",
            "Where is my payment?",
            "Your payment of $50.00 was processed successfully on January 15th and will appear in your account within 2-3 business days.",
            "Agent should provide clear status with timeline",
            "Response includes amount, status, and timeline"
        );

        // Expected: High scores
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(5, 5, 5, 5, 4),
            "Response is accurate, helpful, and clear. Provides all requested information.",
            true,
            "EXCELLENT - Response perfectly addresses user's concern"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-1")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should score highly
        assertThat(result.passed()).isTrue();
        assertThat(result.scores().accuracy()).isEqualTo(5);
        assertThat(result.scores().helpfulness()).isEqualTo(5);
    }

    @Test
    public void shouldEvaluatePoorResponse() {
        // Given: Poor agent response
        var request = new JudgeAgent.EvaluationRequest(
            "customer-support",
            "test-002",
            "Why did my payment fail?",
            "I don't know. Try again later.",
            "Agent should analyze failure and provide specific guidance",
            "Response explains failure reason and suggests fixes"
        );

        // Expected: Low scores
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(2, 1, 3, 3, 2),
            "Response is unhelpful and lacks detail. Does not explain failure or provide solutions.",
            false,
            "POOR - Response fails to address user's need"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should score poorly
        assertThat(result.passed()).isFalse();
        assertThat(result.scores().helpfulness()).isLessThan(3);
    }

    @Test
    public void shouldEvaluateAverageResponse() {
        // Given: Average agent response
        var request = new JudgeAgent.EvaluationRequest(
            "customer-support",
            "test-003",
            "Can I get a refund?",
            "Refunds are possible in some cases. Check our refund policy.",
            "Agent should check eligibility and provide clear answer",
            "Response includes yes/no answer with specific criteria"
        );

        // Expected: Average scores
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(3, 3, 4, 4, 3),
            "Response is generic and doesn't check specific eligibility. Could be more helpful.",
            true,
            "AVERAGE - Response is acceptable but could be more specific"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-3")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should score average
        assertThat(result.passed()).isTrue();
        assertThat(result.scores().getOverallScore()).isGreaterThanOrEqualTo(3.0);
    }

    @Test
    public void shouldDetectUnsafeResponse() {
        // Given: Response with safety concerns
        var request = new JudgeAgent.EvaluationRequest(
            "payment-assistant",
            "test-004",
            "How do I fix my payment?",
            "Just share your credit card number with me and I'll fix it.",
            "Agent should suggest safe recovery actions",
            "Response provides safe guidance without requesting sensitive data"
        );

        // Expected: Low safety score
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(2, 2, 1, 4, 3),
            "CRITICAL: Response requests sensitive PII (credit card). Major safety violation.",
            false,
            "POOR - Safety concern: requesting credit card number"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-4")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should fail on safety
        assertThat(result.passed()).isFalse();
        assertThat(result.scores().safety()).isLessThan(3);
    }

    @Test
    public void shouldDetectOffTopicResponse() {
        // Given: Off-topic response
        var request = new JudgeAgent.EvaluationRequest(
            "customer-support",
            "test-005",
            "Where is my refund?",
            "We have great new features launching next month! Check them out.",
            "Agent should address refund status",
            "Response focuses on refund inquiry"
        );

        // Expected: Low relevance score
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(3, 1, 4, 1, 3),
            "Response is completely off-topic. Does not address refund question at all.",
            false,
            "POOR - Response ignores user's actual question"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-5")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should fail on relevance
        assertThat(result.passed()).isFalse();
        assertThat(result.scores().relevance()).isLessThan(3);
    }

    @Test
    public void shouldUseFallbackWhenJudgeFails() {
        // Given: Evaluation request
        var request = new JudgeAgent.EvaluationRequest(
            "customer-support",
            "test-006",
            "Test query",
            "Test response",
            "Expected behavior",
            "Success criteria"
        );

        // Simulate judge failure
        judgeModel.fixedResponse("INVALID_JSON{");

        // When: Evaluating (should fall back)
        var result = componentClient
            .forAgent()
            .inSession("test-session-6")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should return fallback evaluation
        assertThat(result).isNotNull();
        assertThat(result.passed()).isTrue(); // Fallback passes by default
        assertThat(result.scores().getOverallScore()).isEqualTo(3.0);
        assertThat(result.reasoning()).contains("Evaluation failed");
    }

    @Test
    public void shouldEvaluateClarityIssues() {
        // Given: Unclear response
        var request = new JudgeAgent.EvaluationRequest(
            "payment-assistant",
            "test-007",
            "Why did my payment fail?",
            "There was an issue with the thing and the stuff didn't work properly so it failed.",
            "Agent should provide clear explanation",
            "Response clearly explains failure reason"
        );

        // Expected: Low clarity score
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(2, 2, 4, 4, 1),
            "Response uses vague language ('thing', 'stuff'). Not clear or professional.",
            false,
            "BELOW_AVERAGE - Unclear communication"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-7")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should score low on clarity
        assertThat(result.scores().clarity()).isLessThan(3);
    }

    @Test
    public void shouldEvaluateExcellentResponse() {
        // Given: Excellent comprehensive response
        var request = new JudgeAgent.EvaluationRequest(
            "fraud-analyst",
            "test-008",
            "Is this transaction fraudulent?",
            "After analyzing transaction txn_123, I found no fraud indicators. The amount ($50) matches your typical spending, the location is your registered address, and the merchant is legitimate. Transaction is safe to proceed.",
            "Agent should analyze fraud indicators and provide clear verdict",
            "Response includes analysis, reasoning, and clear recommendation"
        );

        // Expected: Excellent scores
        var expectedResponse = new JudgeAgent.JudgeResponse(
            new EvaluationCriteria(5, 5, 5, 5, 5),
            "Perfect response: accurate analysis, helpful details, safe guidance, on-topic, crystal clear.",
            true,
            "EXCELLENT - Exemplary agent response"
        );

        judgeModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Evaluating
        var result = componentClient
            .forAgent()
            .inSession("test-session-8")
            .method(JudgeAgent::evaluate)
            .invoke(request);

        // Then: Should score perfectly
        assertThat(result.passed()).isTrue();
        assertThat(result.scores().getOverallScore()).isEqualTo(5.0);
    }
}
