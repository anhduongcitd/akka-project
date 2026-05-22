package com.example.payment.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Saved payment method record.
 * Aligned with FR-010: Allow users to save payment methods with PCI-compliant tokenization.
 * Aligned with FR-005: Never store raw card numbers or CVV codes.
 */
public record PaymentMethod(
    String paymentMethodId,
    String customerId,
    String token,              // PCI-compliant token from Stripe
    CardBrand brand,
    String last4Digits,
    YearMonth expirationDate,
    boolean isDefault,
    Instant createdAt
) {
    public PaymentMethod {
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new IllegalArgumentException("Payment method ID cannot be null or blank");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }
        if (brand == null) {
            throw new IllegalArgumentException("Card brand cannot be null");
        }
        if (last4Digits == null || !last4Digits.matches("\\d{4}")) {
            throw new IllegalArgumentException("Last 4 digits must be exactly 4 digits");
        }
        if (expirationDate == null) {
            throw new IllegalArgumentException("Expiration date cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
    }

    /**
     * Check if the card is expired.
     */
    public boolean isExpired() {
        return YearMonth.now().isAfter(expirationDate);
    }

    /**
     * Check if the card is expiring soon (within 30 days).
     */
    public boolean isExpiringSoon() {
        YearMonth now = YearMonth.now();
        long monthsUntilExpiry = now.until(expirationDate, ChronoUnit.MONTHS);
        return monthsUntilExpiry <= 1 && monthsUntilExpiry >= 0;
    }

    public PaymentMethod withIsDefault(boolean isDefault) {
        return new PaymentMethod(
            paymentMethodId, customerId, token, brand,
            last4Digits, expirationDate, isDefault, createdAt
        );
    }

    /**
     * Get masked card number for display (e.g., "**** 4242").
     */
    public String getMaskedNumber() {
        return "**** " + last4Digits;
    }
}
