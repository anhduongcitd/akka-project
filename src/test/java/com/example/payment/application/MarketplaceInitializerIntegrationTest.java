package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MarketplaceInitializer.
 */
public class MarketplaceInitializerIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages(AgentTemplateEntity.class);
    }

    @Test
    public void shouldPopulateMarketplaceOnStartup() {
        // Given: Service started (MarketplaceInitializer runs automatically)
        // Wait for templates to be published
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getAllTemplates)
                    .invoke();

                assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(8);
            });

        // When: Querying all templates
        var result = componentClient.forView()
            .method(AgentTemplateView::getAllTemplates)
            .invoke();

        // Then: Should have all 8 pre-built templates
        assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(8);

        // Verify template IDs
        var templateIds = result.templates().stream()
            .map(AgentTemplateView.TemplateEntry::templateId)
            .toList();

        assertThat(templateIds).contains(
            "template-customer-support-v1",
            "template-fraud-detection-v1",
            "template-payment-assistant-v1",
            "template-general-qa-v1",
            "template-data-analysis-v1",
            "template-email-generator-v1",
            "template-content-moderator-v1",
            "template-transaction-analyzer-v1"
        );
    }

    @Test
    public void shouldHaveCustomerSupportTemplateInMarketplace() {
        // Wait for template
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var template = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke("template-customer-support-v1");

                assertThat(template).isNotNull();
            });

        // When: Getting customer support template
        var template = componentClient.forView()
            .method(AgentTemplateView::getById)
            .invoke("template-customer-support-v1");

        // Then: Should have correct details
        assertThat(template.name()).isEqualTo("Customer Support Agent");
        assertThat(template.category()).isEqualTo("customer-support");
        assertThat(template.author()).isEqualTo("Akka Team");
        assertThat(template.downloads()).isEqualTo(0);
        assertThat(template.rating()).isEqualTo(0.0);
    }

    @Test
    public void shouldHaveFraudDetectionTemplateInMarketplace() {
        // Wait for template
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var template = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke("template-fraud-detection-v1");

                assertThat(template).isNotNull();
            });

        // When: Getting fraud detection template
        var template = componentClient.forView()
            .method(AgentTemplateView::getById)
            .invoke("template-fraud-detection-v1");

        // Then: Should have correct details
        assertThat(template.name()).isEqualTo("Fraud Detection Analyst");
        assertThat(template.category()).isEqualTo("fraud-detection");
        assertThat(template.config().model().temperature()).isEqualTo(0.3);
    }

    @Test
    public void shouldFilterTemplatesByCategory() {
        // Wait for templates
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getByCategory)
                    .invoke("analytics");

                assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Filtering by analytics category
        var result = componentClient.forView()
            .method(AgentTemplateView::getByCategory)
            .invoke("analytics");

        // Then: Should return analytics templates
        assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.templates())
            .allMatch(t -> t.category().equals("analytics"));

        var names = result.templates().stream().map(AgentTemplateView.TemplateEntry::name).toList();
        assertThat(names).contains("Data Analysis Specialist", "Transaction Analysis Expert");
    }

    @Test
    public void shouldSearchTemplates() {
        // Wait for templates
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::searchTemplates)
                    .invoke("%fraud%");

                assertThat(result.templates()).isNotEmpty();
            });

        // When: Searching for fraud
        var result = componentClient.forView()
            .method(AgentTemplateView::searchTemplates)
            .invoke("%fraud%");

        // Then: Should find fraud detection template
        assertThat(result.templates()).isNotEmpty();
        assertThat(result.templates())
            .anyMatch(t -> t.templateId().equals("template-fraud-detection-v1"));
    }

    @Test
    public void shouldDeployPrebuiltTemplate() {
        // Wait for template
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var template = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke("template-general-qa-v1");

                assertThat(template).isNotNull();
            });

        // When: Deploying general Q&A template
        var deploymentId = "test-deployment-qa-001";

        componentClient
            .forKeyValueEntity(deploymentId)
            .method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-general-qa-v1",
                "qa-agent-instance-001",
                java.util.Map.of("customPrompt", "Be extra helpful")
            ));

        // Then: Should create deployment
        var deployment = componentClient
            .forKeyValueEntity(deploymentId)
            .method(TemplateDeploymentEntity::getDeployment)
            .invoke();

        assertThat(deployment.templateId()).isEqualTo("template-general-qa-v1");
        assertThat(deployment.agentId()).isEqualTo("qa-agent-instance-001");
        assertThat(deployment.status()).isEqualTo("active");
        assertThat(deployment.customizations()).containsEntry("customPrompt", "Be extra helpful");
    }

    @Test
    public void shouldIncrementDownloadsOnDeploy() {
        // Wait for template
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var template = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke("template-email-generator-v1");

                assertThat(template).isNotNull();
            });

        // Given: Get initial downloads
        var initialTemplate = componentClient.forView()
            .method(AgentTemplateView::getById)
            .invoke("template-email-generator-v1");

        int initialDownloads = initialTemplate.downloads();

        // When: Deploying template (simulating download)
        componentClient
            .forKeyValueEntity("template-email-generator-v1")
            .method(AgentTemplateEntity::incrementDownloads)
            .invoke(new AgentTemplateEntity.IncrementDownloads());

        // Wait for view update
        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var updated = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke("template-email-generator-v1");

                assertThat(updated.downloads()).isEqualTo(initialDownloads + 1);
            });

        // Then: Downloads should increment
        var updatedTemplate = componentClient.forView()
            .method(AgentTemplateView::getById)
            .invoke("template-email-generator-v1");

        assertThat(updatedTemplate.downloads()).isEqualTo(initialDownloads + 1);
    }
}
