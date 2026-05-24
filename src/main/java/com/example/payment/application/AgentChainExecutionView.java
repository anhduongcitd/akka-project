package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentChainExecution;

import java.time.Instant;
import java.util.List;

/**
 * View for querying agent chain executions.
 */
@Component(id = "agent-chain-execution-view")
public class AgentChainExecutionView extends View {

    /**
     * Execution entry in the view.
     */
    public record ExecutionEntry(
        String executionId,
        String chainId,
        String status,
        int totalSteps,
        int completedSteps,
        int failedSteps,
        long durationMs,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
    ) {}

    /**
     * Wrapper for list results.
     */
    public record ExecutionList(List<ExecutionEntry> executions) {}

    /**
     * Get all executions.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions")
    public QueryEffect<ExecutionList> getAllExecutions() {
        return queryResult();
    }

    /**
     * Get execution by ID.
     */
    @Query("SELECT * FROM agent_chain_executions WHERE executionId = :executionId")
    public QueryEffect<ExecutionEntry> getById(String executionId) {
        return queryResult();
    }

    /**
     * Get executions by chain ID.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions WHERE chainId = :chainId ORDER BY startedAt DESC")
    public QueryEffect<ExecutionList> getByChainId(String chainId) {
        return queryResult();
    }

    /**
     * Get executions by status.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions WHERE status = :status ORDER BY startedAt DESC")
    public QueryEffect<ExecutionList> getByStatus(String status) {
        return queryResult();
    }

    /**
     * Get recent executions.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions ORDER BY startedAt DESC LIMIT :limit")
    public QueryEffect<ExecutionList> getRecent(int limit) {
        return queryResult();
    }

    /**
     * Get failed executions.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions WHERE status = 'FAILED' ORDER BY startedAt DESC")
    public QueryEffect<ExecutionList> getFailedExecutions() {
        return queryResult();
    }

    /**
     * Get running executions.
     */
    @Query("SELECT * AS executions FROM agent_chain_executions WHERE status = 'RUNNING'")
    public QueryEffect<ExecutionList> getRunningExecutions() {
        return queryResult();
    }

    /**
     * Table updater consuming AgentChainExecutionEntity state.
     */
    @Consume.FromKeyValueEntity(AgentChainExecutionEntity.class)
    public static class AgentChainExecutionTableUpdater extends TableUpdater<ExecutionEntry> {

        public Effect<ExecutionEntry> onUpdate(AgentChainExecution execution) {
            if (execution == null) {
                return effects().deleteRow();
            }

            var entry = new ExecutionEntry(
                execution.executionId(),
                execution.chainId(),
                execution.status().name(),
                execution.stepResults().size(),
                execution.getCompletedStepCount(),
                execution.getFailedStepCount(),
                execution.getDurationMs(),
                execution.startedAt(),
                execution.completedAt(),
                execution.errorMessage()
            );

            return effects().updateRow(entry);
        }
    }
}
