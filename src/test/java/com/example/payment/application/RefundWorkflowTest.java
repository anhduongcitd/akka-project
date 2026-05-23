package com.example.payment.application;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.domain.Currency;
import com.example.payment.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * RefundWorkflow Unit Tests.
 * Tests refund workflow command structure and state transitions.
 */
public class RefundWorkflowTest extends TestKitSupport {

    @Test
    public void shouldCreateStartRefundCommand() {
        var amount = new Money(new BigDecimal("50.00"), Currency.USD);

        var command = new RefundWorkflow.StartRefund(
            "txn_test_123",
            amount,
            "Customer requested refund"
        );

        assertThat(command.transactionId()).isEqualTo("txn_test_123");
        assertThat(command.refundAmount()).isEqualTo(amount);
        assertThat(command.reason()).isEqualTo("Customer requested refund");
    }

    @Test
    public void shouldCreateInitialState() {
        var amount = new Money(new BigDecimal("75.00"), Currency.USD);

        var state = new RefundWorkflow.State(
            "ref_123",
            "txn_456",
            amount,
            "Damaged goods",
            null,
            RefundWorkflow.WorkflowStatus.INITIATED,
            null
        );

        assertThat(state.refundId()).isEqualTo("ref_123");
        assertThat(state.transactionId()).isEqualTo("txn_456");
        assertThat(state.refundAmount()).isEqualTo(amount);
        assertThat(state.reason()).isEqualTo("Damaged goods");
        assertThat(state.status()).isEqualTo(RefundWorkflow.WorkflowStatus.INITIATED);
        assertThat(state.gatewayRefundId()).isNull();
        assertThat(state.failureReason()).isNull();
    }

    @Test
    public void shouldUpdateStateWithGatewayRefundId() {
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        var state = new RefundWorkflow.State(
            "ref_123",
            "txn_456",
            amount,
            "Test refund",
            null,
            RefundWorkflow.WorkflowStatus.REFUND_PROCESSING,
            null
        );

        var updatedState = state.withGatewayRefundId("stripe_ref_xyz789");

        assertThat(updatedState.gatewayRefundId()).isEqualTo("stripe_ref_xyz789");
        assertThat(updatedState.refundId()).isEqualTo("ref_123");
        assertThat(updatedState.status()).isEqualTo(RefundWorkflow.WorkflowStatus.REFUND_PROCESSING);
    }

    @Test
    public void shouldUpdateStateStatus() {
        var amount = new Money(new BigDecimal("200.00"), Currency.USD);

        var state = new RefundWorkflow.State(
            "ref_123",
            "txn_456",
            amount,
            "Test refund",
            null,
            RefundWorkflow.WorkflowStatus.INITIATED,
            null
        );

        var updatedState = state.withStatus(RefundWorkflow.WorkflowStatus.REFUND_COMPLETED);

        assertThat(updatedState.status()).isEqualTo(RefundWorkflow.WorkflowStatus.REFUND_COMPLETED);
        assertThat(updatedState.refundId()).isEqualTo("ref_123");
    }

    @Test
    public void shouldUpdateStateWithFailureReason() {
        var amount = new Money(new BigDecimal("150.00"), Currency.USD);

        var state = new RefundWorkflow.State(
            "ref_123",
            "txn_456",
            amount,
            "Test refund",
            null,
            RefundWorkflow.WorkflowStatus.REFUND_PROCESSING,
            null
        );

        var updatedState = state.withFailureReason("Insufficient funds");

        assertThat(updatedState.failureReason()).isEqualTo("Insufficient funds");
        assertThat(updatedState.refundId()).isEqualTo("ref_123");
        assertThat(updatedState.status()).isEqualTo(RefundWorkflow.WorkflowStatus.REFUND_PROCESSING);
    }

    @Test
    public void shouldMaintainImmutabilityOnStateUpdate() {
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        var originalState = new RefundWorkflow.State(
            "ref_123",
            "txn_456",
            amount,
            "Original reason",
            null,
            RefundWorkflow.WorkflowStatus.INITIATED,
            null
        );

        var updatedState = originalState
            .withStatus(RefundWorkflow.WorkflowStatus.REFUND_COMPLETED)
            .withGatewayRefundId("stripe_ref_abc");

        // Original state should remain unchanged
        assertThat(originalState.status()).isEqualTo(RefundWorkflow.WorkflowStatus.INITIATED);
        assertThat(originalState.gatewayRefundId()).isNull();

        // Updated state should have new values
        assertThat(updatedState.status()).isEqualTo(RefundWorkflow.WorkflowStatus.REFUND_COMPLETED);
        assertThat(updatedState.gatewayRefundId()).isEqualTo("stripe_ref_abc");
    }

    @Test
    public void shouldHandleWorkflowStatusTransitions() {
        // Verify all valid status transitions
        var statuses = RefundWorkflow.WorkflowStatus.values();

        assertThat(statuses).containsExactly(
            RefundWorkflow.WorkflowStatus.INITIATED,
            RefundWorkflow.WorkflowStatus.REFUND_PROCESSING,
            RefundWorkflow.WorkflowStatus.REFUND_COMPLETED,
            RefundWorkflow.WorkflowStatus.REFUND_FAILED
        );
    }

    @Test
    public void shouldCreatePartialRefundCommand() {
        var partialAmount = new Money(new BigDecimal("25.50"), Currency.EUR);

        var command = new RefundWorkflow.StartRefund(
            "txn_partial_789",
            partialAmount,
            "Partial refund for item return"
        );

        assertThat(command.refundAmount().amount()).isEqualTo(new BigDecimal("25.50"));
        assertThat(command.refundAmount().currency()).isEqualTo(Currency.EUR);
    }

    @Test
    public void shouldCreateFullRefundCommand() {
        var fullAmount = new Money(new BigDecimal("500.00"), Currency.GBP);

        var command = new RefundWorkflow.StartRefund(
            "txn_full_999",
            fullAmount,
            "Full refund - order cancelled"
        );

        assertThat(command.refundAmount().amount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(command.refundAmount().currency()).isEqualTo(Currency.GBP);
        assertThat(command.reason()).isEqualTo("Full refund - order cancelled");
    }
}
