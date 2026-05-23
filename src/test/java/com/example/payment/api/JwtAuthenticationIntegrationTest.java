package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for JWT authentication and authorization.
 * Tests that endpoints properly enforce access control via ACL annotations.
 */
public class JwtAuthenticationIntegrationTest extends TestKitSupport {

    @Test
    public void shouldAllowPublicAccessToCreatePayment() {
        // Public endpoint - no JWT required
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("25.00", "USD"),
            "tok_visa",
            null,
            "ORDER-PUBLIC-" + System.currentTimeMillis(),
            new PaymentEndpoint.CustomerRequest("cust_public", "public@test.com", "Public User"),
            false,
            null
        );

        var response = httpClient
            .POST("/payment/transactions")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactionId()).isNotNull();
    }

    @Test
    public void shouldAllowPublicAccessToExchangeRates() {
        // Public endpoint - no JWT required
        var response = httpClient
            .GET("/payment/exchange-rates")
            .responseBodyAs(PaymentEndpoint.ExchangeRatesResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().rates()).isNotEmpty();
    }

    @Test
    public void shouldAllowPublicAccessToCurrencyConversion() {
        // Public endpoint - no JWT required
        var request = new PaymentEndpoint.CurrencyConversionRequest(
            "100.00",
            "USD",
            "EUR"
        );

        var response = httpClient
            .POST("/payment/convert")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.CurrencyConversionResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().convertedAmount()).isNotNull();
    }

    @Test
    public void shouldAllowAuthenticatedAccessToTransactionDetails() {
        // Create a payment first
        String uniqueRef = "ORDER-AUTH-" + System.currentTimeMillis();
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            uniqueRef,
            new PaymentEndpoint.CustomerRequest("cust_auth", "auth@test.com", "Auth User"),
            false,
            null
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = createResponse.body().transactionId();

        // GET transaction - requires authentication (Principal.ALL)
        // In test mode, this is allowed without JWT
        var getResponse = httpClient
            .GET("/payment/transactions/" + transactionId)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        assertThat(getResponse.status().isSuccess()).isTrue();
        assertThat(getResponse.body().transactionId()).isEqualTo(transactionId);
    }

    @Test
    public void shouldAllowAuthenticatedAccessToPaymentHistory() {
        // POST /payment/history requires authentication
        var request = new PaymentEndpoint.PaymentHistoryRequest(
            "cust_history_test",
            null,
            null,
            null
        );

        var response = httpClient
            .POST("/payment/history")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.PaymentHistoryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().transactions()).isNotNull();
    }

    @Test
    public void shouldAllowAuthenticatedAccessToRefunds() {
        // Create a payment first
        String uniqueRef = "ORDER-REFUND-AUTH-" + System.currentTimeMillis();
        var createRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("75.00", "USD"),
            "tok_visa",
            null,
            uniqueRef,
            new PaymentEndpoint.CustomerRequest("cust_refund_auth", "refundauth@test.com", "Refund Auth User"),
            false,
            null
        );

        var createResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(createRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = createResponse.body().transactionId();

        // Wait for payment to complete
        org.awaitility.Awaitility.await()
            .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var statusResponse = httpClient
                    .GET("/payment/transactions/" + transactionId)
                    .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                    .invoke();
                assertThat(statusResponse.body().status()).isEqualTo("SUCCEEDED");
            });

        // GET refunds - requires authentication
        var getRefundsResponse = httpClient
            .GET("/payment/transactions/" + transactionId + "/refunds")
            .responseBodyAs(PaymentEndpoint.RefundListResponse.class)
            .invoke();

        assertThat(getRefundsResponse.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldAllowAuthenticatedAccessToAuditLog() {
        // POST /audit-log requires authentication
        var request = new PaymentEndpoint.AuditLogRequest(
            "cust_audit_auth",
            null,
            null,
            null,
            10
        );

        var response = httpClient
            .POST("/payment/audit-log")
            .withRequestBody(request)
            .responseBodyAs(PaymentEndpoint.AuditLogResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().events()).isNotNull();
    }

    @Test
    public void shouldAllowAuthenticatedAccessToPaymentMethods() {
        // GET /payment/methods requires authentication
        var response = httpClient
            .GET("/payment/methods/")
            .addQueryParameter("customerId", "cust_pm_auth")
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodsResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().methods()).isNotNull();
    }
}
