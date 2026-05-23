package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.payment.domain.RateLimitRecord;

/**
 * Rate limit tracking entity.
 * Tracks request counts per IP address or customer ID within time windows.
 */
@Component(id = "rate-limit")
public class RateLimitEntity extends KeyValueEntity<RateLimitRecord> {

    private final String limitId;

    public RateLimitEntity(KeyValueEntityContext context) {
        this.limitId = context.entityId();
    }

    @Override
    public RateLimitRecord emptyState() {
        return null;
    }

    /**
     * Check and record request.
     * Returns "ALLOWED" if within limit, "EXCEEDED" if rate limit exceeded.
     */
    public record CheckRateLimitRequest(
        String identifier,
        RateLimitRecord.RateLimitType type,
        int maxRequests,
        int windowMinutes
    ) {}

    public Effect<String> checkAndRecord(CheckRateLimitRequest request) {
        RateLimitRecord current = currentState();

        // First request or no existing record
        if (current == null) {
            RateLimitRecord newRecord = RateLimitRecord.create(request.identifier, request.type);
            return effects()
                .updateState(newRecord)
                .thenReply("ALLOWED");
        }

        // Window expired - reset
        if (current.isWindowExpired(request.windowMinutes)) {
            RateLimitRecord resetRecord = current.resetWindow();
            return effects()
                .updateState(resetRecord)
                .thenReply("ALLOWED");
        }

        // Within window - check limit
        if (current.isLimitExceeded(request.maxRequests)) {
            // Do not increment - already exceeded
            return effects().reply("EXCEEDED");
        }

        // Within limit - increment count
        RateLimitRecord incremented = current.incrementCount();
        return effects()
            .updateState(incremented)
            .thenReply("ALLOWED");
    }

    /**
     * Get current request count.
     */
    public Effect<Integer> getCurrentCount() {
        RateLimitRecord current = currentState();
        if (current == null) {
            return effects().reply(0);
        }
        return effects().reply(current.requestCount());
    }

    /**
     * Reset rate limit (for testing or manual override).
     */
    public Effect<String> reset() {
        return effects()
            .deleteEntity()
            .thenReply("RESET");
    }
}
