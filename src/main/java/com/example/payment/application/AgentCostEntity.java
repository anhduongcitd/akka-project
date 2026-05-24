package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentCost;

/**
 * Key-Value Entity for tracking agent costs.
 */
@Component(id = "agent-cost")
public class AgentCostEntity extends KeyValueEntity<AgentCost> {

    /**
     * Record agent usage cost.
     */
    public Effect<Done> recordCost(RecordCost command) {
        var cost = AgentCost.create(
            command.agentId(),
            command.sessionId(),
            command.inputTokens(),
            command.outputTokens(),
            command.durationMs(),
            command.modelId()
        );

        if (currentState() == null) {
            return effects()
                .updateState(cost)
                .thenReply(Done.getInstance());
        } else {
            var updated = currentState().add(cost);
            return effects()
                .updateState(updated)
                .thenReply(Done.getInstance());
        }
    }

    /**
     * Get cost summary.
     */
    public Effect<AgentCost> getCost() {
        if (currentState() == null) {
            return effects().error("No cost data available");
        }

        return effects().reply(currentState());
    }

    /**
     * Reset cost tracking.
     */
    public Effect<Done> reset() {
        if (currentState() == null) {
            return effects().error("No cost data to reset");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record RecordCost(
        String agentId,
        String sessionId,
        int inputTokens,
        int outputTokens,
        long durationMs,
        String modelId
    ) {}
}
