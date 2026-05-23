package com.example.payment.domain;

import java.time.Instant;

/**
 * Rate limit tracking record.
 * Tracks request counts within a time window.
 */
public record RateLimitRecord(
    String identifier,      // IP address or customer ID
    int requestCount,       // Number of requests in current window
    Instant windowStart,    // Start of current time window
    Instant lastRequest,    // Timestamp of last request
    RateLimitType type      // IP or CUSTOMER
) {

    public enum RateLimitType {
        IP,
        CUSTOMER
    }

    /**
     * Check if the current window has expired.
     */
    public boolean isWindowExpired(int windowMinutes) {
        Instant now = Instant.now();
        Instant windowEnd = windowStart.plusSeconds(windowMinutes * 60L);
        return now.isAfter(windowEnd);
    }

    /**
     * Check if rate limit is exceeded.
     */
    public boolean isLimitExceeded(int maxRequests) {
        return requestCount >= maxRequests;
    }

    /**
     * Increment request count.
     */
    public RateLimitRecord incrementCount() {
        return new RateLimitRecord(
            identifier,
            requestCount + 1,
            windowStart,
            Instant.now(),
            type
        );
    }

    /**
     * Reset window with new start time.
     */
    public RateLimitRecord resetWindow() {
        return new RateLimitRecord(
            identifier,
            1,  // Start with count of 1 for current request
            Instant.now(),
            Instant.now(),
            type
        );
    }

    /**
     * Create new rate limit record.
     */
    public static RateLimitRecord create(String identifier, RateLimitType type) {
        Instant now = Instant.now();
        return new RateLimitRecord(
            identifier,
            1,
            now,
            now,
            type
        );
    }
}
