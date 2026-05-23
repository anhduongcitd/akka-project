package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnhancedPIIGuard.
 */
public class EnhancedPIIGuardTest {

    private final EnhancedPIIGuard guard = new EnhancedPIIGuard();

    @Test
    public void shouldBlockCreditCardNumber() {
        // Given: Text with credit card number
        String text = "My card is 4242 4242 4242 4242";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Credit card number");
    }

    @Test
    public void shouldBlockCreditCardWithDashes() {
        // Given: Credit card with dashes
        String text = "Card number: 4242-4242-4242-4242";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Credit card number");
    }

    @Test
    public void shouldBlockSSN() {
        // Given: Text with SSN
        String text = "My SSN is 123-45-6789";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Social Security Number");
    }

    @Test
    public void shouldBlockPhoneNumber() {
        // Given: Text with phone number
        String text = "Call me at 415-867-5309";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Phone number");
    }

    @Test
    public void shouldAllowTestPhoneNumbers() {
        // Given: Text with test phone number
        String text = "Test number: 555-0100";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow (test number)
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldBlockRealEmailAddress() {
        // Given: Text with real email
        String text = "Email me at john.doe@gmail.com";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block
        assertThat(result.ok()).isFalse();
        assertThat(result.errorMessage()).contains("Email address");
    }

    @Test
    public void shouldAllowTestEmailAddresses() {
        // Given: Text with test email
        String text = "Contact us at support@example.com";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow (test domain)
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowTestEmail2() {
        // Given: Text with another test email
        String text = "Send to user@test.com";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow (test domain)
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowNormalPaymentText() {
        // Given: Normal payment text without PII
        String text = "Your payment of $50.00 was successful. Transaction ID: txn_abc123";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldAllowTransactionReference() {
        // Given: Text with transaction reference (not PII)
        String text = "Transaction txn_abc123 for customer cust_456";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow
        assertThat(result.ok()).isTrue();
    }

    @Test
    public void shouldBlockMultiplePIITypes() {
        // Given: Text with multiple PII types
        String text = "Card: 4242-4242-4242-4242, SSN: 123-45-6789, Phone: 415-867-5309";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should block (will block on first match)
        assertThat(result.ok()).isFalse();
    }

    @Test
    public void shouldAllowMaskedCardNumber() {
        // Given: Masked card number (last 4 digits only)
        String text = "Your card ending in 4242 was charged";

        // When: Evaluating
        TextGuardrail.Result result = guard.evaluate(text);

        // Then: Should allow (not a full card number)
        assertThat(result.ok()).isTrue();
    }
}
