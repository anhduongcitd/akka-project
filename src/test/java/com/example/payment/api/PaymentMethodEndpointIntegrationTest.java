package com.example.payment.api;

import akka.Done;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.application.CustomerPaymentMethodsView;
import com.example.payment.application.PaymentMethodEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for PaymentMethodEndpoint.
 * Tests the full CRUD lifecycle of payment methods via HTTP API.
 */
public class PaymentMethodEndpointIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);
    }

    @Test
    public void shouldSavePaymentMethod() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_endpoint_1",
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "2028-12",
            true
        );

        var response = httpClient
            .POST("/payment/methods")
            .withRequestBody(request)
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();

        var body = response.body();
        assertThat(body.paymentMethodId()).isNotEmpty();
        assertThat(body.customerId()).isEqualTo("cust_endpoint_1");
        assertThat(body.brand()).isEqualTo("VISA");
        assertThat(body.last4Digits()).isEqualTo("4242");
        assertThat(body.expirationDate()).isEqualTo("2028-12");
        assertThat(body.maskedNumber()).isEqualTo("**** 4242");
        assertThat(body.isDefault()).isTrue();
        assertThat(body.isExpired()).isFalse();
    }

    @Test
    public void shouldRejectInvalidExpirationDate() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_endpoint_2",
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "invalid-date",  // Invalid format
            false
        );

        var response = httpClient
            .POST("/payment/methods")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }

    @Test
    public void shouldRejectExpiredCard() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_endpoint_3",
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "2020-01",  // Expired
            false
        );

        var response = httpClient
            .POST("/payment/methods")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }

    @Test
    public void shouldListPaymentMethodsForCustomer() {
        String customerId = "cust_endpoint_4";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Save first payment method via API
        var request1 = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            customerId,
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "2028-12",
            false
        );
        var response1 = httpClient.POST("/payment/methods")
            .withRequestBody(request1)
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();
        String pm1Id = response1.body().paymentMethodId();

        // Manually publish event for view
        events.publish(
            new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
                customerId, "tok_visa_endpoint", com.example.payment.domain.CardBrand.VISA,
                "4242", java.time.YearMonth.of(2028, 12), false, java.time.Instant.now()
            ),
            pm1Id
        );

        // Save second payment method (default) via API
        var request2 = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            customerId,
            "tok_mastercard_endpoint",
            "MASTERCARD",
            "5555",
            "2029-06",
            true
        );
        var response2 = httpClient.POST("/payment/methods")
            .withRequestBody(request2)
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();
        String pm2Id = response2.body().paymentMethodId();

        // Manually publish event for view
        events.publish(
            new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
                customerId, "tok_mastercard_endpoint", com.example.payment.domain.CardBrand.MASTERCARD,
                "5555", java.time.YearMonth.of(2029, 6), true, java.time.Instant.now()
            ),
            pm2Id
        );

        // Wait for view to update
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/payment/methods?customerId=" + customerId)
                    .responseBodyAs(PaymentMethodEndpoint.PaymentMethodsResponse.class)
                    .invoke();

                assertThat(response.status().isSuccess()).isTrue();

                var body = response.body();
                assertThat(body.methods()).hasSize(2);

                // Default should be first (ordered by default flag)
                assertThat(body.methods().get(0).isDefault()).isTrue();
                assertThat(body.methods().get(0).brand()).isEqualTo("MASTERCARD");

                // Non-default second
                assertThat(body.methods().get(1).isDefault()).isFalse();
                assertThat(body.methods().get(1).brand()).isEqualTo("VISA");
            });
    }

    @Test
    public void shouldSetPaymentMethodAsDefault() {
        String customerId = "cust_endpoint_5";

        // Save payment method (not default)
        var saveRequest = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            customerId,
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "2028-12",
            false
        );

        var saveResponse = httpClient
            .POST("/payment/methods")
            .withRequestBody(saveRequest)
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();

        String paymentMethodId = saveResponse.body().paymentMethodId();
        assertThat(saveResponse.body().isDefault()).isFalse();

        // Set as default
        var setDefaultResponse = httpClient
            .PUT("/payment/methods/" + paymentMethodId + "/default")
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();

        assertThat(setDefaultResponse.status().isSuccess()).isTrue();
        assertThat(setDefaultResponse.body().isDefault()).isTrue();
        assertThat(setDefaultResponse.body().paymentMethodId()).isEqualTo(paymentMethodId);
    }

    @Test
    public void shouldDeletePaymentMethod() {
        String customerId = "cust_endpoint_6";

        // Save payment method
        var saveRequest = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            customerId,
            "tok_visa_endpoint",
            "VISA",
            "4242",
            "2028-12",
            false
        );

        var saveResponse = httpClient
            .POST("/payment/methods")
            .withRequestBody(saveRequest)
            .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
            .invoke();

        String paymentMethodId = saveResponse.body().paymentMethodId();

        // Delete it
        var deleteResponse = httpClient
            .DELETE("/payment/methods/" + paymentMethodId)
            .responseBodyAs(Done.class)
            .invoke();

        assertThat(deleteResponse.status().isSuccess()).isTrue();

        // Wait for view to update
        Awaitility.await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var listResponse = httpClient
                    .GET("/payment/methods?customerId=" + customerId)
                    .responseBodyAs(PaymentMethodEndpoint.PaymentMethodsResponse.class)
                    .invoke();

                // Should be empty after deletion (hard delete from view)
                assertThat(listResponse.body().methods()).isEmpty();
            });
    }

    @Test
    public void shouldRejectDeletingNonExistentPaymentMethod() {
        var response = httpClient
            .DELETE("/payment/methods/pm_nonexistent")
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }

    @Test
    public void shouldRejectInvalidCardBrand() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_endpoint_7",
            "tok_visa_endpoint",
            "INVALID_BRAND",
            "4242",
            "2028-12",
            false
        );

        var response = httpClient
            .POST("/payment/methods")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }

    @Test
    public void shouldRejectInvalidLast4Digits() {
        var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
            "cust_endpoint_8",
            "tok_visa_endpoint",
            "VISA",
            "123",  // Only 3 digits
            "2028-12",
            false
        );

        var response = httpClient
            .POST("/payment/methods")
            .withRequestBody(request)
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }

    @Test
    public void shouldHandleMultiplePaymentMethodsForSameCustomer() {
        String customerId = "cust_endpoint_9";

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Add 3 payment methods
        for (int i = 0; i < 3; i++) {
            var request = new PaymentMethodEndpoint.SavePaymentMethodRequest(
                customerId,
                "tok_test_" + i,
                "VISA",
                "424" + i,
                "2028-12",
                i == 0  // First one is default
            );
            var response = httpClient.POST("/payment/methods")
                .withRequestBody(request)
                .responseBodyAs(PaymentMethodEndpoint.PaymentMethodResponse.class)
                .invoke();

            // Manually publish event for view
            events.publish(
                new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
                    customerId, "tok_test_" + i, com.example.payment.domain.CardBrand.VISA,
                    "424" + i, java.time.YearMonth.of(2028, 12), i == 0, java.time.Instant.now()
                ),
                response.body().paymentMethodId()
            );
        }

        // Wait and verify
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var response = httpClient
                    .GET("/payment/methods?customerId=" + customerId)
                    .responseBodyAs(PaymentMethodEndpoint.PaymentMethodsResponse.class)
                    .invoke();

                assertThat(response.body().methods()).hasSize(3);

                // First should be default
                assertThat(response.body().methods().get(0).isDefault()).isTrue();

                // Others not default
                assertThat(response.body().methods().get(1).isDefault()).isFalse();
                assertThat(response.body().methods().get(2).isDefault()).isFalse();
            });
    }

    @Test
    public void shouldRequireCustomerIdQueryParameter() {
        // Try to list without customerId
        var response = httpClient
            .GET("/payment/methods")
            .invoke();

        assertThat(response.status().isFailure()).isTrue();
    }
}
