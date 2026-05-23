package com.example.payment.api;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import com.example.payment.agents.StreamingSupportAgent;

import java.time.Duration;
import java.util.UUID;

/**
 * Agent Streaming Endpoint - Real-time token streaming for agent responses.
 *
 * Features:
 * - Server-Sent Events (SSE) for streaming agent responses
 * - Token-by-token streaming for LLM responses
 * - Real-time progress updates
 * - Non-blocking streaming
 *
 * Endpoints:
 * - POST /stream/support - Stream customer support agent responses
 * - POST /stream/support/grouped - Stream with grouped tokens (reduced overhead)
 */
@HttpEndpoint("/stream")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentStreamingEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public AgentStreamingEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request records
    public record SupportRequest(
        String sessionId,
        String customerId,
        String query
    ) {}

    /**
     * Stream customer support agent response (token by token).
     */
    @Post("/support")
    public HttpResponse streamSupport(SupportRequest request) {
        String sessionId = request.sessionId() != null
            ? request.sessionId()
            : UUID.randomUUID().toString();

        Source<String, NotUsed> tokenStream = componentClient
            .forAgent()
            .inSession(sessionId)
            .tokenStream(StreamingSupportAgent::handleQuery)
            .source(new StreamingSupportAgent.StreamRequest(
                request.customerId(),
                request.query()
            ));

        return HttpResponses.streamText(tokenStream);
    }

    /**
     * Stream customer support agent response (grouped tokens for efficiency).
     */
    @Post("/support/grouped")
    public HttpResponse streamSupportGrouped(SupportRequest request) {
        String sessionId = request.sessionId() != null
            ? request.sessionId()
            : UUID.randomUUID().toString();

        Source<String, NotUsed> tokenStream = componentClient
            .forAgent()
            .inSession(sessionId)
            .tokenStream(StreamingSupportAgent::handleQuery)
            .source(new StreamingSupportAgent.StreamRequest(
                request.customerId(),
                request.query()
            ));

        // Group tokens to reduce SSE overhead
        var groupedStream = tokenStream
            .groupedWithin(20, Duration.ofMillis(100))
            .map(group -> String.join("", group));

        return HttpResponses.streamText(groupedStream);
    }

    /**
     * Health check endpoint.
     */
    @Get("/health")
    public String health() {
        return "Streaming endpoint active";
    }
}
