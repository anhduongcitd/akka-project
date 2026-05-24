package com.example.payment.agents.domain;

import java.time.Duration;

/**
 * Retry configuration for agent calls.
 */
public record RetryConfig(
    String agentId,
    int maxAttempts,
    RetryStrategy strategy,
    long initialDelayMs,
    long maxDelayMs,
    double backoffMultiplier,
    boolean enabled
) {

    /**
     * Retry strategies.
     */
    public enum RetryStrategy {
        FIXED,              // Fixed delay
        EXPONENTIAL,        // Exponential backoff
        LINEAR              // Linear backoff
    }

    /**
     * Create exponential backoff retry config.
     */
    public static RetryConfig exponentialBackoff(String agentId, int maxAttempts,
                                                   long initialDelayMs, long maxDelayMs) {
        return new RetryConfig(
            agentId,
            maxAttempts,
            RetryStrategy.EXPONENTIAL,
            initialDelayMs,
            maxDelayMs,
            2.0,  // Double delay each time
            true
        );
    }

    /**
     * Create fixed delay retry config.
     */
    public static RetryConfig fixedDelay(String agentId, int maxAttempts, long delayMs) {
        return new RetryConfig(
            agentId,
            maxAttempts,
            RetryStrategy.FIXED,
            delayMs,
            delayMs,
            1.0,
            true
        );
    }

    /**
     * Create linear backoff retry config.
     */
    public static RetryConfig linearBackoff(String agentId, int maxAttempts,
                                             long initialDelayMs, long incrementMs) {
        return new RetryConfig(
            agentId,
            maxAttempts,
            RetryStrategy.LINEAR,
            initialDelayMs,
            Long.MAX_VALUE,
            incrementMs,  // Used as increment in linear strategy
            true
        );
    }

    /**
     * Calculate delay for given attempt.
     */
    public long calculateDelay(int attemptNumber) {
        if (!enabled || attemptNumber <= 0) {
            return 0;
        }

        long delay = switch (strategy) {
            case FIXED -> initialDelayMs;
            case EXPONENTIAL -> {
                long exponentialDelay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1));
                yield Math.min(exponentialDelay, maxDelayMs);
            }
            case LINEAR -> {
                long linearDelay = initialDelayMs + (long) (backoffMultiplier * (attemptNumber - 1));
                yield Math.min(linearDelay, maxDelayMs);
            }
        };

        return Math.min(delay, maxDelayMs);
    }

    /**
     * Get delay as Duration.
     */
    public Duration calculateDelayDuration(int attemptNumber) {
        return Duration.ofMillis(calculateDelay(attemptNumber));
    }

    /**
     * Check if retry should be attempted.
     */
    public boolean shouldRetry(int attemptNumber) {
        return enabled && attemptNumber < maxAttempts;
    }

    /**
     * Enable retry.
     */
    public RetryConfig enable() {
        return new RetryConfig(
            agentId, maxAttempts, strategy, initialDelayMs, maxDelayMs, backoffMultiplier, true
        );
    }

    /**
     * Disable retry.
     */
    public RetryConfig disable() {
        return new RetryConfig(
            agentId, maxAttempts, strategy, initialDelayMs, maxDelayMs, backoffMultiplier, false
        );
    }

    /**
     * Update max attempts.
     */
    public RetryConfig withMaxAttempts(int newMaxAttempts) {
        return new RetryConfig(
            agentId, newMaxAttempts, strategy, initialDelayMs, maxDelayMs, backoffMultiplier, enabled
        );
    }

    /**
     * Get total max delay across all attempts.
     */
    public long getTotalMaxDelay() {
        if (!enabled) {
            return 0;
        }

        long total = 0;
        for (int i = 1; i <= maxAttempts; i++) {
            total += calculateDelay(i);
        }
        return total;
    }
}
