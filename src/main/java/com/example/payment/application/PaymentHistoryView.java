package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.domain.PaymentTransactionEvent;

import java.time.Instant;
import java.util.List;

/**
 * View for querying payment transaction history.
 * Aligned with FR-012: View transaction history with filtering.
 */
@Component(id = "payment-history-view")
public class PaymentHistoryView extends View {

    /**
     * Entry representing a payment transaction in the view.
     * Note: status is stored as String to enable querying.
     * completedAt uses Instant.EPOCH (1970-01-01) as sentinel for null.
     * failureReason uses empty string as sentinel for null.
     */
    public record PaymentHistoryEntry(
        String transactionId,
        String customerId,
        String customerEmail,
        String merchantReference,
        String amountValue,
        String currency,
        String status,  // Stored as String for querying
        Instant createdAt,
        Instant completedAt,  // Use Instant.EPOCH for null
        String failureReason  // Use "" for null
    ) {}

    /**
     * Response wrapper for multiple payment history entries.
     */
    public record PaymentHistoryEntries(List<PaymentHistoryEntry> transactions) {}

    /**
     * Query all transactions for a customer, ordered by most recent first.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomer(String customerId) {
        return queryResult();
    }

    /**
     * Query filter for status-based searches.
     */
    public record StatusFilter(String customerId, String status) {}

    /**
     * Query filter for date range searches.
     */
    public record DateRangeFilter(String customerId, Instant startDate, Instant endDate) {}

    /**
     * Query transactions for a customer with status filter.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND status = :status ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomerAndStatus(StatusFilter filter) {
        return queryResult();
    }

    /**
     * Query transactions within a date range for a customer.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomerAndDateRange(DateRangeFilter filter) {
        return queryResult();
    }

    /**
     * Query successful transactions for a customer.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND status = 'SUCCEEDED' ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getSuccessfulTransactions(String customerId) {
        return queryResult();
    }

    /**
     * Query failed transactions for a customer.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND status = 'FAILED' ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getFailedTransactions(String customerId) {
        return queryResult();
    }

    /**
     * Query transactions by merchant reference.
     */
    @Query("SELECT * AS transactions FROM payment_history WHERE merchantReference = :merchantReference ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByMerchantReference(String merchantReference) {
        return queryResult();
    }

    // Note: COUNT queries are not directly supported in Akka views.
    // Use the list query and count on the client side.

    /**
     * Table updater for payment transaction events.
     */
    @Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
    public static class PaymentHistoryUpdater extends TableUpdater<PaymentHistoryEntry> {

        public Effect<PaymentHistoryEntry> onEvent(PaymentTransactionEvent event) {
            var transactionId = updateContext().eventSubject().orElse("");

            return switch (event) {
                case PaymentTransactionEvent.PaymentInitiated initiated -> {
                    var entry = new PaymentHistoryEntry(
                        transactionId,
                        initiated.customer().customerId(),
                        initiated.customer().email(),
                        initiated.merchantReference(),
                        initiated.amount().amount().toPlainString(),
                        initiated.amount().currency().name(),
                        "PENDING",
                        initiated.timestamp(),
                        Instant.EPOCH,  // Sentinel for null
                        ""  // Sentinel for null
                    );
                    yield effects().updateRow(entry);
                }

                case PaymentTransactionEvent.PaymentSucceeded succeeded -> {
                    if (rowState() == null) {
                        yield effects().ignore(); // Row doesn't exist yet
                    }
                    var updated = new PaymentHistoryEntry(
                        rowState().transactionId,
                        rowState().customerId,
                        rowState().customerEmail,
                        rowState().merchantReference,
                        rowState().amountValue,
                        rowState().currency,
                        "SUCCEEDED",
                        rowState().createdAt,
                        succeeded.timestamp(),
                        ""  // Sentinel for null
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.PaymentFailed failed -> {
                    if (rowState() == null) {
                        yield effects().ignore(); // Row doesn't exist yet
                    }
                    var updated = new PaymentHistoryEntry(
                        rowState().transactionId,
                        rowState().customerId,
                        rowState().customerEmail,
                        rowState().merchantReference,
                        rowState().amountValue,
                        rowState().currency,
                        "FAILED",
                        rowState().createdAt,
                        failed.timestamp(),
                        failed.reason()
                    );
                    yield effects().updateRow(updated);
                }

                // Ignore intermediate state changes (authorized, captured)
                // These don't affect the history view which shows final outcomes
                default -> effects().ignore();
            };
        }
    }
}
