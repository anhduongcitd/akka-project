package com.example.payment.agents.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.CostBudget;
import com.example.payment.application.CostBudgetEntity;

import java.time.Instant;
import java.util.List;

/**
 * View for querying budget status and utilization.
 */
@Component(id = "cost-budget-view")
public class CostBudgetView extends View {

    /**
     * Budget entry with computed fields.
     */
    public record BudgetEntry(
        String budgetId,
        String agentId,
        CostBudget.BudgetPeriod period,
        double limitUsd,
        double currentSpendUsd,
        double utilizationPercent,
        double remainingUsd,
        CostBudget.BudgetAction action,
        double alertThreshold,
        boolean isActive,
        boolean isExceeded,
        boolean shouldAlert,
        Instant periodStart,
        Instant periodEnd,
        Instant createdAt
    ) {}

    /**
     * Wrapper for list results.
     */
    public record BudgetList(List<BudgetEntry> budgets) {}

    /**
     * Get all budgets.
     */
    @Query("SELECT * AS budgets FROM agent_budgets")
    public QueryEffect<BudgetList> getAllBudgets() {
        return queryResult();
    }

    /**
     * Get budgets by agent.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE agentId = :agentId")
    public QueryEffect<BudgetList> getByAgent(String agentId) {
        return queryResult();
    }

    /**
     * Get active budgets.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE isActive = true")
    public QueryEffect<BudgetList> getActiveBudgets() {
        return queryResult();
    }

    /**
     * Get exceeded budgets.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE isExceeded = true ORDER BY utilizationPercent DESC")
    public QueryEffect<BudgetList> getExceededBudgets() {
        return queryResult();
    }

    /**
     * Get budgets in alert state.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE shouldAlert = true AND isActive = true")
    public QueryEffect<BudgetList> getAlertingBudgets() {
        return queryResult();
    }

    /**
     * Get budgets by period.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE period = :period")
    public QueryEffect<BudgetList> getByPeriod(String period) {
        return queryResult();
    }

    /**
     * Get high-utilization budgets.
     */
    @Query("SELECT * AS budgets FROM agent_budgets WHERE utilizationPercent > :threshold AND isActive = true ORDER BY utilizationPercent DESC")
    public QueryEffect<BudgetList> getHighUtilization(double threshold) {
        return queryResult();
    }

    /**
     * Table updater consuming CostBudgetEntity state.
     */
    @Consume.FromKeyValueEntity(CostBudgetEntity.class)
    public static class CostBudgetTableUpdater extends TableUpdater<BudgetEntry> {

        public Effect<BudgetEntry> onUpdate(CostBudget budget) {
            if (budget == null) {
                return effects().deleteRow();
            }

            var entry = new BudgetEntry(
                budget.budgetId(),
                budget.agentId(),
                budget.period(),
                budget.limitUsd(),
                budget.currentSpendUsd(),
                budget.getUtilization(),
                budget.getRemainingUsd(),
                budget.action(),
                budget.alertThreshold(),
                budget.isActive(),
                budget.isExceeded(),
                budget.shouldAlert(),
                budget.periodStart(),
                budget.periodEnd(),
                budget.createdAt()
            );

            return effects().updateRow(entry);
        }
    }
}
