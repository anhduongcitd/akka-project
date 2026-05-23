package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.payment.domain.FraudCheckRecord;

import java.math.BigDecimal;

/**
 * Fraud detection entity.
 * Tracks payment patterns per customer to detect suspicious activity.
 */
@Component(id = "fraud-check")
public class FraudCheckEntity extends KeyValueEntity<FraudCheckRecord> {

    private final String customerId;

    public FraudCheckEntity(KeyValueEntityContext context) {
        this.customerId = context.entityId();
    }

    @Override
    public FraudCheckRecord emptyState() {
        return null;
    }

    /**
     * Check for fraud patterns before processing payment.
     * Returns fraud check result.
     */
    public record CheckFraudRequest(
        String merchantReference,
        BigDecimal amount,
        String currency,
        int velocityLimit,
        int velocityWindowMinutes,
        BigDecimal highValueLimit,
        int highValueWindowMinutes,
        int duplicateWindowMinutes
    ) {}

    public record FraudCheckResult(
        boolean passed,
        String reason  // null if passed, otherwise fraud reason
    ) {}

    public Effect<FraudCheckResult> checkAndRecord(CheckFraudRequest request) {
        FraudCheckRecord current = currentState();

        // First payment - always allow
        if (current == null) {
            FraudCheckRecord newRecord = FraudCheckRecord.create(customerId)
                .addPayment(request.merchantReference, request.amount, request.currency);
            return effects()
                .updateState(newRecord)
                .thenReply(new FraudCheckResult(true, null));
        }

        // Clean up old data
        current = current.cleanup(Math.max(
            request.velocityWindowMinutes,
            Math.max(request.highValueWindowMinutes, request.duplicateWindowMinutes)
        ));

        // Check velocity
        if (current.isVelocityExceeded(request.velocityLimit, request.velocityWindowMinutes)) {
            // Don't update state - reject before recording
            return effects().reply(new FraudCheckResult(
                false,
                "VELOCITY_EXCEEDED: Too many payments in short time"
            ));
        }

        // Check high value (including new payment amount)
        if (current.wouldExceedHighValue(request.amount, request.highValueLimit, request.highValueWindowMinutes)) {
            return effects().reply(new FraudCheckResult(
                false,
                "HIGH_VALUE: Payment amount threshold exceeded"
            ));
        }

        // Check duplicate
        if (current.isDuplicate(request.merchantReference, request.amount, request.duplicateWindowMinutes)) {
            return effects().reply(new FraudCheckResult(
                false,
                "DUPLICATE_TRANSACTION: Similar transaction detected recently"
            ));
        }

        // All checks passed - record payment
        FraudCheckRecord updated = current.addPayment(
            request.merchantReference,
            request.amount,
            request.currency
        );

        return effects()
            .updateState(updated)
            .thenReply(new FraudCheckResult(true, null));
    }

    /**
     * Get recent payment count.
     */
    public Effect<Integer> getRecentPaymentCount(int windowMinutes) {
        FraudCheckRecord current = currentState();
        if (current == null) {
            return effects().reply(0);
        }

        var cutoff = java.time.Instant.now().minusSeconds(windowMinutes * 60L);
        long count = current.recentPayments().stream()
            .filter(p -> p.timestamp().isAfter(cutoff))
            .count();

        return effects().reply((int) count);
    }

    /**
     * Get total recent payment amount.
     */
    public Effect<String> getRecentPaymentTotal(int windowMinutes) {
        FraudCheckRecord current = currentState();
        if (current == null) {
            return effects().reply("0.00");
        }

        var cutoff = java.time.Instant.now().minusSeconds(windowMinutes * 60L);
        BigDecimal total = current.recentPayments().stream()
            .filter(p -> p.timestamp().isAfter(cutoff))
            .map(FraudCheckRecord.PaymentAttempt::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return effects().reply(total.toString());
    }

    /**
     * Reset fraud check (for testing).
     */
    public Effect<String> reset() {
        return effects()
            .deleteEntity()
            .thenReply("RESET");
    }
}
