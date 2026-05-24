package com.example.payment.agents.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Cache configuration for agent responses.
 */
public record CacheConfig(
    String agentId,
    boolean enabled,
    long ttlSeconds,
    int maxSize,
    CacheStrategy strategy,
    Instant createdAt
) {

    /**
     * Cache strategies.
     */
    public enum CacheStrategy {
        LRU,           // Least Recently Used
        LFU,           // Least Frequently Used
        TTL_ONLY       // Time-based only
    }

    /**
     * Create cache config.
     */
    public static CacheConfig create(String agentId, long ttlSeconds, int maxSize, CacheStrategy strategy) {
        return new CacheConfig(
            agentId,
            true,
            ttlSeconds,
            maxSize,
            strategy,
            Instant.now()
        );
    }

    /**
     * Enable cache.
     */
    public CacheConfig enable() {
        return new CacheConfig(agentId, true, ttlSeconds, maxSize, strategy, createdAt);
    }

    /**
     * Disable cache.
     */
    public CacheConfig disable() {
        return new CacheConfig(agentId, false, ttlSeconds, maxSize, strategy, createdAt);
    }

    /**
     * Update TTL.
     */
    public CacheConfig withTTL(long newTtlSeconds) {
        return new CacheConfig(agentId, enabled, newTtlSeconds, maxSize, strategy, createdAt);
    }

    /**
     * Update max size.
     */
    public CacheConfig withMaxSize(int newMaxSize) {
        return new CacheConfig(agentId, enabled, ttlSeconds, newMaxSize, strategy, createdAt);
    }

    /**
     * Get TTL as Duration.
     */
    public Duration getTTLDuration() {
        return Duration.ofSeconds(ttlSeconds);
    }
}
