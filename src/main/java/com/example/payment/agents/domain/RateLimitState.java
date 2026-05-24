package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Rate limit state tracking.
 */
public record RateLimitState(
    String agentId,
    int currentMinuteRequests,
    int currentHourRequests,
    int currentDayRequests,
    Instant windowStart,
    Instant lastRequestAt
) {

    /**
     * Create initial state.
     */
    public static RateLimitState create(String agentId) {
        Instant now = Instant.now();
        return new RateLimitState(agentId, 0, 0, 0, now, now);
    }

    /**
     * Record new request.
     */
    public RateLimitState recordRequest() {
        Instant now = Instant.now();

        // Reset windows if expired
        int minuteRequests = shouldResetMinute(now) ? 1 : currentMinuteRequests + 1;
        int hourRequests = shouldResetHour(now) ? 1 : currentHourRequests + 1;
        int dayRequests = shouldResetDay(now) ? 1 : currentDayRequests + 1;

        Instant newWindowStart = shouldResetMinute(now) ? now : windowStart;

        return new RateLimitState(
            agentId,
            minuteRequests,
            hourRequests,
            dayRequests,
            newWindowStart,
            now
        );
    }

    /**
     * Check if allowed based on config.
     */
    public boolean isAllowed(RateLimitConfig config) {
        if (!config.enabled()) {
            return true;
        }

        Instant now = Instant.now();

        // Check minute limit
        if (!shouldResetMinute(now) && currentMinuteRequests >= config.requestsPerMinute()) {
            return false;
        }

        // Check hour limit
        if (!shouldResetHour(now) && currentHourRequests >= config.requestsPerHour()) {
            return false;
        }

        // Check day limit
        if (!shouldResetDay(now) && currentDayRequests >= config.requestsPerDay()) {
            return false;
        }

        return true;
    }

    /**
     * Reset state.
     */
    public RateLimitState reset() {
        Instant now = Instant.now();
        return new RateLimitState(agentId, 0, 0, 0, now, now);
    }

    private boolean shouldResetMinute(Instant now) {
        return now.getEpochSecond() - windowStart.getEpochSecond() >= 60;
    }

    private boolean shouldResetHour(Instant now) {
        return now.getEpochSecond() - windowStart.getEpochSecond() >= 3600;
    }

    private boolean shouldResetDay(Instant now) {
        return now.getEpochSecond() - windowStart.getEpochSecond() >= 86400;
    }
}
