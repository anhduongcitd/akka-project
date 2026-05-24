package com.example.payment.agents.domain;

/**
 * Condition for triggering an alert.
 */
public record AlertCondition(
    String metric,             // error_rate, latency_p95, cost_usd, availability
    ConditionOperator operator,
    double threshold,
    int durationMinutes        // Condition must be true for this duration
) {

    /**
     * Comparison operators.
     */
    public enum ConditionOperator {
        GREATER_THAN,
        LESS_THAN,
        EQUALS
    }

    /**
     * Evaluate condition against agent health.
     */
    public boolean evaluate(AgentHealthStatus health) {
        double actualValue = getMetricValue(health);

        return switch (operator) {
            case GREATER_THAN -> actualValue > threshold;
            case LESS_THAN -> actualValue < threshold;
            case EQUALS -> Math.abs(actualValue - threshold) < 0.001;
        };
    }

    /**
     * Extract metric value from health status.
     */
    private double getMetricValue(AgentHealthStatus health) {
        return switch (metric) {
            case "error_rate" -> health.errorRate();
            case "latency_p50" -> health.latencyP50Ms();
            case "latency_p95" -> health.latencyP95Ms();
            case "latency_p99" -> health.latencyP99Ms();
            case "availability" -> health.availability();
            case "total_requests" -> (double) health.totalRequests();
            case "failed_requests" -> (double) health.failedRequests();
            default -> 0.0;
        };
    }

    /**
     * Create error rate condition.
     */
    public static AlertCondition errorRateExceeds(double threshold, int durationMinutes) {
        return new AlertCondition("error_rate", ConditionOperator.GREATER_THAN, threshold, durationMinutes);
    }

    /**
     * Create latency condition.
     */
    public static AlertCondition latencyP95Exceeds(double thresholdMs, int durationMinutes) {
        return new AlertCondition("latency_p95", ConditionOperator.GREATER_THAN, thresholdMs, durationMinutes);
    }

    /**
     * Create availability condition.
     */
    public static AlertCondition availabilityBelow(double threshold, int durationMinutes) {
        return new AlertCondition("availability", ConditionOperator.LESS_THAN, threshold, durationMinutes);
    }
}
