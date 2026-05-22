package com.example.payment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment transaction state record with business logic.
 * Aligned with FR-006: Generate unique transaction IDs.
 * Aligned with FR-012: Prevent duplicate payments within 60 seconds.
 * Aligned with FR-014: Maintain complete audit trail.
 */
public record PaymentTransaction(
    String transactionId,
    Customer customer,
    Money amount,
    PaymentStatus status,
    String merchantReference,
    String gatewayTransactionId,  // Stripe charge/payment intent ID
    List<Refund> refunds,
    Instant createdAt,
    Instant completedAt,
    String failureReason
) {
    public PaymentTransaction {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (customer == null) {
            throw new IllegalArgumentException("Customer cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
        if (refunds == null) {
            refunds = new ArrayList<>();
        }
    }

    /**
     * Validate if this transaction can be refunded.
     */
    public boolean canBeRefunded() {
        return status == PaymentStatus.SUCCEEDED;
    }

    /**
     * Calculate total amount already refunded.
     */
    public Money getTotalRefunded() {
        if (refunds.isEmpty()) {
            return new Money(java.math.BigDecimal.ZERO, amount.currency());
        }

        Money total = new Money(java.math.BigDecimal.ZERO, amount.currency());
        for (Refund refund : refunds) {
            if (refund.status() == RefundStatus.SUCCEEDED) {
                total = total.add(refund.amount());
            }
        }
        return total;
    }

    /**
     * Calculate remaining amount that can be refunded.
     */
    public Money getRefundableAmount() {
        return amount.subtract(getTotalRefunded());
    }

    /**
     * Validate if a refund amount is allowed.
     */
    public boolean canRefund(Money refundAmount) {
        if (!canBeRefunded()) {
            return false;
        }
        if (refundAmount == null || refundAmount.isZero()) {
            return false;
        }
        Money refundable = getRefundableAmount();
        return refundAmount.isLessThanOrEqual(refundable);
    }

    /**
     * Check if payment is still pending (not completed/failed).
     */
    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.AUTHORIZED;
    }

    /**
     * Check if payment succeeded.
     */
    public boolean isSucceeded() {
        return status == PaymentStatus.SUCCEEDED;
    }

    /**
     * Check if payment failed.
     */
    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public PaymentTransaction withStatus(PaymentStatus newStatus) {
        return new PaymentTransaction(
            transactionId, customer, amount, newStatus, merchantReference,
            gatewayTransactionId, refunds, createdAt, completedAt, failureReason
        );
    }

    public PaymentTransaction withGatewayTransactionId(String gatewayId) {
        return new PaymentTransaction(
            transactionId, customer, amount, status, merchantReference,
            gatewayId, refunds, createdAt, completedAt, failureReason
        );
    }

    public PaymentTransaction withCompletedAt(Instant timestamp) {
        return new PaymentTransaction(
            transactionId, customer, amount, status, merchantReference,
            gatewayTransactionId, refunds, createdAt, timestamp, failureReason
        );
    }

    public PaymentTransaction withFailureReason(String reason) {
        return new PaymentTransaction(
            transactionId, customer, amount, status, merchantReference,
            gatewayTransactionId, refunds, createdAt, completedAt, reason
        );
    }

    public PaymentTransaction addRefund(Refund refund) {
        List<Refund> updated = new ArrayList<>(refunds);
        updated.add(refund);
        return new PaymentTransaction(
            transactionId, customer, amount, status, merchantReference,
            gatewayTransactionId, updated, createdAt, completedAt, failureReason
        );
    }
}
