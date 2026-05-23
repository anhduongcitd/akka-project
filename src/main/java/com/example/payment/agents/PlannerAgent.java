package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.agent.Agent;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.agents.domain.PlanStep;

import java.util.List;

/**
 * Planner Agent - Analyzes requests and creates dynamic execution plans.
 *
 * Capabilities:
 * - Analyze user queries to determine required agents
 * - Create optimal execution strategy (sequential/parallel/hybrid)
 * - Tailor queries for each agent based on context
 * - Adapt plans based on agent availability and performance
 *
 * Available Agents:
 * - customer-support: Payment inquiries, refunds, transaction history
 * - fraud-analyst: Fraud detection, risk assessment, pattern analysis
 * - payment-assistant: Failure analysis, recovery suggestions, card issues
 */
@Component(id = "planner-agent")
public class PlannerAgent extends Agent {

    // Request/Response records
    public record PlanningRequest(
        String userQuery,           // Original user query
        String customerId,          // Customer making the request
        String context              // Additional context (transaction ID, etc.)
    ) {}

    // Available agent information for planning
    public record AgentInfo(
        String agentId,
        String capabilities,
        double averageLatencyMs,
        double successRate
    ) {}

    /**
     * Main command handler - create execution plan for user query.
     */
    public Effect<ExecutionPlan> createPlan(PlanningRequest request) {
        String systemPrompt = """
            You are an expert planning agent for a payment processing system.

            Your role:
            - Analyze user queries to determine which agents are needed
            - Create optimal execution plans with appropriate strategy
            - Tailor specific queries for each agent
            - Consider agent dependencies and data flow

            Available Agents:
            1. customer-support
               - Capabilities: Answer payment questions, check refund eligibility, transaction history
               - Use when: Customer asks about payment status, refunds, history

            2. fraud-analyst
               - Capabilities: Detect fraud patterns, risk assessment, transaction analysis
               - Use when: Checking for suspicious activity, high-value transactions, fraud investigation

            3. payment-assistant
               - Capabilities: Analyze payment failures, suggest recovery actions, check card issues
               - Use when: Payment failed, need recovery suggestions, card problems

            Execution Strategies:
            - SEQUENTIAL: One agent at a time, output of one feeds into next
              Use when: Agents depend on each other's results
              Example: Check fraud THEN suggest recovery

            - PARALLEL: Multiple agents at same time, combine results
              Use when: Independent queries that don't depend on each other
              Example: Check payment status AND refund eligibility simultaneously

            - HYBRID: Mix of sequential and parallel
              Use when: Some dependencies exist but not all
              Example: Fraud check first, THEN (support + assistant in parallel)

            Priority Rules:
            - Priority 1 (highest): Critical for answering the query
            - Priority 2: Important but not critical
            - Priority 3: Nice to have, optional

            Required vs Optional:
            - required=true: Must succeed or entire plan fails
            - required=false: Can skip if it fails, won't block other steps

            Response Format:
            {
              "steps": [
                {
                  "agentId": "fraud-analyst",
                  "query": "Analyze transaction txn_123 for fraud indicators",
                  "priority": 1,
                  "required": true
                },
                {
                  "agentId": "payment-assistant",
                  "query": "Why did transaction txn_123 fail and how to fix?",
                  "priority": 2,
                  "required": true
                }
              ],
              "strategy": "SEQUENTIAL",
              "reasoning": "Need fraud check before recovery suggestions to ensure legitimate transaction"
            }

            Guidelines:
            - Keep plans simple: 1-3 agents maximum
            - Write clear, specific queries for each agent
            - Explain your reasoning
            - Consider cost: fewer agents = lower cost
            - Default to SEQUENTIAL unless agents are truly independent
            """;

        String userMessage = String.format(
            "Create an execution plan for this request:\n\n" +
            "User Query: %s\n" +
            "Customer ID: %s\n" +
            "Context: %s\n\n" +
            "Analyze what the user wants and create the optimal plan.",
            request.userQuery(),
            request.customerId(),
            request.context() != null ? request.context() : "No additional context"
        );

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(userMessage)
            .responseConformsTo(ExecutionPlan.class)
            .onFailure(ex -> createFallbackPlan(request))
            .thenReply();
    }

    /**
     * Function tool: Get available agents and their capabilities.
     */
    @FunctionTool(description = "Get list of available agents with their capabilities and current performance metrics")
    private List<AgentInfo> getAvailableAgents() {
        // In production, query AgentPerformanceView for real metrics
        return List.of(
            new AgentInfo(
                "customer-support",
                "Payment inquiries, refund eligibility, transaction history",
                800.0,
                0.92
            ),
            new AgentInfo(
                "fraud-analyst",
                "Fraud detection, risk assessment, pattern analysis",
                1200.0,
                0.88
            ),
            new AgentInfo(
                "payment-assistant",
                "Failure analysis, recovery suggestions, card issue resolution",
                950.0,
                0.90
            )
        );
    }

    /**
     * Function tool: Analyze query complexity to determine strategy.
     */
    @FunctionTool(description = "Analyze query complexity to recommend execution strategy")
    private String analyzeQueryComplexity(String query) {
        query = query.toLowerCase();

        // Multi-part queries suggest parallel strategy
        if (query.contains(" and ") || query.contains(" also ")) {
            return "Complex multi-part query. Consider PARALLEL or HYBRID strategy.";
        }

        // Sequential indicators
        if (query.contains("then") || query.contains("after") || query.contains("before")) {
            return "Sequential dependency detected. Use SEQUENTIAL strategy.";
        }

        // Simple queries
        if (query.split(" ").length < 10) {
            return "Simple query. Single agent with SEQUENTIAL strategy recommended.";
        }

        return "Moderate complexity. SEQUENTIAL strategy is safe default.";
    }

    /**
     * Create fallback plan when AI planning fails.
     */
    private ExecutionPlan createFallbackPlan(PlanningRequest request) {
        String query = request.userQuery().toLowerCase();

        // Route to customer-support by default (safest option)
        PlanStep step = PlanStep.required(
            "customer-support",
            request.userQuery(),
            1
        );

        return new ExecutionPlan(
            List.of(step),
            "SEQUENTIAL",
            "Fallback plan: routing to customer-support agent for general inquiry handling"
        );
    }
}
