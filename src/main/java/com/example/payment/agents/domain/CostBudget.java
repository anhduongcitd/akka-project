package com.example.payment.agents.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Budget definition with limits and enforcement.
 */
public record CostBudget(
    String budgetId,
    String agentId,              // null for organization-wide
    BudgetPeriod period,
    double limitUsd,
    double currentSpendUsd,
    BudgetAction action,
    double alertThreshold,       // 0.8 = 80%
    boolean isActive,
    Instant periodStart,
    Instant periodEnd,
    Instant createdAt
) {

    /**
     * Budget periods.
     */
    public enum BudgetPeriod {
        DAILY,
        WEEKLY,
        MONTHLY
    }

    /**
     * Actions when budget exceeded.
     */
    public enum BudgetAction {
        ALERT,      // Send notification only
        THROTTLE,   // Reduce request rate
        BLOCK       // Stop all requests
    }

    /**
     * Create new budget.
     */
    public static CostBudget create(String budgetId, String agentId,
                                     BudgetPeriod period, double limitUsd,
                                     BudgetAction action, double alertThreshold) {
        Instant now = Instant.now();
        Instant periodEnd = calculatePeriodEnd(now, period);

        return new CostBudget(
            budgetId,
            agentId,
            period,
            limitUsd,
            0.0,  // No spend yet
            action,
            alertThreshold,
            true,
            now,
            periodEnd,
            now
        );
    }

    /**
     * Record spending.
     */
    public CostBudget recordSpend(double costUsd) {
        return new CostBudget(
            budgetId,
            agentId,
            period,
            limitUsd,
            currentSpendUsd + costUsd,
            action,
            alertThreshold,
            isActive,
            periodStart,
            periodEnd,
            createdAt
        );
    }

    /**
     * Reset budget for new period.
     */
    public CostBudget reset() {
        Instant now = Instant.now();
        Instant newPeriodEnd = calculatePeriodEnd(now, period);

        return new CostBudget(
            budgetId,
            agentId,
            period,
            limitUsd,
            0.0,  // Reset spend
            action,
            alertThreshold,
            isActive,
            now,
            newPeriodEnd,
            createdAt
        );
    }

    /**
     * Activate budget.
     */
    public CostBudget activate() {
        return new CostBudget(
            budgetId, agentId, period, limitUsd, currentSpendUsd,
            action, alertThreshold, true, periodStart, periodEnd, createdAt
        );
    }

    /**
     * Deactivate budget.
     */
    public CostBudget deactivate() {
        return new CostBudget(
            budgetId, agentId, period, limitUsd, currentSpendUsd,
            action, alertThreshold, false, periodStart, periodEnd, createdAt
        );
    }

    /**
     * Check if budget limit exceeded.
     */
    public boolean isExceeded() {
        return currentSpendUsd >= limitUsd;
    }

    /**
     * Check if alert threshold reached.
     */
    public boolean shouldAlert() {
        return currentSpendUsd >= (limitUsd * alertThreshold);
    }

    /**
     * Get utilization percentage.
     */
    public double getUtilization() {
        return (currentSpendUsd / limitUsd) * 100.0;
    }

    /**
     * Get remaining budget.
     */
    public double getRemainingUsd() {
        return Math.max(0, limitUsd - currentSpendUsd);
    }

    /**
     * Check if period expired.
     */
    public boolean isPeriodExpired() {
        return Instant.now().isAfter(periodEnd);
    }

    /**
     * Calculate period end date.
     */
    private static Instant calculatePeriodEnd(Instant start, BudgetPeriod period) {
        return switch (period) {
            case DAILY -> start.plus(1, ChronoUnit.DAYS);
            case WEEKLY -> start.plus(7, ChronoUnit.DAYS);
            case MONTHLY -> start.plus(30, ChronoUnit.DAYS);
        };
    }
}
