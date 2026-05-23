package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;

/**
 * Streaming Support Agent - Real-time streaming responses for customer support.
 *
 * Optimized for:
 * - Token-by-token streaming
 * - Real-time user experience
 * - Progressive response display
 *
 * Note: This agent streams plain text responses without structured output.
 * For structured responses (JSON), use CustomerSupportAgent instead.
 */
@Component(id = "streaming-support")
public class StreamingSupportAgent extends Agent {

    public record StreamRequest(
        String customerId,
        String query
    ) {}

    /**
     * Stream support response token by token.
     */
    public StreamEffect handleQuery(StreamRequest request) {
        String systemPrompt = """
            You are a helpful payment support agent for an online payment service.

            Your role:
            - Answer customer questions about payments, refunds, and transactions
            - Be friendly, professional, and concise
            - Provide helpful information about payment status
            - Suggest next steps when appropriate

            Context:
            - Customer ID: %s
            - Question: %s

            Provide a clear, conversational response to help the customer.
            """.formatted(request.customerId(), request.query());

        return streamEffects()
            .systemMessage(systemPrompt)
            .userMessage(request.query())
            .thenReply();
    }
}
