package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;

import java.util.Collection;
import java.util.Map;

/**
 * Summarizer Agent - Combines results from multiple agents into coherent response.
 *
 * Capabilities:
 * - Synthesize outputs from multiple agents
 * - Resolve conflicts between agent recommendations
 * - Provide unified, coherent answer to user
 * - Prioritize critical information
 */
@Component(id = "summarizer-agent")
public class SummarizerAgent extends Agent {

    // Request/Response records
    public record SummarizationRequest(
        String originalQuery,               // User's original question
        Map<String, String> agentResponses, // Map of agentId -> response
        String executionStrategy            // Strategy used (SEQUENTIAL/PARALLEL/HYBRID)
    ) {}

    public record SummarizedResponse(
        String answer,                  // Unified answer to user
        String confidence,              // HIGH, MEDIUM, LOW based on agent agreement
        Map<String, String> sources,    // Which agents contributed what
        String recommendation           // Final recommendation if applicable
    ) {}

    /**
     * Main command handler - summarize multi-agent results.
     */
    public Effect<SummarizedResponse> summarize(SummarizationRequest request) {
        String systemPrompt = """
            You are an expert summarization agent for a payment processing system.

            Your role:
            - Combine responses from multiple AI agents into a single coherent answer
            - Resolve conflicts or contradictions between agents
            - Prioritize critical information (fraud alerts, failures, errors)
            - Maintain professional, helpful tone
            - Give credit to source agents when appropriate

            Guidelines:
            1. If agents agree: Synthesize into clear, confident answer
            2. If agents conflict: Explain different perspectives, recommend safest option
            3. If fraud detected: ALWAYS prioritize fraud agent's assessment
            4. If failure detected: Include recovery suggestions from payment-assistant
            5. Keep response concise but complete (2-4 sentences ideal)

            Confidence Levels:
            - HIGH: All agents agree, clear answer
            - MEDIUM: Some uncertainty or conflicting info
            - LOW: Agents disagree or insufficient information

            Response Format:
            {
              "answer": "Clear, concise answer to user's question",
              "confidence": "HIGH",
              "sources": {
                "fraud-analyst": "No fraud detected",
                "payment-assistant": "Card expired, update required"
              },
              "recommendation": "Update your card expiration date to retry payment"
            }

            Important:
            - Never mention "agents" to the user - speak as unified system
            - Don't say "The fraud agent said..." - say "Our fraud detection shows..."
            - Be direct and actionable
            - If critical issue (fraud, failure), lead with that
            """;

        String userMessage = buildSummarizationPrompt(request);

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(userMessage)
            .responseConformsTo(SummarizedResponse.class)
            .onFailure(ex -> createFallbackSummary(request))
            .thenReply();
    }

    /**
     * Build detailed prompt with agent responses.
     */
    private String buildSummarizationPrompt(SummarizationRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize these agent responses into a unified answer:\n\n");
        prompt.append("Original User Query: ").append(request.originalQuery()).append("\n\n");
        prompt.append("Execution Strategy: ").append(request.executionStrategy()).append("\n\n");
        prompt.append("Agent Responses:\n");

        for (var entry : request.agentResponses().entrySet()) {
            prompt.append("- ").append(entry.getKey()).append(": ")
                  .append(entry.getValue()).append("\n");
        }

        prompt.append("\nProvide a unified, coherent response to the user.");
        return prompt.toString();
    }

    /**
     * Create fallback summary when AI fails.
     */
    private SummarizedResponse createFallbackSummary(SummarizationRequest request) {
        // Combine agent responses with simple concatenation
        String combinedAnswer = String.join(" ", request.agentResponses().values());

        return new SummarizedResponse(
            combinedAnswer,
            "LOW",
            request.agentResponses(),
            "Please review the information above. Contact support if you need further assistance."
        );
    }
}
