package com.example.payment.agents.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerConfigTest {

    @Test
    public void shouldCreateCircuitBreaker() {
        var cb = CircuitBreakerConfig.create("agent-1", 5, 3, 60000);

        assertThat(cb.agentId()).isEqualTo("agent-1");
        assertThat(cb.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
        assertThat(cb.failureThreshold()).isEqualTo(5);
        assertThat(cb.successThreshold()).isEqualTo(3);
        assertThat(cb.timeoutMs()).isEqualTo(60000);
        assertThat(cb.currentFailures()).isEqualTo(0);
    }

    @Test
    public void shouldRecordSuccessInClosedState() {
        var cb = CircuitBreakerConfig.create("agent-1", 3, 2, 5000)
            .recordFailure()
            .recordFailure();

        var updated = cb.recordSuccess();

        assertThat(updated.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
        assertThat(updated.currentFailures()).isEqualTo(0);
    }

    @Test
    public void shouldOpenAfterThresholdFailures() {
        var cb = CircuitBreakerConfig.create("agent-1", 3, 2, 5000);

        var updated = cb
            .recordFailure()
            .recordFailure()
            .recordFailure();

        assertThat(updated.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.OPEN);
        assertThat(updated.currentFailures()).isEqualTo(3);
        assertThat(updated.openedAt()).isNotNull();
    }

    @Test
    public void shouldTransitionToHalfOpenAfterTimeout() throws InterruptedException {
        var cb = CircuitBreakerConfig.create("agent-1", 2, 2, 100)
            .recordFailure()
            .recordFailure();

        assertThat(cb.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.OPEN);

        Thread.sleep(150);

        var halfOpen = cb.tryHalfOpen();
        assertThat(halfOpen.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.HALF_OPEN);
    }

    @Test
    public void shouldCloseFromHalfOpenAfterSuccesses() {
        var cb = CircuitBreakerConfig.create("agent-1", 2, 2, 5000)
            .recordFailure()
            .recordFailure()
            .tryHalfOpen();

        assertThat(cb.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.HALF_OPEN);

        var updated = cb.recordSuccess().recordSuccess();

        assertThat(updated.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
        assertThat(updated.currentFailures()).isEqualTo(0);
    }

    @Test
    public void shouldReopenFromHalfOpenOnFailure() {
        var cb = CircuitBreakerConfig.create("agent-1", 2, 2, 5000)
            .recordFailure()
            .recordFailure()
            .tryHalfOpen();

        var updated = cb.recordFailure();

        assertThat(updated.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.OPEN);
    }

    @Test
    public void shouldAllowRequestInClosedState() {
        var cb = CircuitBreakerConfig.create("agent-1", 3, 2, 5000);

        assertThat(cb.allowsRequest()).isTrue();
    }

    @Test
    public void shouldBlockRequestInOpenState() {
        var cb = CircuitBreakerConfig.create("agent-1", 2, 2, 60000)
            .recordFailure()
            .recordFailure();

        assertThat(cb.allowsRequest()).isFalse();
    }

    @Test
    public void shouldManuallyReset() {
        var cb = CircuitBreakerConfig.create("agent-1", 2, 2, 5000)
            .recordFailure()
            .recordFailure();

        var reset = cb.reset();

        assertThat(reset.state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
        assertThat(reset.currentFailures()).isEqualTo(0);
        assertThat(reset.openedAt()).isNull();
    }
}
