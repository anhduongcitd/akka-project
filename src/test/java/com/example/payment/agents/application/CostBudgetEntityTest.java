package com.example.payment.agents.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.CostBudget;
import com.example.payment.application.CostBudgetEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CostBudgetEntityTest {

    @Test
    public void shouldCreateBudget() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        var result = testKit
            .method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1",
                "agent-1",
                CostBudget.BudgetPeriod.DAILY,
                100.0,
                CostBudget.BudgetAction.ALERT,
                0.8
            ));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().budgetId()).isEqualTo("budget-1");
        assertThat(testKit.getState().limitUsd()).isEqualTo(100.0);
    }

    @Test
    public void shouldRecordSpending() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.ALERT, 0.8
            ));

        var result = testKit
            .method(CostBudgetEntity::recordSpend)
            .invoke(new CostBudgetEntity.RecordSpend(25.0));

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isTrue();
        assertThat(testKit.getState().currentSpendUsd()).isEqualTo(25.0);
    }

    @Test
    public void shouldBlockWhenBudgetExceeded() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.BLOCK, 0.8
            ));

        testKit.method(CostBudgetEntity::recordSpend)
            .invoke(new CostBudgetEntity.RecordSpend(110.0));

        var result = testKit
            .method(CostBudgetEntity::recordSpend)
            .invoke(new CostBudgetEntity.RecordSpend(10.0));

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isFalse();
    }

    @Test
    public void shouldAlertAtThreshold() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.ALERT, 0.8
            ));

        var result = testKit
            .method(CostBudgetEntity::recordSpend)
            .invoke(new CostBudgetEntity.RecordSpend(85.0));

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().message()).contains("Alert");
    }

    @Test
    public void shouldResetBudget() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.ALERT, 0.8
            ));

        testKit.method(CostBudgetEntity::recordSpend)
            .invoke(new CostBudgetEntity.RecordSpend(75.0));

        var result = testKit.method(CostBudgetEntity::resetBudget).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().currentSpendUsd()).isEqualTo(0.0);
    }

    @Test
    public void shouldActivateAndDeactivate() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.ALERT, 0.8
            ));

        testKit.method(CostBudgetEntity::deactivate).invoke();
        assertThat(testKit.getState().isActive()).isFalse();

        testKit.method(CostBudgetEntity::activate).invoke();
        assertThat(testKit.getState().isActive()).isTrue();
    }

    @Test
    public void shouldGetBudget() {
        var testKit = KeyValueEntityTestKit.of("budget-1", CostBudgetEntity::new);

        testKit.method(CostBudgetEntity::createBudget)
            .invoke(new CostBudgetEntity.CreateBudget(
                "budget-1", "agent-1", CostBudget.BudgetPeriod.DAILY,
                100.0, CostBudget.BudgetAction.ALERT, 0.8
            ));

        var result = testKit.method(CostBudgetEntity::getBudget).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isNotNull();
    }
}
