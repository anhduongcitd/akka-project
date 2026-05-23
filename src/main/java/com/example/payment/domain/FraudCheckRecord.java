package com.example.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fraud detection tracking record.
 * Tracks payment patterns to detect suspicious activity.
 */
public record FraudCheckRecord(
    String customerId,
    List<PaymentAttempt> recentPayments,
    Instant windowStart
) {

    public record PaymentAttempt(
        String merchantReference,
        BigDecimal amount,
        String currency,
        Instant timestamp
    ) {}

    /**
     * Check if velocity limit exceeded (too many payments in short time).
     */
    public boolean isVelocityExceeded(int maxPayments, int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        long recentCount = recentPayments.stream()
            .filter(p -> p.timestamp.isAfter(cutoff))
            .count();
        return recentCount >= maxPayments;
    }

    /**
     * Check if high value threshold exceeded.
     */
    public boolean isHighValueExceeded(BigDecimal maxAmount, int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        BigDecimal total = recentPayments.stream()
            .filter(p -> p.timestamp.isAfter(cutoff))
            .map(PaymentAttempt::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.compareTo(maxAmount) > 0;
    }

    /**
     * Check if adding new payment would exceed high value threshold.
     */
    public boolean wouldExceedHighValue(BigDecimal newAmount, BigDecimal maxAmount, int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        BigDecimal currentTotal = recentPayments.stream()
            .filter(p -> p.timestamp.isAfter(cutoff))
            .map(PaymentAttempt::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWithNew = currentTotal.add(newAmount);
        return totalWithNew.compareTo(maxAmount) > 0;
    }

    /**
     * Check for duplicate transaction (same amount + merchant ref).
     */
    public boolean isDuplicate(String merchantReference, BigDecimal amount, int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        return recentPayments.stream()
            .filter(p -> p.timestamp.isAfter(cutoff))
            .anyMatch(p -> p.merchantReference.equals(merchantReference)
                        && p.amount.compareTo(amount) == 0);
    }

    /**
     * Add new payment attempt.
     */
    public FraudCheckRecord addPayment(String merchantReference, BigDecimal amount, String currency) {
        List<PaymentAttempt> updated = new ArrayList<>(recentPayments);
        updated.add(new PaymentAttempt(merchantReference, amount, currency, Instant.now()));

        // Keep only last 100 payments to prevent memory issues
        if (updated.size() > 100) {
            updated = updated.subList(updated.size() - 100, updated.size());
        }

        return new FraudCheckRecord(customerId, updated, windowStart);
    }

    /**
     * Clean up old payments outside all windows.
     */
    public FraudCheckRecord cleanup(int maxWindowMinutes) {
        Instant cutoff = Instant.now().minusSeconds(maxWindowMinutes * 60L);
        List<PaymentAttempt> filtered = recentPayments.stream()
            .filter(p -> p.timestamp.isAfter(cutoff))
            .toList();

        return new FraudCheckRecord(customerId, filtered, windowStart);
    }

    /**
     * Create new fraud check record.
     */
    public static FraudCheckRecord create(String customerId) {
        return new FraudCheckRecord(customerId, new ArrayList<>(), Instant.now());
    }
}
