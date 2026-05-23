package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for payment validation edge cases.
 */
public class PaymentValidationEdgeCasesTest extends TestKitSupport {

    @Test
    public void shouldRejectPaymentWithNegativeAmount() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("-50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-NEGATIVE",
            new PaymentEndpoint.CustomerRequest("cust_negative", "negative@test.com", "Negative User"),
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectPaymentWithZeroAmount() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("0.00", "USD"),
            "tok_visa",
            null,
            "ORDER-ZERO",
            new PaymentEndpoint.CustomerRequest("cust_zero", "zero@test.com", "Zero User"),
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectPaymentWithInvalidCurrency() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "INVALID"),
            "tok_visa",
            null,
            "ORDER-INVALID-CURRENCY",
            new PaymentEndpoint.CustomerRequest("cust_invalid", "invalid@test.com", "Invalid User"),
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldRejectPaymentWithoutAmount() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            null,  // No amount
            "tok_visa",
            null,
            "ORDER-NO-AMOUNT",
            new PaymentEndpoint.CustomerRequest("cust_noamount", "noamount@test.com", "No Amount User"),
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Amount is required");
    }

    @Test
    public void shouldRejectPaymentWithoutCustomer() {
        var request = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "tok_visa",
            null,
            "ORDER-NO-CUSTOMER",
            null,  // No customer
            false,
            null
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions")
                .withRequestBody(request)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Customer information is required");
    }

    @Test
    public void shouldRejectRefundWithNegativeAmount() {
        // First create a successful payment
        var paymentRequest = new PaymentEndpoint.CreatePaymentRequest(
            new PaymentEndpoint.MoneyRequest("100.00", "USD"),
            "tok_visa",
            null,
            "ORDER-REFUND-NEGATIVE",
            new PaymentEndpoint.CustomerRequest("cust_refund_neg", "refundneg@test.com", "Refund Neg User"),
            false,
            null
        );

        var paymentResponse = httpClient
            .POST("/payment/transactions")
            .withRequestBody(paymentRequest)
            .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
            .invoke();

        String transactionId = paymentResponse.body().transactionId();

        // Try to refund with negative amount
        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("-50.00", "USD"),
            "Negative refund"
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions/" + transactionId + "/refunds")
                .withRequestBody(refundRequest)
                .responseBodyAs(PaymentEndpoint.RefundResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldHandleQueryingNonExistentTransaction() {
        String nonExistentId = "txn_nonexistent_12345";

        assertThatThrownBy(() ->
            httpClient
                .GET("/payment/transactions/" + nonExistentId)
                .responseBodyAs(PaymentEndpoint.PaymentResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldHandleRefundOnNonExistentTransaction() {
        String nonExistentId = "txn_nonexistent_refund";

        var refundRequest = new PaymentEndpoint.RefundRequest(
            new PaymentEndpoint.MoneyRequest("50.00", "USD"),
            "Refund on non-existent"
        );

        assertThatThrownBy(() ->
            httpClient
                .POST("/payment/transactions/" + nonExistentId + "/refunds")
                .withRequestBody(refundRequest)
                .responseBodyAs(PaymentEndpoint.RefundResponse.class)
                .invoke()
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
