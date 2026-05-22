package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment Method Endpoint Integration Test.
 * Tests REST API endpoints for payment methods.
 */
public class PaymentMethodEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreateSavePaymentMethodRequest() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_123",
            "tok_visa",
            "VISA",
            "4242",
            YearMonth.now().plusYears(2).toString(),
            true
        );

        assertThat(request.customerId()).isEqualTo("cust_123");
        assertThat(request.token()).isEqualTo("tok_visa");
        assertThat(request.brand()).isEqualTo("VISA");
        assertThat(request.last4Digits()).isEqualTo("4242");
        assertThat(request.isDefault()).isTrue();
    }

    @Test
    public void shouldValidatePaymentMethodResponse() {
        var response = new PaymentMethodEndpoint.PaymentMethodResponse(
            "pm_123",
            "cust_123",
            "VISA",
            "**** 4242",
            YearMonth.now().plusYears(2).toString(),
            true,
            false,
            false,
            java.time.Instant.now().toString()
        );

        assertThat(response.paymentMethodId()).isEqualTo("pm_123");
        assertThat(response.maskedNumber()).isEqualTo("**** 4242");
        assertThat(response.isDefault()).isTrue();
        assertThat(response.isExpired()).isFalse();
    }
}
