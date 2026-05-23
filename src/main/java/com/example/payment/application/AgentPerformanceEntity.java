package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentPerformance;

/**
 * Agent Performance Entity - Tracks metrics for each agent.
 *
 * Entity ID: agentId (e.g., "customer-support", "fraud-analyst")
 *
 * Tracks:
 * - Total calls, success/failure counts
 * - Average latency
 * - Token usage and cost
 */
@Component(id = "agent-performance")
public class AgentPerformanceEntity extends KeyValueEntity<AgentPerformance> {

    // Command records
    public record RecordSuccess(
        double latencyMs,
        double tokensUsed,
        double costUsd
    ) {}

    public record RecordFailure(
        double latencyMs
    ) {}

    /**
     * Initialize empty performance record.
     */
    @Override
    public AgentPerformance emptyState() {
        return AgentPerformance.empty(commandContext().entityId());
    }

    /**
     * Record successful agent call.
     */
    public Effect<Done> recordSuccess(RecordSuccess command) {
        AgentPerformance updated = currentState().recordSuccess(
            command.latencyMs(),
            command.tokensUsed(),
            command.costUsd()
        );

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Record failed agent call.
     */
    public Effect<Done> recordFailure(RecordFailure command) {
        AgentPerformance updated = currentState().recordFailure(command.latencyMs());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get current performance metrics.
     */
    public Effect<AgentPerformance> getPerformance() {
        return effects().reply(currentState());
    }

    /**
     * Reset performance metrics (for testing or maintenance).
     */
    public Effect<Done> reset() {
        return effects()
            .updateState(AgentPerformance.empty(commandContext().entityId()))
            .thenReply(Done.getInstance());
    }
}
