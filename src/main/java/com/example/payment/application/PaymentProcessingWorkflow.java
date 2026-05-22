package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.payment.domain.Customer;
import com.example.payment.domain.Money;

import java.time.Duration;

/**
 * Payment Processing Workflow.
 * Orchestrates payment authorization and capture with Stripe.
 * Implements compensation for failures.
 * Aligned with FR-015: Handle payment timeout gracefully (3 minutes).
 */
@Component(id = "payment-processing-workflow")
public class PaymentProcessingWorkflow extends Workflow<PaymentProcessingWorkflow.State> {

    public record State(
        String transactionId,
        Customer customer,
        Money amount,
        String merchantReference,
        String cardToken,
        String gatewayTransactionId,
        WorkflowStatus status,
        String failureReason
    ) {
        public State withGatewayTransactionId(String id) {
            return new State(transactionId, customer, amount, merchantReference,
                cardToken, id, status, failureReason);
        }

        public State withStatus(WorkflowStatus newStatus) {
            return new State(transactionId, customer, amount, merchantReference,
                cardToken, gatewayTransactionId, newStatus, failureReason);
        }

        public State withFailureReason(String reason) {
            return new State(transactionId, customer, amount, merchantReference,
                cardToken, gatewayTransactionId, status, reason);
        }
    }

    public enum WorkflowStatus {
        INITIATED,
        PAYMENT_AUTHORIZED,
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED
    }

    public record StartPayment(
        String transactionId,
        Customer customer,
        Money amount,
        String merchantReference,
        String cardToken
    ) {}

    private final ComponentClient componentClient;
    private final StripePaymentGateway stripeGateway;

    public PaymentProcessingWorkflow(
        ComponentClient componentClient,
        StripePaymentGateway stripeGateway
    ) {
        this.componentClient = componentClient;
        this.stripeGateway = stripeGateway;
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .defaultStepTimeout(Duration.ofSeconds(180)) // 3 minutes per FR-015
            .stepRecovery(
                PaymentProcessingWorkflow::authorizePaymentStep,
                RecoverStrategy.maxRetries(2).failoverTo(PaymentProcessingWorkflow::paymentFailedStep)
            )
            .build();
    }

    @Override
    public State emptyState() {
        return null;
    }

    // Command handlers
    public Effect<String> startPayment(StartPayment command) {
        if (currentState() != null) {
            return effects().error("Workflow already started");
        }

        var initialState = new State(
            command.transactionId,
            command.customer,
            command.amount,
            command.merchantReference,
            command.cardToken,
            null,
            WorkflowStatus.INITIATED,
            null
        );

        return effects()
            .updateState(initialState)
            .transitionTo(PaymentProcessingWorkflow::initiatePaymentStep)
            .thenReply(command.transactionId);
    }

    public Effect<State> getStatus() {
        if (currentState() == null) {
            return effects().error("Workflow not found");
        }
        return effects().reply(currentState());
    }

    // Workflow steps
    @StepName("initiate-payment")
    private StepEffect initiatePaymentStep() {
        var initiateCommand = new PaymentTransactionEntity.InitiatePayment(
            currentState().customer,
            currentState().amount,
            currentState().merchantReference
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::initiatePayment)
            .invoke(initiateCommand);

        return stepEffects()
            .thenTransitionTo(PaymentProcessingWorkflow::authorizePaymentStep);
    }

    @StepName("authorize-payment")
    private StepEffect authorizePaymentStep() {
        // Call Stripe to authorize payment
        var paymentResult = stripeGateway.authorizePayment(
            currentState().cardToken,
            currentState().amount,
            "Payment for " + currentState().merchantReference,
            currentState().transactionId  // Idempotency key
        ).join();  // Blocking call within workflow step

        if (!paymentResult.success()) {
            return stepEffects()
                .updateState(currentState()
                    .withStatus(WorkflowStatus.PAYMENT_FAILED)
                    .withFailureReason(paymentResult.failureReason())
                )
                .thenTransitionTo(PaymentProcessingWorkflow::paymentFailedStep);
        }

        // Update entity with gateway transaction ID
        var authorizeCommand = new PaymentTransactionEntity.AuthorizePayment(
            paymentResult.chargeId()
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::authorizePayment)
            .invoke(authorizeCommand);

        return stepEffects()
            .updateState(currentState()
                .withGatewayTransactionId(paymentResult.chargeId())
                .withStatus(WorkflowStatus.PAYMENT_AUTHORIZED)
            )
            .thenTransitionTo(PaymentProcessingWorkflow::completePaymentStep);
    }

    @StepName("complete-payment")
    private StepEffect completePaymentStep() {
        var completeCommand = new PaymentTransactionEntity.CompletePayment();

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::completePayment)
            .invoke(completeCommand);

        return stepEffects()
            .updateState(currentState().withStatus(WorkflowStatus.PAYMENT_SUCCEEDED))
            .thenEnd();
    }

    @StepName("payment-failed")
    private StepEffect paymentFailedStep() {
        var failCommand = new PaymentTransactionEntity.FailPayment(
            currentState().failureReason != null
                ? currentState().failureReason
                : "Payment processing failed"
        );

        componentClient
            .forEventSourcedEntity(currentState().transactionId)
            .method(PaymentTransactionEntity::failPayment)
            .invoke(failCommand);

        return stepEffects()
            .updateState(currentState().withStatus(WorkflowStatus.PAYMENT_FAILED))
            .thenEnd();
    }
}
