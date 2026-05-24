package com.example.payment.agents.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.CacheEntry;

/**
 * Key-Value Entity for cache entries.
 */
@Component(id = "cache-entry")
public class CacheEntryEntity extends KeyValueEntity<CacheEntry> {

    /**
     * Store cache entry.
     */
    public Effect<Done> store(StoreEntry command) {
        var entry = CacheEntry.create(
            command.cacheKey(),
            command.agentId(),
            command.requestHash(),
            command.response(),
            command.ttlSeconds()
        );

        return effects()
            .updateState(entry)
            .thenReply(Done.getInstance());
    }

    /**
     * Get cached response.
     */
    public Effect<CacheHit> get() {
        if (currentState() == null) {
            return effects().reply(new CacheHit(false, null, 0));
        }

        if (currentState().isExpired()) {
            // Delete expired entry
            return effects()
                .deleteEntity()
                .thenReply(new CacheHit(false, null, 0));
        }

        // Record access and return response
        var updated = currentState().recordAccess();

        return effects()
            .updateState(updated)
            .thenReply(new CacheHit(
                true,
                updated.response(),
                updated.accessCount()
            ));
    }

    /**
     * Extend expiration.
     */
    public Effect<Done> extendExpiration(long ttlSeconds) {
        if (currentState() == null) {
            return effects().error("Cache entry does not exist");
        }

        var updated = currentState().extendExpiration(ttlSeconds);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Invalidate cache entry.
     */
    public Effect<Done> invalidate() {
        if (currentState() == null) {
            return effects().error("Cache entry does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record StoreEntry(
        String cacheKey,
        String agentId,
        String requestHash,
        String response,
        long ttlSeconds
    ) {}

    public record CacheHit(
        boolean hit,
        String response,
        int accessCount
    ) {}
}
