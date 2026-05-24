package com.example.payment.agents.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CostBudgetTest {

    @Test
    public void shouldCreateBudget() {
        var budget = CostBudget.create(
            "budget-1",
            "agent-1",
            CostBudget.BudgetPeriod.DAILY,
            100.0,
            CostBudget.BudgetAction.ALERT,
            0.8
        );

        assertThat(budget.budgetId()).isEqualTo("budget-1");
        assertThat(budget.agentId()).isEqualTo("agent-1");
        assertThat(budget.limitUsd()).isEqualTo(100.0);
        assertThat(budget.currentSpendUsd()).isEqualTo(0.0);
        assertThat(budget.isActive()).isTrue();
    }

    @Test
    public void shouldRecordSpending() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var updated = budget.recordSpend(25.0);

        assertThat(updated.currentSpendUsd()).isEqualTo(25.0);
    }

    @Test
    public void shouldDetectExceededBudget() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var updated = budget.recordSpend(150.0);

        assertThat(updated.isExceeded()).isTrue();
    }

    @Test
    public void shouldDetectAlertThreshold() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var updated = budget.recordSpend(85.0);

        assertThat(updated.shouldAlert()).isTrue();
        assertThat(updated.isExceeded()).isFalse();
    }

    @Test
    public void shouldCalculateUtilization() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var updated = budget.recordSpend(75.0);

        assertThat(updated.getUtilization()).isEqualTo(75.0);
    }

    @Test
    public void shouldCalculateRemainingBudget() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var updated = budget.recordSpend(40.0);

        assertThat(updated.getRemainingUsd()).isEqualTo(60.0);
    }

    @Test
    public void shouldResetBudget() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8)
            .recordSpend(75.0);

        var reset = budget.reset();

        assertThat(reset.currentSpendUsd()).isEqualTo(0.0);
        assertThat(reset.isExceeded()).isFalse();
    }

    @Test
    public void shouldActivateAndDeactivate() {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        var deactivated = budget.deactivate();
        assertThat(deactivated.isActive()).isFalse();

        var activated = deactivated.activate();
        assertThat(activated.isActive()).isTrue();
    }

    @Test
    public void shouldCheckPeriodExpiration() throws InterruptedException {
        var budget = CostBudget.create("budget-1", "agent-1",
            CostBudget.BudgetPeriod.DAILY, 100.0,
            CostBudget.BudgetAction.ALERT, 0.8);

        assertThat(budget.isPeriodExpired()).isFalse();
    }
}
