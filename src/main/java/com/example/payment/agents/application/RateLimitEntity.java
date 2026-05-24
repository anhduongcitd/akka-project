package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.RateLimitConfig;
import com.example.payment.agents.domain.RateLimitState;

/**
 * Key-Value Entity for rate limiting.
 */
@Component(id = "rate-limit")
public class RateLimitEntity extends KeyValueEntity<RateLimitEntity.RateLimitEntityState> {

    public record RateLimitEntityState(
        RateLimitConfig config,
        RateLimitState state
    ) {}

    /**
     * Create rate limit.
     */
    public Effect<Done> createRateLimit(CreateRateLimit command) {
        if (currentState() != null) {
            return effects().error("Rate limit already exists");
        }

        var config = RateLimitConfig.create(
            command.agentId(),
            command.requestsPerMinute(),
            command.requestsPerHour(),
            command.requestsPerDay(),
            command.strategy()
        );

        var state = RateLimitState.create(command.agentId());

        return effects()
            .updateState(new RateLimitEntityState(config, state))
            .thenReply(Done.getInstance());
    }

    /**
     * Check if request is allowed.
     */
    public Effect<RateLimitCheck> checkLimit() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        boolean allowed = currentState().state().isAllowed(currentState().config());

        if (allowed) {
            var newState = currentState().state().recordRequest();
            return effects()
                .updateState(new RateLimitEntityState(currentState().config(), newState))
                .thenReply(new RateLimitCheck(
                    true,
                    newState.currentMinuteRequests(),
                    currentState().config().requestsPerMinute(),
                    null
                ));
        } else {
            return effects().reply(new RateLimitCheck(
                false,
                currentState().state().currentMinuteRequests(),
                currentState().config().requestsPerMinute(),
                "Rate limit exceeded"
            ));
        }
    }

    /**
     * Update limits.
     */
    public Effect<Done> updateLimits(UpdateLimits command) {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        var newConfig = currentState().config().withLimits(
            command.requestsPerMinute(),
            command.requestsPerHour(),
            command.requestsPerDay()
        );

        return effects()
            .updateState(new RateLimitEntityState(newConfig, currentState().state()))
            .thenReply(Done.getInstance());
    }

    /**
     * Enable rate limiting.
     */
    public Effect<Done> enable() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        var enabled = currentState().config().enable();

        return effects()
            .updateState(new RateLimitEntityState(enabled, currentState().state()))
            .thenReply(Done.getInstance());
    }

    /**
     * Disable rate limiting.
     */
    public Effect<Done> disable() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        var disabled = currentState().config().disable();

        return effects()
            .updateState(new RateLimitEntityState(disabled, currentState().state()))
            .thenReply(Done.getInstance());
    }

    /**
     * Reset rate limit state.
     */
    public Effect<Done> reset() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        var resetState = currentState().state().reset();

        return effects()
            .updateState(new RateLimitEntityState(currentState().config(), resetState))
            .thenReply(Done.getInstance());
    }

    /**
     * Get rate limit info.
     */
    public Effect<RateLimitEntityState> getInfo() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete rate limit.
     */
    public Effect<Done> deleteRateLimit() {
        if (currentState() == null) {
            return effects().error("Rate limit does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateRateLimit(
        String agentId,
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay,
        RateLimitConfig.RateLimitStrategy strategy
    ) {}

    public record UpdateLimits(
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay
    ) {}

    public record RateLimitCheck(
        boolean allowed,
        int currentRequests,
        int limit,
        String message
    ) {}
}
