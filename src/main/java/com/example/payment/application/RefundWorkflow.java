package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.payment.domain.Money;
import com.example.payment.domain.PaymentTransaction;
import com.example.payment.domain.PaymentTransactionEvent;
import com.example.payment.domain.RefundStatus;

import java.time.Duration;
import java.util.UUID;

/**
 * Refund Processing Workflow.
 * Orchestrates refund processing with Stripe payment gateway.
 * Handles full and partial refunds with compensation for failures.
 * Aligned with FR-013: Support full and partial refunds up to original payment amount.
 */
@Component(id = "refund-workflow")
public class RefundWorkflow extends Workflow<RefundWorkflow.State> {

    public record State(
        String refundId,
        String transactionId,
        Money refundAmount,
        String reason,
        String gatewayRefundId,
        WorkflowStatus status,
        String failureReason
    ) {
        public State withGatewayRefundId(String id) {
            return new State(refundId, transactionId, refundAmount, reason,
                id, status, failureReason);
        }

        public State withStatus(WorkflowStatus newStatus) {
            return new State(refundId, transactionId, refundAmount, reason,
                gatewayRefundId, newStatus, failureReason);
        }

        public State withFailureReason(String reason) {
            return new State(refundId, transactionId, refundAmount, this.reason,
                gatewayRefundId, status, reason);
        }
    }

    public enum WorkflowStatus {
        INITIATED,
        REFUND_PROCESSING,
        REFUND_COMPLETED,
        REFUND_FAILED
    }

    public record StartRefund(
        String transactionId,
        Money refundAmount,
        String reason
    ) {}

    private final ComponentClient componentClient;
    private final StripePaymentGateway stripeGateway;

    public RefundWorkflow(
        ComponentClient componentClient,
        StripePaymentGateway stripeGateway
    ) {
        this.componentClient = componentClient;
        this.stripeGateway = stripeGateway;
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .defaultStepTimeout(Duration.ofSeconds(60)) // Refunds typically faster than payments
            .stepRecovery(
                RefundWorkflow::processRefundStep,
                RecoverStrategy.maxRetries(2).failoverTo(RefundWorkflow::refundFailedStep)
            )
            .build();
    }

    @Override
    public State emptyState() {
        return null;
    }

    // Command handlers
    public Effect<String> startRefund(StartRefund command) {
        if (currentState() != null) {
            return effects().error("Refund workflow already started");
        }

        // Generate unique refund ID
        String refundId = UUID.randomUUID().toString();

        var initialState = new State(
            refundId,
            command.transactionId,
            command.refundAmount,
            command.reason,
            null,
            WorkflowStatus.INITIATED,
            null
        );

        return effects()
            .updateState(initialState)
            .transitionTo(RefundWorkflow::initiateRefundStep)
            .thenReply(refundId);
    }

    public Effect<String> getStatus() {
        if (currentState() == null) {
            return effects().error("Refund workflow not started");
        }
        return effects().reply(currentState().status.name());
    }

    // Workflow steps

    @StepName("initiate-refund")
    private StepEffect initiateRefundStep() {
        // Record refund initiation in payment transaction entity
        var event = new PaymentTransactionEvent.RefundInitiated(
            currentState().refundId,
            currentState().refundAmount,
            currentState().reason
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::recordRefundInitiated)
            .invoke(event);

        return stepEffects()
            .updateState(currentState().withStatus(WorkflowStatus.REFUND_PROCESSING))
            .thenTransitionTo(RefundWorkflow::processRefundStep);
    }

    @StepName("process-refund")
    private StepEffect processRefundStep() {
        // Get payment transaction to retrieve gateway transaction ID
        var transaction = componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::getTransaction)
            .invoke();

        if (transaction == null) {
            return stepEffects()
                .updateState(currentState()
                    .withStatus(WorkflowStatus.REFUND_FAILED)
                    .withFailureReason("Transaction not found"))
                .thenTransitionTo(RefundWorkflow::refundFailedStep);
        }

        // Validate refund is possible
        if (transaction.status() != com.example.payment.domain.PaymentStatus.SUCCEEDED) {
            return stepEffects()
                .updateState(currentState()
                    .withStatus(WorkflowStatus.REFUND_FAILED)
                    .withFailureReason("Cannot refund a " + transaction.status() + " payment"))
                .thenTransitionTo(RefundWorkflow::refundFailedStep);
        }

        // Calculate total already refunded
        Money totalRefunded = transaction.refunds().stream()
            .filter(r -> r.status() == RefundStatus.COMPLETED)
            .map(r -> r.amount())
            .reduce(Money::add)
            .orElse(new Money(java.math.BigDecimal.ZERO, currentState().refundAmount.currency()));

        Money totalWithNewRefund = totalRefunded.add(currentState().refundAmount);

        if (totalWithNewRefund.amount().compareTo(transaction.amount().amount()) > 0) {
            return stepEffects()
                .updateState(currentState()
                    .withStatus(WorkflowStatus.REFUND_FAILED)
                    .withFailureReason("Refund amount exceeds original payment amount"))
                .thenTransitionTo(RefundWorkflow::refundFailedStep);
        }

        // Process refund with Stripe
        try {
            String gatewayRefundId = stripeGateway.refund(
                transaction.stripePaymentIntentId(),
                currentState().refundAmount
            );

            return stepEffects()
                .updateState(currentState().withGatewayRefundId(gatewayRefundId))
                .thenTransitionTo(RefundWorkflow::completeRefundStep);
        } catch (Exception e) {
            return stepEffects()
                .updateState(currentState()
                    .withStatus(WorkflowStatus.REFUND_FAILED)
                    .withFailureReason("Gateway error: " + e.getMessage()))
                .thenTransitionTo(RefundWorkflow::refundFailedStep);
        }
    }

    @StepName("complete-refund")
    private StepEffect completeRefundStep() {
        // Record successful refund in payment transaction entity
        var event = new PaymentTransactionEvent.RefundCompleted(
            currentState().refundId,
            currentState().gatewayRefundId
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::recordRefundCompleted)
            .invoke(event);

        return stepEffects()
            .updateState(currentState().withStatus(WorkflowStatus.REFUND_COMPLETED))
            .thenEnd();
    }

    @StepName("refund-failed")
    private StepEffect refundFailedStep() {
        // Record failed refund in payment transaction entity
        var event = new PaymentTransactionEvent.RefundFailed(
            currentState().refundId,
            currentState().failureReason
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::recordRefundFailed)
            .invoke(event);

        return stepEffects()
            .updateState(currentState().withStatus(WorkflowStatus.REFUND_FAILED))
            .thenEnd();
    }
}
