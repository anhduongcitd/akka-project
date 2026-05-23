package com.example.payment.application;

import akka.Done;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.EvaluationCriteria;
import com.example.payment.agents.domain.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentEvaluationEntity.
 */
public class AgentEvaluationEntityTest {

    @Test
    public void shouldRecordEvaluation() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("customer-support", AgentEvaluationEntity::new);

        // When: Recording evaluation
        var result = new EvaluationResult(
            "eval_001",
            "customer-support",
            "test_001",
            "Where is my payment?",
            "Your payment is being processed",
            new EvaluationCriteria(4, 4, 5, 5, 4),
            "Good response with clear information",
            true,
            Instant.now()
        );

        var response = testKit.method(AgentEvaluationEntity::recordEvaluation)
            .invoke(new AgentEvaluationEntity.RecordEvaluation(result));

        // Then: Should succeed and update state
        assertThat(response.isReply()).isTrue();
        assertThat(response.getReply()).isEqualTo(Done.getInstance());

        var state = testKit.getState();
        assertThat(state.agentId()).isEqualTo("customer-support");
        assertThat(state.totalEvaluations()).isEqualTo(1);
        assertThat(state.passedEvaluations()).isEqualTo(1);
        assertThat(state.failedEvaluations()).isEqualTo(0);
        assertThat(state.averageScore()).isEqualTo(4.4);
        assertThat(state.getPassRate()).isEqualTo(1.0);
    }

    @Test
    public void shouldTrackMultipleEvaluations() {
        // Given: Entity with one evaluation
        var testKit = KeyValueEntityTestKit.of("payment-assistant", AgentEvaluationEntity::new);

        var result1 = new EvaluationResult(
            "eval_001",
            "payment-assistant",
            "test_001",
            "Query 1",
            "Response 1",
            new EvaluationCriteria(5, 5, 5, 5, 5),
            "Perfect",
            true,
            Instant.now()
        );

        testKit.method(AgentEvaluationEntity::recordEvaluation)
            .invoke(new AgentEvaluationEntity.RecordEvaluation(result1));

        // When: Recording second evaluation
        var result2 = new EvaluationResult(
            "eval_002",
            "payment-assistant",
            "test_002",
            "Query 2",
            "Response 2",
            new EvaluationCriteria(3, 3, 3, 3, 3),
            "Average",
            true,
            Instant.now()
        );

        testKit.method(AgentEvaluationEntity::recordEvaluation)
            .invoke(new AgentEvaluationEntity.RecordEvaluation(result2));

        // Then: Should track both
        var state = testKit.getState();
        assertThat(state.totalEvaluations()).isEqualTo(2);
        assertThat(state.passedEvaluations()).isEqualTo(2);
        assertThat(state.averageScore()).isEqualTo(4.0); // (5.0 + 3.0) / 2
    }

    @Test
    public void shouldTrackFailedEvaluations() {
        // Given: Entity
        var testKit = KeyValueEntityTestKit.of("fraud-analyst", AgentEvaluationEntity::new);

        // When: Recording failed evaluation
        var result = new EvaluationResult(
            "eval_003",
            "fraud-analyst",
            "test_003",
            "Query",
            "Poor response",
            new EvaluationCriteria(2, 2, 3, 2, 2),
            "Below standards",
            false,
            Instant.now()
        );

        testKit.method(AgentEvaluationEntity::recordEvaluation)
            .invoke(new AgentEvaluationEntity.RecordEvaluation(result));

        // Then: Should track failure
        var state = testKit.getState();
        assertThat(state.totalEvaluations()).isEqualTo(1);
        assertThat(state.passedEvaluations()).isEqualTo(0);
        assertThat(state.failedEvaluations()).isEqualTo(1);
        assertThat(state.getPassRate()).isEqualTo(0.0);
    }

    @Test
    public void shouldCalculatePassRate() {
        // Given: Entity with mixed results
        var testKit = KeyValueEntityTestKit.of("test-agent", AgentEvaluationEntity::new);

        // Pass
        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_001", true, 4.0))
        );

        // Pass
        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_002", true, 5.0))
        );

        // Fail
        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_003", false, 2.0))
        );

        // Then: Pass rate should be 2/3
        var state = testKit.getState();
        assertThat(state.totalEvaluations()).isEqualTo(3);
        assertThat(state.passedEvaluations()).isEqualTo(2);
        assertThat(state.failedEvaluations()).isEqualTo(1);
        assertThat(state.getPassRate()).isCloseTo(0.666, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    public void shouldGetHistory() {
        // Given: Entity with evaluations
        var testKit = KeyValueEntityTestKit.of("support-agent", AgentEvaluationEntity::new);

        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_001", true, 4.0))
        );

        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_002", true, 5.0))
        );

        // When: Getting history
        var response = testKit.method(AgentEvaluationEntity::getHistory)
            .invoke(new AgentEvaluationEntity.GetHistory(10));

        // Then: Should return evaluations
        assertThat(response.isReply()).isTrue();
        var history = response.getReply();
        assertThat(history.evaluations()).hasSize(2);
        assertThat(history.averageScore()).isEqualTo(4.5);
        assertThat(history.passRate()).isEqualTo(1.0);
    }

    @Test
    public void shouldLimitHistory() {
        // Given: Entity with many evaluations
        var testKit = KeyValueEntityTestKit.of("test-agent", AgentEvaluationEntity::new);

        for (int i = 1; i <= 5; i++) {
            testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
                new AgentEvaluationEntity.RecordEvaluation(createResult("eval_" + i, true, 4.0))
            );
        }

        // When: Getting limited history
        var response = testKit.method(AgentEvaluationEntity::getHistory)
            .invoke(new AgentEvaluationEntity.GetHistory(3));

        // Then: Should return only last 3
        assertThat(response.isReply()).isTrue();
        var history = response.getReply();
        assertThat(history.evaluations()).hasSize(3);
        assertThat(history.totalEvaluations()).isEqualTo(5);
    }

    @Test
    public void shouldGetStats() {
        // Given: Entity with evaluations
        var testKit = KeyValueEntityTestKit.of("stats-agent", AgentEvaluationEntity::new);

        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_001", true, 4.0))
        );

        // When: Getting stats
        var response = testKit.method(AgentEvaluationEntity::getStats).invoke();

        // Then: Should return current state
        assertThat(response.isReply()).isTrue();
        var state = response.getReply();
        assertThat(state.agentId()).isEqualTo("stats-agent");
        assertThat(state.totalEvaluations()).isEqualTo(1);
        assertThat(state.averageScore()).isEqualTo(4.0);
    }

    @Test
    public void shouldReset() {
        // Given: Entity with data
        var testKit = KeyValueEntityTestKit.of("reset-agent", AgentEvaluationEntity::new);

        testKit.method(AgentEvaluationEntity::recordEvaluation).invoke(
            new AgentEvaluationEntity.RecordEvaluation(createResult("eval_001", true, 4.0))
        );

        // When: Resetting
        var response = testKit.method(AgentEvaluationEntity::reset).invoke();

        // Then: Should clear state
        assertThat(response.isReply()).isTrue();
        var state = testKit.getState();
        assertThat(state.totalEvaluations()).isEqualTo(0);
        assertThat(state.evaluations()).isEmpty();
    }

    private EvaluationResult createResult(String id, boolean passed, double score) {
        int intScore = (int) score;
        return new EvaluationResult(
            id,
            "test-agent",
            "test-case",
            "Query",
            "Response",
            new EvaluationCriteria(intScore, intScore, intScore, intScore, intScore),
            "Test evaluation",
            passed,
            Instant.now()
        );
    }
}
