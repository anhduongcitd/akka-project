package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DashboardEndpoint.
 *
 * Tests static dashboard UI serving.
 */
public class DashboardEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldServeDashboardHtml() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should return HTML content
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.status().intValue()).isEqualTo(200);

        String html = response.body();
        assertThat(html).isNotNull();
        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("Agent Analytics Dashboard");
    }

    @Test
    public void shouldContainChartJsLibrary() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should include Chart.js library
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("chart.js");
        assertThat(html).contains("cdn.jsdelivr.net");
    }

    @Test
    public void shouldContainAllChartElements() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should contain all chart canvases
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();

        // Check for chart canvas elements
        assertThat(html).contains("successRateChart");
        assertThat(html).contains("costChart");
        assertThat(html).contains("latencyChart");
        assertThat(html).contains("callsChart");
    }

    @Test
    public void shouldContainSummaryCardsSection() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should contain summary cards section
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("summary-cards");
        assertThat(html).contains("summaryCards");
    }

    @Test
    public void shouldContainAgentsTable() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should contain agents table
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("agentsTable");
        assertThat(html).contains("<table");
        assertThat(html).contains("<thead>");
        assertThat(html).contains("<tbody>");
    }

    @Test
    public void shouldHaveAutoRefreshScript() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should contain auto-refresh logic
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("setInterval");
        assertThat(html).contains("loadData");
        assertThat(html).contains("5000"); // 5 second refresh interval
    }

    @Test
    public void shouldMakeApiCallsToAnalyticsEndpoint() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: JavaScript should call analytics API
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("/analytics/agents");
        assertThat(html).contains("/analytics/costs");
        assertThat(html).contains("fetch");
    }

    @Test
    public void shouldHaveResponsiveDesign() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should include responsive CSS
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("viewport");
        assertThat(html).contains("@media");
        assertThat(html).contains("grid-template-columns");
    }

    @Test
    public void shouldHaveStatusBadgeStyles() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should include status badge CSS
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("status-badge");
        assertThat(html).contains("status-active");
        assertThat(html).contains("status-idle");
        assertThat(html).contains("status-error");
    }

    @Test
    public void shouldHaveRefreshIndicator() {
        // When: Accessing /dashboard
        var response = httpClient
            .GET("/dashboard")
            .invoke();

        // Then: Should include refresh indicator
        assertThat(response.status().isSuccess()).isTrue();
        String html = response.body();
        assertThat(html).contains("refreshIndicator");
        assertThat(html).contains("Last updated");
    }
}
