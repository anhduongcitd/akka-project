package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentHealthStatus;

import java.time.Instant;
import java.util.List;

/**
 * View for querying agent health status.
 */
@Component(id = "agent-health-view")
public class AgentHealthView extends View {

    /**
     * Health entry in the view.
     */
    public record HealthEntry(
        String agentId,
        String state,
        double latencyP50Ms,
        double latencyP95Ms,
        double latencyP99Ms,
        double errorRate,
        double availability,
        int totalRequests,
        int successfulRequests,
        int failedRequests,
        Instant lastCheckAt,
        String healthMessage
    ) {}

    /**
     * Wrapper for list results.
     */
    public record HealthList(List<HealthEntry> agents) {}

    /**
     * Get all agent health statuses.
     */
    @Query("SELECT * AS agents FROM agent_health")
    public QueryEffect<HealthList> getAllHealth() {
        return queryResult();
    }

    /**
     * Get health by agent ID.
     */
    @Query("SELECT * FROM agent_health WHERE agentId = :agentId")
    public QueryEffect<HealthEntry> getByAgentId(String agentId) {
        return queryResult();
    }

    /**
     * Get unhealthy agents.
     */
    @Query("SELECT * AS agents FROM agent_health WHERE state IN ('UNHEALTHY', 'DOWN')")
    public QueryEffect<HealthList> getUnhealthyAgents() {
        return queryResult();
    }

    /**
     * Get degraded agents.
     */
    @Query("SELECT * AS agents FROM agent_health WHERE state = 'DEGRADED'")
    public QueryEffect<HealthList> getDegradedAgents() {
        return queryResult();
    }

    /**
     * Get agents by state.
     */
    @Query("SELECT * AS agents FROM agent_health WHERE state = :state")
    public QueryEffect<HealthList> getByState(String state) {
        return queryResult();
    }

    /**
     * Get agents with high error rate.
     */
    @Query("SELECT * AS agents FROM agent_health WHERE errorRate > :threshold ORDER BY errorRate DESC")
    public QueryEffect<HealthList> getHighErrorRate(double threshold) {
        return queryResult();
    }

    /**
     * Table updater consuming AgentHealthEntity state.
     */
    @Consume.FromKeyValueEntity(AgentHealthEntity.class)
    public static class AgentHealthTableUpdater extends TableUpdater<HealthEntry> {

        public Effect<HealthEntry> onUpdate(AgentHealthStatus health) {
            if (health == null) {
                return effects().deleteRow();
            }

            var entry = new HealthEntry(
                health.agentId(),
                health.state().name(),
                health.latencyP50Ms(),
                health.latencyP95Ms(),
                health.latencyP99Ms(),
                health.errorRate(),
                health.availability(),
                health.totalRequests(),
                health.successfulRequests(),
                health.failedRequests(),
                health.lastCheckAt(),
                health.healthMessage()
            );

            return effects().updateRow(entry);
        }
    }
}
