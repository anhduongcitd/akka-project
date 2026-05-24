package com.example.payment.agents.domain;

/**
 * Fallback strategy for agent failures.
 */
public record FallbackStrategy(
    String agentId,
    FallbackType type,
    String fallbackAgentId,      // For AGENT type
    String cachedResponse,        // For CACHED type
    String defaultResponse,       // For DEFAULT type
    boolean enabled
) {

    /**
     * Fallback types.
     */
    public enum FallbackType {
        AGENT,         // Fallback to another agent
        CACHED,        // Use cached response
        DEFAULT,       // Return default response
        NONE           // No fallback
    }

    /**
     * Create fallback to another agent.
     */
    public static FallbackStrategy toAgent(String agentId, String fallbackAgentId) {
        return new FallbackStrategy(
            agentId,
            FallbackType.AGENT,
            fallbackAgentId,
            null,
            null,
            true
        );
    }

    /**
     * Create cached response fallback.
     */
    public static FallbackStrategy toCached(String agentId, String cachedResponse) {
        return new FallbackStrategy(
            agentId,
            FallbackType.CACHED,
            null,
            cachedResponse,
            null,
            true
        );
    }

    /**
     * Create default response fallback.
     */
    public static FallbackStrategy toDefault(String agentId, String defaultResponse) {
        return new FallbackStrategy(
            agentId,
            FallbackType.DEFAULT,
            null,
            null,
            defaultResponse,
            true
        );
    }

    /**
     * Create no fallback.
     */
    public static FallbackStrategy none(String agentId) {
        return new FallbackStrategy(
            agentId,
            FallbackType.NONE,
            null,
            null,
            null,
            false
        );
    }

    /**
     * Enable fallback.
     */
    public FallbackStrategy enable() {
        return new FallbackStrategy(
            agentId, type, fallbackAgentId, cachedResponse, defaultResponse, true
        );
    }

    /**
     * Disable fallback.
     */
    public FallbackStrategy disable() {
        return new FallbackStrategy(
            agentId, type, fallbackAgentId, cachedResponse, defaultResponse, false
        );
    }

    /**
     * Update cached response.
     */
    public FallbackStrategy withCachedResponse(String newCachedResponse) {
        return new FallbackStrategy(
            agentId, type, fallbackAgentId, newCachedResponse, defaultResponse, enabled
        );
    }

    /**
     * Check if fallback is available.
     */
    public boolean isAvailable() {
        if (!enabled || type == FallbackType.NONE) {
            return false;
        }

        return switch (type) {
            case AGENT -> fallbackAgentId != null && !fallbackAgentId.isEmpty();
            case CACHED -> cachedResponse != null && !cachedResponse.isEmpty();
            case DEFAULT -> defaultResponse != null && !defaultResponse.isEmpty();
            case NONE -> false;
        };
    }

    /**
     * Get fallback response.
     */
    public String getFallbackResponse() {
        if (!isAvailable()) {
            return null;
        }

        return switch (type) {
            case CACHED -> cachedResponse;
            case DEFAULT -> defaultResponse;
            case AGENT, NONE -> null; // Agent fallback requires invocation
        };
    }
}
