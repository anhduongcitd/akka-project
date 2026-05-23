package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.payment.domain.*;

import java.time.Instant;
import java.util.ArrayList;

/**
 * Payment Transaction Event Sourced Entity.
 * Maintains complete audit trail of payment lifecycle.
 * Aligned with FR-006: Unique transaction IDs.
 * Aligned with FR-014: Complete audit trail.
 */
@Component(id = "payment-transaction")
public class PaymentTransactionEntity extends EventSourcedEntity<PaymentTransaction, PaymentTransactionEvent> {

    private final String entityId;

    public PaymentTransactionEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public PaymentTransaction emptyState() {
        return null;
    }

    // Command records
    public record InitiatePayment(
        Customer customer,
        Money amount,
        String merchantReference
    ) {}

    public record AuthorizePayment(String gatewayTransactionId) {}

    public record CompletePayment() {}

    public record FailPayment(String reason) {}

    public record InitiateRefund(String refundId, Money amount, String reason) {}

    public record CompleteRefund(String refundId) {}

    public record FailRefund(String refundId, String reason) {}

    // Command handlers
    public Effect<String> initiatePayment(InitiatePayment command) {
        if (currentState() != null) {
            return effects().error("Payment already initiated");
        }

        if (command.customer == null) {
            return effects().error("Customer is required");
        }

        if (command.amount == null || command.amount.isZero()) {
            return effects().error("Invalid payment amount");
        }

        var event = new PaymentTransactionEvent.PaymentInitiated(
            command.customer,
            command.amount,
            command.merchantReference,
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(state -> entityId);
    }

    public Effect<String> authorizePayment(AuthorizePayment command) {
        if (currentState() == null) {
            return effects().error("Payment not initiated");
        }

        if (currentState().status() != PaymentStatus.PENDING) {
            return effects().error("Payment already processed");
        }

        if (command.gatewayTransactionId == null || command.gatewayTransactionId.isBlank()) {
            return effects().error("Gateway transaction ID is required");
        }

        var event = new PaymentTransactionEvent.PaymentAuthorized(
            command.gatewayTransactionId,
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(state -> "Payment authorized");
    }

    public Effect<String> completePayment(CompletePayment command) {
        if (currentState() == null) {
            return effects().error("Payment not initiated");
        }

        if (currentState().status() != PaymentStatus.AUTHORIZED) {
            return effects().error("Payment not authorized");
        }

        var event = new PaymentTransactionEvent.PaymentSucceeded(Instant.now());

        return effects()
            .persist(event)
            .thenReply(state -> "Payment completed");
    }

    public Effect<String> failPayment(FailPayment command) {
        if (currentState() == null) {
            return effects().error("Payment not initiated");
        }

        if (!currentState().isPending()) {
            return effects().error("Payment already finalized");
        }

        var event = new PaymentTransactionEvent.PaymentFailed(
            command.reason,
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(state -> "Payment failed");
    }

    public Effect<String> initiateRefund(InitiateRefund command) {
        if (currentState() == null) {
            return effects().error("Payment not found");
        }

        if (!currentState().canRefund(command.amount)) {
            return effects().error("Refund amount exceeds refundable amount");
        }

        var event = new PaymentTransactionEvent.RefundInitiated(
            command.refundId,
            command.amount,
            command.reason
        );

        return effects()
            .persist(event)
            .thenReply(state -> command.refundId);
    }

    public Effect<String> completeRefund(CompleteRefund command) {
        if (currentState() == null) {
            return effects().error("Payment not found");
        }

        var refund = currentState().refunds().stream()
            .filter(r -> r.refundId().equals(command.refundId))
            .findFirst()
            .orElse(null);

        if (refund == null) {
            return effects().error("Refund not found");
        }

        if (refund.status() != RefundStatus.PENDING) {
            return effects().error("Refund already processed");
        }

        var event = new PaymentTransactionEvent.RefundCompleted(
            command.refundId,
            null // Gateway refund ID set by workflow
        );

        return effects()
            .persist(event)
            .thenReply(state -> "Refund completed");
    }

    public Effect<String> failRefund(FailRefund command) {
        if (currentState() == null) {
            return effects().error("Payment not found");
        }

        var refund = currentState().refunds().stream()
            .filter(r -> r.refundId().equals(command.refundId))
            .findFirst()
            .orElse(null);

        if (refund == null) {
            return effects().error("Refund not found");
        }

        if (refund.status() != RefundStatus.PENDING) {
            return effects().error("Refund already processed");
        }

        var event = new PaymentTransactionEvent.RefundFailed(
            command.refundId,
            command.reason
        );

        return effects()
            .persist(event)
            .thenReply(state -> "Refund failed");
    }

    // Methods for workflow integration
    public Effect<akka.Done> recordRefundInitiated(PaymentTransactionEvent.RefundInitiated event) {
        return effects()
            .persist(event)
            .thenReply(state -> akka.Done.getInstance());
    }

    public Effect<akka.Done> recordRefundCompleted(PaymentTransactionEvent.RefundCompleted event) {
        return effects()
            .persist(event)
            .thenReply(state -> akka.Done.getInstance());
    }

    public Effect<akka.Done> recordRefundFailed(PaymentTransactionEvent.RefundFailed event) {
        return effects()
            .persist(event)
            .thenReply(state -> akka.Done.getInstance());
    }

    public Effect<PaymentTransaction> getTransaction() {
        if (currentState() == null) {
            return effects().reply(null);
        }
        return effects().reply(currentState());
    }

    // Query command
    public Effect<PaymentTransaction> getPayment() {
        if (currentState() == null) {
            return effects().error("Payment not found");
        }
        return effects().reply(currentState());
    }

    // Event handlers
    @Override
    public PaymentTransaction applyEvent(PaymentTransactionEvent event) {
        return switch (event) {
            case PaymentTransactionEvent.PaymentInitiated initiated ->
                new PaymentTransaction(
                    entityId,
                    initiated.customer(),
                    initiated.amount(),
                    PaymentStatus.PENDING,
                    initiated.merchantReference(),
                    null,
                    new ArrayList<>(),
                    initiated.timestamp(),
                    null,
                    null
                );

            case PaymentTransactionEvent.PaymentAuthorized authorized ->
                currentState()
                    .withStatus(PaymentStatus.AUTHORIZED)
                    .withGatewayTransactionId(authorized.gatewayTransactionId());

            case PaymentTransactionEvent.PaymentCaptured captured ->
                currentState();

            case PaymentTransactionEvent.PaymentSucceeded succeeded ->
                currentState()
                    .withStatus(PaymentStatus.SUCCEEDED)
                    .withCompletedAt(succeeded.timestamp());

            case PaymentTransactionEvent.PaymentFailed failed ->
                currentState()
                    .withStatus(PaymentStatus.FAILED)
                    .withFailureReason(failed.reason())
                    .withCompletedAt(failed.timestamp());

            case PaymentTransactionEvent.RefundInitiated refundInitiated -> {
                var refund = new Refund(
                    refundInitiated.refundId(),
                    entityId,
                    refundInitiated.refundAmount(),
                    refundInitiated.reason(),
                    RefundStatus.PENDING,
                    Instant.now(),
                    null
                );
                yield currentState().addRefund(refund);
            }

            case PaymentTransactionEvent.RefundCompleted refundCompleted -> {
                var updatedRefunds = new ArrayList<>(currentState().refunds());
                for (int i = 0; i < updatedRefunds.size(); i++) {
                    if (updatedRefunds.get(i).refundId().equals(refundCompleted.refundId())) {
                        updatedRefunds.set(i,
                            updatedRefunds.get(i)
                                .withStatus(RefundStatus.SUCCEEDED)
                                .withCompletedAt(Instant.now())
                        );
                        break;
                    }
                }

                // Update transaction status if fully refunded
                Money totalRefunded = new Money(java.math.BigDecimal.ZERO, currentState().amount().currency());
                for (Refund r : updatedRefunds) {
                    if (r.status() == RefundStatus.SUCCEEDED) {
                        totalRefunded = totalRefunded.add(r.amount());
                    }
                }

                PaymentStatus newStatus = totalRefunded.amount()
                    .compareTo(currentState().amount().amount()) >= 0
                    ? PaymentStatus.REFUNDED
                    : currentState().status();

                yield new PaymentTransaction(
                    currentState().transactionId(),
                    currentState().customer(),
                    currentState().amount(),
                    newStatus,
                    currentState().merchantReference(),
                    currentState().gatewayTransactionId(),
                    updatedRefunds,
                    currentState().createdAt(),
                    currentState().completedAt(),
                    currentState().failureReason()
                );
            }

            case PaymentTransactionEvent.RefundFailed refundFailed -> {
                var updatedRefunds = new ArrayList<>(currentState().refunds());
                for (int i = 0; i < updatedRefunds.size(); i++) {
                    if (updatedRefunds.get(i).refundId().equals(refundFailed.refundId())) {
                        updatedRefunds.set(i,
                            updatedRefunds.get(i)
                                .withStatus(RefundStatus.FAILED)
                        );
                        break;
                    }
                }

                yield new PaymentTransaction(
                    currentState().transactionId(),
                    currentState().customer(),
                    currentState().amount(),
                    currentState().status(),
                    currentState().merchantReference(),
                    currentState().gatewayTransactionId(),
                    updatedRefunds,
                    currentState().createdAt(),
                    currentState().completedAt(),
                    currentState().failureReason()
                );
            }
        };
    }
}
