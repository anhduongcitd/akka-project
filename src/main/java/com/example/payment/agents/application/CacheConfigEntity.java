package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.CacheConfig;

/**
 * Key-Value Entity for cache configuration.
 */
@Component(id = "cache-config")
public class CacheConfigEntity extends KeyValueEntity<CacheConfig> {

    /**
     * Create cache config.
     */
    public Effect<Done> createConfig(CreateConfig command) {
        if (currentState() != null) {
            return effects().error("Cache config already exists");
        }

        var config = CacheConfig.create(
            command.agentId(),
            command.ttlSeconds(),
            command.maxSize(),
            command.strategy()
        );

        return effects()
            .updateState(config)
            .thenReply(Done.getInstance());
    }

    /**
     * Enable cache.
     */
    public Effect<Done> enable() {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        var enabled = currentState().enable();

        return effects()
            .updateState(enabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Disable cache.
     */
    public Effect<Done> disable() {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        var disabled = currentState().disable();

        return effects()
            .updateState(disabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Update TTL.
     */
    public Effect<Done> updateTTL(long ttlSeconds) {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        var updated = currentState().withTTL(ttlSeconds);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Update max size.
     */
    public Effect<Done> updateMaxSize(int maxSize) {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        var updated = currentState().withMaxSize(maxSize);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get cache config.
     */
    public Effect<CacheConfig> getConfig() {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete cache config.
     */
    public Effect<Done> deleteConfig() {
        if (currentState() == null) {
            return effects().error("Cache config does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateConfig(
        String agentId,
        long ttlSeconds,
        int maxSize,
        CacheConfig.CacheStrategy strategy
    ) {}
}
