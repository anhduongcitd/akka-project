package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.CardBrand;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CustomerPaymentMethodsView.
 * Tests view updates based on payment method entity events.
 */
public class CustomerPaymentMethodsViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);
    }

    @Test
    public void shouldProjectSavedPaymentMethodToView() {
        String customerId = "cust_view_test_1";
        String paymentMethodId = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Publish PaymentMethodSaved event
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            true,
            java.time.Instant.now()
        );

        events.publish(savedEvent, paymentMethodId);

        // Wait for view to be updated
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.methods()).hasSize(1);
                var method = result.methods().get(0);
                assertThat(method.paymentMethodId()).isEqualTo(paymentMethodId);
                assertThat(method.customerId()).isEqualTo(customerId);
                assertThat(method.brand()).isEqualTo(CardBrand.VISA);
                assertThat(method.last4Digits()).isEqualTo("4242");
                assertThat(method.expirationDate()).isEqualTo("2028-12");
                assertThat(method.isDefault()).isTrue();
                assertThat(method.isExpired()).isFalse();
                assertThat(method.isExpiringSoon()).isFalse();
            });
    }

    @Test
    public void shouldUpdateDefaultFlagInView() {
        String customerId = "cust_view_test_2";
        String paymentMethodId = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // First publish saved event (not default)
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false,  // Not default
            java.time.Instant.now()
        );
        events.publish(savedEvent, paymentMethodId);

        // Wait for view to be updated
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.methods()).hasSize(1);
                assertThat(result.methods().get(0).isDefault()).isFalse();
            });

        // Now publish SetDefault event
        var setDefaultEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSetDefault(
            java.time.Instant.now()
        );
        events.publish(setDefaultEvent, paymentMethodId);

        // Wait for view to be updated with default flag
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.methods()).hasSize(1);
                assertThat(result.methods().get(0).isDefault()).isTrue();
            });
    }

    @Test
    public void shouldDeletePaymentMethodFromView() {
        String customerId = "cust_view_test_3";
        String paymentMethodId = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Publish saved event
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false,
            java.time.Instant.now()
        );
        events.publish(savedEvent, paymentMethodId);

        // Wait for view to have the payment method
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.methods()).hasSize(1);
            });

        // Now delete it
        var deletedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodDeleted(
            java.time.Instant.now()
        );
        events.publish(deletedEvent, paymentMethodId);

        // Wait for view to remove the payment method
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);
                assertThat(result.methods()).isEmpty();
            });
    }

    @Test
    public void shouldListMultiplePaymentMethodsOrderedByDefault() {
        String customerId = "cust_view_test_4";
        String paymentMethodId1 = "pm_" + UUID.randomUUID();
        String paymentMethodId2 = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Add first payment method (not default)
        var savedEvent1 = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false,
            java.time.Instant.now()
        );
        events.publish(savedEvent1, paymentMethodId1);

        // Add second payment method (default)
        var savedEvent2 = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_mastercard_test",
            CardBrand.MASTERCARD,
            "5555",
            YearMonth.of(2029, 6),
            true,  // This is default
            java.time.Instant.now()
        );
        events.publish(savedEvent2, paymentMethodId2);

        // Wait and verify ordering (default first)
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.methods()).hasSize(2);
                // Default should be first
                assertThat(result.methods().get(0).paymentMethodId()).isEqualTo(paymentMethodId2);
                assertThat(result.methods().get(0).isDefault()).isTrue();
                assertThat(result.methods().get(0).brand()).isEqualTo(CardBrand.MASTERCARD);

                // Non-default should be second
                assertThat(result.methods().get(1).paymentMethodId()).isEqualTo(paymentMethodId1);
                assertThat(result.methods().get(1).isDefault()).isFalse();
                assertThat(result.methods().get(1).brand()).isEqualTo(CardBrand.VISA);
            });
    }

    @Test
    public void shouldGetDefaultPaymentMethod() {
        String customerId = "cust_view_test_5";
        String paymentMethodId1 = "pm_" + UUID.randomUUID();
        String paymentMethodId2 = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Add non-default payment method
        var savedEvent1 = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false,
            java.time.Instant.now()
        );
        events.publish(savedEvent1, paymentMethodId1);

        // Add default payment method
        var savedEvent2 = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_mastercard_test",
            CardBrand.MASTERCARD,
            "5555",
            YearMonth.of(2029, 6),
            true,
            java.time.Instant.now()
        );
        events.publish(savedEvent2, paymentMethodId2);

        // Query for default payment method only
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getDefaultByCustomer)
                    .invoke(customerId);

                assertThat(result.methods()).hasSize(1);
                assertThat(result.methods().get(0).paymentMethodId()).isEqualTo(paymentMethodId2);
                assertThat(result.methods().get(0).isDefault()).isTrue();
            });
    }

    @Test
    public void shouldDetectExpiredCard() {
        String customerId = "cust_view_test_6";
        String paymentMethodId = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Add payment method with past expiration date
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2020, 1),  // Expired
            false,
            java.time.Instant.now()
        );
        events.publish(savedEvent, paymentMethodId);

        // Wait and verify expired flag
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.methods()).hasSize(1);
                assertThat(result.methods().get(0).isExpired()).isTrue();
            });
    }

    @Test
    public void shouldDetectExpiringSoonCard() {
        String customerId = "cust_view_test_7";
        String paymentMethodId = "pm_" + UUID.randomUUID();

        var events = testKit.getEventSourcedEntityIncomingMessages(PaymentMethodEntity.class);

        // Add payment method expiring next month
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        var savedEvent = new com.example.payment.domain.PaymentMethodEvent.PaymentMethodSaved(
            customerId,
            "tok_visa_test",
            CardBrand.VISA,
            "4242",
            nextMonth,
            false,
            java.time.Instant.now()
        );
        events.publish(savedEvent, paymentMethodId);

        // Wait and verify expiring soon flag
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(CustomerPaymentMethodsView::getByCustomer)
                    .invoke(customerId);

                assertThat(result.methods()).hasSize(1);
                assertThat(result.methods().get(0).isExpiringSoon()).isTrue();
                assertThat(result.methods().get(0).isExpired()).isFalse();
            });
    }
}
