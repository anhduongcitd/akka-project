package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.List;

/**
 * Conversation Memory - Persistent storage for agent conversation history.
 *
 * Stores:
 * - Full conversation turns (user messages + agent responses)
 * - Metadata (timestamps, agent ID, session context)
 * - Summary for long conversations
 */
public record ConversationMemory(
    String sessionId,
    String agentId,
    List<ConversationTurn> turns,
    String summary,
    Instant createdAt,
    Instant updatedAt
) {
    public ConversationMemory {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be null or blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
    }

    /**
     * Add a new conversation turn.
     */
    public ConversationMemory addTurn(ConversationTurn turn) {
        var updatedTurns = new java.util.ArrayList<>(turns);
        updatedTurns.add(turn);
        return new ConversationMemory(
            sessionId,
            agentId,
            updatedTurns,
            summary,
            createdAt,
            Instant.now()
        );
    }

    /**
     * Update conversation summary.
     */
    public ConversationMemory withSummary(String newSummary) {
        return new ConversationMemory(
            sessionId,
            agentId,
            turns,
            newSummary,
            createdAt,
            Instant.now()
        );
    }

    /**
     * Get recent turns (last N).
     */
    public List<ConversationTurn> getRecentTurns(int count) {
        int size = turns.size();
        int fromIndex = Math.max(0, size - count);
        return turns.subList(fromIndex, size);
    }

    /**
     * Get total turn count.
     */
    public int getTurnCount() {
        return turns.size();
    }

    /**
     * Check if conversation is empty.
     */
    public boolean isEmpty() {
        return turns.isEmpty();
    }

    /**
     * Get last turn.
     */
    public ConversationTurn getLastTurn() {
        return turns.isEmpty() ? null : turns.get(turns.size() - 1);
    }

    /**
     * Single conversation turn.
     */
    public record ConversationTurn(
        String userMessage,
        String agentResponse,
        Instant timestamp,
        String context
    ) {
        public ConversationTurn {
            if (userMessage == null || userMessage.isBlank()) {
                throw new IllegalArgumentException("User message cannot be null or blank");
            }
            if (agentResponse == null || agentResponse.isBlank()) {
                throw new IllegalArgumentException("Agent response cannot be null or blank");
            }
        }
    }
}
