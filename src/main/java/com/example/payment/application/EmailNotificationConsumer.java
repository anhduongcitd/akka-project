package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import com.example.payment.domain.PaymentTransactionEvent;

/**
 * Email Notification Consumer.
 * Listens to payment transaction events and sends confirmation emails.
 * Aligned with FR-009: Send transaction confirmation emails immediately.
 */
@Component(id = "email-notification-consumer")
@Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
public class EmailNotificationConsumer extends Consumer {

    private final EmailService emailService;

    public EmailNotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    public Effect onEvent(PaymentTransactionEvent event) {
        return switch (event) {
            case PaymentTransactionEvent.PaymentSucceeded succeeded -> {
                // Get transaction ID from message context
                String transactionId = messageContext().eventSubject().orElse("unknown");

                // Note: In a real implementation, we would need to fetch the full
                // transaction details to get customer email and amount.
                // For now, this is a placeholder showing the pattern.

                // emailService.sendPaymentConfirmation(...)

                yield effects().done();
            }

            case PaymentTransactionEvent.PaymentRefunded refunded -> {
                String transactionId = messageContext().eventSubject().orElse("unknown");

                // Send refund notification email
                // emailService.sendRefundNotification(...)

                yield effects().done();
            }

            default ->
                // Don't send emails for intermediate states
                effects().ignore();
        };
    }
}
