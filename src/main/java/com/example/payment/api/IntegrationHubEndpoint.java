package com.example.payment.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.domain.IntegrationConfig;
import com.example.payment.application.IntegrationHubEntity;
import com.example.payment.application.IntegrationHubView;

import java.util.List;
import java.util.Map;

/**
 * Integration Hub Endpoint - API for managing external integrations.
 *
 * Endpoints:
 * - POST /integrations/{id} - Create integration
 * - PUT /integrations/{id}/settings - Update settings
 * - PUT /integrations/{id}/rate-limits - Update rate limits
 * - PUT /integrations/{id}/enable - Enable integration
 * - PUT /integrations/{id}/disable - Disable integration
 * - POST /integrations/{id}/test - Test connection
 * - GET /integrations/{id} - Get integration config
 * - GET /integrations - List all integrations
 * - GET /integrations/type/{type} - List by type
 * - GET /integrations/enabled - List enabled
 * - GET /integrations/disabled - List disabled
 * - DELETE /integrations/{id} - Delete integration
 */
@HttpEndpoint("/integrations")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class IntegrationHubEndpoint {

    private final ComponentClient componentClient;

    public IntegrationHubEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record CreateIntegrationRequest(
        String integrationType,
        String name,
        Map<String, String> credentials,
        Map<String, String> settings
    ) {}

    public record UpdateSettingsRequest(Map<String, String> settings) {}

    public record UpdateRateLimitsRequest(
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay
    ) {}

    public record IntegrationResponse(
        String integrationId,
        String integrationType,
        String name,
        boolean enabled,
        Map<String, String> settings,
        RateLimitsResponse rateLimits,
        String createdAt,
        String updatedAt
    ) {}

    public record RateLimitsResponse(
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay
    ) {}

    public record TestConnectionResponse(
        boolean success,
        String message,
        long responseTimeMs
    ) {}

    public record IntegrationListResponse(List<IntegrationSummary> integrations) {}

    public record IntegrationSummary(
        String integrationId,
        String integrationType,
        String name,
        boolean enabled,
        int requestsPerMinute,
        String createdAt
    ) {}

    /**
     * Create a new integration.
     */
    @Post("/{integrationId}")
    public Done createIntegration(String integrationId, CreateIntegrationRequest request) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                request.integrationType(),
                request.name(),
                request.credentials(),
                request.settings()
            ));
    }

    /**
     * Update integration settings.
     */
    @Put("/{integrationId}/settings")
    public Done updateSettings(String integrationId, UpdateSettingsRequest request) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::updateSettings)
            .invoke(new IntegrationHubEntity.UpdateSettings(request.settings()));
    }

    /**
     * Update rate limits.
     */
    @Put("/{integrationId}/rate-limits")
    public Done updateRateLimits(String integrationId, UpdateRateLimitsRequest request) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::updateRateLimits)
            .invoke(new IntegrationHubEntity.UpdateRateLimits(
                request.requestsPerMinute(),
                request.requestsPerHour(),
                request.requestsPerDay()
            ));
    }

    /**
     * Enable integration.
     */
    @Put("/{integrationId}/enable")
    public Done enableIntegration(String integrationId) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::enableIntegration)
            .invoke(new IntegrationHubEntity.EnableIntegration());
    }

    /**
     * Disable integration.
     */
    @Put("/{integrationId}/disable")
    public Done disableIntegration(String integrationId) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::disableIntegration)
            .invoke(new IntegrationHubEntity.DisableIntegration());
    }

    /**
     * Test integration connection.
     */
    @Post("/{integrationId}/test")
    public TestConnectionResponse testConnection(String integrationId) {
        var result = componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::testConnection)
            .invoke(new IntegrationHubEntity.TestConnection());

        return new TestConnectionResponse(
            result.success(),
            result.message(),
            result.responseTimeMs()
        );
    }

    /**
     * Get integration configuration.
     */
    @Get("/{integrationId}")
    public IntegrationResponse getIntegration(String integrationId) {
        var config = componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::getConfig)
            .invoke();

        return toApi(config);
    }

    /**
     * List all integrations.
     */
    @Get
    public IntegrationListResponse listAll() {
        var entries = componentClient
            .forView()
            .method(IntegrationHubView::getAll)
            .invoke();

        var summaries = entries.integrations().stream()
            .map(this::toSummary)
            .toList();

        return new IntegrationListResponse(summaries);
    }

    /**
     * List integrations by type.
     */
    @Get("/type/{type}")
    public IntegrationListResponse listByType(String type) {
        var entries = componentClient
            .forView()
            .method(IntegrationHubView::getByType)
            .invoke(type);

        var summaries = entries.integrations().stream()
            .map(this::toSummary)
            .toList();

        return new IntegrationListResponse(summaries);
    }

    /**
     * List enabled integrations.
     */
    @Get("/enabled")
    public IntegrationListResponse listEnabled() {
        var entries = componentClient
            .forView()
            .method(IntegrationHubView::getEnabled)
            .invoke();

        var summaries = entries.integrations().stream()
            .map(this::toSummary)
            .toList();

        return new IntegrationListResponse(summaries);
    }

    /**
     * List disabled integrations.
     */
    @Get("/disabled")
    public IntegrationListResponse listDisabled() {
        var entries = componentClient
            .forView()
            .method(IntegrationHubView::getDisabled)
            .invoke();

        var summaries = entries.integrations().stream()
            .map(this::toSummary)
            .toList();

        return new IntegrationListResponse(summaries);
    }

    /**
     * Delete integration.
     */
    @Delete("/{integrationId}")
    public Done deleteIntegration(String integrationId) {
        return componentClient
            .forKeyValueEntity(integrationId)
            .method(IntegrationHubEntity::deleteIntegration)
            .invoke();
    }

    /**
     * Convert IntegrationConfig to API response.
     */
    private IntegrationResponse toApi(IntegrationConfig config) {
        return new IntegrationResponse(
            config.integrationId(),
            config.integrationType(),
            config.name(),
            config.enabled(),
            config.settings(),
            new RateLimitsResponse(
                config.rateLimits().requestsPerMinute(),
                config.rateLimits().requestsPerHour(),
                config.rateLimits().requestsPerDay()
            ),
            config.createdAt().toString(),
            config.updatedAt().toString()
        );
    }

    /**
     * Convert IntegrationEntry to summary.
     */
    private IntegrationSummary toSummary(IntegrationHubView.IntegrationEntry entry) {
        return new IntegrationSummary(
            entry.integrationId(),
            entry.integrationType(),
            entry.name(),
            entry.enabled(),
            entry.requestsPerMinute(),
            entry.createdAt()
        );
    }
}
