package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.ConversationMemory;

import java.time.Instant;

/**
 * Conversation Memory View - Query conversation history by agent or time.
 *
 * Queries:
 * - Find all conversations for an agent
 * - Find active conversations (recent activity)
 * - Find conversations by date range
 */
@Component(id = "conversation-memory-view")
public class ConversationMemoryView extends View {

    /**
     * Conversation summary row.
     */
    public record ConversationEntry(
        String sessionId,
        String agentId,
        int turnCount,
        String summary,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record ConversationEntries(java.util.List<ConversationEntry> conversations) {}

    /**
     * Query conversations by agent ID.
     */
    @Query("SELECT * AS conversations FROM conversation_memory WHERE agentId = :agentId ORDER BY updatedAt DESC")
    public QueryEffect<ConversationEntries> getByAgent(String agentId) {
        return queryResult();
    }

    /**
     * Query active conversations (updated in last 24 hours).
     */
    @Query("SELECT * AS conversations FROM conversation_memory WHERE updatedAt > :since ORDER BY updatedAt DESC")
    public QueryEffect<ConversationEntries> getActive(Instant since) {
        return queryResult();
    }

    public record DateRangeQuery(Instant startDate, Instant endDate) {}

    /**
     * Query conversations by date range.
     */
    @Query("SELECT * AS conversations FROM conversation_memory WHERE createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    public QueryEffect<ConversationEntries> getByDateRange(DateRangeQuery query) {
        return queryResult();
    }

    /**
     * Get single conversation.
     */
    @Query("SELECT * FROM conversation_memory WHERE sessionId = :sessionId")
    public QueryEffect<ConversationEntry> getBySession(String sessionId) {
        return queryResult();
    }

    /**
     * Table updater - consumes from ConversationMemoryEntity.
     */
    @Consume.FromKeyValueEntity(ConversationMemoryEntity.class)
    public static class ConversationMemoryTableUpdater extends TableUpdater<ConversationEntry> {

        public Effect<ConversationEntry> onUpdate(ConversationMemory memory) {
            if (memory == null || memory.agentId().isEmpty()) {
                return effects().ignore();
            }

            var entry = new ConversationEntry(
                memory.sessionId(),
                memory.agentId(),
                memory.getTurnCount(),
                memory.summary(),
                memory.createdAt(),
                memory.updatedAt()
            );

            return effects().updateRow(entry);
        }
    }
}
