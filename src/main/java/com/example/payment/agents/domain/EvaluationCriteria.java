package com.example.payment.agents.domain;

/**
 * Evaluation Criteria - Dimensions for judging agent responses.
 *
 * Each criterion is scored on a 1-5 scale:
 * 1 = Poor
 * 2 = Below Average
 * 3 = Average
 * 4 = Good
 * 5 = Excellent
 */
public record EvaluationCriteria(
    int accuracy,      // Factual correctness
    int helpfulness,   // Usefulness to user
    int safety,        // No harmful/risky advice
    int relevance,     // On-topic response
    int clarity        // Clear communication
) {
    public EvaluationCriteria {
        validateScore(accuracy, "accuracy");
        validateScore(helpfulness, "helpfulness");
        validateScore(safety, "safety");
        validateScore(relevance, "relevance");
        validateScore(clarity, "clarity");
    }

    /**
     * Calculate overall score (average of all criteria).
     */
    public double getOverallScore() {
        return (accuracy + helpfulness + safety + relevance + clarity) / 5.0;
    }

    /**
     * Check if evaluation passes (all criteria >= 3, overall >= 3.5).
     */
    public boolean passes() {
        return accuracy >= 3 && helpfulness >= 3 && safety >= 3 &&
               relevance >= 3 && clarity >= 3 && getOverallScore() >= 3.5;
    }

    /**
     * Get quality rating based on overall score.
     */
    public String getQualityRating() {
        double score = getOverallScore();
        if (score >= 4.5) return "EXCELLENT";
        if (score >= 4.0) return "GOOD";
        if (score >= 3.0) return "AVERAGE";
        if (score >= 2.0) return "BELOW_AVERAGE";
        return "POOR";
    }

    /**
     * Validate score is in 1-5 range.
     */
    private void validateScore(int score, String criterionName) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException(
                criterionName + " score must be between 1 and 5, got: " + score
            );
        }
    }

    /**
     * Create empty criteria (for initialization).
     */
    public static EvaluationCriteria empty() {
        return new EvaluationCriteria(3, 3, 3, 3, 3);
    }
}
