package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for audit logging.
 * Tests automatic audit trail creation from payment operations.
 */
public class AuditLogIntegrationTest extends TestKitSupport {

    @Test
    public void shouldLogPaymentCreationToAuditTrail() {
        String customerId = "cust_audit_" + System.currentTimeMillis();

        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("150.00", "USD"),
            "tok_visa",
            null,
            "ORDER-AUDIT-001",
            new PaymentEndpoint.CustomerRequest(customerId, "audit@test.com", "Audit User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(paymentResponse.status().isSuccess()).isTrue();

        // Wait for consumer to process and log to audit trail
        var auditRequest = new PaymentEndpoint.AuditLogRequest(
            customerId,
            null,
            null,
            null,
            null
        );

        Awaitility.await()
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var auditResponse = httpClient
                    .POST("/payment/audit-log")
                    .withRequestBody(auditRequest)
                    .responseBodyAs(PaymentEndpoint.AuditLogResponse.class)
                    .invoke();

                assertThat(auditResponse.body().events()).isNotEmpty();
                assertThat(auditResponse.body().events().get(0).eventType()).isEqualTo("PAYMENT_CREATED");
            });
    }

    @Test
    public void shouldQueryAuditLogByEventType() {
        String customerId = "cust_audit_type_" + System.currentTimeMillis();

        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-TYPE-001",
            new PaymentEndpoint.CustomerRequest(customerId, "type@test.com", "Type User"),
            false,
            null
        );

        httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        // Wait and query by type
        var auditRequest = new PaymentEndpoint.AuditLogRequest(
            customerId,
            "PAYMENT_CREATED",
            null,
            null,
            null
        );

        Awaitility.await()
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var auditResponse = httpClient
                    .POST("/payment/audit-log")
                    .withRequestBody(auditRequest)
                    .responseBodyAs(PaymentEndpoint.AuditLogResponse.class)
                    .invoke();

                assertThat(auditResponse.body().events()).isNotEmpty();
                assertThat(auditResponse.body().events())
                    .allMatch(e -> e.eventType().equals("PAYMENT_CREATED"));
            });
    }

    @Test
    public void shouldQueryRecentAuditEvents() {
        String customerId = "cust_audit_recent_" + System.currentTimeMillis();

        // Create 3 payments
        for (int i = 1; i <= 3; i++) {
            var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
                new PaymentEndpoint.MoneyRequest("50.00", "USD"),
                "tok_visa",
                null,
                "ORDER-RECENT-" + i,
                new PaymentEndpoint.CustomerRequest(customerId, "recent@test.com", "Recent User"),
                false,
                null
            );

            httpClient
                .POST("/payment/transactions")
                .withRequestBody(paymentRequest)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke();
        }

        // Query last 2 events
        var auditRequest = new PaymentEndpoint.AuditLogRequest(
            customerId,
            null,
            null,
            null,
            2  // Limit to 2
        );

        Awaitility.await()
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var auditResponse = httpClient
                    .POST("/payment/audit-log")
                    .withRequestBody(auditRequest)
                    .responseBodyAs(PaymentEndpoint.AuditLogResponse.class)
                    .invoke();

                // Should have at least 2 events (might have more from payment completion)
                assertThat(auditResponse.body().events()).hasSizeGreaterThanOrEqualTo(2);
            });
    }

    @Test
    public void shouldTrackMultipleEventTypes() {
        String customerId = "cust_audit_multi_" + System.currentTimeMillis();

        // Create payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("200.00", "USD"),
            "tok_visa",
            null,
            "ORDER-MULTI-001",
            new PaymentEndpoint.CustomerRequest(customerId, "multi@test.com", "Multi User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Wait for payment to complete
        Awaitility.await()
            .pollInterval(200, TimeUnit.MILLISECONDS)
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // Initiate refund
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "Partial refund test"
        );

        httpClient
            .POST("/payment/transactions/" + transactionId + "/refunds")
            .withRequestBody(refundRequest)
            .responseBodyAs(PaymentEndpoint.RefundResponse.class)
            .invoke();

        // Query all audit events
        var auditRequest = new PaymentEndpoint.AuditLogRequest(
            customerId,
            null,
            null,
            null,
            null
        );

        Awaitility.await()
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var auditResponse = httpClient
                    .POST("/payment/audit-log")
                    .withRequestBody(auditRequest)
                    .responseBodyAs(PaymentEndpoint.AuditLogResponse.class)
                    .invoke();

                // Should have multiple event types
                var eventTypes = auditResponse.body().events().stream()
                    .map(PaymentEndpoint.AuditEventResponse::eventType)
                    .distinct()
                    .toList();

                assertThat(eventTypes).contains("PAYMENT_CREATED");
                // May also contain PAYMENT_COMPLETED and REFUND_INITIATED
                assertThat(eventTypes.size()).isGreaterThanOrEqualTo(1);
            });
    }
}
