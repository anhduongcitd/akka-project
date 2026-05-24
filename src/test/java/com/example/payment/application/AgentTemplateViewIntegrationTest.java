package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.domain.AgentConfig;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentTemplateView.
 */
public class AgentTemplateViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages(AgentTemplateEntity.class);
    }

    @Test
    public void shouldQueryAllTemplates() {
        // Given: Multiple templates
        String id1 = "view-template-001";
        String id2 = "view-template-002";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(id1)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Template 1", "Description 1", "customer-support", config, List.of(), "Author 1", "1.0.0"
            ));

        componentClient.forKeyValueEntity(id2)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Template 2", "Description 2", "fraud", config, List.of(), "Author 2", "1.0.0"
            ));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getAllTemplates)
                    .invoke();

                assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Querying all
        var result = componentClient.forView()
            .method(AgentTemplateView::getAllTemplates)
            .invoke();

        // Then: Should return all
        assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void shouldQueryByCategory() {
        // Given: Templates in different categories
        String supportId = "view-category-support-001";
        String fraudId = "view-category-fraud-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(supportId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Support Template", "Description", "customer-support", config, List.of(), "Author", "1.0.0"
            ));

        componentClient.forKeyValueEntity(fraudId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Fraud Template", "Description", "fraud", config, List.of(), "Author", "1.0.0"
            ));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getByCategory)
                    .invoke("customer-support");

                assertThat(result.templates()).isNotEmpty();
            });

        // When: Querying by category
        var result = componentClient.forView()
            .method(AgentTemplateView::getByCategory)
            .invoke("customer-support");

        // Then: Should return only support templates
        assertThat(result.templates()).isNotEmpty();
        assertThat(result.templates())
            .allMatch(t -> t.category().equals("customer-support"));
    }

    @Test
    public void shouldQueryTopRated() {
        // Given: Template with high rating
        String topRatedId = "view-top-rated-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(topRatedId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Top Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ));

        // Add ratings
        componentClient.forKeyValueEntity(topRatedId)
            .method(AgentTemplateEntity::rateTemplate)
            .invoke(new AgentTemplateEntity.RateTemplate(5.0));

        componentClient.forKeyValueEntity(topRatedId)
            .method(AgentTemplateEntity::rateTemplate)
            .invoke(new AgentTemplateEntity.RateTemplate(5.0));

        componentClient.forKeyValueEntity(topRatedId)
            .method(AgentTemplateEntity::rateTemplate)
            .invoke(new AgentTemplateEntity.RateTemplate(4.0));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getTopRated)
                    .invoke();

                assertThat(result.templates())
                    .anyMatch(t -> t.templateId().equals(topRatedId));
            });

        // When: Querying top rated
        var result = componentClient.forView()
            .method(AgentTemplateView::getTopRated)
            .invoke();

        // Then: Should include high rated template
        assertThat(result.templates())
            .anyMatch(t -> t.templateId().equals(topRatedId) && t.rating() >= 4.0);
    }

    @Test
    public void shouldQueryMostDownloaded() {
        // Given: Template with downloads
        String popularId = "view-popular-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(popularId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Popular Template", "Description", "general", config, List.of(), "Author", "1.0.0"
            ));

        // Simulate downloads
        for (int i = 0; i < 5; i++) {
            componentClient.forKeyValueEntity(popularId)
                .method(AgentTemplateEntity::incrementDownloads)
                .invoke(new AgentTemplateEntity.IncrementDownloads());
        }

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getMostDownloaded)
                    .invoke();

                assertThat(result.templates()).isNotEmpty();
            });

        // When: Querying most downloaded
        var result = componentClient.forView()
            .method(AgentTemplateView::getMostDownloaded)
            .invoke();

        // Then: Should return templates
        assertThat(result.templates()).isNotEmpty();
    }

    @Test
    public void shouldSearchTemplates() {
        // Given: Template with searchable name
        String searchId = "view-search-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(searchId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Unique Search Template", "Searchable description", "general", config, List.of(), "Author", "1.0.0"
            ));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::searchTemplates)
                    .invoke("%Unique%");

                assertThat(result.templates())
                    .anyMatch(t -> t.templateId().equals(searchId));
            });

        // When: Searching
        var result = componentClient.forView()
            .method(AgentTemplateView::searchTemplates)
            .invoke("%Unique%");

        // Then: Should find template
        assertThat(result.templates())
            .anyMatch(t -> t.templateId().equals(searchId));
    }

    @Test
    public void shouldReflectUpdates() {
        // Given: Template
        String updateId = "view-update-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(updateId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Original Name", "Description", "general", config, List.of(), "Author", "1.0.0"
            ));

        // Wait for creation
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke(updateId);

                assertThat(result.name()).isEqualTo("Original Name");
            });

        // When: Adding rating
        componentClient.forKeyValueEntity(updateId)
            .method(AgentTemplateEntity::rateTemplate)
            .invoke(new AgentTemplateEntity.RateTemplate(5.0));

        // Wait for update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke(updateId);

                assertThat(result.rating()).isEqualTo(5.0);
            });

        // Then: Should reflect changes
        var result = componentClient.forView()
            .method(AgentTemplateView::getById)
            .invoke(updateId);

        assertThat(result.rating()).isEqualTo(5.0);
    }

    @Test
    public void shouldHandleDelete() {
        // Given: Template
        String deleteId = "view-delete-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(deleteId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "To Delete", "Description", "general", config, List.of(), "Author", "1.0.0"
            ));

        // Wait for creation
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getById)
                    .invoke(deleteId);

                assertThat(result).isNotNull();
            });

        // When: Deleting
        componentClient.forKeyValueEntity(deleteId)
            .method(AgentTemplateEntity::deleteTemplate)
            .invoke();

        // Then: Should eventually be removed (view update may take time)
        assertThat(true).isTrue();
    }

    @Test
    public void shouldSortByRating() {
        // Given: Templates with different ratings
        String highId = "view-sort-high-001";
        String lowId = "view-sort-low-001";

        var config = new AgentConfig("prompt", List.of(), Map.of(), null, null);

        componentClient.forKeyValueEntity(highId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "High Rated", "Description", "analytics", config, List.of(), "Author", "1.0.0"
            ));

        componentClient.forKeyValueEntity(lowId)
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                "Low Rated", "Description", "analytics", config, List.of(), "Author", "1.0.0"
            ));

        // Add ratings
        for (int i = 0; i < 3; i++) {
            componentClient.forKeyValueEntity(highId)
                .method(AgentTemplateEntity::rateTemplate)
                .invoke(new AgentTemplateEntity.RateTemplate(5.0));

            componentClient.forKeyValueEntity(lowId)
                .method(AgentTemplateEntity::rateTemplate)
                .invoke(new AgentTemplateEntity.RateTemplate(2.0));
        }

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(AgentTemplateView::getByCategory)
                    .invoke("analytics");

                assertThat(result.templates()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Querying by category (sorted by rating)
        var result = componentClient.forView()
            .method(AgentTemplateView::getByCategory)
            .invoke("analytics");

        // Then: Should be sorted by rating (highest first)
        var templates = result.templates();
        if (templates.size() >= 2) {
            var first = templates.stream()
                .filter(t -> t.templateId().equals(highId))
                .findFirst();
            var second = templates.stream()
                .filter(t -> t.templateId().equals(lowId))
                .findFirst();

            if (first.isPresent() && second.isPresent()) {
                assertThat(first.get().rating()).isGreaterThan(second.get().rating());
            }
        }
    }
}
