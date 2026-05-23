package com.example.payment.application;

import akka.Done;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.ConversationMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConversationMemoryEntity.
 */
public class ConversationMemoryEntityTest {

    @Test
    public void shouldCreateConversation() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("session-001", ConversationMemoryEntity::new);

        // When: Creating conversation
        var response = testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("customer-support"));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(response.getReply()).isEqualTo(Done.getInstance());

        var state = testKit.getState();
        assertThat(state.sessionId()).isEqualTo("session-001");
        assertThat(state.agentId()).isEqualTo("customer-support");
        assertThat(state.turns()).isEmpty();
        assertThat(state.isEmpty()).isTrue();
    }

    @Test
    public void shouldRejectDuplicateCreate() {
        // Given: Existing conversation
        var testKit = KeyValueEntityTestKit.of("session-002", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent-1"));

        // When: Creating again
        var response = testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent-2"));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already exists");
    }

    @Test
    public void shouldAddTurn() {
        // Given: Conversation
        var testKit = KeyValueEntityTestKit.of("session-003", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("customer-support"));

        // When: Adding turn
        var response = testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn(
                "Where is my payment?",
                "Your payment is being processed",
                "payment-inquiry"
            ));

        // Then: Should add turn
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.getTurnCount()).isEqualTo(1);
        assertThat(state.isEmpty()).isFalse();

        var turn = state.getLastTurn();
        assertThat(turn.userMessage()).isEqualTo("Where is my payment?");
        assertThat(turn.agentResponse()).isEqualTo("Your payment is being processed");
        assertThat(turn.context()).isEqualTo("payment-inquiry");
    }

    @Test
    public void shouldAddMultipleTurns() {
        // Given: Conversation
        var testKit = KeyValueEntityTestKit.of("session-004", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("support-agent"));

        // When: Adding multiple turns
        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Question 1", "Answer 1", "ctx-1"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Question 2", "Answer 2", "ctx-2"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Question 3", "Answer 3", "ctx-3"));

        // Then: Should track all turns
        var state = testKit.getState();
        assertThat(state.getTurnCount()).isEqualTo(3);
        assertThat(state.getLastTurn().userMessage()).isEqualTo("Question 3");
    }

    @Test
    public void shouldRejectAddTurnWithoutCreate() {
        // Given: Uninitialized entity
        var testKit = KeyValueEntityTestKit.of("session-005", ConversationMemoryEntity::new);

        // When: Adding turn without create
        var response = testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn(
                "Question",
                "Answer",
                "context"
            ));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("not initialized");
    }

    @Test
    public void shouldUpdateSummary() {
        // Given: Conversation with turns
        var testKit = KeyValueEntityTestKit.of("session-006", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q1", "A1", "ctx"));

        // When: Updating summary
        var response = testKit.method(ConversationMemoryEntity::updateSummary)
            .invoke(new ConversationMemoryEntity.UpdateSummary(
                "Customer asking about payment status. Agent provided tracking info."
            ));

        // Then: Should update summary
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.summary()).contains("payment status");
        assertThat(state.summary()).contains("tracking info");
    }

    @Test
    public void shouldGetRecentTurns() {
        // Given: Conversation with many turns
        var testKit = KeyValueEntityTestKit.of("session-007", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent"));

        for (int i = 1; i <= 10; i++) {
            testKit.method(ConversationMemoryEntity::addTurn)
                .invoke(new ConversationMemoryEntity.AddTurn(
                    "Question " + i,
                    "Answer " + i,
                    "context-" + i
                ));
        }

        // When: Getting recent 5 turns
        var response = testKit.method(ConversationMemoryEntity::getRecentTurns)
            .invoke(new ConversationMemoryEntity.GetRecentTurns(5));

        // Then: Should return last 5
        assertThat(response.isReply()).isTrue();
        var result = response.getReply();
        assertThat(result.turns()).hasSize(5);
        assertThat(result.turns().get(0).userMessage()).isEqualTo("Question 6");
        assertThat(result.turns().get(4).userMessage()).isEqualTo("Question 10");
    }

    @Test
    public void shouldGetRecentTurnsWithDefaultLimit() {
        // Given: Conversation with many turns
        var testKit = KeyValueEntityTestKit.of("session-008", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent"));

        for (int i = 1; i <= 15; i++) {
            testKit.method(ConversationMemoryEntity::addTurn)
                .invoke(new ConversationMemoryEntity.AddTurn("Q" + i, "A" + i, "ctx"));
        }

        // When: Getting recent turns with 0 count (should default to 10)
        var response = testKit.method(ConversationMemoryEntity::getRecentTurns)
            .invoke(new ConversationMemoryEntity.GetRecentTurns(0));

        // Then: Should return last 10
        assertThat(response.isReply()).isTrue();
        assertThat(response.getReply().turns()).hasSize(10);
    }

    @Test
    public void shouldGetFullConversation() {
        // Given: Conversation with data
        var testKit = KeyValueEntityTestKit.of("session-009", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("support-agent"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q1", "A1", "ctx1"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q2", "A2", "ctx2"));

        testKit.method(ConversationMemoryEntity::updateSummary)
            .invoke(new ConversationMemoryEntity.UpdateSummary("Test summary"));

        // When: Getting full conversation
        var response = testKit.method(ConversationMemoryEntity::getConversation)
            .invoke();

        // Then: Should return complete state
        assertThat(response.isReply()).isTrue();
        var memory = response.getReply();
        assertThat(memory.sessionId()).isEqualTo("session-009");
        assertThat(memory.agentId()).isEqualTo("support-agent");
        assertThat(memory.getTurnCount()).isEqualTo(2);
        assertThat(memory.summary()).isEqualTo("Test summary");
    }

    @Test
    public void shouldClearHistory() {
        // Given: Conversation with data
        var testKit = KeyValueEntityTestKit.of("session-010", ConversationMemoryEntity::new);
        testKit.method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation("agent"));

        testKit.method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn("Q", "A", "ctx"));

        // When: Clearing history
        var response = testKit.method(ConversationMemoryEntity::clearHistory)
            .invoke(new ConversationMemoryEntity.ClearHistory());

        // Then: Should reset to empty
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.agentId()).isEmpty();
        assertThat(state.turns()).isEmpty();
    }
}
