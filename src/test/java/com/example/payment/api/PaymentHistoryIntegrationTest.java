package com.example.payment.api;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.application.PaymentTransactionEntity;
import com.example.payment.domain.Customer;
import com.example.payment.domain.Currency;
import com.example.payment.domain.Money;
import com.example.payment.domain.PaymentTransactionEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for payment history API endpoint.
 * Tests POST /payment/history with various filter combinations.
 */
public class PaymentHistoryIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);
    }

    @Test
    public void shouldGetAllTransactionsForCustomer() {
        String customerId = "cust_history_api_1";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        // Create 2 transactions
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        String txn1 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-001", Instant.now()
        ), txn1);

        String txn2 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-002", Instant.now()
        ), txn2);

        // Wait for view to update
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, null, null, null);
                var response = httpClient
                    .POST("/payment/history")
                    .withRequestBody(request)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();

                assertThat(response.status().isSuccess()).isTrue();
                assertThat(response.body().transactions()).hasSize(2);
            });
    }

    @Test
    public void shouldFilterByStatus() {
        String customerId = "cust_history_api_2";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("50.00"), Currency.USD);

        // Create successful transaction
        String txn1 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-S1", Instant.now()
        ), txn1);
        events.publish(new PaymentTransactionEvent.PaymentSucceeded(
            Instant.now()
        ), txn1);

        // Create failed transaction
        String txn2 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-F1", Instant.now()
        ), txn2);
        events.publish(new PaymentTransactionEvent.PaymentFailed(
            "Insufficient funds", Instant.now()
        ), txn2);

        // Wait and query for successful transactions only
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, "SUCCEEDED", null, null);
                var response = httpClient
                    .POST("/payment/history")
                    .withRequestBody(request)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();

                assertThat(response.status().isSuccess()).isTrue();
                assertThat(response.body().transactions()).hasSize(1);
                assertThat(response.body().transactions().get(0).status()).isEqualTo("SUCCEEDED");
            });

        // Query for failed transactions
        var failedRequest = new PaymentEndpoint.PaymentHistoryRequest(customerId, "FAILED", null, null);
        var failedResponse = httpClient
            .POST("/payment/history")
            .withRequestBody(failedRequest)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(failedResponse.status().isSuccess()).isTrue();
        assertThat(failedResponse.body().transactions()).hasSize(1);
        assertThat(failedResponse.body().transactions().get(0).status()).isEqualTo("FAILED");
    }

    @Test
    public void shouldFilterByDateRange() {
        String customerId = "cust_history_api_3";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        // Create transaction with yesterday's timestamp
        String txn1 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-OLD", yesterday
        ), txn1);

        // Create transaction with today's timestamp
        String txn2 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-NEW", now
        ), txn2);

        // Wait for view to update
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, null, null, null);
                var response = httpClient
                    .POST("/payment/history")
                    .withRequestBody(request)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();
                assertThat(response.body().transactions()).hasSize(2);
            });

        // Query with date range that includes only today
        var todayRequest = new PaymentEndpoint.PaymentHistoryRequest(
            customerId,
            null,
            now.minus(1, ChronoUnit.HOURS).toString(),
            tomorrow.toString()
        );
        var todayResponse = httpClient
            .POST("/payment/history")
            .withRequestBody(todayRequest)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(todayResponse.status().isSuccess()).isTrue();
        assertThat(todayResponse.body().transactions()).hasSize(1);
        assertThat(todayResponse.body().transactions().get(0).merchantReference()).isEqualTo("ORDER-NEW");
    }

    @Test
    public void shouldFilterByStatusWithMultipleMatches() {
        String customerId = "cust_history_api_4";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);

        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("75.00"), Currency.USD);

        // Create old successful transaction
        String txn1 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-OLD-SUCCESS", yesterday
        ), txn1);
        events.publish(new PaymentTransactionEvent.PaymentSucceeded(
            yesterday
        ), txn1);

        // Create new successful transaction
        String txn2 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-NEW-SUCCESS", now
        ), txn2);
        events.publish(new PaymentTransactionEvent.PaymentSucceeded(
            now
        ), txn2);

        // Create failed transaction
        String txn3 = "txn_" + UUID.randomUUID();
        events.publish(new PaymentTransactionEvent.PaymentInitiated(
            customer, amount, "ORDER-FAIL", now
        ), txn3);
        events.publish(new PaymentTransactionEvent.PaymentFailed(
            "Card declined", now
        ), txn3);

        // Wait for all to be visible
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, null, null, null);
                var response = httpClient
                    .POST("/payment/history")
                    .withRequestBody(request)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();
                assertThat(response.body().transactions()).hasSize(3);
            });

        // Query: all successful transactions (should get both old and new)
        var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, "SUCCEEDED", null, null);
        var response = httpClient
            .POST("/payment/history")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactions()).hasSize(2);
        // Both should have SUCCEEDED status
        assertThat(response.body().transactions()).allMatch(tx -> tx.status().equals("SUCCEEDED"));
    }

    @Test
    public void shouldReturnEmptyListForNonExistentCustomer() {
        var request = new PaymentEndpoint.PaymentHistoryRequest("cust_nonexistent", null, null, null);
        var response = httpClient
            .POST("/payment/history")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactions()).isEmpty();
    }

    @Test
    public void shouldOrderByMostRecentFirst() {
        String customerId = "cust_history_api_5";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentTransactionEntity.class);

        Instant now = Instant.now();
        var customer = new Customer(customerId, "test@example.com", "Test User");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        // Create 3 transactions with different times
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

        // Wait and verify ordering
        Awaitility.await()
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var request = new PaymentEndpoint.PaymentHistoryRequest(customerId, null, null, null);
                var response = httpClient
                    .POST("/payment/history")
                    .withRequestBody(request)
                    .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
                    .invoke();

                assertThat(response.body().transactions()).hasSize(3);
                // Most recent first
                assertThat(response.body().transactions().get(0).merchantReference()).isEqualTo("ORDER-2");
                assertThat(response.body().transactions().get(1).merchantReference()).isEqualTo("ORDER-3");
                assertThat(response.body().transactions().get(2).merchantReference()).isEqualTo("ORDER-1");
            });
    }
}
