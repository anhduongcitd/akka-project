package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentChainExecution;

import java.util.Map;

/**
 * Key-Value Entity for tracking agent chain executions.
 */
@Component(id = "agent-chain-execution")
public class AgentChainExecutionEntity extends KeyValueEntity<AgentChainExecution> {

    /**
     * Start a new chain execution.
     */
    public Effect<Done> startExecution(StartExecution command) {
        if (currentState() != null) {
            return effects().error("Execution " + command.executionId() + " already exists");
        }

        var execution = AgentChainExecution.create(
            command.executionId(),
            command.chainId(),
            command.initialContext()
        ).start();

        return effects()
            .updateState(execution)
            .thenReply(Done.getInstance());
    }

    /**
     * Add step result to execution.
     */
    public Effect<Done> addStepResult(AddStepResult command) {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        if (currentState().isComplete()) {
            return effects().error("Execution already completed");
        }

        var updatedExecution = currentState().addStepResult(command.result());

        return effects()
            .updateState(updatedExecution)
            .thenReply(Done.getInstance());
    }

    /**
     * Complete execution successfully.
     */
    public Effect<Done> completeExecution(CompleteExecution command) {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        if (currentState().isComplete()) {
            return effects().error("Execution already completed");
        }

        var completedExecution = currentState().complete(command.finalOutput());

        return effects()
            .updateState(completedExecution)
            .thenReply(Done.getInstance());
    }

    /**
     * Fail execution.
     */
    public Effect<Done> failExecution(FailExecution command) {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        if (currentState().isComplete()) {
            return effects().error("Execution already completed");
        }

        var failedExecution = currentState().fail(command.errorMessage());

        return effects()
            .updateState(failedExecution)
            .thenReply(Done.getInstance());
    }

    /**
     * Cancel execution.
     */
    public Effect<Done> cancelExecution() {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        if (currentState().isComplete()) {
            return effects().error("Execution already completed");
        }

        var cancelledExecution = currentState().cancel();

        return effects()
            .updateState(cancelledExecution)
            .thenReply(Done.getInstance());
    }

    /**
     * Update execution context.
     */
    public Effect<Done> updateContext(UpdateContext command) {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        var updatedExecution = currentState().updateContext(command.key(), command.value());

        return effects()
            .updateState(updatedExecution)
            .thenReply(Done.getInstance());
    }

    /**
     * Get execution state.
     */
    public Effect<AgentChainExecution> getExecution() {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete execution.
     */
    public Effect<Done> deleteExecution() {
        if (currentState() == null) {
            return effects().error("Execution does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record StartExecution(
        String executionId,
        String chainId,
        Map<String, Object> initialContext
    ) {}

    public record AddStepResult(AgentChainExecution.StepResult result) {}

    public record CompleteExecution(String finalOutput) {}

    public record FailExecution(String errorMessage) {}

    public record UpdateContext(String key, Object value) {}
}
