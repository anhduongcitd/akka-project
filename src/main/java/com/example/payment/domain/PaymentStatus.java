package com.example.payment.domain;

/**
 * Payment transaction status lifecycle.
 * Aligned with FR-008: Real-time payment status tracking.
 */
public enum PaymentStatus {
    PENDING,      // Payment initiated, waiting for gateway response
    AUTHORIZED,   // Card authorized, not yet captured
    SUCCEEDED,    // Payment completed successfully
    FAILED,       // Payment failed (declined, expired, etc.)
    REFUNDED      // Payment fully or partially refunded
}
