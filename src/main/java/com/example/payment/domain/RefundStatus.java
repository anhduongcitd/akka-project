package com.example.payment.domain;

/**
 * Refund transaction status lifecycle.
 * Aligned with FR-013: Support for full and partial refunds.
 */
public enum RefundStatus {
    PENDING,      // Refund initiated, waiting for gateway
    PROCESSING,   // Refund being processed by gateway
    SUCCEEDED,    // Refund completed successfully
    FAILED        // Refund failed (insufficient settled funds, etc.)
}
