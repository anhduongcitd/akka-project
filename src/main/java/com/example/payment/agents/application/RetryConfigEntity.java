package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.RetryConfig;

/**
 * Key-Value Entity for retry configuration management.
 */
@Component(id = "retry-config")
public class RetryConfigEntity extends KeyValueEntity<RetryConfig> {

    /**
     * Create retry configuration.
     */
    public Effect<Done> createConfig(CreateConfig command) {
        if (currentState() != null) {
            return effects().error("Retry config already exists");
        }

        var config = switch (command.strategy()) {
            case EXPONENTIAL -> RetryConfig.exponentialBackoff(
                command.agentId(),
                command.maxAttempts(),
                command.initialDelayMs(),
                command.maxDelayMs()
            );
            case FIXED -> RetryConfig.fixedDelay(
                command.agentId(),
                command.maxAttempts(),
                command.initialDelayMs()
            );
            case LINEAR -> RetryConfig.linearBackoff(
                command.agentId(),
                command.maxAttempts(),
                command.initialDelayMs(),
                (long) command.backoffMultiplier()
            );
        };

        return effects()
            .updateState(config)
            .thenReply(Done.getInstance());
    }

    /**
     * Calculate retry delay.
     */
    public Effect<RetryDelay> calculateDelay(int attemptNumber) {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        long delayMs = currentState().calculateDelay(attemptNumber);
        boolean shouldRetry = currentState().shouldRetry(attemptNumber);

        return effects().reply(new RetryDelay(
            attemptNumber,
            delayMs,
            shouldRetry,
            currentState().maxAttempts()
        ));
    }

    /**
     * Enable retry.
     */
    public Effect<Done> enable() {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        var enabled = currentState().enable();

        return effects()
            .updateState(enabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Disable retry.
     */
    public Effect<Done> disable() {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        var disabled = currentState().disable();

        return effects()
            .updateState(disabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Update max attempts.
     */
    public Effect<Done> updateMaxAttempts(int maxAttempts) {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        var updated = currentState().withMaxAttempts(maxAttempts);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get retry configuration.
     */
    public Effect<RetryConfig> getConfig() {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete retry configuration.
     */
    public Effect<Done> deleteConfig() {
        if (currentState() == null) {
            return effects().error("Retry config does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateConfig(
        String agentId,
        int maxAttempts,
        RetryConfig.RetryStrategy strategy,
        long initialDelayMs,
        long maxDelayMs,
        double backoffMultiplier
    ) {}

    public record RetryDelay(
        int attemptNumber,
        long delayMs,
        boolean shouldRetry,
        int maxAttempts
    ) {}
}
