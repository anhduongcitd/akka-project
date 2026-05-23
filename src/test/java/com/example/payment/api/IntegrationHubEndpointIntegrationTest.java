package com.example.payment.api;

import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for IntegrationHubEndpoint.
 */
public class IntegrationHubEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreateIntegration() {
        // Given: Create request
        String integrationId = "endpoint-create-001";
        var request = new IntegrationHubEndpoint.CreateIntegrationRequest(
            "slack",
            "Slack Integration",
            Map.of("token", "xoxb-test-token"),
            Map.of("channel", "#payments")
        );

        // When: Creating integration
        var response = httpClient
            .POST("/integrations/" + integrationId)
            .withRequestBody(request)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo(Done.getInstance());
    }

    @Test
    public void shouldGetIntegration() {
        // Given: Existing integration
        String integrationId = "endpoint-get-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "email",
                "Email Integration",
                Map.of("smtp", "smtp.example.com"),
                Map.of("from", "noreply@example.com")
            ))
            .invoke();

        // When: Getting integration
        var response = httpClient
            .GET("/integrations/" + integrationId)
            .responseBodyAs(IntegrationHubEndpoint.IntegrationResponse.class)
            .invoke();

        // Then: Should return integration
        assertThat(response.status().isSuccess()).isTrue();
        var integration = response.body();
        assertThat(integration.integrationId()).isEqualTo(integrationId);
        assertThat(integration.integrationType()).isEqualTo("email");
        assertThat(integration.name()).isEqualTo("Email Integration");
        assertThat(integration.enabled()).isTrue();
    }

    @Test
    public void shouldUpdateSettings() {
        // Given: Integration
        String integrationId = "endpoint-settings-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "webhook",
                "Webhook",
                Map.of(),
                Map.of("url", "https://old-url.com")
            ))
            .invoke();

        // When: Updating settings
        var settingsRequest = new IntegrationHubEndpoint.UpdateSettingsRequest(
            Map.of("url", "https://new-url.com", "retry", "3")
        );

        var response = httpClient
            .PUT("/integrations/" + integrationId + "/settings")
            .withRequestBody(settingsRequest)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should update
        assertThat(response.status().isSuccess()).isTrue();

        var integration = httpClient
            .GET("/integrations/" + integrationId)
            .responseBodyAs(IntegrationHubEndpoint.IntegrationResponse.class)
            .invoke()
            .body();

        assertThat(integration.settings()).containsEntry("url", "https://new-url.com");
        assertThat(integration.settings()).containsEntry("retry", "3");
    }

    @Test
    public void shouldUpdateRateLimits() {
        // Given: Integration
        String integrationId = "endpoint-ratelimit-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "api",
                "API Integration",
                Map.of(),
                Map.of()
            ))
            .invoke();

        // When: Updating rate limits
        var rateLimitRequest = new IntegrationHubEndpoint.UpdateRateLimitsRequest(100, 5000, 100000);

        var response = httpClient
            .PUT("/integrations/" + integrationId + "/rate-limits")
            .withRequestBody(rateLimitRequest)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should update
        assertThat(response.status().isSuccess()).isTrue();

        var integration = httpClient
            .GET("/integrations/" + integrationId)
            .responseBodyAs(IntegrationHubEndpoint.IntegrationResponse.class)
            .invoke()
            .body();

        assertThat(integration.rateLimits().requestsPerMinute()).isEqualTo(100);
        assertThat(integration.rateLimits().requestsPerHour()).isEqualTo(5000);
        assertThat(integration.rateLimits().requestsPerDay()).isEqualTo(100000);
    }

    @Test
    public void shouldEnableAndDisableIntegration() {
        // Given: Integration
        String integrationId = "endpoint-enable-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "slack",
                "Toggle Slack",
                Map.of(),
                Map.of()
            ))
            .invoke();

        // When: Disabling
        var disableResponse = httpClient
            .PUT("/integrations/" + integrationId + "/disable")
            .responseBodyAs(Done.class)
            .invoke();

        assertThat(disableResponse.status().isSuccess()).isTrue();

        var disabledIntegration = httpClient
            .GET("/integrations/" + integrationId)
            .responseBodyAs(IntegrationHubEndpoint.IntegrationResponse.class)
            .invoke()
            .body();

        assertThat(disabledIntegration.enabled()).isFalse();

        // When: Enabling
        var enableResponse = httpClient
            .PUT("/integrations/" + integrationId + "/enable")
            .responseBodyAs(Done.class)
            .invoke();

        assertThat(enableResponse.status().isSuccess()).isTrue();

        var enabledIntegration = httpClient
            .GET("/integrations/" + integrationId)
            .responseBodyAs(IntegrationHubEndpoint.IntegrationResponse.class)
            .invoke()
            .body();

        assertThat(enabledIntegration.enabled()).isTrue();
    }

    @Test
    public void shouldTestConnection() {
        // Given: Integration with credentials
        String integrationId = "endpoint-test-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "api",
                "Test API",
                Map.of("api-key", "test-key"),
                Map.of()
            ))
            .invoke();

        // When: Testing connection
        var response = httpClient
            .POST("/integrations/" + integrationId + "/test")
            .responseBodyAs(IntegrationHubEndpoint.TestConnectionResponse.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
        var result = response.body();
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Connection successful");
    }

    @Test
    public void shouldListAllIntegrations() {
        // Given: Multiple integrations
        httpClient.POST("/integrations/endpoint-list-001")
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "slack", "Slack 1", Map.of(), Map.of()
            ))
            .invoke();

        httpClient.POST("/integrations/endpoint-list-002")
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "email", "Email 1", Map.of(), Map.of()
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/integrations")
                    .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
                    .invoke();

                assertThat(result.body().integrations()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Listing all
        var response = httpClient
            .GET("/integrations")
            .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
            .invoke();

        // Then: Should return all
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().integrations()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void shouldListByType() {
        // Given: Integrations of different types
        httpClient.POST("/integrations/endpoint-type-slack-001")
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "slack", "Slack Type", Map.of(), Map.of()
            ))
            .invoke();

        httpClient.POST("/integrations/endpoint-type-email-001")
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "email", "Email Type", Map.of(), Map.of()
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/integrations/type/slack")
                    .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
                    .invoke();

                assertThat(result.body().integrations()).isNotEmpty();
            });

        // When: Listing by type
        var response = httpClient
            .GET("/integrations/type/slack")
            .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
            .invoke();

        // Then: Should return only slack
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().integrations()).isNotEmpty();
        assertThat(response.body().integrations())
            .allMatch(i -> i.integrationType().equals("slack"));
    }

    @Test
    public void shouldListEnabledIntegrations() {
        // Given: Enabled integration
        httpClient.POST("/integrations/endpoint-enabled-001")
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "webhook", "Enabled Webhook", Map.of(), Map.of()
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/integrations/enabled")
                    .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
                    .invoke();

                assertThat(result.body().integrations()).isNotEmpty();
            });

        // When: Listing enabled
        var response = httpClient
            .GET("/integrations/enabled")
            .responseBodyAs(IntegrationHubEndpoint.IntegrationListResponse.class)
            .invoke();

        // Then: Should return enabled integrations
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().integrations()).isNotEmpty();
        assertThat(response.body().integrations()).allMatch(i -> i.enabled());
    }

    @Test
    public void shouldDeleteIntegration() {
        // Given: Integration
        String integrationId = "endpoint-delete-001";
        httpClient.POST("/integrations/" + integrationId)
            .withRequestBody(new IntegrationHubEndpoint.CreateIntegrationRequest(
                "api", "To Delete", Map.of(), Map.of()
            ))
            .invoke();

        // When: Deleting
        var response = httpClient
            .DELETE("/integrations/" + integrationId)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should delete
        assertThat(response.status().isSuccess()).isTrue();
    }
}
