package com.example.payment.agents.domain;

import java.time.Instant;

/**
 * Evaluation Result - Results from judging an agent response.
 *
 * Contains:
 * - Scores across all criteria
 * - Reasoning for each score
 * - Overall assessment
 * - Pass/fail determination
 */
public record EvaluationResult(
    String evaluationId,
    String targetAgentId,
    String testCaseId,
    String query,
    String response,
    EvaluationCriteria scores,
    String reasoning,
    boolean passed,
    Instant timestamp
) {
    public EvaluationResult {
        if (evaluationId == null || evaluationId.isBlank()) {
            throw new IllegalArgumentException("Evaluation ID cannot be null or blank");
        }
        if (targetAgentId == null || targetAgentId.isBlank()) {
            throw new IllegalArgumentException("Target agent ID cannot be null or blank");
        }
        if (scores == null) {
            throw new IllegalArgumentException("Scores cannot be null");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Get overall score (0.0 - 5.0).
     */
    public double getOverallScore() {
        return scores.getOverallScore();
    }

    /**
     * Get quality rating.
     */
    public String getQualityRating() {
        return scores.getQualityRating();
    }

    /**
     * Check if this is a regression (score dropped significantly).
     */
    public boolean isRegression(EvaluationResult previous) {
        if (previous == null) {
            return false;
        }
        return this.getOverallScore() < previous.getOverallScore() - 0.5;
    }

    /**
     * Check if this is an improvement.
     */
    public boolean isImprovement(EvaluationResult previous) {
        if (previous == null) {
            return false;
        }
        return this.getOverallScore() > previous.getOverallScore() + 0.5;
    }

    /**
     * Create a summary string.
     */
    public String getSummary() {
        return String.format(
            "%s evaluation for %s: %.1f/5.0 (%s) - %s",
            passed ? "✓" : "✗",
            targetAgentId,
            getOverallScore(),
            getQualityRating(),
            passed ? "PASSED" : "FAILED"
        );
    }
}
