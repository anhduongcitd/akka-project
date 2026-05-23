package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentStreamingEndpoint.
 */
public class AgentStreamingEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldStreamSupportResponse() {
        // Given: Support request
        var request = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_123",
            "Where is my payment?"
        );

        // When: Streaming support
        var response = httpClient
            .POST("/stream/support")
            .withRequestBody(request)
            .invoke();

        // Then: Should return streaming response
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.status().intValue()).isEqualTo(200);
    }

    @Test
    public void shouldStreamSupportResponseGrouped() {
        // Given: Support request
        var request = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_456",
            "When will I receive my refund?"
        );

        // When: Streaming support (grouped)
        var response = httpClient
            .POST("/stream/support/grouped")
            .withRequestBody(request)
            .invoke();

        // Then: Should return streaming response
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.status().intValue()).isEqualTo(200);
    }

    @Test
    public void shouldStreamWithSessionId() {
        // Given: Request with session ID
        var request = new AgentStreamingEndpoint.SupportRequest(
            "session-test-001",
            "cust_789",
            "Show me my transaction history"
        );

        // When: Streaming
        var response = httpClient
            .POST("/stream/support")
            .withRequestBody(request)
            .invoke();

        // Then: Should return streaming response
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldStreamLongQuery() {
        // Given: Long query
        var request = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_long",
            "I made a payment of $500 on January 10th for order #12345, but I haven't received confirmation yet. Can you check the status?"
        );

        // When: Streaming
        var response = httpClient
            .POST("/stream/support")
            .withRequestBody(request)
            .invoke();

        // Then: Should handle long query
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldStreamMultipleRequests() {
        // Given: Multiple requests
        var request1 = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_multi_1",
            "Question 1"
        );

        var request2 = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_multi_2",
            "Question 2"
        );

        // When: Streaming multiple
        var response1 = httpClient
            .POST("/stream/support")
            .withRequestBody(request1)
            .invoke();

        var response2 = httpClient
            .POST("/stream/support")
            .withRequestBody(request2)
            .invoke();

        // Then: Both should succeed
        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldHandleEmptyQuery() {
        // Given: Empty query
        var request = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_empty",
            ""
        );

        // When: Streaming
        var response = httpClient
            .POST("/stream/support")
            .withRequestBody(request)
            .invoke();

        // Then: Should handle empty query
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldStreamGroupedTokens() {
        // Given: Request for grouped streaming
        var request = new AgentStreamingEndpoint.SupportRequest(
            "session-grouped-001",
            "cust_grouped",
            "Tell me about your refund policy in detail"
        );

        // When: Streaming grouped
        var response = httpClient
            .POST("/stream/support/grouped")
            .withRequestBody(request)
            .invoke();

        // Then: Should return grouped stream
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldHandleSpecialCharacters() {
        // Given: Query with special characters
        var request = new AgentStreamingEndpoint.SupportRequest(
            null,
            "cust_special",
            "Transaction #abc-123 (amount: $50.00) - status?"
        );

        // When: Streaming
        var response = httpClient
            .POST("/stream/support")
            .withRequestBody(request)
            .invoke();

        // Then: Should handle special characters
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldStreamConcurrently() {
        // Given: Multiple concurrent requests
        var request1 = new AgentStreamingEndpoint.SupportRequest(
            "session-concurrent-1",
            "cust_concurrent_1",
            "Query 1"
        );

        var request2 = new AgentStreamingEndpoint.SupportRequest(
            "session-concurrent-2",
            "cust_concurrent_2",
            "Query 2"
        );

        var request3 = new AgentStreamingEndpoint.SupportRequest(
            "session-concurrent-3",
            "cust_concurrent_3",
            "Query 3"
        );

        // When: Streaming concurrently
        var response1 = httpClient.POST("/stream/support").withRequestBody(request1).invoke();
        var response2 = httpClient.POST("/stream/support").withRequestBody(request2).invoke();
        var response3 = httpClient.POST("/stream/support").withRequestBody(request3).invoke();

        // Then: All should succeed
        assertThat(response1.status().isSuccess()).isTrue();
        assertThat(response2.status().isSuccess()).isTrue();
        assertThat(response3.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldHealthCheck() {
        // When: Health check
        var response = httpClient
            .GET("/stream/health")
            .responseBodyAs(String.class)
            .invoke();

        // Then: Should return healthy
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo("Streaming endpoint active");
    }
}
