package com.example.payment.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class PaymentTransactionTest {

    private Customer createTestCustomer() {
        return new Customer("cust_123", "test@example.com", "Test Customer");
    }

    private Money createTestAmount() {
        return new Money(new BigDecimal("100.00"), Currency.USD);
    }

    private PaymentTransaction createTestTransaction() {
        return new PaymentTransaction(
            "txn_123",
            createTestCustomer(),
            createTestAmount(),
            PaymentStatus.PENDING,
            "ORDER-001",
            null,
            new ArrayList<>(),
            Instant.now(),
            null,
            null
        );
    }

    @Test
    public void shouldCreateValidTransaction() {
        PaymentTransaction txn = createTestTransaction();

        assertThat(txn.transactionId()).isEqualTo("txn_123");
        assertThat(txn.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(txn.amount().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    public void shouldRejectNullTransactionId() {
        assertThatThrownBy(() -> new PaymentTransaction(
            null,
            createTestCustomer(),
            createTestAmount(),
            PaymentStatus.PENDING,
            "ORDER-001",
            null,
            new ArrayList<>(),
            Instant.now(),
            null,
            null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Transaction ID cannot be null");
    }

    @Test
    public void shouldAllowRefundForSucceededTransaction() {
        PaymentTransaction txn = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED);

        assertThat(txn.canBeRefunded()).isTrue();
    }

    @Test
    public void shouldNotAllowRefundForPendingTransaction() {
        PaymentTransaction txn = createTestTransaction();

        assertThat(txn.canBeRefunded()).isFalse();
    }

    @Test
    public void shouldCalculateTotalRefunded() {
        Refund refund1 = new Refund(
            "ref_1",
            "txn_123",
            new Money(new BigDecimal("30.00"), Currency.USD),
            "Partial refund",
            RefundStatus.SUCCEEDED,
            Instant.now(),
            Instant.now()
        );

        Refund refund2 = new Refund(
            "ref_2",
            "txn_123",
            new Money(new BigDecimal("20.00"), Currency.USD),
            "Another refund",
            RefundStatus.SUCCEEDED,
            Instant.now(),
            Instant.now()
        );

        PaymentTransaction txn = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED)
            .addRefund(refund1)
            .addRefund(refund2);

        Money totalRefunded = txn.getTotalRefunded();

        assertThat(totalRefunded.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    public void shouldCalculateRefundableAmount() {
        Refund refund = new Refund(
            "ref_1",
            "txn_123",
            new Money(new BigDecimal("30.00"), Currency.USD),
            "Partial refund",
            RefundStatus.SUCCEEDED,
            Instant.now(),
            Instant.now()
        );

        PaymentTransaction txn = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED)
            .addRefund(refund);

        Money refundable = txn.getRefundableAmount();

        assertThat(refundable.amount()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    public void shouldValidateRefundAmount() {
        PaymentTransaction txn = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED);

        Money validRefund = new Money(new BigDecimal("50.00"), Currency.USD);
        Money invalidRefund = new Money(new BigDecimal("150.00"), Currency.USD);

        assertThat(txn.canRefund(validRefund)).isTrue();
        assertThat(txn.canRefund(invalidRefund)).isFalse();
    }

    @Test
    public void shouldNotAllowZeroRefund() {
        PaymentTransaction txn = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED);

        Money zeroRefund = new Money(BigDecimal.ZERO, Currency.USD);

        assertThat(txn.canRefund(zeroRefund)).isFalse();
    }

    @Test
    public void shouldIdentifyPendingStatus() {
        PaymentTransaction pending = createTestTransaction();
        PaymentTransaction authorized = createTestTransaction()
            .withStatus(PaymentStatus.AUTHORIZED);
        PaymentTransaction succeeded = createTestTransaction()
            .withStatus(PaymentStatus.SUCCEEDED);

        assertThat(pending.isPending()).isTrue();
        assertThat(authorized.isPending()).isTrue();
        assertThat(succeeded.isPending()).isFalse();
    }

    @Test
    public void shouldUpdateStatus() {
        PaymentTransaction txn = createTestTransaction();

        PaymentTransaction updated = txn.withStatus(PaymentStatus.SUCCEEDED);

        assertThat(updated.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(txn.status()).isEqualTo(PaymentStatus.PENDING); // Original unchanged
    }

    @Test
    public void shouldAddGatewayTransactionId() {
        PaymentTransaction txn = createTestTransaction();

        PaymentTransaction updated = txn.withGatewayTransactionId("stripe_ch_123");

        assertThat(updated.gatewayTransactionId()).isEqualTo("stripe_ch_123");
    }
}
