package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SummarizerAgent.
 *
 * Tests the summarization logic with mocked LLM responses.
 */
public class SummarizerAgentTest extends TestKitSupport {

    private final TestModelProvider summarizerModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(SummarizerAgent.class, summarizerModel);
    }

    @Test
    public void shouldCombineAgentResponsesWithHighConfidence() {
        // Given: Multiple agents agree on payment status
        var request = new SummarizerAgent.SummarizationRequest(
            "Where is my payment?",
            Map.of(
                "customer-support", "Payment txn_123 was processed successfully on Jan 15th",
                "payment-assistant", "Transaction completed, funds should arrive in 2-3 business days"
            ),
            "SEQUENTIAL"
        );

        // Expected summary: High confidence with combined info
        var expectedResponse = new SummarizerAgent.SummarizedResponse(
            "Your payment was processed successfully on January 15th and funds should arrive in 2-3 business days.",
            "HIGH",
            Map.of(
                "customer-support", "Payment status confirmed",
                "payment-assistant", "Timeline provided"
            ),
            "No action needed, payment is on track"
        );

        summarizerModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Summarizing
        var result = componentClient
            .forAgent()
            .inSession("test-session-1")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Should produce high-confidence unified answer
        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("processed successfully");
        assertThat(result.confidence()).isEqualTo("HIGH");
        assertThat(result.sources()).hasSize(2);
    }

    @Test
    public void shouldPrioritizeFraudAlerts() {
        // Given: Fraud detected alongside normal info
        var request = new SummarizerAgent.SummarizationRequest(
            "Process my transaction",
            Map.of(
                "fraud-analyst", "CRITICAL: High fraud risk detected - multiple suspicious patterns",
                "customer-support", "Transaction details look normal",
                "payment-assistant", "Card is valid and has sufficient funds"
            ),
            "HYBRID"
        );

        // Expected summary: Fraud alert prioritized
        var expectedResponse = new SummarizerAgent.SummarizedResponse(
            "We've detected high fraud risk on this transaction and cannot proceed. Multiple suspicious patterns were identified.",
            "HIGH",
            Map.of(
                "fraud-analyst", "Critical fraud risk",
                "customer-support", "Normal details",
                "payment-assistant", "Valid card"
            ),
            "Transaction blocked for security. Contact support to verify your identity."
        );

        summarizerModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Summarizing
        var result = componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Fraud alert should dominate response
        assertThat(result).isNotNull();
        assertThat(result.answer()).containsIgnoringCase("fraud risk");
        assertThat(result.confidence()).isEqualTo("HIGH");
        assertThat(result.recommendation()).contains("blocked");
    }

    @Test
    public void shouldResolveConflictingInformation() {
        // Given: Agents provide conflicting info
        var request = new SummarizerAgent.SummarizationRequest(
            "Is my payment refundable?",
            Map.of(
                "customer-support", "Refund is eligible, transaction within 30-day window",
                "payment-assistant", "Refund may be difficult due to merchant policy restrictions"
            ),
            "PARALLEL"
        );

        // Expected summary: Acknowledge both perspectives
        var expectedResponse = new SummarizerAgent.SummarizedResponse(
            "Your payment is within the 30-day refund window, but merchant policies may apply restrictions. We recommend contacting the merchant first.",
            "MEDIUM",
            Map.of(
                "customer-support", "Time window eligible",
                "payment-assistant", "Merchant policy concerns"
            ),
            "Contact merchant to understand their refund policy, then proceed if acceptable"
        );

        summarizerModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Summarizing
        var result = componentClient
            .forAgent()
            .inSession("test-session-3")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Should acknowledge both perspectives with medium confidence
        assertThat(result).isNotNull();
        assertThat(result.confidence()).isEqualTo("MEDIUM");
        assertThat(result.answer()).contains("30-day");
        assertThat(result.answer()).contains("merchant");
    }

    @Test
    public void shouldHandlePaymentFailureWithRecovery() {
        // Given: Payment failed with recovery suggestions
        var request = new SummarizerAgent.SummarizationRequest(
            "Why did my payment fail?",
            Map.of(
                "payment-assistant", "Card expired on Dec 2025, update card to retry",
                "customer-support", "Transaction declined due to expired payment method"
            ),
            "SEQUENTIAL"
        );

        // Expected summary: Clear explanation with action
        var expectedResponse = new SummarizerAgent.SummarizedResponse(
            "Your payment failed because the card expired in December 2025. Update your payment method to complete the transaction.",
            "HIGH",
            Map.of(
                "payment-assistant", "Expiration issue identified",
                "customer-support", "Decline confirmed"
            ),
            "Update your card's expiration date or use a different payment method"
        );

        summarizerModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Summarizing
        var result = componentClient
            .forAgent()
            .inSession("test-session-4")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Should provide clear action
        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("expired");
        assertThat(result.recommendation()).containsIgnoringCase("update");
    }

    @Test
    public void shouldHandleSingleAgentResponse() {
        // Given: Only one agent responded
        var request = new SummarizerAgent.SummarizationRequest(
            "Check my balance",
            Map.of(
                "customer-support", "Current balance is $150.00"
            ),
            "SEQUENTIAL"
        );

        // Expected summary: Direct passthrough with high confidence
        var expectedResponse = new SummarizerAgent.SummarizedResponse(
            "Your current balance is $150.00.",
            "HIGH",
            Map.of("customer-support", "Balance provided"),
            "No action needed"
        );

        summarizerModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        // When: Summarizing
        var result = componentClient
            .forAgent()
            .inSession("test-session-5")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Should pass through cleanly
        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("$150.00");
        assertThat(result.confidence()).isEqualTo("HIGH");
    }

    @Test
    public void shouldUseFallbackOnModelFailure() {
        // Given: Summarization request
        var request = new SummarizerAgent.SummarizationRequest(
            "Help me",
            Map.of(
                "customer-support", "Response A",
                "payment-assistant", "Response B"
            ),
            "PARALLEL"
        );

        // Simulate model failure
        summarizerModel.fixedResponse("INVALID_JSON{");

        // When: Summarizing (should fall back)
        var result = componentClient
            .forAgent()
            .inSession("test-session-6")
            .method(SummarizerAgent::summarize)
            .invoke(request);

        // Then: Should return fallback summary
        assertThat(result).isNotNull();
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.answer()).contains("Response A");
        assertThat(result.answer()).contains("Response B");
        assertThat(result.recommendation()).contains("contact support");
    }
}
