package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Cached agent response entry.
 */
public record CacheEntry(
    String cacheKey,
    String agentId,
    String requestHash,
    String response,
    int accessCount,
    Instant cachedAt,
    Instant lastAccessedAt,
    Instant expiresAt
) {

    /**
     * Create cache entry.
     */
    public static CacheEntry create(String cacheKey, String agentId, String requestHash,
                                     String response, long ttlSeconds) {
        Instant now = Instant.now();
        return new CacheEntry(
            cacheKey,
            agentId,
            requestHash,
            response,
            0,
            now,
            now,
            now.plusSeconds(ttlSeconds)
        );
    }

    /**
     * Record access.
     */
    public CacheEntry recordAccess() {
        return new CacheEntry(
            cacheKey,
            agentId,
            requestHash,
            response,
            accessCount + 1,
            cachedAt,
            Instant.now(),
            expiresAt
        );
    }

    /**
     * Check if expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Update expiration.
     */
    public CacheEntry extendExpiration(long ttlSeconds) {
        return new CacheEntry(
            cacheKey,
            agentId,
            requestHash,
            response,
            accessCount,
            cachedAt,
            lastAccessedAt,
            Instant.now().plusSeconds(ttlSeconds)
        );
    }

    /**
     * Get age in seconds.
     */
    public long getAgeSeconds() {
        return Instant.now().getEpochSecond() - cachedAt.getEpochSecond();
    }

    /**
     * Get time until expiration in seconds.
     */
    public long getTimeToLiveSeconds() {
        return Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
    }
}
