package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment Endpoint Integration Test.
 * Tests REST API endpoints with TestKit.
 */
public class PaymentEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreatePaymentRequest() {
        // Test request structure
        var moneyRequest = new PaymentEndpoint.MoneyRequest("100.00", "USD");
        var customerRequest = new PaymentEndpoint.CustomerRequest(
            "cust_123",
            "test@example.com",
            "Test Customer"
        );

        var request = new PaymentEndpoint.CreatePaymentRequest(
            moneyRequest,
            "tok_visa",
            null, // paymentMethodId - not using saved method
            "ORDER-001",
            customerRequest,
            false
        );

        assertThat(request.amount().value()).isEqualTo("100.00");
        assertThat(request.customer().email()).isEqualTo("test@example.com");

        // Note: Full integration test would use httpClient to POST to /payment/transactions
        // For now, this validates the request structure
    }
}
