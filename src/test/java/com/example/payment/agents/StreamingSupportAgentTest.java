package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamingSupportAgent.
 */
public class StreamingSupportAgentTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.custom-provider.api-key = test-key")
            .withModelProvider(StreamingSupportAgent.class, agentModel);
    }

    @Test
    public void shouldStreamPaymentInquiry() {
        // Given: Payment status inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_123",
            "Where is my payment for transaction txn_abc123?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Your payment for transaction txn_abc123 is being processed and will arrive in 2-3 business days.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-1")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamRefundInquiry() {
        // Given: Refund status inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_456",
            "When will I receive my refund?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Your refund of $50.00 was processed on January 15th and will appear in your account within 5-7 business days.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamTransactionHistory() {
        // Given: Transaction history inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_789",
            "Show me my recent transactions"
        );

        // Mock streaming response
        agentModel.fixedResponse("Here are your recent transactions: 1) $50.00 on Jan 15 - Success, 2) $25.00 on Jan 10 - Success");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-3")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamFailedPaymentInquiry() {
        // Given: Failed payment inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_fail",
            "Why did my payment fail?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Your payment failed due to insufficient funds. Please ensure your account has sufficient balance and try again.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-4")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamGeneralInquiry() {
        // Given: General inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_general",
            "How long does it take to process a payment?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Payment processing typically takes 2-3 business days for standard transactions. Credit card payments are usually faster, completing within 24-48 hours.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-5")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamRefundPolicyInquiry() {
        // Given: Refund policy inquiry
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_policy",
            "What is your refund policy?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Our refund policy allows refunds within 30 days of purchase. Refunds are processed within 5-7 business days after approval.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-6")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldHandleEmptyQuery() {
        // Given: Empty query
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_empty",
            ""
        );

        // Mock streaming response
        agentModel.fixedResponse("I'm here to help with your payment questions. Please let me know what you'd like assistance with.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-7")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamWithLongQuery() {
        // Given: Long detailed query
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_long",
            "I made a payment of $500 on January 10th for order #12345, but I haven't received confirmation yet. Can you check the status and let me know if there are any issues? Also, if there's a problem, can I get a refund?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Let me check your payment for order #12345 ($500 on Jan 10). The payment was processed successfully. Confirmation was sent to your email. If you didn't receive it, please check your spam folder. No refund is needed as the payment went through.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-8")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamMultipleQuestions() {
        // Given: Multiple questions in one query
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_multi",
            "What's my payment status? And when will my refund arrive? Also, do you have my transaction history?"
        );

        // Mock streaming response
        agentModel.fixedResponse("I'll address each question: 1) Payment status - Your recent payment is completed. 2) Refund timing - Your refund will arrive in 5-7 days. 3) Transaction history - Yes, I can access your history. Would you like me to show it?");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-9")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }

    @Test
    public void shouldStreamWithSpecialCharacters() {
        // Given: Query with special characters
        var request = new StreamingSupportAgent.StreamRequest(
            "cust_special",
            "Transaction #abc-123 (amount: $50.00) - status?"
        );

        // Mock streaming response
        agentModel.fixedResponse("Transaction #abc-123 for $50.00 is completed successfully.");

        // When: Streaming query
        var result = componentClient
            .forAgent()
            .inSession("test-session-10")
            .method(StreamingSupportAgent::handleQuery)
            .invoke(request);

        // Then: Should return response
        assertThat(result).isNotNull();
    }
}
