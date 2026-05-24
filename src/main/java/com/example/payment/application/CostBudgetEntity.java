package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.CostBudget;

/**
 * Key-Value Entity for budget management.
 */
@Component(id = "cost-budget")
public class CostBudgetEntity extends KeyValueEntity<CostBudget> {

    /**
     * Create budget.
     */
    public Effect<Done> createBudget(CreateBudget command) {
        if (currentState() != null) {
            return effects().error("Budget " + command.budgetId() + " already exists");
        }

        var budget = CostBudget.create(
            command.budgetId(),
            command.agentId(),
            command.period(),
            command.limitUsd(),
            command.action(),
            command.alertThreshold()
        );

        return effects()
            .updateState(budget)
            .thenReply(Done.getInstance());
    }

    /**
     * Record spending against budget.
     */
    public Effect<BudgetCheckResult> recordSpend(RecordSpend command) {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        if (!currentState().isActive()) {
            return effects().reply(new BudgetCheckResult(true, "Budget inactive", 0.0));
        }

        // Check if period expired, reset if needed
        var budget = currentState().isPeriodExpired()
            ? currentState().reset()
            : currentState();

        // Check if already exceeded
        if (budget.isExceeded()) {
            return switch (budget.action()) {
                case BLOCK -> effects().reply(new BudgetCheckResult(
                    false,
                    "Budget limit exceeded",
                    budget.getUtilization()
                ));
                case THROTTLE -> effects().reply(new BudgetCheckResult(
                    true,
                    "Budget exceeded - throttling",
                    budget.getUtilization()
                ));
                case ALERT -> {
                    var updated = budget.recordSpend(command.costUsd());
                    yield effects()
                        .updateState(updated)
                        .thenReply(new BudgetCheckResult(
                            true,
                            "Budget exceeded - alert sent",
                            updated.getUtilization()
                        ));
                }
            };
        }

        // Record spend
        var updated = budget.recordSpend(command.costUsd());

        return effects()
            .updateState(updated)
            .thenReply(new BudgetCheckResult(
                true,
                updated.shouldAlert() ? "Alert threshold reached" : "OK",
                updated.getUtilization()
            ));
    }

    /**
     * Reset budget for new period.
     */
    public Effect<Done> resetBudget() {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        var reset = currentState().reset();

        return effects()
            .updateState(reset)
            .thenReply(Done.getInstance());
    }

    /**
     * Activate budget.
     */
    public Effect<Done> activate() {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        var activated = currentState().activate();

        return effects()
            .updateState(activated)
            .thenReply(Done.getInstance());
    }

    /**
     * Deactivate budget.
     */
    public Effect<Done> deactivate() {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        var deactivated = currentState().deactivate();

        return effects()
            .updateState(deactivated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get budget status.
     */
    public Effect<CostBudget> getBudget() {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete budget.
     */
    public Effect<Done> deleteBudget() {
        if (currentState() == null) {
            return effects().error("Budget does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateBudget(
        String budgetId,
        String agentId,
        CostBudget.BudgetPeriod period,
        double limitUsd,
        CostBudget.BudgetAction action,
        double alertThreshold
    ) {}

    public record RecordSpend(double costUsd) {}

    public record BudgetCheckResult(
        boolean allowed,
        String message,
        double utilization
    ) {}
}
