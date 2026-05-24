package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Circuit breaker configuration and state.
 */
public record CircuitBreakerConfig(
    String agentId,
    CircuitBreakerState state,
    int failureThreshold,         // Failures before opening
    int successThreshold,         // Successes needed to close
    long timeoutMs,               // Half-open timeout
    int currentFailures,
    int currentSuccesses,
    Instant lastStateChange,
    Instant openedAt
) {

    /**
     * Circuit breaker states.
     */
    public enum CircuitBreakerState {
        CLOSED,      // Normal operation
        OPEN,        // Blocking requests
        HALF_OPEN    // Testing recovery
    }

    /**
     * Create new circuit breaker.
     */
    public static CircuitBreakerConfig create(String agentId, int failureThreshold,
                                               int successThreshold, long timeoutMs) {
        return new CircuitBreakerConfig(
            agentId,
            CircuitBreakerState.CLOSED,
            failureThreshold,
            successThreshold,
            timeoutMs,
            0,
            0,
            Instant.now(),
            null
        );
    }

    /**
     * Record successful call.
     */
    public CircuitBreakerConfig recordSuccess() {
        return switch (state) {
            case CLOSED -> new CircuitBreakerConfig(
                agentId, CircuitBreakerState.CLOSED, failureThreshold, successThreshold,
                timeoutMs, 0, 0, lastStateChange, openedAt
            );
            case HALF_OPEN -> {
                int newSuccesses = currentSuccesses + 1;
                if (newSuccesses >= successThreshold) {
                    // Transition to CLOSED
                    yield new CircuitBreakerConfig(
                        agentId, CircuitBreakerState.CLOSED, failureThreshold, successThreshold,
                        timeoutMs, 0, 0, Instant.now(), null
                    );
                } else {
                    yield new CircuitBreakerConfig(
                        agentId, CircuitBreakerState.HALF_OPEN, failureThreshold, successThreshold,
                        timeoutMs, 0, newSuccesses, lastStateChange, openedAt
                    );
                }
            }
            case OPEN -> this; // Ignore success in OPEN state
        };
    }

    /**
     * Record failed call.
     */
    public CircuitBreakerConfig recordFailure() {
        return switch (state) {
            case CLOSED -> {
                int newFailures = currentFailures + 1;
                if (newFailures >= failureThreshold) {
                    // Transition to OPEN
                    yield new CircuitBreakerConfig(
                        agentId, CircuitBreakerState.OPEN, failureThreshold, successThreshold,
                        timeoutMs, newFailures, 0, Instant.now(), Instant.now()
                    );
                } else {
                    yield new CircuitBreakerConfig(
                        agentId, CircuitBreakerState.CLOSED, failureThreshold, successThreshold,
                        timeoutMs, newFailures, 0, lastStateChange, openedAt
                    );
                }
            }
            case HALF_OPEN -> {
                // Single failure returns to OPEN
                yield new CircuitBreakerConfig(
                    agentId, CircuitBreakerState.OPEN, failureThreshold, successThreshold,
                    timeoutMs, currentFailures + 1, 0, Instant.now(), Instant.now()
                );
            }
            case OPEN -> this; // Already open
        };
    }

    /**
     * Check if circuit breaker allows request.
     */
    public boolean allowsRequest() {
        return switch (state) {
            case CLOSED -> true;
            case HALF_OPEN -> true;
            case OPEN -> {
                // Check if timeout has elapsed
                if (openedAt != null && Instant.now().isAfter(openedAt.plusMillis(timeoutMs))) {
                    yield true; // Allow transition to HALF_OPEN
                }
                yield false;
            }
        };
    }

    /**
     * Attempt transition to half-open if timeout elapsed.
     */
    public CircuitBreakerConfig tryHalfOpen() {
        if (state == CircuitBreakerState.OPEN &&
            openedAt != null &&
            Instant.now().isAfter(openedAt.plusMillis(timeoutMs))) {
            return new CircuitBreakerConfig(
                agentId, CircuitBreakerState.HALF_OPEN, failureThreshold, successThreshold,
                timeoutMs, currentFailures, 0, Instant.now(), openedAt
            );
        }
        return this;
    }

    /**
     * Manually reset circuit breaker to closed.
     */
    public CircuitBreakerConfig reset() {
        return new CircuitBreakerConfig(
            agentId, CircuitBreakerState.CLOSED, failureThreshold, successThreshold,
            timeoutMs, 0, 0, Instant.now(), null
        );
    }

    /**
     * Update configuration thresholds.
     */
    public CircuitBreakerConfig updateConfig(int newFailureThreshold,
                                              int newSuccessThreshold,
                                              long newTimeoutMs) {
        return new CircuitBreakerConfig(
            agentId, state, newFailureThreshold, newSuccessThreshold,
            newTimeoutMs, currentFailures, currentSuccesses, lastStateChange, openedAt
        );
    }

    /**
     * Check if circuit breaker is open.
     */
    public boolean isOpen() {
        return state == CircuitBreakerState.OPEN;
    }

    /**
     * Check if circuit breaker is half-open.
     */
    public boolean isHalfOpen() {
        return state == CircuitBreakerState.HALF_OPEN;
    }

    /**
     * Check if circuit breaker is closed.
     */
    public boolean isClosed() {
        return state == CircuitBreakerState.CLOSED;
    }
}
