package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Batch processing configuration for agent requests.
 */
public record BatchConfig(
    String agentId,
    boolean enabled,
    int batchSize,
    long maxWaitMs,
    Instant createdAt
) {

    /**
     * Create batch config.
     */
    public static BatchConfig create(String agentId, int batchSize, long maxWaitMs) {
        return new BatchConfig(
            agentId,
            true,
            batchSize,
            maxWaitMs,
            Instant.now()
        );
    }

    /**
     * Enable batching.
     */
    public BatchConfig enable() {
        return new BatchConfig(agentId, true, batchSize, maxWaitMs, createdAt);
    }

    /**
     * Disable batching.
     */
    public BatchConfig disable() {
        return new BatchConfig(agentId, false, batchSize, maxWaitMs, createdAt);
    }

    /**
     * Update batch size.
     */
    public BatchConfig withBatchSize(int newBatchSize) {
        return new BatchConfig(agentId, enabled, newBatchSize, maxWaitMs, createdAt);
    }

    /**
     * Update max wait time.
     */
    public BatchConfig withMaxWait(long newMaxWaitMs) {
        return new BatchConfig(agentId, enabled, batchSize, newMaxWaitMs, createdAt);
    }
}
