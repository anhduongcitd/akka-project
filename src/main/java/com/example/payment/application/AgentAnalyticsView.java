package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentPerformance;

import java.time.Instant;
import java.util.List;

/**
 * Agent Analytics View - Aggregated metrics for monitoring dashboard.
 *
 * Provides:
 * - Summary of all agent performance metrics
 * - Query methods for dashboard visualization
 * - Real-time updates from AgentPerformanceEntity
 */
@Component(id = "agent-analytics-view")
public class AgentAnalyticsView extends View {

    /**
     * Row record for agent analytics.
     */
    public record AgentMetricsRow(
        String agentId,
        int totalCalls,
        int successfulCalls,
        int failedCalls,
        double successRate,
        double averageLatencyMs,
        double totalTokensUsed,
        double totalCostUsd,
        double averageCostPerCall,
        double averageTokensPerCall,
        Instant lastUpdated
    ) {
        public static AgentMetricsRow fromPerformance(AgentPerformance perf) {
            return new AgentMetricsRow(
                perf.agentId(),
                perf.totalCalls(),
                perf.successfulCalls(),
                perf.failedCalls(),
                perf.getSuccessRate(),
                perf.averageLatencyMs(),
                perf.totalTokensUsed(),
                perf.totalCostUsd(),
                perf.getAverageCostPerCall(),
                perf.getAverageTokensPerCall(),
                perf.lastUpdated()
            );
        }
    }

    /**
     * Response record for all agents summary.
     */
    public record AllAgentsMetrics(
        List<AgentMetricsRow> agents,
        int totalAgents,
        int totalCalls,
        double totalCost,
        double averageSuccessRate
    ) {}

    /**
     * Get metrics for all agents.
     */
    @Query("SELECT * AS agents FROM agent_analytics")
    public QueryEffect<AllAgentsMetrics> getAllAgentMetrics() {
        return queryResult();
    }

    /**
     * Get metrics for specific agent.
     */
    @Query("SELECT * FROM agent_analytics WHERE agentId = :agentId")
    public QueryEffect<AgentMetricsRow> getAgentMetrics(String agentId) {
        return queryResult();
    }

    /**
     * Get agents sorted by total calls (most active first).
     */
    @Query("SELECT * AS agents FROM agent_analytics ORDER BY totalCalls DESC")
    public QueryEffect<AllAgentsMetrics> getAgentsByActivity() {
        return queryResult();
    }

    /**
     * Get agents sorted by success rate (best performing first).
     */
    @Query("SELECT * AS agents FROM agent_analytics ORDER BY successRate DESC")
    public QueryEffect<AllAgentsMetrics> getAgentsByPerformance() {
        return queryResult();
    }

    /**
     * Get agents sorted by total cost (highest cost first).
     */
    @Query("SELECT * AS agents FROM agent_analytics ORDER BY totalCostUsd DESC")
    public QueryEffect<AllAgentsMetrics> getAgentsByCost() {
        return queryResult();
    }

    /**
     * Table updater consuming from AgentPerformanceEntity.
     */
    @Consume.FromKeyValueEntity(AgentPerformanceEntity.class)
    public static class AgentAnalyticsUpdater extends TableUpdater<AgentMetricsRow> {

        /**
         * Update view when agent performance changes.
         */
        public Effect<AgentMetricsRow> onUpdate(AgentPerformance performance) {
            var row = AgentMetricsRow.fromPerformance(performance);
            return effects().updateRow(row);
        }

        /**
         * Remove row when agent performance entity is deleted.
         */
        @DeleteHandler
        public Effect<AgentMetricsRow> onDelete() {
            return effects().deleteRow();
        }
    }
}
