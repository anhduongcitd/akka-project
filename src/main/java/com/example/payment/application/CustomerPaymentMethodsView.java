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
 * View for querying customer's saved payment methods.
 * Aligned with FR-010: Allow users to save payment methods for faster checkout.
 */
@Component(id = "customer-payment-methods-view")
public class CustomerPaymentMethodsView extends View {

    /**
     * Entry representing a saved payment method in the view.
     */
    public record PaymentMethodEntry(
        String paymentMethodId,
        String customerId,
        CardBrand brand,
        String last4Digits,
        String expirationDate,  // Stored as "yyyy-MM" format
        boolean isDefault,
        boolean isExpired,
        boolean isExpiringSoon,
        Instant createdAt
    ) {}

    /**
     * Response wrapper for multiple payment methods.
     */
    public record PaymentMethods(List<PaymentMethodEntry> methods) {}

    @Query("SELECT * AS methods FROM customer_payment_methods WHERE customerId = :customerId ORDER BY isDefault DESC, createdAt DESC")
    public QueryEffect<PaymentMethods> getByCustomer(String customerId) {
        return queryResult();
    }

    @Query("SELECT * AS methods FROM customer_payment_methods WHERE customerId = :customerId AND isDefault = true")
    public QueryEffect<PaymentMethods> getDefaultByCustomer(String customerId) {
        return queryResult();
    }

    @Query("SELECT * AS methods FROM customer_payment_methods WHERE isExpiringSoon = true ORDER BY expirationDate ASC")
    public QueryEffect<PaymentMethods> getExpiringSoon() {
        return queryResult();
    }

    /**
     * Table updater for payment method events.
     */
    @Consume.FromEventSourcedEntity(PaymentMethodEntity.class)
    public static class CustomerPaymentMethodsUpdater extends TableUpdater<PaymentMethodEntry> {

        public Effect<PaymentMethodEntry> onEvent(PaymentMethodEvent event) {
            var paymentMethodId = updateContext().eventSubject().orElse("");

            return switch (event) {
                case PaymentMethodEvent.PaymentMethodSaved saved -> {
                    var entry = new PaymentMethodEntry(
                        paymentMethodId,
                        saved.customerId(),
                        saved.brand(),
                        saved.last4Digits(),
                        saved.expirationDate().toString(), // Convert YearMonth to String
                        saved.isDefault(),
                        isExpired(saved.expirationDate()),
                        isExpiringSoon(saved.expirationDate()),
                        saved.timestamp()
                    );
                    yield effects().updateRow(entry);
                }

                case PaymentMethodEvent.PaymentMethodSetDefault setDefault -> {
                    if (rowState() == null) {
                        yield effects().ignore(); // Row doesn't exist yet
                    }
                    var updated = new PaymentMethodEntry(
                        rowState().paymentMethodId,
                        rowState().customerId,
                        rowState().brand,
                        rowState().last4Digits,
                        rowState().expirationDate,
                        true, // Set as default
                        rowState().isExpired,
                        rowState().isExpiringSoon,
                        rowState().createdAt
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentMethodEvent.PaymentMethodDeleted deleted -> {
                    // Hard delete from view
                    yield effects().deleteRow();
                }
            };
        }

        private boolean isExpired(YearMonth expirationDate) {
            return YearMonth.now().isAfter(expirationDate);
        }

        private boolean isExpiringSoon(YearMonth expirationDate) {
            YearMonth now = YearMonth.now();
            long monthsUntilExpiry = now.until(expirationDate, java.time.temporal.ChronoUnit.MONTHS);
            return monthsUntilExpiry <= 1 && monthsUntilExpiry >= 0;
        }
    }
}
