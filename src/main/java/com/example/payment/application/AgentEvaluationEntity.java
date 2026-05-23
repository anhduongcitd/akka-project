package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.EvaluationResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent Evaluation Entity - Stores evaluation history per agent.
 *
 * Entity ID: targetAgentId (e.g., "customer-support")
 *
 * Tracks:
 * - All evaluation results over time
 * - Latest scores
 * - Pass/fail trends
 * - Quality trends
 */
@Component(id = "agent-evaluation")
public class AgentEvaluationEntity extends KeyValueEntity<AgentEvaluationEntity.State> {

    /**
     * State record - evaluation history for an agent.
     */
    public record State(
        String agentId,
        List<EvaluationResult> evaluations,
        int totalEvaluations,
        int passedEvaluations,
        int failedEvaluations,
        double averageScore,
        Instant lastEvaluated
    ) {
        public State addEvaluation(EvaluationResult result) {
            var newEvaluations = new ArrayList<>(evaluations);
            newEvaluations.add(result);

            int newTotal = totalEvaluations + 1;
            int newPassed = passedEvaluations + (result.passed() ? 1 : 0);
            int newFailed = failedEvaluations + (result.passed() ? 0 : 1);

            // Calculate new average (weighted with new result)
            double newAverage = ((averageScore * totalEvaluations) + result.getOverallScore()) / newTotal;

            return new State(
                agentId,
                newEvaluations,
                newTotal,
                newPassed,
                newFailed,
                newAverage,
                result.timestamp()
            );
        }

        public double getPassRate() {
            return totalEvaluations > 0 ? (double) passedEvaluations / totalEvaluations : 0.0;
        }

        public EvaluationResult getLatest() {
            return evaluations.isEmpty() ? null : evaluations.get(evaluations.size() - 1);
        }

        public List<EvaluationResult> getRecent(int count) {
            int size = evaluations.size();
            int fromIndex = Math.max(0, size - count);
            return evaluations.subList(fromIndex, size);
        }
    }

    // Command records
    public record RecordEvaluation(EvaluationResult result) {}

    public record GetHistory(int limit) {}

    public record HistoryResponse(
        String agentId,
        List<EvaluationResult> evaluations,
        double averageScore,
        double passRate,
        int totalEvaluations
    ) {}

    /**
     * Initialize empty state.
     */
    @Override
    public State emptyState() {
        String agentId = commandContext().entityId();
        return new State(agentId, new ArrayList<>(), 0, 0, 0, 0.0, null);
    }

    /**
     * Record an evaluation result.
     */
    public Effect<Done> recordEvaluation(RecordEvaluation command) {
        State updated = currentState().addEvaluation(command.result());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get evaluation history.
     */
    public Effect<HistoryResponse> getHistory(GetHistory command) {
        int limit = command.limit() > 0 ? command.limit() : 10;
        var recent = currentState().getRecent(limit);

        return effects().reply(new HistoryResponse(
            currentState().agentId(),
            recent,
            currentState().averageScore(),
            currentState().getPassRate(),
            currentState().totalEvaluations()
        ));
    }

    /**
     * Get current statistics.
     */
    public Effect<State> getStats() {
        return effects().reply(currentState());
    }

    /**
     * Reset evaluation history (for testing).
     */
    public Effect<Done> reset() {
        return effects()
            .updateState(emptyState())
            .thenReply(Done.getInstance());
    }
}
