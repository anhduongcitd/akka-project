package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentCost;

import java.time.Instant;
import java.util.List;

/**
 * View for cost analytics and queries.
 */
@Component(id = "cost-analytics-view")
public class CostAnalyticsView extends View {

    /**
     * Cost entry in the view.
     */
    public record CostEntry(
        String agentId,
        String sessionId,
        double costUsd,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        long durationMs,
        String modelId,
        Instant recordedAt
    ) {}

    /**
     * Wrapper for list results.
     */
    public record CostList(List<CostEntry> costs) {}

    /**
     * Get all costs.
     */
    @Query("SELECT * AS costs FROM agent_costs")
    public QueryEffect<CostList> getAllCosts() {
        return queryResult();
    }

    /**
     * Get costs by agent.
     */
    @Query("SELECT * AS costs FROM agent_costs WHERE agentId = :agentId ORDER BY recordedAt DESC")
    public QueryEffect<CostList> getByAgent(String agentId) {
        return queryResult();
    }

    /**
     * Get costs by session.
     */
    @Query("SELECT * AS costs FROM agent_costs WHERE sessionId = :sessionId")
    public QueryEffect<CostList> getBySession(String sessionId) {
        return queryResult();
    }

    /**
     * Get recent costs.
     */
    @Query("SELECT * AS costs FROM agent_costs ORDER BY recordedAt DESC LIMIT :limit")
    public QueryEffect<CostList> getRecent(int limit) {
        return queryResult();
    }

    /**
     * Get high-cost sessions.
     */
    @Query("SELECT * AS costs FROM agent_costs WHERE costUsd > :threshold ORDER BY costUsd DESC")
    public QueryEffect<CostList> getHighCost(double threshold) {
        return queryResult();
    }

    /**
     * Table updater consuming AgentCostEntity state.
     */
    @Consume.FromKeyValueEntity(AgentCostEntity.class)
    public static class CostAnalyticsTableUpdater extends TableUpdater<CostEntry> {

        public Effect<CostEntry> onUpdate(AgentCost cost) {
            if (cost == null) {
                return effects().deleteRow();
            }

            var entry = new CostEntry(
                cost.agentId(),
                cost.sessionId(),
                cost.costUsd(),
                cost.inputTokens(),
                cost.outputTokens(),
                cost.totalTokens(),
                cost.durationMs(),
                cost.modelId(),
                cost.recordedAt()
            );

            return effects().updateRow(entry);
        }
    }
}
