package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Integration Configuration - External service integration settings.
 *
 * Stores:
 * - API credentials and endpoints
 * - Integration status (enabled/disabled)
 * - Rate limits and quotas
 * - Metadata and configuration
 */
public record IntegrationConfig(
    String integrationId,
    String integrationType,   // "slack", "email", "webhook", "api"
    String name,
    boolean enabled,
    Map<String, String> credentials,
    Map<String, String> settings,
    RateLimits rateLimits,
    Instant createdAt,
    Instant updatedAt
) {
    public IntegrationConfig {
        if (integrationId == null || integrationId.isBlank()) {
            throw new IllegalArgumentException("Integration ID cannot be null or blank");
        }
        if (integrationType == null || integrationType.isBlank()) {
            throw new IllegalArgumentException("Integration type cannot be null or blank");
        }
    }

    public IntegrationConfig withEnabled(boolean newEnabled) {
        return new IntegrationConfig(
            integrationId,
            integrationType,
            name,
            newEnabled,
            credentials,
            settings,
            rateLimits,
            createdAt,
            Instant.now()
        );
    }

    public IntegrationConfig withSettings(Map<String, String> newSettings) {
        return new IntegrationConfig(
            integrationId,
            integrationType,
            name,
            enabled,
            credentials,
            newSettings,
            rateLimits,
            createdAt,
            Instant.now()
        );
    }

    public IntegrationConfig withRateLimits(RateLimits newLimits) {
        return new IntegrationConfig(
            integrationId,
            integrationType,
            name,
            enabled,
            credentials,
            settings,
            newLimits,
            createdAt,
            Instant.now()
        );
    }

    public record RateLimits(
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay
    ) {
        public static RateLimits DEFAULT = new RateLimits(60, 3600, 86400);
    }
}
