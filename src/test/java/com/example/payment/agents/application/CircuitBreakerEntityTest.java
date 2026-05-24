package com.example.payment.agents.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerEntityTest {

    @Test
    public void shouldCreateCircuitBreaker() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        var result = testKit
            .method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
    }

    @Test
    public void shouldRecordSuccess() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        var result = testKit.method(CircuitBreakerEntity::recordSuccess).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
    }

    @Test
    public void shouldOpenCircuitAfterFailures() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        testKit.method(CircuitBreakerEntity::recordFailure).invoke();
        testKit.method(CircuitBreakerEntity::recordFailure).invoke();
        var result = testKit.method(CircuitBreakerEntity::recordFailure).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.OPEN);
    }

    @Test
    public void shouldAllowRequestInClosedState() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        var result = testKit.method(CircuitBreakerEntity::allowsRequest).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isTrue();
    }

    @Test
    public void shouldBlockRequestInOpenState() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 2, 2, 60000));

        testKit.method(CircuitBreakerEntity::recordFailure).invoke();
        testKit.method(CircuitBreakerEntity::recordFailure).invoke();

        var result = testKit.method(CircuitBreakerEntity::allowsRequest).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isFalse();
    }

    @Test
    public void shouldResetCircuitBreaker() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 2, 2, 5000));

        testKit.method(CircuitBreakerEntity::recordFailure).invoke();
        testKit.method(CircuitBreakerEntity::recordFailure).invoke();

        var result = testKit.method(CircuitBreakerEntity::reset).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().state()).isEqualTo(CircuitBreakerConfig.CircuitBreakerState.CLOSED);
    }

    @Test
    public void shouldUpdateConfiguration() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        var result = testKit
            .method(CircuitBreakerEntity::updateConfig)
            .invoke(new CircuitBreakerEntity.UpdateConfig(5, 3, 10000));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().failureThreshold()).isEqualTo(5);
        assertThat(testKit.getState().successThreshold()).isEqualTo(3);
        assertThat(testKit.getState().timeoutMs()).isEqualTo(10000);
    }

    @Test
    public void shouldGetCircuitBreakerState() {
        var testKit = KeyValueEntityTestKit.of("agent-1", CircuitBreakerEntity::new);

        testKit.method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker("agent-1", 3, 2, 5000));

        var result = testKit.method(CircuitBreakerEntity::getState).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isNotNull();
    }
}
