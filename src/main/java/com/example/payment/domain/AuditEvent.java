package com.example.payment.domain;

import akka.javasdk.annotations.TypeName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

/**
 * Audit event types for compliance and forensics.
 * Immutable audit trail of all critical payment operations.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AuditEvent.PaymentCreated.class, name = "audit-payment-created"),
    @JsonSubTypes.Type(value = AuditEvent.PaymentCompleted.class, name = "audit-payment-completed"),
    @JsonSubTypes.Type(value = AuditEvent.PaymentFailed.class, name = "audit-payment-failed"),
    @JsonSubTypes.Type(value = AuditEvent.RefundInitiated.class, name = "audit-refund-initiated"),
    @JsonSubTypes.Type(value = AuditEvent.RefundCompleted.class, name = "audit-refund-completed"),
    @JsonSubTypes.Type(value = AuditEvent.PaymentMethodSaved.class, name = "audit-payment-method-saved"),
    @JsonSubTypes.Type(value = AuditEvent.PaymentMethodDeleted.class, name = "audit-payment-method-deleted"),
    @JsonSubTypes.Type(value = AuditEvent.FraudAlertTriggered.class, name = "audit-fraud-alert-triggered")
})
public sealed interface AuditEvent {

    String customerId();
    Instant timestamp();
    String eventType();
    String description();

    @TypeName("audit-payment-created")
    record PaymentCreated(
        String customerId,
        String transactionId,
        String amount,
        String currency,
        String merchantReference,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "PAYMENT_CREATED"; }
    }

    @TypeName("audit-payment-completed")
    record PaymentCompleted(
        String customerId,
        String transactionId,
        String amount,
        String currency,
        String gatewayTransactionId,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "PAYMENT_COMPLETED"; }
    }

    @TypeName("audit-payment-failed")
    record PaymentFailed(
        String customerId,
        String transactionId,
        String amount,
        String currency,
        String failureReason,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "PAYMENT_FAILED"; }
    }

    @TypeName("audit-refund-initiated")
    record RefundInitiated(
        String customerId,
        String transactionId,
        String refundId,
        String amount,
        String currency,
        String reason,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "REFUND_INITIATED"; }
    }

    @TypeName("audit-refund-completed")
    record RefundCompleted(
        String customerId,
        String transactionId,
        String refundId,
        String amount,
        String currency,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "REFUND_COMPLETED"; }
    }

    @TypeName("audit-payment-method-saved")
    record PaymentMethodSaved(
        String customerId,
        String paymentMethodId,
        String brand,
        String last4,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "PAYMENT_METHOD_SAVED"; }
    }

    @TypeName("audit-payment-method-deleted")
    record PaymentMethodDeleted(
        String customerId,
        String paymentMethodId,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "PAYMENT_METHOD_DELETED"; }
    }

    @TypeName("audit-fraud-alert-triggered")
    record FraudAlertTriggered(
        String customerId,
        String transactionId,
        String fraudType,
        String amount,
        String currency,
        String details,
        Instant timestamp,
        String description
    ) implements AuditEvent {
        @Override
        public String eventType() { return "FRAUD_ALERT"; }
    }
}
