package com.example.payment.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.DynamicAgentWorkflow;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.application.AgentPerformanceEntity;
import com.example.payment.agents.domain.AgentPerformance;

import java.util.Map;
import java.util.UUID;

/**
 * Agent Collaboration Endpoint - Multi-agent orchestration API.
 *
 * Endpoints:
 * - POST /agents/collaborate - Multi-agent collaboration with dynamic planning
 * - GET /agents/performance/{agentId} - Query agent performance metrics
 */
@HttpEndpoint("/agents")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentCollaborationEndpoint {

    private final ComponentClient componentClient;

    public AgentCollaborationEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record CollaborationRequest(
        String userQuery,
        String customerId,
        String context,
        String sessionId  // Optional - generated if not provided
    ) {}

    public record CollaborationResponse(
        String sessionId,
        String answer,
        String confidence,
        ExecutionPlan planUsed,
        Map<String, String> agentContributions,
        String status  // COMPLETED, PENDING, FAILED
    ) {}

    public record PerformanceResponse(
        String agentId,
        int totalCalls,
        int successfulCalls,
        int failedCalls,
        double successRate,
        double averageLatencyMs,
        double averageTokensPerCall,
        double averageCostPerCall,
        double totalCostUsd
    ) {}

    /**
     * Start multi-agent collaboration with dynamic planning.
     *
     * The workflow will:
     * 1. Use PlannerAgent to analyze query and create execution plan
     * 2. Execute plan steps (sequential/parallel/hybrid)
     * 3. Track performance for each agent call
     * 4. Use SummarizerAgent to combine results
     * 5. Return unified response
     */
    @Post("/collaborate")
    public CollaborationResponse collaborate(CollaborationRequest request) {
        // Generate session ID if not provided
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
            ? request.sessionId()
            : UUID.randomUUID().toString();

        // Start workflow
        var workflowRequest = new DynamicAgentWorkflow.CollaborationRequest(
            sessionId,
            request.userQuery(),
            request.customerId(),
            request.context()
        );

        componentClient
            .forWorkflow(sessionId)
            .method(DynamicAgentWorkflow::start)
            .invoke(workflowRequest);

        // Get result (workflow is async, so this may return PENDING)
        try {
            var result = componentClient
                .forWorkflow(sessionId)
                .method(DynamicAgentWorkflow::getResult)
                .invoke();

            return new CollaborationResponse(
                sessionId,
                result.answer(),
                result.confidence(),
                result.planUsed(),
                result.agentContributions(),
                "COMPLETED"
            );
        } catch (Exception ex) {
            // Workflow not yet completed
            return new CollaborationResponse(
                sessionId,
                "Processing...",
                "PENDING",
                null,
                Map.of(),
                "PENDING"
            );
        }
    }

    /**
     * Get performance metrics for a specific agent.
     *
     * Agent IDs:
     * - customer-support
     * - fraud-analyst
     * - payment-assistant
     * - planner-agent
     * - summarizer-agent
     */
    @Get("/performance/{agentId}")
    public PerformanceResponse getPerformance(String agentId) {
        var performance = componentClient
            .forKeyValueEntity(agentId)
            .method(AgentPerformanceEntity::getPerformance)
            .invoke();

        return toApi(performance);
    }

    /**
     * Convert domain AgentPerformance to API response.
     */
    private PerformanceResponse toApi(AgentPerformance performance) {
        return new PerformanceResponse(
            performance.agentId(),
            performance.totalCalls(),
            performance.successfulCalls(),
            performance.failedCalls(),
            performance.getSuccessRate(),
            performance.averageLatencyMs(),
            performance.getAverageTokensPerCall(),
            performance.getAverageCostPerCall(),
            performance.totalCostUsd()
        );
    }
}
