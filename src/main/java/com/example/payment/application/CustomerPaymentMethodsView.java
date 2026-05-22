package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.domain.CardBrand;
import com.example.payment.domain.PaymentMethodEvent;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * Customer Payment Methods View.
 * Queries saved payment methods by customer ID.
 * Aligned with FR-010: Display saved payment methods for quick selection.
 */
@Component(id = "customer-payment-methods-view")
public class CustomerPaymentMethodsView extends View {

    public record PaymentMethodEntry(
        String paymentMethodId,
        String customerId,
        CardBrand brand,
        String last4Digits,
        String expirationDate,
        boolean isDefault,
        boolean isExpired,
        boolean isExpiringSoon,
        Instant createdAt
    ) {}

    public record PaymentMethodEntries(List<PaymentMethodEntry> methods) {}

    @Query("SELECT * AS methods FROM payment_methods WHERE customerId = :customerId ORDER BY isDefault DESC, createdAt DESC")
    public QueryEffect<PaymentMethodEntries> getByCustomerId(String customerId) {
        return queryResult();
    }

    @Consume.FromEventSourcedEntity(PaymentMethodEntity.class)
    public static class PaymentMethodsTableUpdater extends TableUpdater<PaymentMethodEntry> {

        public Effect<PaymentMethodEntry> onEvent(PaymentMethodEvent event) {
            String paymentMethodId = updateContext().eventSubject().orElse("");

            return switch (event) {
                case PaymentMethodEvent.PaymentMethodSaved saved -> {
                    YearMonth expiration = saved.expirationDate();
                    YearMonth now = YearMonth.now();

                    boolean isExpired = now.isAfter(expiration);
                    boolean isExpiringSoon = !isExpired &&
                        java.time.temporal.ChronoUnit.MONTHS.between(now, expiration) <= 1;

                    var entry = new PaymentMethodEntry(
                        paymentMethodId,
                        saved.customerId(),
                        saved.brand(),
                        saved.last4Digits(),
                        saved.expirationDate().toString(),
                        saved.isDefault(),
                        isExpired,
                        isExpiringSoon,
                        saved.timestamp()
                    );
                    yield effects().updateRow(entry);
                }

                case PaymentMethodEvent.PaymentMethodSetDefault setDefault -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentMethodEntry(
                        currentRow.paymentMethodId,
                        currentRow.customerId,
                        currentRow.brand,
                        currentRow.last4Digits,
                        currentRow.expirationDate,
                        true, // Set as default
                        currentRow.isExpired,
                        currentRow.isExpiringSoon,
                        currentRow.createdAt
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentMethodEvent.PaymentMethodDeleted deleted ->
                    effects().deleteRow();
            };
        }
    }
}
