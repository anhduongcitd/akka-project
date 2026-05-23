package com.example.payment.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

/**
 * Payment transaction events for Event Sourcing.
 * Maintains complete audit trail (FR-014).
 */
public sealed interface PaymentTransactionEvent {

    @TypeName("payment-initiated")
    record PaymentInitiated(
        Customer customer,
        Money amount,
        String merchantReference,
        Instant timestamp
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-authorized")
    record PaymentAuthorized(
        String gatewayTransactionId,
        Instant timestamp
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-captured")
    record PaymentCaptured(
        Instant timestamp
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-succeeded")
    record PaymentSucceeded(
        Instant timestamp
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-failed")
    record PaymentFailed(
        String reason,
        Instant timestamp
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-refund-initiated")
    record RefundInitiated(
        String refundId,
        Money refundAmount,
        String reason
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-refund-completed")
    record RefundCompleted(
        String refundId,
        String gatewayRefundId
    ) implements PaymentTransactionEvent {}

    @TypeName("payment-refund-failed")
    record RefundFailed(
        String refundId,
        String reason
    ) implements PaymentTransactionEvent {}
}
