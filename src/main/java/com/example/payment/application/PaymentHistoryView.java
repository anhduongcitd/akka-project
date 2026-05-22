package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.domain.PaymentTransactionEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Payment History View.
 * Queries payment transaction history with filtering capabilities.
 * Aligned with FR-014: Complete audit trail of all transactions.
 */
@Component(id = "payment-history-view")
public class PaymentHistoryView extends View {

    public record PaymentHistoryEntry(
        String transactionId,
        String customerId,
        String customerEmail,
        String customerName,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String merchantReference,
        String gatewayTransactionId,
        Instant createdAt,
        Instant completedAt,
        String failureReason,
        boolean hasRefunds
    ) {}

    public record PaymentHistoryEntries(List<PaymentHistoryEntry> transactions) {}

    // Query parameter records
    public record StatusFilter(String customerId, String status) {}
    public record DateRangeFilter(String customerId, Instant startDate, Instant endDate) {}

    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomerId(String customerId) {
        return queryResult();
    }

    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND status = :status ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomerIdAndStatus(StatusFilter filter) {
        return queryResult();
    }

    @Query("SELECT * AS transactions FROM payment_history WHERE customerId = :customerId AND createdAt >= :startDate AND createdAt <= :endDate ORDER BY createdAt DESC")
    public QueryEffect<PaymentHistoryEntries> getByCustomerIdAndDateRange(DateRangeFilter filter) {
        return queryResult();
    }

    @Query("SELECT * AS transactions FROM payment_history WHERE merchantReference = :merchantReference")
    public QueryEffect<PaymentHistoryEntries> getByMerchantReference(String merchantReference) {
        return queryResult();
    }

    @Consume.FromEventSourcedEntity(PaymentTransactionEntity.class)
    public static class PaymentHistoryTableUpdater extends TableUpdater<PaymentHistoryEntry> {

        public Effect<PaymentHistoryEntry> onEvent(PaymentTransactionEvent event) {
            String transactionId = updateContext().eventSubject().orElse("");

            return switch (event) {
                case PaymentTransactionEvent.PaymentInitiated initiated -> {
                    var entry = new PaymentHistoryEntry(
                        transactionId,
                        initiated.customer().customerId(),
                        initiated.customer().email(),
                        initiated.customer().name(),
                        initiated.amount().amount(),
                        initiated.amount().currency().name(),
                        PaymentStatus.PENDING,
                        initiated.merchantReference(),
                        null,
                        initiated.timestamp(),
                        null,
                        null,
                        false
                    );
                    yield effects().updateRow(entry);
                }

                case PaymentTransactionEvent.PaymentAuthorized authorized -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentHistoryEntry(
                        currentRow.transactionId,
                        currentRow.customerId,
                        currentRow.customerEmail,
                        currentRow.customerName,
                        currentRow.amount,
                        currentRow.currency,
                        PaymentStatus.AUTHORIZED,
                        currentRow.merchantReference,
                        authorized.gatewayTransactionId(),
                        currentRow.createdAt,
                        currentRow.completedAt,
                        currentRow.failureReason,
                        currentRow.hasRefunds
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.PaymentSucceeded succeeded -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentHistoryEntry(
                        currentRow.transactionId,
                        currentRow.customerId,
                        currentRow.customerEmail,
                        currentRow.customerName,
                        currentRow.amount,
                        currentRow.currency,
                        PaymentStatus.SUCCEEDED,
                        currentRow.merchantReference,
                        currentRow.gatewayTransactionId,
                        currentRow.createdAt,
                        succeeded.timestamp(),
                        currentRow.failureReason,
                        currentRow.hasRefunds
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.PaymentFailed failed -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentHistoryEntry(
                        currentRow.transactionId,
                        currentRow.customerId,
                        currentRow.customerEmail,
                        currentRow.customerName,
                        currentRow.amount,
                        currentRow.currency,
                        PaymentStatus.FAILED,
                        currentRow.merchantReference,
                        currentRow.gatewayTransactionId,
                        currentRow.createdAt,
                        failed.timestamp(),
                        failed.reason(),
                        currentRow.hasRefunds
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.RefundInitiated refundInitiated -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentHistoryEntry(
                        currentRow.transactionId,
                        currentRow.customerId,
                        currentRow.customerEmail,
                        currentRow.customerName,
                        currentRow.amount,
                        currentRow.currency,
                        currentRow.status,
                        currentRow.merchantReference,
                        currentRow.gatewayTransactionId,
                        currentRow.createdAt,
                        currentRow.completedAt,
                        currentRow.failureReason,
                        true // Has refunds
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.PaymentRefunded refunded -> {
                    var currentRow = rowState();
                    if (currentRow == null) {
                        yield effects().ignore();
                    }
                    var updated = new PaymentHistoryEntry(
                        currentRow.transactionId,
                        currentRow.customerId,
                        currentRow.customerEmail,
                        currentRow.customerName,
                        currentRow.amount,
                        currentRow.currency,
                        currentRow.status,
                        currentRow.merchantReference,
                        currentRow.gatewayTransactionId,
                        currentRow.createdAt,
                        currentRow.completedAt,
                        currentRow.failureReason,
                        true // Has refunds
                    );
                    yield effects().updateRow(updated);
                }

                case PaymentTransactionEvent.PaymentCaptured captured ->
                    effects().ignore();
            };
        }
    }
}
