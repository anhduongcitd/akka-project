package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HallucinationGuard.
 */
public class HallucinationGuardTest {

    private final HallucinationGuard guard = new HallucinationGuard();

    @Test
    public void shouldBlockWhenAgentAdmitsNoAccess() {
        // Given: Agent admits inability to access data
        String response = "I don't have access to your transaction details. Please contact support.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("inability to access data");
    }

    @Test
    public void shouldBlockWhenAgentCannotVerify() {
        // Given: Agent says it cannot verify
        String response = "I cannot verify the transaction status at this time.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("inability to access data");
    }

    @Test
    public void shouldBlockAsAnAI() {
        // Given: Agent mentions "as an AI"
        String response = "As an AI, I cannot access real-time payment data.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("inability to access data");
    }

    @Test
    public void shouldBlockInconsistentFailedAndCompleted() {
        // Given: Response mentions both failed and completed
        String response = "The payment failed but also completed successfully.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Inconsistent");
    }

    @Test
    public void shouldBlockInconsistentRefundAndSuccess() {
        // Given: Response mentions both refund and successful payment
        String response = "Your payment successful and we have issued a refund.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Inconsistent");
    }

    @Test
    public void shouldAllowValidSuccessResponse() {
        // Given: Normal success response
        String response = "Your payment of $50.00 was processed successfully on January 15th.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowValidFailureResponse() {
        // Given: Normal failure response
        String response = "Your payment failed due to insufficient funds. Please update your payment method.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowRefundResponse() {
        // Given: Normal refund response
        String response = "Your refund of $25.00 has been processed and will appear in 5-7 business days.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowTransactionDetails() {
        // Given: Response with transaction details
        String response = "Transaction txn_abc123 for $50.00 was completed on 2024-01-15.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldBeCaseInsensitive() {
        // Given: Hallucination indicator in mixed case
        String response = "I CANNOT ACCESS your payment information.";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(response);

        // Then: Should block
        assertThat(result.ok()).isFalse();
    }
}
