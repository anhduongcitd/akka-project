package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.IntegrationConfig;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration Hub Entity - Manages external service integrations.
 *
 * Entity ID: integrationId
 *
 * Features:
 * - Configure external integrations (Slack, Email, Webhooks, APIs)
 * - Enable/disable integrations
 * - Rate limiting per integration
 * - Credential management
 */
@Component(id = "integration-hub")
public class IntegrationHubEntity extends KeyValueEntity<IntegrationConfig> {

    // Command records
    public record CreateIntegration(
        String integrationType,
        String name,
        Map<String, String> credentials,
        Map<String, String> settings
    ) {}

    public record UpdateSettings(Map<String, String> settings) {}

    public record UpdateRateLimits(
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay
    ) {}

    public record EnableIntegration() {}

    public record DisableIntegration() {}

    public record TestConnection() {}

    public record ConnectionTestResult(
        boolean success,
        String message,
        long responseTimeMs
    ) {}

    /**
     * Initialize empty state.
     */
    @Override
    public IntegrationConfig emptyState() {
        return null;
    }

    /**
     * Create a new integration.
     */
    public Effect<Done> createIntegration(CreateIntegration command) {
        if (currentState() != null) {
            return effects().error("Integration already exists");
        }

        String integrationId = commandContext().entityId();

        var config = new IntegrationConfig(
            integrationId,
            command.integrationType(),
            command.name(),
            true, // Enabled by default
            command.credentials() != null ? command.credentials() : new HashMap<>(),
            command.settings() != null ? command.settings() : new HashMap<>(),
            IntegrationConfig.RateLimits.DEFAULT,
            Instant.now(),
            Instant.now()
        );

        return effects()
            .updateState(config)
            .thenReply(Done.getInstance());
    }

    /**
     * Update integration settings.
     */
    public Effect<Done> updateSettings(UpdateSettings command) {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        var updated = currentState().withSettings(command.settings());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Update rate limits.
     */
    public Effect<Done> updateRateLimits(UpdateRateLimits command) {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        var limits = new IntegrationConfig.RateLimits(
            command.requestsPerMinute(),
            command.requestsPerHour(),
            command.requestsPerDay()
        );

        var updated = currentState().withRateLimits(limits);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Enable integration.
     */
    public Effect<Done> enableIntegration(EnableIntegration command) {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        var updated = currentState().withEnabled(true);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Disable integration.
     */
    public Effect<Done> disableIntegration(DisableIntegration command) {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        var updated = currentState().withEnabled(false);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Test integration connection.
     */
    public Effect<ConnectionTestResult> testConnection(TestConnection command) {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        if (!currentState().enabled()) {
            return effects().reply(new ConnectionTestResult(
                false,
                "Integration is disabled",
                0
            ));
        }

        // Simulate connection test
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String message = "Connection successful";

        // Check if credentials exist
        if (currentState().credentials().isEmpty()) {
            success = false;
            message = "No credentials configured";
        }

        long responseTime = System.currentTimeMillis() - startTime;

        return effects().reply(new ConnectionTestResult(
            success,
            message,
            responseTime
        ));
    }

    /**
     * Get current configuration.
     */
    public Effect<IntegrationConfig> getConfig() {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete integration.
     */
    public Effect<Done> deleteIntegration() {
        if (currentState() == null) {
            return effects().error("Integration not found");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }
}
