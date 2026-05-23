package com.example.payment.api;

import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ConversationMemoryEndpoint.
 */
public class ConversationMemoryEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreateConversation() {
        // Given: Create request
        String sessionId = "endpoint-session-001";
        var request = new ConversationMemoryEndpoint.CreateConversationRequest("customer-support");

        // When: Creating conversation
        var response = httpClient
            .POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(request)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body()).isEqualTo(Done.getInstance());
    }

    @Test
    public void shouldAddTurn() {
        // Given: Existing conversation
        String sessionId = "endpoint-session-002";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("agent"))
            .invoke();

        var turnRequest = new ConversationMemoryEndpoint.AddTurnRequest(
            "Where is my payment?",
            "Your payment is being processed and will arrive in 2-3 business days.",
            "payment-inquiry"
        );

        // When: Adding turn
        var response = httpClient
            .POST("/memory/conversations/" + sessionId + "/turns")
            .withRequestBody(turnRequest)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldUpdateSummary() {
        // Given: Conversation with turns
        String sessionId = "endpoint-session-003";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("agent"))
            .invoke();

        httpClient.POST("/memory/conversations/" + sessionId + "/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q", "A", "ctx"))
            .invoke();

        var summaryRequest = new ConversationMemoryEndpoint.UpdateSummaryRequest(
            "Customer asked about payment status. Agent provided tracking information."
        );

        // When: Updating summary
        var response = httpClient
            .POST("/memory/conversations/" + sessionId + "/summary")
            .withRequestBody(summaryRequest)
            .responseBodyAs(Done.class)
            .invoke();

        // Then: Should succeed
        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldGetFullConversation() {
        // Given: Conversation with data
        String sessionId = "endpoint-session-004";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("support-agent"))
            .invoke();

        httpClient.POST("/memory/conversations/" + sessionId + "/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q1", "A1", "ctx1"))
            .invoke();

        httpClient.POST("/memory/conversations/" + sessionId + "/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q2", "A2", "ctx2"))
            .invoke();

        httpClient.POST("/memory/conversations/" + sessionId + "/summary")
            .withRequestBody(new ConversationMemoryEndpoint.UpdateSummaryRequest("Summary"))
            .invoke();

        // When: Getting full conversation
        var response = httpClient
            .GET("/memory/conversations/" + sessionId)
            .responseBodyAs(ConversationMemoryEndpoint.ConversationResponse.class)
            .invoke();

        // Then: Should return complete conversation
        assertThat(response.status().isSuccess()).isTrue();
        var conversation = response.body();
        assertThat(conversation.sessionId()).isEqualTo(sessionId);
        assertThat(conversation.agentId()).isEqualTo("support-agent");
        assertThat(conversation.turns()).hasSize(2);
        assertThat(conversation.summary()).isEqualTo("Summary");
        assertThat(conversation.turns().get(0).userMessage()).isEqualTo("Q1");
        assertThat(conversation.turns().get(1).userMessage()).isEqualTo("Q2");
    }

    @Test
    public void shouldGetRecentTurns() {
        // Given: Conversation with many turns
        String sessionId = "endpoint-session-005";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("agent"))
            .invoke();

        for (int i = 1; i <= 10; i++) {
            httpClient.POST("/memory/conversations/" + sessionId + "/turns")
                .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest(
                    "Question " + i,
                    "Answer " + i,
                    "context-" + i
                ))
                .invoke();
        }

        // When: Getting recent 5 turns
        var response = httpClient
            .GET("/memory/conversations/" + sessionId + "/recent?count=5")
            .responseBodyAs(ConversationMemoryEndpoint.RecentTurnsResponse.class)
            .invoke();

        // Then: Should return last 5 turns
        assertThat(response.status().isSuccess()).isTrue();
        var recent = response.body();
        assertThat(recent.turns()).hasSize(5);
        assertThat(recent.turns().get(0).userMessage()).isEqualTo("Question 6");
        assertThat(recent.turns().get(4).userMessage()).isEqualTo("Question 10");
    }

    @Test
    public void shouldGetRecentTurnsWithDefaultLimit() {
        // Given: Conversation with many turns
        String sessionId = "endpoint-session-006";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("agent"))
            .invoke();

        for (int i = 1; i <= 15; i++) {
            httpClient.POST("/memory/conversations/" + sessionId + "/turns")
                .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q" + i, "A" + i, "ctx"))
                .invoke();
        }

        // When: Getting recent turns without count param
        var response = httpClient
            .GET("/memory/conversations/" + sessionId + "/recent")
            .responseBodyAs(ConversationMemoryEndpoint.RecentTurnsResponse.class)
            .invoke();

        // Then: Should return default 10 turns
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().turns()).hasSize(10);
    }

    @Test
    public void shouldGetAgentConversations() {
        // Given: Multiple conversations for agent
        String agentId = "endpoint-test-agent";
        String session1 = "endpoint-session-007";
        String session2 = "endpoint-session-008";

        httpClient.POST("/memory/conversations/" + session1 + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest(agentId))
            .invoke();

        httpClient.POST("/memory/conversations/" + session2 + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest(agentId))
            .invoke();

        httpClient.POST("/memory/conversations/" + session1 + "/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q", "A", "ctx"))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/memory/agents/" + agentId + "/conversations")
                    .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
                    .invoke();

                assertThat(result.body().conversations()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Getting agent conversations
        var response = httpClient
            .GET("/memory/agents/" + agentId + "/conversations")
            .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
            .invoke();

        // Then: Should return all conversations
        assertThat(response.status().isSuccess()).isTrue();
        var conversations = response.body().conversations();
        assertThat(conversations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(conversations).allMatch(conv -> conv.agentId().equals(agentId));
    }

    @Test
    public void shouldGetActiveConversations() {
        // Given: New conversation
        String sessionId = "endpoint-session-active-001";
        httpClient.POST("/memory/conversations/" + sessionId + "/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest("active-agent"))
            .invoke();

        httpClient.POST("/memory/conversations/" + sessionId + "/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q", "A", "ctx"))
            .invoke();

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = httpClient
                    .GET("/memory/conversations/active")
                    .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
                    .invoke();

                assertThat(result.body().conversations()).isNotEmpty();
            });

        // When: Getting active conversations
        var response = httpClient
            .GET("/memory/conversations/active")
            .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
            .invoke();

        // Then: Should include recent conversation
        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().conversations()).isNotEmpty();
    }

    @Test
    public void shouldTrackMultipleAgents() {
        // Given: Conversations for different agents
        String agent1 = "multi-agent-1";
        String agent2 = "multi-agent-2";

        httpClient.POST("/memory/conversations/multi-session-001/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest(agent1))
            .invoke();

        httpClient.POST("/memory/conversations/multi-session-002/create")
            .withRequestBody(new ConversationMemoryEndpoint.CreateConversationRequest(agent2))
            .invoke();

        httpClient.POST("/memory/conversations/multi-session-001/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q1", "A1", "ctx"))
            .invoke();

        httpClient.POST("/memory/conversations/multi-session-002/turns")
            .withRequestBody(new ConversationMemoryEndpoint.AddTurnRequest("Q2", "A2", "ctx"))
            .invoke();

        // Wait for view updates
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result1 = httpClient
                    .GET("/memory/agents/" + agent1 + "/conversations")
                    .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
                    .invoke();

                var result2 = httpClient
                    .GET("/memory/agents/" + agent2 + "/conversations")
                    .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
                    .invoke();

                assertThat(result1.body().conversations()).isNotEmpty();
                assertThat(result2.body().conversations()).isNotEmpty();
            });

        // When: Getting conversations for each agent
        var conv1 = httpClient.GET("/memory/agents/" + agent1 + "/conversations")
            .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
            .invoke().body().conversations();

        var conv2 = httpClient.GET("/memory/agents/" + agent2 + "/conversations")
            .responseBodyAs(ConversationMemoryEndpoint.ConversationListResponse.class)
            .invoke().body().conversations();

        // Then: Each should have independent conversations
        assertThat(conv1).isNotEmpty();
        assertThat(conv2).isNotEmpty();
        assertThat(conv1).allMatch(c -> c.agentId().equals(agent1));
        assertThat(conv2).allMatch(c -> c.agentId().equals(agent2));
    }
}
