package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.CircuitBreakerConfig;

/**
 * Key-Value Entity for circuit breaker management.
 */
@Component(id = "circuit-breaker")
public class CircuitBreakerEntity extends KeyValueEntity<CircuitBreakerConfig> {

    /**
     * Create circuit breaker.
     */
    public Effect<Done> createCircuitBreaker(CreateCircuitBreaker command) {
        if (currentState() != null) {
            return effects().error("Circuit breaker already exists");
        }

        var config = CircuitBreakerConfig.create(
            command.agentId(),
            command.failureThreshold(),
            command.successThreshold(),
            command.timeoutMs()
        );

        return effects()
            .updateState(config)
            .thenReply(Done.getInstance());
    }

    /**
     * Record successful call.
     */
    public Effect<CircuitBreakerConfig> recordSuccess() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        var updated = currentState().recordSuccess();

        return effects()
            .updateState(updated)
            .thenReply(updated);
    }

    /**
     * Record failed call.
     */
    public Effect<CircuitBreakerConfig> recordFailure() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        var updated = currentState().recordFailure();

        return effects()
            .updateState(updated)
            .thenReply(updated);
    }

    /**
     * Check if request is allowed.
     */
    public Effect<RequestAllowed> allowsRequest() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        // Try transition to half-open if timeout elapsed
        var config = currentState().tryHalfOpen();

        if (config != currentState()) {
            // State changed to HALF_OPEN
            return effects()
                .updateState(config)
                .thenReply(new RequestAllowed(
                    config.allowsRequest(),
                    config.state().toString(),
                    "Transitioned to HALF_OPEN"
                ));
        }

        return effects().reply(new RequestAllowed(
            config.allowsRequest(),
            config.state().toString(),
            config.isOpen() ? "Circuit breaker is OPEN" : "OK"
        ));
    }

    /**
     * Manually reset circuit breaker.
     */
    public Effect<Done> reset() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        var reset = currentState().reset();

        return effects()
            .updateState(reset)
            .thenReply(Done.getInstance());
    }

    /**
     * Update configuration.
     */
    public Effect<Done> updateConfig(UpdateConfig command) {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        var updated = currentState().updateConfig(
            command.failureThreshold(),
            command.successThreshold(),
            command.timeoutMs()
        );

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get circuit breaker state.
     */
    public Effect<CircuitBreakerConfig> getState() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete circuit breaker.
     */
    public Effect<Done> deleteCircuitBreaker() {
        if (currentState() == null) {
            return effects().error("Circuit breaker does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateCircuitBreaker(
        String agentId,
        int failureThreshold,
        int successThreshold,
        long timeoutMs
    ) {}

    public record UpdateConfig(
        int failureThreshold,
        int successThreshold,
        long timeoutMs
    ) {}

    public record RequestAllowed(
        boolean allowed,
        String state,
        String message
    ) {}
}
