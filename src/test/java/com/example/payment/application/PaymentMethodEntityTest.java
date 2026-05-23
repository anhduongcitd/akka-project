package com.example.payment.application;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.payment.domain.CardBrand;
import com.example.payment.domain.PaymentMethod;
import com.example.payment.domain.PaymentMethodEvent;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PaymentMethodEntity.
 * Tests the payment method lifecycle: save, set default, delete.
 */
public class PaymentMethodEntityTest {

    @Test
    public void testSavePaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_test_1", ctx -> new PaymentMethodEntity(ctx));

        var command = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            true
        );

        var result = testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(command);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo(Done.getInstance());

        // Verify event was persisted
        assertThat(result.getAllEvents()).hasSize(1);
        var event = (PaymentMethodEvent.PaymentMethodSaved) result.getAllEvents().get(0);
        assertThat(event.customerId()).isEqualTo("cust_123");
        assertThat(event.token()).isEqualTo("tok_visa");
        assertThat(event.brand()).isEqualTo(CardBrand.VISA);
        assertThat(event.last4Digits()).isEqualTo("4242");
        assertThat(event.expirationDate()).isEqualTo(YearMonth.of(2028, 12));
        assertThat(event.isDefault()).isTrue();

        // Verify state was updated
        var state = testKit.getState();
        assertThat(state).isNotNull();
        assertThat(state.paymentMethodId()).isEqualTo("pm_test_1");
        assertThat(state.customerId()).isEqualTo("cust_123");
        assertThat(state.brand()).isEqualTo(CardBrand.VISA);
        assertThat(state.last4Digits()).isEqualTo("4242");
        assertThat(state.isDefault()).isTrue();
    }

    @Test
    public void testSavePaymentMethodValidation() {
        var testKit = EventSourcedTestKit.of("pm_test_2", ctx -> new PaymentMethodEntity(ctx));

        // Test missing customer ID
        var invalidCommand1 = new PaymentMethodEntity.SavePaymentMethodCommand(
            "",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false
        );
        var result1 = testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(invalidCommand1);
        assertThat(result1.isError()).isTrue();
        assertThat(result1.getError()).contains("Customer ID is required");

        // Test invalid last 4 digits
        var invalidCommand2 = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "123",  // Only 3 digits
            YearMonth.of(2028, 12),
            false
        );
        var result2 = testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(invalidCommand2);
        assertThat(result2.isError()).isTrue();
        assertThat(result2.getError()).contains("4 digits");

        // Test expired card
        var invalidCommand3 = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2020, 12),  // Expired
            false
        );
        var result3 = testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(invalidCommand3);
        assertThat(result3.isError()).isTrue();
        assertThat(result3.getError()).contains("expired");
    }

    @Test
    public void testCannotSaveDuplicatePaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_test_3", ctx -> new PaymentMethodEntity(ctx));

        // First save should succeed
        var command = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(command);

        // Second save with same ID should fail
        var result = testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(command);
        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("already exists");
    }

    @Test
    public void testSetAsDefault() {
        var testKit = EventSourcedTestKit.of("pm_test_4", ctx -> new PaymentMethodEntity(ctx));

        // First save a payment method (not default)
        var saveCommand = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false  // Not default
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);
        assertThat(testKit.getState().isDefault()).isFalse();

        // Set as default
        var result = testKit.method(PaymentMethodEntity::setAsDefault).invoke();
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo(Done.getInstance());

        // Verify event was persisted
        assertThat(result.getAllEvents()).hasSize(1);
        assertThat(result.getAllEvents().get(0)).isInstanceOf(PaymentMethodEvent.PaymentMethodSetDefault.class);

        // Verify state was updated
        assertThat(testKit.getState().isDefault()).isTrue();
    }

    @Test
    public void testSetAsDefaultAlreadyDefault() {
        var testKit = EventSourcedTestKit.of("pm_test_5", ctx -> new PaymentMethodEntity(ctx));

        // Save as default
        var saveCommand = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            true  // Already default
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);

        // Try to set as default again - should be no-op
        var result = testKit.method(PaymentMethodEntity::setAsDefault).invoke();
        assertThat(result.isReply()).isTrue();
        assertThat(result.getAllEvents()).isEmpty(); // No new events
    }

    @Test
    public void testSetAsDefaultNotFound() {
        var testKit = EventSourcedTestKit.of("pm_test_6", ctx -> new PaymentMethodEntity(ctx));

        // Try to set default on non-existent payment method
        var result = testKit.method(PaymentMethodEntity::setAsDefault).invoke();
        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("not found");
    }

    @Test
    public void testDeletePaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_test_7", ctx -> new PaymentMethodEntity(ctx));

        // First save a payment method
        var saveCommand = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            false
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);

        // Delete it
        var result = testKit.method(PaymentMethodEntity::delete).invoke();
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo(Done.getInstance());

        // Verify event was persisted
        assertThat(result.getAllEvents()).hasSize(1);
        assertThat(result.getAllEvents().get(0)).isInstanceOf(PaymentMethodEvent.PaymentMethodDeleted.class);

        // Verify state is marked as deleted
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().isDeleted()).isTrue();
    }

    @Test
    public void testDeleteNotFound() {
        var testKit = EventSourcedTestKit.of("pm_test_8", ctx -> new PaymentMethodEntity(ctx));

        // Try to delete non-existent payment method
        var result = testKit.method(PaymentMethodEntity::delete).invoke();
        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("not found");
    }

    @Test
    public void testGetPaymentMethod() {
        var testKit = EventSourcedTestKit.of("pm_test_9", ctx -> new PaymentMethodEntity(ctx));

        // First save a payment method
        var saveCommand = new PaymentMethodEntity.SavePaymentMethodCommand(
            "cust_123",
            "tok_visa",
            CardBrand.VISA,
            "4242",
            YearMonth.of(2028, 12),
            true
        );
        testKit.method(PaymentMethodEntity::savePaymentMethod).invoke(saveCommand);

        // Get it
        var result = testKit.method(PaymentMethodEntity::getPaymentMethod).invoke();
        assertThat(result.isReply()).isTrue();

        PaymentMethod method = result.getReply();
        assertThat(method.paymentMethodId()).isEqualTo("pm_test_9");
        assertThat(method.customerId()).isEqualTo("cust_123");
        assertThat(method.brand()).isEqualTo(CardBrand.VISA);
        assertThat(method.last4Digits()).isEqualTo("4242");
        assertThat(method.isDefault()).isTrue();
        assertThat(method.getMaskedNumber()).isEqualTo("**** 4242");
    }

    @Test
    public void testGetPaymentMethodNotFound() {
        var testKit = EventSourcedTestKit.of("pm_test_10", ctx -> new PaymentMethodEntity(ctx));

        // Try to get non-existent payment method
        var result = testKit.method(PaymentMethodEntity::getPaymentMethod).invoke();
        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("not found");
    }
}
