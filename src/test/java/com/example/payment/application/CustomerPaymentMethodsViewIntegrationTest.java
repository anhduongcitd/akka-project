package com.example.payment.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.CardBrand;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Customer Payment Methods View Integration Test.
 * Tests view projections from payment method events.
 */
public class CustomerPaymentMethodsViewIntegrationTest extends TestKitSupport {

    @Override
    protected akka.javasdk.testkit.TestKit.Settings testKitSettings() {
        return akka.javasdk.testkit.TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);
    }

    @Test
    public void shouldProjectSavedPaymentMethod() {
        String paymentMethodId = "pm_test_" + System.currentTimeMillis();
        String customerId = "cust_test_123";

        // Publish event
        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusYears(2),
            true,
            java.time.Instant.now()
        );
        events.publish(savedEvent, paymentMethodId);

        // Wait for view to update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomerId)
                    .invoke(customerId);

                assertThat(result).isNotNull();
                assertThat(result.methods()).isNotEmpty();
                assertThat(result.methods().get(0).paymentMethodId()).isEqualTo(paymentMethodId);
                assertThat(result.methods().get(0).customerId()).isEqualTo(customerId);
                assertThat(result.methods().get(0).brand()).isEqualTo(CardBrand.VISA);
                assertThat(result.methods().get(0).last4Digits()).isEqualTo("4242");
            });
    }
}
