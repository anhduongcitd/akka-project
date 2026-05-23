package com.example.payment.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.domain.ConversationMemory;
import com.example.payment.application.ConversationMemoryEntity;
import com.example.payment.application.ConversationMemoryView;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Conversation Memory Endpoint - API for agent conversation persistence.
 *
 * Endpoints:
 * - POST /memory/conversations/{sessionId}/create - Create new conversation
 * - POST /memory/conversations/{sessionId}/turns - Add conversation turn
 * - POST /memory/conversations/{sessionId}/summary - Update summary
 * - GET /memory/conversations/{sessionId} - Get full conversation
 * - GET /memory/conversations/{sessionId}/recent - Get recent turns
 * - GET /memory/agents/{agentId}/conversations - Get all conversations for agent
 * - GET /memory/conversations/active - Get active conversations
 */
@HttpEndpoint("/memory")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ConversationMemoryEndpoint {

    private final ComponentClient componentClient;

    public ConversationMemoryEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record CreateConversationRequest(String agentId) {}

    public record AddTurnRequest(
        String userMessage,
        String agentResponse,
        String context
    ) {}

    public record UpdateSummaryRequest(String summary) {}

    public record ConversationResponse(
        String sessionId,
        String agentId,
        List<TurnResponse> turns,
        String summary,
        String createdAt,
        String updatedAt
    ) {}

    public record TurnResponse(
        String userMessage,
        String agentResponse,
        String timestamp,
        String context
    ) {}

    public record RecentTurnsResponse(
        String sessionId,
        String agentId,
        List<TurnResponse> turns,
        String summary
    ) {}

    public record ConversationListResponse(
        List<ConversationSummary> conversations
    ) {}

    public record ConversationSummary(
        String sessionId,
        String agentId,
        int turnCount,
        String summary,
        String createdAt,
        String updatedAt
    ) {}

    /**
     * Create a new conversation.
     */
    @Post("/conversations/{sessionId}/create")
    public Done createConversation(String sessionId, CreateConversationRequest request) {
        return componentClient
            .forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::createConversation)
            .invoke(new ConversationMemoryEntity.CreateConversation(request.agentId()));
    }

    /**
     * Add a conversation turn.
     */
    @Post("/conversations/{sessionId}/turns")
    public Done addTurn(String sessionId, AddTurnRequest request) {
        return componentClient
            .forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::addTurn)
            .invoke(new ConversationMemoryEntity.AddTurn(
                request.userMessage(),
                request.agentResponse(),
                request.context()
            ));
    }

    /**
     * Update conversation summary.
     */
    @Post("/conversations/{sessionId}/summary")
    public Done updateSummary(String sessionId, UpdateSummaryRequest request) {
        return componentClient
            .forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::updateSummary)
            .invoke(new ConversationMemoryEntity.UpdateSummary(request.summary()));
    }

    /**
     * Get full conversation.
     */
    @Get("/conversations/{sessionId}")
    public ConversationResponse getConversation(String sessionId) {
        var memory = componentClient
            .forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::getConversation)
            .invoke();

        return toApi(memory);
    }

    /**
     * Get recent turns (default last 10).
     */
    @Get("/conversations/{sessionId}/recent")
    public RecentTurnsResponse getRecentTurns(String sessionId, Integer count) {
        int limit = count != null && count > 0 ? count : 10;

        var response = componentClient
            .forKeyValueEntity(sessionId)
            .method(ConversationMemoryEntity::getRecentTurns)
            .invoke(new ConversationMemoryEntity.GetRecentTurns(limit));

        var turns = response.turns().stream()
            .map(this::toTurnApi)
            .toList();

        return new RecentTurnsResponse(
            response.sessionId(),
            response.agentId(),
            turns,
            response.summary()
        );
    }

    /**
     * Get all conversations for an agent.
     */
    @Get("/agents/{agentId}/conversations")
    public ConversationListResponse getAgentConversations(String agentId) {
        var entries = componentClient
            .forView()
            .method(ConversationMemoryView::getByAgent)
            .invoke(agentId);

        var summaries = entries.conversations().stream()
            .map(this::toSummaryApi)
            .toList();

        return new ConversationListResponse(summaries);
    }

    /**
     * Get active conversations (last 24 hours).
     */
    @Get("/conversations/active")
    public ConversationListResponse getActiveConversations() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        var entries = componentClient
            .forView()
            .method(ConversationMemoryView::getActive)
            .invoke(since);

        var summaries = entries.conversations().stream()
            .map(this::toSummaryApi)
            .toList();

        return new ConversationListResponse(summaries);
    }

    /**
     * Convert ConversationMemory to API response.
     */
    private ConversationResponse toApi(ConversationMemory memory) {
        var turns = memory.turns().stream()
            .map(this::toTurnApi)
            .toList();

        return new ConversationResponse(
            memory.sessionId(),
            memory.agentId(),
            turns,
            memory.summary(),
            memory.createdAt().toString(),
            memory.updatedAt().toString()
        );
    }

    /**
     * Convert ConversationTurn to API response.
     */
    private TurnResponse toTurnApi(ConversationMemory.ConversationTurn turn) {
        return new TurnResponse(
            turn.userMessage(),
            turn.agentResponse(),
            turn.timestamp().toString(),
            turn.context()
        );
    }

    /**
     * Convert ConversationEntry to API summary.
     */
    private ConversationSummary toSummaryApi(ConversationMemoryView.ConversationEntry entry) {
        return new ConversationSummary(
            entry.sessionId(),
            entry.agentId(),
            entry.turnCount(),
            entry.summary(),
            entry.createdAt().toString(),
            entry.updatedAt().toString()
        );
    }
}
