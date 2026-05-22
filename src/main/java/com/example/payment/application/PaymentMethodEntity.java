package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.payment.domain.*;

import java.time.Instant;
import java.time.YearMonth;

/**
 * Payment Method Event Sourced Entity.
 * Manages saved payment methods with PCI-compliant tokenization.
 * Aligned with FR-010: Save payment methods securely.
 * Aligned with FR-011: Allow users to delete saved payment methods.
 */
@Component(id = "payment-method")
public class PaymentMethodEntity extends EventSourcedEntity<PaymentMethod, PaymentMethodEvent> {

    private final String entityId;

    public PaymentMethodEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    @Override
    public PaymentMethod emptyState() {
        return null;
    }

    // Command records
    public record SavePaymentMethod(
        String customerId,
        String token,
        CardBrand brand,
        String last4Digits,
        YearMonth expirationDate,
        boolean isDefault
    ) {}

    public record SetDefaultPaymentMethod() {}

    public record DeletePaymentMethod() {}

    // Command handlers
    public Effect<String> savePaymentMethod(SavePaymentMethod command) {
        if (currentState() != null) {
            return effects().error("Payment method already exists");
        }

        if (command.customerId == null || command.customerId.isBlank()) {
            return effects().error("Customer ID is required");
        }

        if (command.token == null || command.token.isBlank()) {
            return effects().error("Token is required");
        }

        if (command.brand == null) {
            return effects().error("Card brand is required");
        }

        if (command.last4Digits == null || !command.last4Digits.matches("\\d{4}")) {
            return effects().error("Last 4 digits must be exactly 4 digits");
        }

        if (command.expirationDate == null) {
            return effects().error("Expiration date is required");
        }

        // Check if card is already expired
        if (YearMonth.now().isAfter(command.expirationDate)) {
            return effects().error("Card is expired");
        }

        var event = new PaymentMethodEvent.PaymentMethodSaved(
            command.customerId,
            command.token,
            command.brand,
            command.last4Digits,
            command.expirationDate,
            command.isDefault,
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(state -> entityId);
    }

    public Effect<String> setDefault(SetDefaultPaymentMethod command) {
        if (currentState() == null) {
            return effects().error("Payment method not found");
        }

        if (currentState().isDefault()) {
            return effects().reply("Already default");
        }

        var event = new PaymentMethodEvent.PaymentMethodSetDefault(Instant.now());

        return effects()
            .persist(event)
            .thenReply(state -> "Set as default");
    }

    public Effect<String> deletePaymentMethod(DeletePaymentMethod command) {
        if (currentState() == null) {
            return effects().error("Payment method not found");
        }

        var event = new PaymentMethodEvent.PaymentMethodDeleted(Instant.now());

        return effects()
            .persist(event)
            .thenReply(state -> "Payment method deleted");
    }

    // Query command
    public Effect<PaymentMethod> getPaymentMethod() {
        if (currentState() == null) {
            return effects().error("Payment method not found");
        }
        return effects().reply(currentState());
    }

    // Event handlers
    @Override
    public PaymentMethod applyEvent(PaymentMethodEvent event) {
        return switch (event) {
            case PaymentMethodEvent.PaymentMethodSaved saved ->
                new PaymentMethod(
                    entityId,
                    saved.customerId(),
                    saved.token(),
                    saved.brand(),
                    saved.last4Digits(),
                    saved.expirationDate(),
                    saved.isDefault(),
                    saved.timestamp()
                );

            case PaymentMethodEvent.PaymentMethodSetDefault setDefault ->
                currentState().withIsDefault(true);

            case PaymentMethodEvent.PaymentMethodDeleted deleted ->
                currentState(); // Keep state, view handles deletion
        };
    }
}
