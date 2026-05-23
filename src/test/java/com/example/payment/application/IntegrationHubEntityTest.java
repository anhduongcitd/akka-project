package com.example.payment.application;

import akka.Done;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IntegrationHubEntity.
 */
public class IntegrationHubEntityTest {

    @Test
    public void shouldCreateIntegration() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("slack-integration-001", IntegrationHubEntity::new);

        // When: Creating integration
        var response = testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "slack",
                "Slack Notifications",
                Map.of("token", "xoxb-test-token"),
                Map.of("channel", "#payments")
            ));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(response.getReply()).isEqualTo(Done.getInstance());

        var state = testKit.getState();
        assertThat(state.integrationId()).isEqualTo("slack-integration-001");
        assertThat(state.integrationType()).isEqualTo("slack");
        assertThat(state.name()).isEqualTo("Slack Notifications");
        assertThat(state.enabled()).isTrue();
        assertThat(state.credentials()).containsEntry("token", "xoxb-test-token");
        assertThat(state.settings()).containsEntry("channel", "#payments");
    }

    @Test
    public void shouldRejectDuplicateCreate() {
        // Given: Existing integration
        var testKit = KeyValueEntityTestKit.of("email-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "email",
                "Email Alerts",
                Map.of(),
                Map.of()
            ));

        // When: Creating again
        var response = testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "email",
                "Duplicate",
                Map.of(),
                Map.of()
            ));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already exists");
    }

    @Test
    public void shouldUpdateSettings() {
        // Given: Integration
        var testKit = KeyValueEntityTestKit.of("webhook-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "webhook",
                "Webhook",
                Map.of(),
                Map.of("url", "https://example.com/webhook")
            ));

        // When: Updating settings
        var response = testKit.method(IntegrationHubEntity::updateSettings)
            .invoke(new IntegrationHubEntity.UpdateSettings(
                Map.of("url", "https://new-url.com/webhook", "retry", "3")
            ));

        // Then: Should update
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.settings()).containsEntry("url", "https://new-url.com/webhook");
        assertThat(state.settings()).containsEntry("retry", "3");
    }

    @Test
    public void shouldUpdateRateLimits() {
        // Given: Integration
        var testKit = KeyValueEntityTestKit.of("api-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "api",
                "External API",
                Map.of(),
                Map.of()
            ));

        // When: Updating rate limits
        var response = testKit.method(IntegrationHubEntity::updateRateLimits)
            .invoke(new IntegrationHubEntity.UpdateRateLimits(100, 5000, 100000));

        // Then: Should update
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.rateLimits().requestsPerMinute()).isEqualTo(100);
        assertThat(state.rateLimits().requestsPerHour()).isEqualTo(5000);
        assertThat(state.rateLimits().requestsPerDay()).isEqualTo(100000);
    }

    @Test
    public void shouldEnableIntegration() {
        // Given: Disabled integration
        var testKit = KeyValueEntityTestKit.of("disabled-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "slack",
                "Disabled Slack",
                Map.of(),
                Map.of()
            ));

        testKit.method(IntegrationHubEntity::disableIntegration)
            .invoke(new IntegrationHubEntity.DisableIntegration());

        // When: Enabling
        var response = testKit.method(IntegrationHubEntity::enableIntegration)
            .invoke(new IntegrationHubEntity.EnableIntegration());

        // Then: Should enable
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState().enabled()).isTrue();
    }

    @Test
    public void shouldDisableIntegration() {
        // Given: Enabled integration
        var testKit = KeyValueEntityTestKit.of("enabled-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "email",
                "Enabled Email",
                Map.of(),
                Map.of()
            ));

        // When: Disabling
        var response = testKit.method(IntegrationHubEntity::disableIntegration)
            .invoke(new IntegrationHubEntity.DisableIntegration());

        // Then: Should disable
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState().enabled()).isFalse();
    }

    @Test
    public void shouldTestConnection() {
        // Given: Integration with credentials
        var testKit = KeyValueEntityTestKit.of("test-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "api",
                "Test API",
                Map.of("api-key", "test-key"),
                Map.of()
            ));

        // When: Testing connection
        var response = testKit.method(IntegrationHubEntity::testConnection)
            .invoke(new IntegrationHubEntity.TestConnection());

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        var result = response.getReply();
        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Connection successful");
    }

    @Test
    public void shouldFailConnectionTestWhenDisabled() {
        // Given: Disabled integration
        var testKit = KeyValueEntityTestKit.of("disabled-test-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "api",
                "Disabled API",
                Map.of("api-key", "key"),
                Map.of()
            ));

        testKit.method(IntegrationHubEntity::disableIntegration)
            .invoke(new IntegrationHubEntity.DisableIntegration());

        // When: Testing connection
        var response = testKit.method(IntegrationHubEntity::testConnection)
            .invoke(new IntegrationHubEntity.TestConnection());

        // Then: Should fail
        assertThat(response.isReply()).isTrue();
        var result = response.getReply();
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("disabled");
    }

    @Test
    public void shouldFailConnectionTestWithoutCredentials() {
        // Given: Integration without credentials
        var testKit = KeyValueEntityTestKit.of("no-creds-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "api",
                "No Creds API",
                Map.of(),
                Map.of()
            ));

        // When: Testing connection
        var response = testKit.method(IntegrationHubEntity::testConnection)
            .invoke(new IntegrationHubEntity.TestConnection());

        // Then: Should fail
        assertThat(response.isReply()).isTrue();
        var result = response.getReply();
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("credentials");
    }

    @Test
    public void shouldGetConfig() {
        // Given: Integration
        var testKit = KeyValueEntityTestKit.of("get-config-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "slack",
                "Slack Config",
                Map.of("token", "xoxb-token"),
                Map.of("channel", "#general")
            ));

        // When: Getting config
        var response = testKit.method(IntegrationHubEntity::getConfig)
            .invoke();

        // Then: Should return config
        assertThat(response.isReply()).isTrue();
        var config = response.getReply();
        assertThat(config.integrationId()).isEqualTo("get-config-001");
        assertThat(config.integrationType()).isEqualTo("slack");
        assertThat(config.name()).isEqualTo("Slack Config");
    }

    @Test
    public void shouldDeleteIntegration() {
        // Given: Integration
        var testKit = KeyValueEntityTestKit.of("delete-integration-001", IntegrationHubEntity::new);
        testKit.method(IntegrationHubEntity::createIntegration)
            .invoke(new IntegrationHubEntity.CreateIntegration(
                "email",
                "To Delete",
                Map.of(),
                Map.of()
            ));

        // When: Deleting
        var response = testKit.method(IntegrationHubEntity::deleteIntegration)
            .invoke();

        // Then: Should delete
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void shouldRejectOperationsOnNonExistent() {
        // Given: Non-existent integration
        var testKit = KeyValueEntityTestKit.of("non-existent-001", IntegrationHubEntity::new);

        // When: Updating settings
        var response = testKit.method(IntegrationHubEntity::updateSettings)
            .invoke(new IntegrationHubEntity.UpdateSettings(Map.of()));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("not found");
    }
}
