package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentHealthStatus;
import com.example.payment.agents.domain.HealthCheckResult;

import java.util.List;

/**
 * Key-Value Entity for tracking agent health status.
 */
@Component(id = "agent-health")
public class AgentHealthEntity extends KeyValueEntity<AgentHealthStatus> {

    /**
     * Initialize health tracking.
     */
    public Effect<Done> initialize(Initialize command) {
        if (currentState() != null) {
            return effects().error("Health tracking already initialized for agent " + command.agentId());
        }

        var initialHealth = AgentHealthStatus.createHealthy(command.agentId());

        return effects()
            .updateState(initialHealth)
            .thenReply(Done.getInstance());
    }

    /**
     * Record a health check result.
     */
    public Effect<Done> recordHealthCheck(RecordHealthCheck command) {
        if (currentState() == null) {
            return effects().error("Health tracking not initialized");
        }

        var result = command.result();
        var updatedHealth = currentState().recordRequest(result.success(), result.durationMs());

        return effects()
            .updateState(updatedHealth)
            .thenReply(Done.getInstance());
    }

    /**
     * Update latency percentiles.
     */
    public Effect<Done> updateLatencies(UpdateLatencies command) {
        if (currentState() == null) {
            return effects().error("Health tracking not initialized");
        }

        var percentiles = AgentHealthStatus.calculatePercentiles(command.latencySamples());
        var updatedHealth = currentState().updateLatencies(
            percentiles.p50(),
            percentiles.p95(),
            percentiles.p99()
        );

        return effects()
            .updateState(updatedHealth)
            .thenReply(Done.getInstance());
    }

    /**
     * Get current health status.
     */
    public Effect<AgentHealthStatus> getHealth() {
        if (currentState() == null) {
            return effects().error("Health tracking not initialized");
        }

        return effects().reply(currentState());
    }

    /**
     * Reset health tracking.
     */
    public Effect<Done> reset() {
        if (currentState() == null) {
            return effects().error("Health tracking not initialized");
        }

        var resetHealth = AgentHealthStatus.createHealthy(currentState().agentId());

        return effects()
            .updateState(resetHealth)
            .thenReply(Done.getInstance());
    }

    /**
     * Delete health tracking.
     */
    public Effect<Done> deleteHealth() {
        if (currentState() == null) {
            return effects().error("Health tracking not initialized");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record Initialize(String agentId) {}

    public record RecordHealthCheck(HealthCheckResult result) {}

    public record UpdateLatencies(List<Long> latencySamples) {}
}
