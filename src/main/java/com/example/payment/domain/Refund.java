package com.example.payment.domain;

import java.time.Instant;

/**
 * Refund record for tracking refund transactions.
 * Aligned with FR-013: Full and partial refund support.
 */
public record Refund(
    String refundId,
    String transactionId,
    Money amount,
    String reason,
    RefundStatus status,
    Instant createdAt,
    Instant completedAt
) {
    public Refund {
        if (refundId == null || refundId.isBlank()) {
            throw new IllegalArgumentException("Refund ID cannot be null or blank");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Refund amount cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Refund status cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
    }

    public Refund withStatus(RefundStatus newStatus) {
        return new Refund(refundId, transactionId, amount, reason, newStatus, createdAt, completedAt);
    }

    public Refund withCompletedAt(Instant timestamp) {
        return new Refund(refundId, transactionId, amount, reason, status, createdAt, timestamp);
    }
}
