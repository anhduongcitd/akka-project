package com.example.payment.api;

import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.domain.AgentConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentMarketplaceEndpoint.
 */
public class AgentMarketplaceEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldPublishTemplate() {
        // Given: Publish request
        String templateId = "endpoint-template-001";

        var config = new AgentConfig(
            "You are a helpful agent",
            List.of(),
            Map.of(),
            new AgentConfig.GuardrailConfig(List.of("pii-guard")),
            AgentConfig.ModelConfig.DEFAULT
        );

        var request = new AgentMarketplaceEndpoint.PublishTemplateRequest(
            templateId,
            "My Template",
            "A test template",
            "general",
            config,
            List.of("test", "sample"),
            "Test Author",
            "1.0.0"
        );

        // When: Publishing
        var response = httpClient
            .POST("/marketplace/templates")
            .withRequestBody(request)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo(Done.getInstance());
    }

    @Test
    public void shouldGetTemplate() {
        // Given: Existing template
        String templateId = "endpoint-get-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                templateId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // When: Getting template
        var response = httpClient
            .GET("/marketplace/templates/" + templateId)
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateResponse.class)
            .invoke();

        // Then: Should return template
        assertThat(response.status().isSuccess()).isTrue();
        var template = response.body();
        assertThat(template.templateId()).isEqualTo(templateId);
        assertThat(template.name()).isEqualTo("Template");
    }

    @Test
    public void shouldUpdateTemplate() {
        // Given: Existing template
        String templateId = "endpoint-update-001";

        var config = new AgentConfig("original", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                templateId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // When: Updating
        var newConfig = new AgentConfig("updated prompt", List.of(), Map.of(), null, null);

        var updateResponse = httpClient
            .PUT("/marketplace/templates/" + templateId)
            .withRequestBody(new AgentMarketplaceEndpoint.UpdateTemplateRequest(newConfig))
            .responseBodyAs(Done.class)
            .invoke();

        assertThat(updateResponse.status().isSuccess()).isTrue();

        // Then: Should be updated
        var getResponse = httpClient
            .GET("/marketplace/templates/" + templateId)
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateResponse.class)
            .invoke();

        assertThat(getResponse.body().config().systemPrompt()).isEqualTo("updated prompt");
    }

    @Test
    public void shouldDeleteTemplate() {
        // Given: Existing template
        String templateId = "endpoint-delete-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                templateId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // When: Deleting
        var response = httpClient
            .DELETE("/marketplace/templates/" + templateId)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldListAllTemplates() {
        // Given: Multiple templates
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                "endpoint-list-001", "Template 1", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                "endpoint-list-002", "Template 2", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/marketplace/templates")
                    .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
                    .invoke();

                assertThat(result.body().templates()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Listing all
        var response = httpClient
            .GET("/marketplace/templates")
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
            .invoke();

        // Then: Should return all
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().templates()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void shouldFilterByCategory() {
        // Given: Templates in different categories
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                "endpoint-category-support-001", "Support", "Description", "customer-support", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                "endpoint-category-fraud-001", "Fraud", "Description", "fraud", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/marketplace/templates/category/customer-support")
                    .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
                    .invoke();

                assertThat(result.body().templates()).isNotEmpty();
            });

        // When: Filtering by category
        var response = httpClient
            .GET("/marketplace/templates/category/customer-support")
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
            .invoke();

        // Then: Should return only support templates
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().templates())
            .allMatch(t -> t.category().equals("customer-support"));
    }

    @Test
    public void shouldGetTopRated() {
        // Given: Template with ratings
        String topId = "endpoint-top-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                topId, "Top Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // Add ratings
        for (int i = 0; i < 3; i++) {
            httpClient.POST("/marketplace/templates/" + topId + "/rate")
                .withRequestBody(new AgentMarketplaceEndpoint.RateTemplateRequest(5.0))
                .invoke();
        }

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/marketplace/templates/top-rated")
                    .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
                    .invoke();

                assertThat(result.body().templates()).isNotEmpty();
            });

        // When: Getting top rated
        var response = httpClient
            .GET("/marketplace/templates/top-rated")
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
            .invoke();

        // Then: Should return templates
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().templates()).isNotEmpty();
    }

    @Test
    public void shouldGetPopular() {
        // Given: Template with downloads
        String popularId = "endpoint-popular-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                popularId, "Popular", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/marketplace/templates/popular")
                    .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
                    .invoke();

                assertThat(result.body().templates()).isNotEmpty();
            });

        // When: Getting popular
        var response = httpClient
            .GET("/marketplace/templates/popular")
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
            .invoke();

        // Then: Should return templates
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().templates()).isNotEmpty();
    }

    @Test
    public void shouldSearchTemplates() {
        // Given: Template with searchable name
        String searchId = "endpoint-search-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                searchId, "Unique Searchable Name", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/marketplace/templates/search?query=Unique")
                    .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
                    .invoke();

                assertThat(result.body().templates())
                    .anyMatch(t -> t.templateId().equals(searchId));
            });

        // When: Searching
        var response = httpClient
            .GET("/marketplace/templates/search?query=Unique")
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateListResponse.class)
            .invoke();

        // Then: Should find template
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().templates())
            .anyMatch(t -> t.templateId().equals(searchId));
    }

    @Test
    public void shouldRateTemplate() {
        // Given: Template
        String rateId = "endpoint-rate-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                rateId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // When: Rating
        var response = httpClient
            .POST("/marketplace/templates/" + rateId + "/rate")
            .withRequestBody(new AgentMarketplaceEndpoint.RateTemplateRequest(4.5))
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();

        var template = httpClient
            .GET("/marketplace/templates/" + rateId)
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateResponse.class)
            .invoke().body();

        assertThat(template.rating()).isEqualTo(4.5);
    }

    @Test
    public void shouldDeployTemplate() {
        // Given: Template
        String deployId = "endpoint-deploy-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                deployId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        // When: Deploying
        var deployRequest = new AgentMarketplaceEndpoint.DeployTemplateRequest(
            "deployed-agent-001",
            Map.of("custom", "value")
        );

        var response = httpClient
            .POST("/marketplace/deploy/" + deployId)
            .withRequestBody(deployRequest)
            .responseBodyAs(AgentMarketplaceEndpoint.DeploymentResponse.class)
            .invoke();

        // Then: Should deploy
        assertThat(response.status().isSuccess()).isTrue();
        var deployment = response.body();
        assertThat(deployment.templateId()).isEqualTo(deployId);
        assertThat(deployment.agentId()).isEqualTo("deployed-agent-001");
        assertThat(deployment.status()).isEqualTo("active");
    }

    @Test
    public void shouldIncrementDownloadsOnDeploy() {
        // Given: Template
        String downloadId = "endpoint-download-001";
        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        httpClient.POST("/marketplace/templates")
            .withRequestBody(new AgentMarketplaceEndpoint.PublishTemplateRequest(
                downloadId, "Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ))
            .invoke();

        var initialTemplate = httpClient
            .GET("/marketplace/templates/" + downloadId)
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateResponse.class)
            .invoke().body();

        int initialDownloads = initialTemplate.downloads();

        // When: Deploying
        httpClient.POST("/marketplace/deploy/" + downloadId)
            .withRequestBody(new AgentMarketplaceEndpoint.DeployTemplateRequest(
                "agent-001", Map.of()
            ))
            .invoke();

        // Then: Download count should increment
        var updatedTemplate = httpClient
            .GET("/marketplace/templates/" + downloadId)
            .responseBodyAs(AgentMarketplaceEndpoint.TemplateResponse.class)
            .invoke().body();

        assertThat(updatedTemplate.downloads()).isEqualTo(initialDownloads + 1);
    }
}
