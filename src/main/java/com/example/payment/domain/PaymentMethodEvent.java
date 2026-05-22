package com.example.payment.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;
import java.time.YearMonth;

/**
 * Payment method events for Event Sourcing.
 * Tracks saved payment method lifecycle (FR-010, FR-011).
 */
public sealed interface PaymentMethodEvent {

    @TypeName("payment-method-saved")
    record PaymentMethodSaved(
        String customerId,
        String token,
        CardBrand brand,
        String last4Digits,
        YearMonth expirationDate,
        boolean isDefault,
        Instant timestamp
    ) implements PaymentMethodEvent {}

    @TypeName("payment-method-set-default")
    record PaymentMethodSetDefault(
        Instant timestamp
    ) implements PaymentMethodEvent {}

    @TypeName("payment-method-deleted")
    record PaymentMethodDeleted(
        Instant timestamp
    ) implements PaymentMethodEvent {}
}
