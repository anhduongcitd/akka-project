package com.example.payment.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.payment.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

public class PaymentTransactionEntityTest {

    private Customer createTestCustomer() {
        return new Customer("cust_123", "test@example.com", "Test Customer");
    }

    private Money createTestAmount() {
        return new Money(new BigDecimal("100.00"), Currency.USD);
    }

    @Test
    public void shouldInitiatePayment() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var command = new PaymentTransactionEntity.InitiatePayment(
            createTestCustomer(),
            createTestAmount(),
            "ORDER-001"
        );

        var result = testKit.method(PaymentTransactionEntity::initiatePayment)
            .invoke(command);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("txn_123");

        var state = testKit.getState();
        assertThat(state).isNotNull();
        assertThat(state.transactionId()).isEqualTo("txn_123");
        assertThat(state.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(state.amount().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    public void shouldRejectDuplicateInitiation() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var command = new PaymentTransactionEntity.InitiatePayment(
            createTestCustomer(),
            createTestAmount(),
            "ORDER-001"
        );

        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(command);

        var result2 = testKit.method(PaymentTransactionEntity::initiatePayment)
            .invoke(command);

        assertThat(result2.isError()).isTrue();
        assertThat(result2.getError()).contains("already initiated");
    }

    @Test
    public void shouldAuthorizePayment() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var initiateCommand = new PaymentTransactionEntity.InitiatePayment(
            createTestCustomer(),
            createTestAmount(),
            "ORDER-001"
        );
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(initiateCommand);

        var authorizeCommand = new PaymentTransactionEntity.AuthorizePayment("ch_stripe_123");
        var result = testKit.method(PaymentTransactionEntity::authorizePayment)
            .invoke(authorizeCommand);

        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(state.gatewayTransactionId()).isEqualTo("ch_stripe_123");
    }

    @Test
    public void shouldCompletePayment() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        // Initiate
        var initiateCommand = new PaymentTransactionEntity.InitiatePayment(
            createTestCustomer(),
            createTestAmount(),
            "ORDER-001"
        );
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(initiateCommand);

        // Authorize
        var authorizeCommand = new PaymentTransactionEntity.AuthorizePayment("ch_stripe_123");
        testKit.method(PaymentTransactionEntity::authorizePayment).invoke(authorizeCommand);

        // Complete
        var completeCommand = new PaymentTransactionEntity.CompletePayment();
        var result = testKit.method(PaymentTransactionEntity::completePayment)
            .invoke(completeCommand);

        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(state.completedAt()).isNotNull();
    }

    @Test
    public void shouldFailPayment() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        var initiateCommand = new PaymentTransactionEntity.InitiatePayment(
            createTestCustomer(),
            createTestAmount(),
            "ORDER-001"
        );
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(initiateCommand);

        var failCommand = new PaymentTransactionEntity.FailPayment("Card declined");
        var result = testKit.method(PaymentTransactionEntity::failPayment)
            .invoke(failCommand);

        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(state.failureReason()).isEqualTo("Card declined");
    }

    @Test
    public void shouldInitiateRefund() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        // Complete a successful payment
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(
            new PaymentTransactionEntity.InitiatePayment(
                createTestCustomer(),
                createTestAmount(),
                "ORDER-001"
            )
        );
        testKit.method(PaymentTransactionEntity::authorizePayment).invoke(
            new PaymentTransactionEntity.AuthorizePayment("ch_stripe_123")
        );
        testKit.method(PaymentTransactionEntity::completePayment).invoke(
            new PaymentTransactionEntity.CompletePayment()
        );

        // Initiate refund
        var refundCommand = new PaymentTransactionEntity.InitiateRefund(
            "ref_123",
            new Money(new BigDecimal("50.00"), Currency.USD),
            "Customer request"
        );
        var result = testKit.method(PaymentTransactionEntity::initiateRefund)
            .invoke(refundCommand);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("ref_123");

        var state = testKit.getState();
        assertThat(state.refunds()).hasSize(1);
        assertThat(state.refunds().get(0).refundId()).isEqualTo("ref_123");
        assertThat(state.refunds().get(0).status()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    public void shouldRejectRefundExceedingAmount() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        // Complete payment
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(
            new PaymentTransactionEntity.InitiatePayment(
                createTestCustomer(),
                createTestAmount(),
                "ORDER-001"
            )
        );
        testKit.method(PaymentTransactionEntity::authorizePayment).invoke(
            new PaymentTransactionEntity.AuthorizePayment("ch_stripe_123")
        );
        testKit.method(PaymentTransactionEntity::completePayment).invoke(
            new PaymentTransactionEntity.CompletePayment()
        );

        // Try to refund more than payment amount
        var refundCommand = new PaymentTransactionEntity.InitiateRefund(
            "ref_123",
            new Money(new BigDecimal("150.00"), Currency.USD),
            "Too much"
        );
        var result = testKit.method(PaymentTransactionEntity::initiateRefund)
            .invoke(refundCommand);

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("exceeds refundable amount");
    }

    @Test
    public void shouldCompleteRefund() {
        var testKit = EventSourcedTestKit.of("txn_123", PaymentTransactionEntity::new);

        // Complete payment and initiate refund
        testKit.method(PaymentTransactionEntity::initiatePayment).invoke(
            new PaymentTransactionEntity.InitiatePayment(
                createTestCustomer(),
                createTestAmount(),
                "ORDER-001"
            )
        );
        testKit.method(PaymentTransactionEntity::authorizePayment).invoke(
            new PaymentTransactionEntity.AuthorizePayment("ch_stripe_123")
        );
        testKit.method(PaymentTransactionEntity::completePayment).invoke(
            new PaymentTransactionEntity.CompletePayment()
        );
        testKit.method(PaymentTransactionEntity::initiateRefund).invoke(
            new PaymentTransactionEntity.InitiateRefund(
                "ref_123",
                new Money(new BigDecimal("100.00"), Currency.USD),
                "Full refund"
            )
        );

        // Complete refund
        var completeRefundCommand = new PaymentTransactionEntity.CompleteRefund("ref_123");
        var result = testKit.method(PaymentTransactionEntity::completeRefund)
            .invoke(completeRefundCommand);

        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.refunds().get(0).status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(state.status()).isEqualTo(PaymentStatus.REFUNDED); // Fully refunded
    }
}
