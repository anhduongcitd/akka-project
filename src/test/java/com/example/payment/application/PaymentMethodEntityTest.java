package com.example.payment.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.payment.domain.CardBrand;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.*;

public class PaymentMethodEntityTest {

    @Test
    public void shouldSavePaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        var command = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusMonths(12),
            true
        );

        var result = testKit.method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("pm_123");

        var state = testKit.getState();
        assertThat(state).isNotNull();
        assertThat(state.customerId()).isEqualTo("cust_123");
        assertThat(state.brand()).isEqualTo(CardBrand.VISA);
        assertThat(state.last4Digits()).isEqualTo("4242");
        assertThat(state.isDefault()).isTrue();
    }

    @Test
    public void shouldRejectDuplicateSave() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        var command = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusMonths(12),
            false
        );

        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(command);

        var result2 = testKit.method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        assertThat(result2.isError()).isTrue();
        assertThat(result2.getError()).contains("already exists");
    }

    @Test
    public void shouldRejectExpiredCard() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        var command = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().minusMonths(1), // Expired
            false
        );

        var result = testKit.method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("expired");
    }

    @Test
    public void shouldSetAsDefault() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        // Save payment method (not default)
        var saveCommand = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusMonths(12),
            false
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);

        // Set as default
        var setDefaultCommand = new PaymentMethodEntity.SetDefaultPaymentMethod();
        var result = testKit.method(PaymentMethodEntity::setDefault)
            .invoke(setDefaultCommand);

        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.isDefault()).isTrue();
    }

    @Test
    public void shouldDeletePaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        // Save payment method
        var saveCommand = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusMonths(12),
            false
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);

        // Verify it exists
        assertThat(testKit.getState()).isNotNull();

        // Delete
        var deleteCommand = new PaymentMethodEntity.DeletePaymentMethod();
        var result = testKit.method(PaymentMethodEntity::deletePaymentMethod)
            .invoke(deleteCommand);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("Payment method deleted");

        // State remains (view handles removal from query results)
        assertThat(testKit.getState()).isNotNull();
    }

    @Test
    public void shouldValidateLast4Digits() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        var command = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "42", // Invalid - must be 4 digits
            YearMonth.now().plusMonths(12),
            false
        );

        var result = testKit.method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("4 digits");
    }

    @Test
    public void shouldDetectExpiringSoonCard() {
        var testKit = EventSourcedTestKit.of("pm_123", PaymentMethodEntity::new);

        var command = new PaymentMethodEntity.SavePaymentMethod(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.now().plusMonths(1), // Expires in 1 month
            false
        );

        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(command);

        var state = testKit.getState();
        assertThat(state.isExpiringSoon()).isTrue();
        assertThat(state.isExpired()).isFalse();
    }
}
