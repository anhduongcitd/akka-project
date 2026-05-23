package com.example.payment.domain;

import java.time.Instant;

/**
 * Domain model for idempotency tracking.
 * Prevents duplicate payment processing when clients retry requests.
 */
public record IdempotencyRecord(
    String idempotencyKey,
    String transactionId,
    Instant createdAt,
    Instant expiresAt
) {
    
    /**
     * Check if this idempotency record has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * Create a new idempotency record with 24-hour TTL.
     */
    public static IdempotencyRecord create(String idempotencyKey, String transactionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(24 * 60 * 60); // 24 hours
        return new IdempotencyRecord(idempotencyKey, transactionId, now, expiry);
    }
}
