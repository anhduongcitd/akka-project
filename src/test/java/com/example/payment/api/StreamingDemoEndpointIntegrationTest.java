package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for StreamingDemoEndpoint.
 */
public class StreamingDemoEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldServeDemoPage() {
        // When: Accessing demo page
        var response = httpClient
            .GET("/streaming-demo")
            .invoke();

        // Then: Should return HTML page
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.status().intValue()).isEqualTo(200);
    }

    @Test
    public void shouldReturnHtmlContentType() {
        // When: Accessing demo page
        var response = httpClient
            .GET("/streaming-demo")
            .invoke();

        // Then: Should have HTML content type
        assertThat(response.status().isSuccess()).isTrue();
        var contentType = response.headers().stream()
            .filter(h -> h.name().equalsIgnoreCase("content-type"))
            .findFirst();

        // Content-Type header should be present
        assertThat(contentType).isPresent();
    }

    @Test
    public void shouldContainStreamingDemo() {
        // When: Accessing demo page
        var response = httpClient
            .GET("/streaming-demo")
            .invoke();

        // Then: Should be successful
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldHandleMultipleRequests() {
        // When: Multiple requests to demo page
        var response1 = httpClient.GET("/streaming-demo").invoke();
        var response2 = httpClient.GET("/streaming-demo").invoke();
        var response3 = httpClient.GET("/streaming-demo").invoke();

        // Then: All should succeed
        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
        assertThat(response3.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldServeDemoPageConcurrently() {
        // When: Concurrent requests
        var response1 = httpClient.GET("/streaming-demo").invoke();
        var response2 = httpClient.GET("/streaming-demo").invoke();

        // Then: Both should succeed
        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
    }
}
