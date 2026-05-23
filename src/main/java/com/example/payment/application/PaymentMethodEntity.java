package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.payment.domain.CardBrand;
import com.example.payment.domain.PaymentMethod;
import com.example.payment.domain.PaymentMethodEvent;

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
              false,  // Not deleted initially
              saved.timestamp()
          );
      case PaymentMethodEvent.PaymentMethodSetDefault setDefault ->
          currentState().withIsDefault(true);
      case PaymentMethodEvent.PaymentMethodDeleted deleted ->
          currentState().withDeleted(true);
    };
  }

  public record SavePaymentMethodCommand(
      String customerId,
      String token,
      CardBrand brand,
      String last4Digits,
      java.time.YearMonth expirationDate,
      boolean isDefault
  ) {}

  public Effect<Done> savePaymentMethod(SavePaymentMethodCommand command) {
    // Validation
    if (command.customerId == null || command.customerId.isBlank()) {
      return effects().error("Customer ID is required");
    }
    if (command.token == null || command.token.isBlank()) {
      return effects().error("Payment method token is required");
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
    if (java.time.YearMonth.now().isAfter(command.expirationDate)) {
      return effects().error("Card has expired");
    }

    // Check if already exists
    if (currentState() != null) {
      return effects().error("Payment method already exists");
    }

    var event = new PaymentMethodEvent.PaymentMethodSaved(
        command.customerId,
        command.token,
        command.brand,
        command.last4Digits,
        command.expirationDate,
        command.isDefault,
        java.time.Instant.now()
    );

    return effects().persist(event).thenReply(state -> Done.getInstance());
  }

  public Effect<Done> setAsDefault() {
    if (currentState() == null || currentState().isDeleted()) {
      return effects().error("Payment method not found");
    }
    if (currentState().isDefault()) {
      return effects().reply(Done.getInstance()); // Already default, no-op
    }

    var event = new PaymentMethodEvent.PaymentMethodSetDefault(java.time.Instant.now());
    return effects().persist(event).thenReply(state -> Done.getInstance());
  }

  public Effect<Done> delete() {
    if (currentState() == null || currentState().isDeleted()) {
      return effects().error("Payment method not found");
    }

    var event = new PaymentMethodEvent.PaymentMethodDeleted(java.time.Instant.now());
    return effects().persist(event).thenReply(state -> Done.getInstance());
  }

  public Effect<PaymentMethod> getPaymentMethod() {
    if (currentState() == null || currentState().isDeleted()) {
      return effects().error("Payment method not found");
    }
    return effects().reply(currentState());
  }
}
