package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.FallbackStrategy;

/**
 * Key-Value Entity for fallback strategy management.
 */
@Component(id = "fallback-strategy")
public class FallbackStrategyEntity extends KeyValueEntity<FallbackStrategy> {

    /**
     * Create fallback strategy.
     */
    public Effect<Done> createStrategy(CreateStrategy command) {
        if (currentState() != null) {
            return effects().error("Fallback strategy already exists");
        }

        var strategy = switch (command.type()) {
            case AGENT -> FallbackStrategy.toAgent(command.agentId(), command.fallbackAgentId());
            case CACHED -> FallbackStrategy.toCached(command.agentId(), command.cachedResponse());
            case DEFAULT -> FallbackStrategy.toDefault(command.agentId(), command.defaultResponse());
            case NONE -> FallbackStrategy.none(command.agentId());
        };

        return effects()
            .updateState(strategy)
            .thenReply(Done.getInstance());
    }

    /**
     * Enable fallback.
     */
    public Effect<Done> enable() {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        var enabled = currentState().enable();

        return effects()
            .updateState(enabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Disable fallback.
     */
    public Effect<Done> disable() {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        var disabled = currentState().disable();

        return effects()
            .updateState(disabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Update cached response.
     */
    public Effect<Done> updateCachedResponse(String cachedResponse) {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        if (currentState().type() != FallbackStrategy.FallbackType.CACHED) {
            return effects().error("Not a cached response fallback");
        }

        var updated = currentState().withCachedResponse(cachedResponse);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Check if fallback is available.
     */
    public Effect<FallbackAvailable> checkAvailability() {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        return effects().reply(new FallbackAvailable(
            currentState().isAvailable(),
            currentState().type().toString(),
            currentState().enabled()
        ));
    }

    /**
     * Get fallback strategy.
     */
    public Effect<FallbackStrategy> getStrategy() {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete fallback strategy.
     */
    public Effect<Done> deleteStrategy() {
        if (currentState() == null) {
            return effects().error("Fallback strategy does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateStrategy(
        String agentId,
        FallbackStrategy.FallbackType type,
        String fallbackAgentId,
        String cachedResponse,
        String defaultResponse
    ) {}

    public record FallbackAvailable(
        boolean available,
        String type,
        boolean enabled
    ) {}
}
