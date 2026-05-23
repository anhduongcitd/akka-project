package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.ConversationMemory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation Memory Entity - Persists agent conversation history.
 *
 * Entity ID: sessionId
 *
 * Features:
 * - Store full conversation history
 * - Retrieve recent turns for context
 * - Automatic summarization for long conversations
 * - Query by session or agent
 */
@Component(id = "conversation-memory")
public class ConversationMemoryEntity extends KeyValueEntity<ConversationMemory> {

    // Command records
    public record CreateConversation(String agentId) {}

    public record AddTurn(
        String userMessage,
        String agentResponse,
        String context
    ) {}

    public record UpdateSummary(String summary) {}

    public record GetRecentTurns(int count) {}

    public record RecentTurnsResponse(
        String sessionId,
        String agentId,
        List<ConversationMemory.ConversationTurn> turns,
        String summary
    ) {}

    public record ClearHistory() {}

    /**
     * Initialize empty state.
     */
    @Override
    public ConversationMemory emptyState() {
        String sessionId = commandContext().entityId();
        return new ConversationMemory(
            sessionId,
            "",
            new ArrayList<>(),
            "",
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * Create a new conversation.
     */
    public Effect<Done> createConversation(CreateConversation command) {
        if (currentState() != null && !currentState().agentId().isEmpty()) {
            return effects().error("Conversation already exists");
        }

        var newState = new ConversationMemory(
            commandContext().entityId(),
            command.agentId(),
            new ArrayList<>(),
            "",
            Instant.now(),
            Instant.now()
        );

        return effects()
            .updateState(newState)
            .thenReply(Done.getInstance());
    }

    /**
     * Add a conversation turn.
     */
    public Effect<Done> addTurn(AddTurn command) {
        // Initialize if needed
        ConversationMemory state = currentState();
        if (state == null || state.agentId().isEmpty()) {
            return effects().error("Conversation not initialized. Call createConversation first.");
        }

        var turn = new ConversationMemory.ConversationTurn(
            command.userMessage(),
            command.agentResponse(),
            Instant.now(),
            command.context()
        );

        var updated = state.addTurn(turn);

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Update conversation summary.
     */
    public Effect<Done> updateSummary(UpdateSummary command) {
        if (currentState() == null || currentState().agentId().isEmpty()) {
            return effects().error("Conversation not initialized");
        }

        var updated = currentState().withSummary(command.summary());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get recent conversation turns.
     */
    public Effect<RecentTurnsResponse> getRecentTurns(GetRecentTurns command) {
        if (currentState() == null || currentState().agentId().isEmpty()) {
            return effects().error("Conversation not initialized");
        }

        int count = command.count() > 0 ? command.count() : 10;
        var recentTurns = currentState().getRecentTurns(count);

        return effects().reply(new RecentTurnsResponse(
            currentState().sessionId(),
            currentState().agentId(),
            recentTurns,
            currentState().summary()
        ));
    }

    /**
     * Get full conversation state.
     */
    public Effect<ConversationMemory> getConversation() {
        if (currentState() == null || currentState().agentId().isEmpty()) {
            return effects().error("Conversation not initialized");
        }

        return effects().reply(currentState());
    }

    /**
     * Clear conversation history (for testing or reset).
     */
    public Effect<Done> clearHistory(ClearHistory command) {
        return effects()
            .updateState(emptyState())
            .thenReply(Done.getInstance());
    }
}
