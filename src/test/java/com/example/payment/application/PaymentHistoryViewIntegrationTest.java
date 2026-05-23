package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PaymentHistoryView.
 * Tests view updates based on payment transaction entity events.
 */
public class PaymentHistoryViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);
    }

    @Test
    public void shouldProjectPaymentTransactionToView() {
        String customerId = "cust_history_1";
        String transactionId = "txn_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        // Publish PaymentInitiated event
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);
        var initiated = new PaymentTransactionEvent.PaymentInitiated(
            customer,
            amount,
            "ORDER-001",
            Instant.now()
        );

        events.publish(initiated, transactionId);

        // Wait for view to be updated
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.transactions()).hasSize(1);
                var tx = result.transactions().get(0);
                assertThat(tx.transactionId()).isEqualTo(transactionId);
                assertThat(tx.customerId()).isEqualTo(customerId);
                assertThat(tx.status()).isEqualTo("PENDING");
                assertThat(tx.amountValue()).isEqualTo("100.00");
                assertThat(tx.currency()).isEqualTo("USD");
                assertThat(tx.completedAt()).isEqualTo(Instant.EPOCH);
                assertThat(tx.failureReason()).isEmpty();
            });
    }

    @Test
    public void shouldUpdateStatusWhenPaymentSucceeds() {
        String customerId = "cust_history_2";
        String transactionId = "txn_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        // Publish initiated event
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("50.00"), Currency.USD);
        var initiated = new PaymentTransactionEvent.PaymentInitiated(
            customer,
            amount,
            "ORDER-002",
            Instant.now()
        );
        events.publish(initiated, transactionId);

        // Wait for initiated to appear
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.transactions()).hasSize(1);
            });

        // Publish succeeded event
        var succeeded = new PaymentTransactionEvent.PaymentSucceeded(
            Instant.now()
        );
        events.publish(succeeded, transactionId);

        // Verify status updated
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.transactions()).hasSize(1);
                var tx = result.transactions().get(0);
                assertThat(tx.status()).isEqualTo("SUCCEEDED");
                assertThat(tx.completedAt()).isNotEqualTo(Instant.EPOCH);
            });
    }

    @Test
    public void shouldFilterByStatus() {
        String customerId = "cust_history_3";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        // Create successful transaction
        String txn1 = "txn_" + UUID.randomUUID();
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount1 = new Money(new BigDecimal("100.00"), Currency.USD);

        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount1, "ORDER-S1", Instant.now()
        ), txn1);
        events.publish(new PaymentTransactionEvent.PaymentSucceeded(
            Instant.now()
        ), txn1);

        // Create failed transaction
        String txn2 = "txn_" + UUID.randomUUID();
        var amount2 = new Money(new BigDecimal("50.00"), Currency.USD);

        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount2, "ORDER-F1", Instant.now()
        ), txn2);
        events.publish(new PaymentTransactionEvent.PaymentFailed(
            "Insufficient funds", Instant.now()
        ), txn2);

        // Query successful transactions only
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var filter = new PaymentHistoryView.StatusFilter(customerId, "SUCCEEDED");
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomerAndStatus)
                    .invoke(filter);

                assertThat(result.transactions()).hasSize(1);
                assertThat(result.transactions().get(0).status()).isEqualTo("SUCCEEDED");
            });

        // Query failed transactions only
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var filter = new PaymentHistoryView.StatusFilter(customerId, "FAILED");
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomerAndStatus)
                    .invoke(filter);

                assertThat(result.transactions()).hasSize(1);
                assertThat(result.transactions().get(0).status()).isEqualTo("FAILED");
            });
    }

    @Test
    public void shouldFilterByDateRange() {
        String customerId = "cust_history_4";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        // Create transaction with yesterday's timestamp
        String txn1 = "txn_" + UUID.randomUUID();
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-D1", yesterday
        ), txn1);

        // Wait for view update
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.transactions()).hasSize(1);
            });

        // Query with date range that includes yesterday
        var filter = new PaymentHistoryView.DateRangeFilter(
            customerId,
            yesterday.minus(1, ChronoUnit.HOURS),
            tomorrow
        );

        var result = componentClient.forView()
            .method(PaymentHistoryView::getByCustomerAndDateRange)
            .invoke(filter);

        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().get(0).transactionId()).isEqualTo(txn1);
    }

    @Test
    public void shouldOrderByCreatedAtDesc() {
        String customerId = "cust_history_5";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        Instant now = Instant.now();
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        // Create 3 transactions with different timestamps
        String txn1 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-1", now.minus(2, ChronoUnit.HOURS)
        ), txn1);

        String txn2 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-2", now
        ), txn2);

        String txn3 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-3", now.minus(1, ChronoUnit.HOURS)
        ), txn3);

        // Verify ordering (most recent first)
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(PaymentHistoryView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.transactions()).hasSize(3);
                // Most recent first
                assertThat(result.transactions().get(0).merchantReference()).isEqualTo("ORDER-2");
                assertThat(result.transactions().get(1).merchantReference()).isEqualTo("ORDER-3");
                assertThat(result.transactions().get(2).merchantReference()).isEqualTo("ORDER-1");
            });
    }
}
