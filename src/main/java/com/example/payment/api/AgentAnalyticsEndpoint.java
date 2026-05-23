package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.AgentAnalyticsView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent Analytics Endpoint - REST API for monitoring dashboard.
 *
 * Endpoints:
 * - GET /analytics/agents - All agents summary
 * - GET /analytics/agents/{agentId} - Specific agent metrics
 * - GET /analytics/summary - Dashboard summary statistics
 * - GET /analytics/costs - Cost breakdown by agent
 */
@HttpEndpoint("/analytics")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentAnalyticsEndpoint {

    private final ComponentClient componentClient;

    public AgentAnalyticsEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Response records
    public record AgentMetrics(
        String agentId,
        String displayName,
        int totalCalls,
        int successfulCalls,
        int failedCalls,
        double successRate,
        double averageLatencyMs,
        double totalTokensUsed,
        double totalCostUsd,
        double averageCostPerCall,
        double averageTokensPerCall,
        String lastUpdated,
        String status  // ACTIVE, IDLE, ERROR
    ) {}

    public record AllAgentsResponse(
        List<AgentMetrics> agents,
        DashboardSummary summary
    ) {}

    public record DashboardSummary(
        int totalAgents,
        int totalCalls,
        int totalSuccesses,
        int totalFailures,
        double overallSuccessRate,
        double totalCostUsd,
        double totalTokensUsed,
        String timestamp
    ) {}

    public record CostBreakdown(
        List<AgentCost> byAgent,
        double totalCost,
        String period
    ) {}

    public record AgentCost(
        String agentId,
        String displayName,
        double cost,
        double percentage,
        int callCount
    ) {}

    /**
     * Get all agents with their metrics.
     */
    @Get("/agents")
    public AllAgentsResponse getAllAgents() {
        var result = componentClient
            .forView()
            .method(AgentAnalyticsView::getAllAgentMetrics)
            .invoke();

        var agents = result.agents().stream()
            .map(this::toApi)
            .toList();

        var summary = createSummary(result.agents());

        return new AllAgentsResponse(agents, summary);
    }

    /**
     * Get metrics for specific agent.
     */
    @Get("/agents/{agentId}")
    public AgentMetrics getAgentMetrics(String agentId) {
        var row = componentClient
            .forView()
            .method(AgentAnalyticsView::getAgentMetrics)
            .invoke(agentId);

        return toApi(row);
    }

    /**
     * Get dashboard summary statistics.
     */
    @Get("/summary")
    public DashboardSummary getSummary() {
        var result = componentClient
            .forView()
            .method(AgentAnalyticsView::getAllAgentMetrics)
            .invoke();

        return createSummary(result.agents());
    }

    /**
     * Get cost breakdown by agent.
     */
    @Get("/costs")
    public CostBreakdown getCostBreakdown() {
        var result = componentClient
            .forView()
            .method(AgentAnalyticsView::getAgentsByCost)
            .invoke();

        double totalCost = result.agents().stream()
            .mapToDouble(AgentAnalyticsView.AgentMetricsRow::totalCostUsd)
            .sum();

        var agentCosts = result.agents().stream()
            .map(row -> new AgentCost(
                row.agentId(),
                getDisplayName(row.agentId()),
                row.totalCostUsd(),
                totalCost > 0 ? (row.totalCostUsd() / totalCost * 100) : 0,
                row.totalCalls()
            ))
            .toList();

        return new CostBreakdown(
            agentCosts,
            totalCost,
            "all-time"
        );
    }

    /**
     * Get agents sorted by activity.
     */
    @Get("/agents/by-activity")
    public AllAgentsResponse getAgentsByActivity() {
        var result = componentClient
            .forView()
            .method(AgentAnalyticsView::getAgentsByActivity)
            .invoke();

        var agents = result.agents().stream()
            .map(this::toApi)
            .toList();

        var summary = createSummary(result.agents());

        return new AllAgentsResponse(agents, summary);
    }

    /**
     * Get agents sorted by performance (success rate).
     */
    @Get("/agents/by-performance")
    public AllAgentsResponse getAgentsByPerformance() {
        var result = componentClient
            .forView()
            .method(AgentAnalyticsView::getAgentsByPerformance)
            .invoke();

        var agents = result.agents().stream()
            .map(this::toApi)
            .toList();

        var summary = createSummary(result.agents());

        return new AllAgentsResponse(agents, summary);
    }

    /**
     * Convert view row to API response.
     */
    private AgentMetrics toApi(AgentAnalyticsView.AgentMetricsRow row) {
        return new AgentMetrics(
            row.agentId(),
            getDisplayName(row.agentId()),
            row.totalCalls(),
            row.successfulCalls(),
            row.failedCalls(),
            Math.round(row.successRate() * 100.0) / 100.0,  // Round to 2 decimals
            Math.round(row.averageLatencyMs() * 100.0) / 100.0,
            row.totalTokensUsed(),
            Math.round(row.totalCostUsd() * 100.0) / 100.0,
            Math.round(row.averageCostPerCall() * 10000.0) / 10000.0,  // Round to 4 decimals
            Math.round(row.averageTokensPerCall() * 100.0) / 100.0,
            row.lastUpdated().toString(),
            determineStatus(row)
        );
    }

    /**
     * Create summary statistics from agent rows.
     */
    private DashboardSummary createSummary(List<AgentAnalyticsView.AgentMetricsRow> rows) {
        int totalAgents = rows.size();
        int totalCalls = rows.stream().mapToInt(AgentAnalyticsView.AgentMetricsRow::totalCalls).sum();
        int totalSuccesses = rows.stream().mapToInt(AgentAnalyticsView.AgentMetricsRow::successfulCalls).sum();
        int totalFailures = rows.stream().mapToInt(AgentAnalyticsView.AgentMetricsRow::failedCalls).sum();
        double totalCost = rows.stream().mapToDouble(AgentAnalyticsView.AgentMetricsRow::totalCostUsd).sum();
        double totalTokens = rows.stream().mapToDouble(AgentAnalyticsView.AgentMetricsRow::totalTokensUsed).sum();

        double overallSuccessRate = totalCalls > 0 ? (double) totalSuccesses / totalCalls : 0.0;

        return new DashboardSummary(
            totalAgents,
            totalCalls,
            totalSuccesses,
            totalFailures,
            Math.round(overallSuccessRate * 100.0) / 100.0,
            Math.round(totalCost * 100.0) / 100.0,
            totalTokens,
            Instant.now().toString()
        );
    }

    /**
     * Get human-readable display name for agent ID.
     */
    private String getDisplayName(String agentId) {
        return switch (agentId) {
            case "customer-support" -> "Customer Support";
            case "fraud-analyst" -> "Fraud Analyst";
            case "payment-assistant" -> "Payment Assistant";
            case "planner-agent" -> "Planner";
            case "summarizer-agent" -> "Summarizer";
            default -> agentId;
        };
    }

    /**
     * Determine agent status based on metrics.
     */
    private String determineStatus(AgentAnalyticsView.AgentMetricsRow row) {
        if (row.totalCalls() == 0) {
            return "IDLE";
        }
        if (row.successRate() < 0.5) {
            return "ERROR";
        }
        return "ACTIVE";
    }
}
