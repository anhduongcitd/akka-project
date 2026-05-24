package com.example.payment.application;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ConversationMemoryView.
 */
public class ConversationMemoryViewIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages(ConversationMemoryEntity.class);
    }

    @Test
    public void shouldQueryByAgent() {
        // Given: Conversations for agent
        String agentId = "test-agent-view-1";
        String session1 = "view-session-001";
        String session2 = "view-session-002";

        componentClient.forKeyValueEntity(session1)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        componentClient.forKeyValueEntity(session2)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getByAgent)
                    .invoke(agentId);

                assertThat(result.conversations()).hasSizeGreaterThanOrEqualTo(2);
            });

        // When: Querying by agent
        var result = componentClient.forView()
            .method(ConversationMemoryView::getByAgent)
            .invoke(agentId);

        // Then: Should return conversations
        assertThat(result.conversations()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.conversations())
            .allMatch(conv -> conv.agentId().equals(agentId));
    }

    @Test
    public void shouldQueryActiveConversations() {
        // Given: New conversation
        String sessionId = "view-session-active-001";
        String agentId = "active-agent";

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Question", "Answer", "context"));

        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getActive)
                    .invoke(since);

                assertThat(result.conversations()).isNotEmpty();
            });

        // When: Querying active conversations
        var result = componentClient.forView()
            .method(ConversationMemoryView::getActive)
            .invoke(since);

        // Then: Should include recent conversation
        assertThat(result.conversations()).isNotEmpty();
        assertThat(result.conversations())
            .anyMatch(conv -> conv.sessionId().equals(sessionId));
    }

    @Test
    public void shouldQueryBySession() {
        // Given: Specific conversation
        String sessionId = "view-session-specific-001";
        String agentId = "specific-agent";

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q", "A", "ctx"));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getBySession)
                    .invoke(sessionId);

                assertThat(result).isNotNull();
                assertThat(result.sessionId()).isEqualTo(sessionId);
            });

        // When: Querying by session
        var result = componentClient.forView()
            .method(ConversationMemoryView::getBySession)
            .invoke(sessionId);

        // Then: Should return conversation
        assertThat(result).isNotNull();
        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.agentId()).isEqualTo(agentId);
        assertThat(result.turnCount()).isEqualTo(1);
    }

    @Test
    public void shouldTrackTurnCount() {
        // Given: Conversation with multiple turns
        String sessionId = "view-session-count-001";
        String agentId = "count-agent";

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        for (int i = 1; i <= 5; i++) {
            componentClient.forKeyValueEntity(sessionId)
                .method(ConversationMemoryEntity::addTurn)
                .invoke(new ConversationMemoryEntity.AddTurn("Q" + i, "A" + i, "ctx"));
        }

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getBySession)
                    .invoke(sessionId);

                assertThat(result.turnCount()).isEqualTo(5);
            });

        // When: Querying conversation
        var result = componentClient.forView()
            .method(ConversationMemoryView::getBySession)
            .invoke(sessionId);

        // Then: Should track turn count
        assertThat(result.turnCount()).isEqualTo(5);
    }

    @Test
    public void shouldTrackSummary() {
        // Given: Conversation with summary
        String sessionId = "view-session-summary-001";
        String agentId = "summary-agent";

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::updateSummary)
            .invoke(new ConversationMemoryEntity.UpdateSummary("Test summary content"));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getBySession)
                    .invoke(sessionId);

                assertThat(result.summary()).isEqualTo("Test summary content");
            });

        // When: Querying conversation
        var result = componentClient.forView()
            .method(ConversationMemoryView::getBySession)
            .invoke(sessionId);

        // Then: Should include summary
        assertThat(result.summary()).isEqualTo("Test summary content");
    }

    @Test
    public void shouldUpdateTimestamps() {
        // Given: Conversation
        String sessionId = "view-session-timestamp-001";
        String agentId = "timestamp-agent";

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(agentId));

        Instant beforeUpdate = Instant.now();

        // Wait a moment
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        componentClient.forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q", "A", "ctx"));

        // Wait for view update
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var result = componentClient.forView()
                    .method(ConversationMemoryView::getBySession)
                    .invoke(sessionId);

                assertThat(result.updatedAt()).isAfter(beforeUpdate);
            });

        // When: Querying conversation
        var result = componentClient.forView()
            .method(ConversationMemoryView::getBySession)
            .invoke(sessionId);

        // Then: Should have updated timestamp
        assertThat(result.updatedAt()).isAfter(beforeUpdate);
    }
}
