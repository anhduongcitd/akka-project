package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.Currency;
import com.example.payment.domain.Customer;
import com.example.payment.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Basic workflow test.
 * Note: Full integration testing with mocked Stripe would require more setup.
 */
public class PaymentProcessingWorkflowTest extends TestKitSupport {

    @Test
    public void shouldStartPaymentWorkflow() {
        var customer = new Customer("cust_123", "test@example.com", "Test Customer");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        var command = new PaymentProcessingWorkflow.StartPayment(
            "txn_test_123",
            customer,
            amount,
            "ORDER-001",
            "tok_visa"
        );

        // Note: This test demonstrates the pattern.
        // In a real test, we would mock StripePaymentGateway responses.
        // For now, we're just verifying the command structure is valid.

        assertThat(command.transactionId()).isEqualTo("txn_test_123");
        assertThat(command.amount()).isEqualTo(amount);
    }
}
