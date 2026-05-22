package com.example.payment.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.Currency;
import com.example.payment.domain.Customer;
import com.example.payment.domain.Money;
import com.example.payment.domain.PaymentTransactionEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment History View Integration Test.
 * Tests view projections from payment transaction events.
 */
public class PaymentHistoryViewIntegrationTest extends TestKitSupport {

    @Override
    protected akka.javasdk.testkit.TestKit.Settings testKitSettings() {
        return akka.javasdk.testkit.TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);
    }

    @Test
    public void shouldProjectPaymentTransaction() {
        String transactionId = "txn_history_test_" + System.currentTimeMillis();
        String customerId = "cust_history_123";
        Customer customer = new Customer(customerId, "test@example.com", "Test Customer");
        Money amount = new Money(new BigDecimal("100.00"), Currency.USD);

        // Publish payment initiated event
        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);
        var initiatedEvent = new PaymentTransactionEvent.PaymentInitiated(
            customer,
            amount,
            "ORDER-TEST-001",
            Instant.now()
        );
        events.publish(initiatedEvent, transactionId);

        // Wait for view to update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomerId)
                    .invoke(customerId);

                assertThat(result).isNotNull();
                assertThat(result.transactions()).isNotEmpty();

                var entry = result.transactions().get(0);
                assertThat(entry.transactionId()).isEqualTo(transactionId);
                assertThat(entry.customerId()).isEqualTo(customerId);
                assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
            });
    }
}
