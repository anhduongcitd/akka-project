package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.payment.application.*;
import com.example.payment.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payment REST API Endpoint.
 * Provides payment transaction operations.
 * Aligned with FR-001 to FR-021: All payment functional requirements.
 */
@HttpEndpoint("/payment")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class PaymentEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;
    private final ReceiptGenerator receiptGenerator;

    public PaymentEndpoint(ComponentClient componentClient, ReceiptGenerator receiptGenerator) {
        this.componentClient = componentClient;
        this.receiptGenerator = receiptGenerator;
    }

    // Request/Response records
    public record CreatePaymentRequest(
        MoneyRequest amount,
        String cardToken,              // For new card payments (mutually exclusive with paymentMethodId)
        String paymentMethodId,        // For saved payment method (mutually exclusive with cardToken)
        String merchantReference,
        CustomerRequest customer,
        boolean savePaymentMethod      // Only applies when using cardToken
    ) {}

    public record MoneyRequest(String value, String currency) {
        public Money toMoney() {
            return new Money(
                new BigDecimal(value),
                Currency.valueOf(currency.toUpperCase())
            );
        }
    }

    public record CustomerRequest(
        String customerId,
        String email,
        String name
    ) {
        public Customer toCustomer() {
            return new Customer(customerId, email, name);
        }
    }

    public record PaymentResponse(
        String transactionId,
        String status,
        MoneyResponse amount,
        String merchantReference,
        String createdAt,
        String completedAt,
        String failureReason
    ) {}

    public record MoneyResponse(String value, String currency, String formatted) {}

    public record RefundRequest(
        MoneyRequest amount,
        String reason
    ) {}

    public record RefundResponse(
        String refundId,
        String transactionId,
        String status,
        MoneyResponse amount,
        String reason
    ) {}

    public record RefundListResponse(List<RefundInfo> refunds) {}

    public record RefundInfo(
        String refundId,
        MoneyResponse amount,
        String status,
        String reason,
        String createdAt,
        String completedAt
    ) {}

    public record PaymentHistoryResponse(List<PaymentHistoryItem> transactions) {}

    public record PaymentHistoryItem(
        String transactionId,
        String merchantReference,
        MoneyResponse amount,
        String status,
        String createdAt,
        String completedAt,
        String failureReason
    ) {}

    public record PaymentHistoryRequest(
        String customerId,
        String status,      // Optional: filter by status
        String startDate,   // Optional: filter by date range (ISO instant format)
        String endDate      // Optional: filter by date range (ISO instant format)
    ) {}

    // API Methods
    @Post("/transactions")
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Validate request
        if (request.amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        // Must provide either cardToken or paymentMethodId, but not both
        if (request.cardToken == null && request.paymentMethodId == null) {
            throw new IllegalArgumentException("Either cardToken or paymentMethodId is required");
        }
        if (request.cardToken != null && request.paymentMethodId != null) {
            throw new IllegalArgumentException("Cannot provide both cardToken and paymentMethodId");
        }

        if (request.customer == null) {
            throw new IllegalArgumentException("Customer information is required");
        }

        // Generate transaction ID
        String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        // Convert request to domain objects
        Money amount = request.amount.toMoney();
        Customer customer = request.customer.toCustomer();

        // Determine payment source
        String paymentSource = request.cardToken != null ? request.cardToken : request.paymentMethodId;
        boolean isUsingSavedMethod = request.paymentMethodId != null;

        // Start payment workflow
        var startCommand = new PaymentProcessingWorkflow.StartPayment(
            transactionId,
            customer,
            amount,
            request.merchantReference,
            paymentSource,
            isUsingSavedMethod,
            request.savePaymentMethod && !isUsingSavedMethod // Only save if using new card
        );

        componentClient
            .forWorkflow(transactionId)
            .method(PaymentProcessingWorkflow::startPayment)
            .invoke(startCommand);

        // Return immediate response
        return new PaymentResponse(
            transactionId,
            "PENDING",
            toMoneyResponse(amount),
            request.merchantReference,
            Instant.now().toString(),
            null,
            null
        );
    }

    @Get("/transactions/{transactionId}")
    public PaymentResponse getTransaction(String transactionId) {
        // Get payment transaction from entity
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        return toPaymentResponse(transaction);
    }

    @Post("/transactions/{transactionId}/refunds")
    public RefundResponse initiateRefund(String transactionId, RefundRequest request) {
        // Validate request
        if (request.amount == null) {
            throw new IllegalArgumentException("Refund amount is required");
        }

        Money refundAmount = request.amount.toMoney();

        // Generate refund workflow ID
        String refundWorkflowId = "refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        // Use default reason if none provided
        String reason = request.reason != null ? request.reason : "Customer requested refund";

        // Start refund workflow
        var startCommand = new RefundWorkflow.StartRefund(
            transactionId,
            refundAmount,
            reason
        );

        String refundId = componentClient
            .forWorkflow(refundWorkflowId)
            .method(RefundWorkflow::startRefund)
            .invoke(startCommand);

        // Return immediate response
        return new RefundResponse(
            refundId,
            transactionId,
            "PENDING",
            toMoneyResponse(refundAmount),
            reason
        );
    }

    @Get("/transactions/{transactionId}/refunds")
    public RefundListResponse getRefunds(String transactionId) {
        // Get payment transaction from entity
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }

        List<RefundInfo> refunds = transaction.refunds().stream()
            .map(this::toRefundInfo)
            .toList();

        return new RefundListResponse(refunds);
    }

    @Post("/history")
    public PaymentHistoryResponse getPaymentHistory(PaymentHistoryRequest request) {
        if (request.customerId == null || request.customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }

        PaymentHistoryView.PaymentHistoryEntries entries;

        // Apply filters based on request parameters
        if (request.status != null && !request.status.isBlank()) {
            // Filter by status
            // Validate the status
            PaymentStatus.valueOf(request.status.toUpperCase()); // Throws exception if invalid
            var filter = new PaymentHistoryView.StatusFilter(request.customerId, request.status.toUpperCase());
            entries = componentClient
                .forView()
                .method(PaymentHistoryView::getByCustomerAndStatus)
                .invoke(filter);
        } else if (request.startDate != null && request.endDate != null) {
            // Filter by date range
            Instant start = Instant.parse(request.startDate);
            Instant end = Instant.parse(request.endDate);
            var filter = new PaymentHistoryView.DateRangeFilter(request.customerId, start, end);
            entries = componentClient
                .forView()
                .method(PaymentHistoryView::getByCustomerAndDateRange)
                .invoke(filter);
        } else {
            // No filters - get all transactions
            entries = componentClient
                .forView()
                .method(PaymentHistoryView::getByCustomer)
                .invoke(request.customerId);
        }

        List<PaymentHistoryItem> items = entries.transactions().stream()
            .map(this::toPaymentHistoryItem)
            .toList();

        return new PaymentHistoryResponse(items);
    }

    @Get("/transactions/{transactionId}/receipt")
    public akka.http.javadsl.model.HttpResponse getReceipt(String transactionId, String format) {
        // Get transaction
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }

        // Default to text if format not specified
        if (format == null) {
            format = "text";
        }

        if ("html".equalsIgnoreCase(format)) {
            String htmlReceipt = receiptGenerator.generateHtmlReceipt(transaction);
            return akka.javasdk.http.HttpResponses.ok(htmlReceipt)
                .withHeaders(List.of(
                    akka.http.javadsl.model.headers.ContentType.create(
                        akka.http.javadsl.model.ContentTypes.TEXT_HTML_UTF8
                    )
                ));
        } else {
            String textReceipt = receiptGenerator.generateTextReceipt(transaction);
            return akka.javasdk.http.HttpResponses.ok(textReceipt)
                .withHeaders(List.of(
                    akka.http.javadsl.model.headers.ContentType.create(
                        akka.http.javadsl.model.ContentTypes.TEXT_PLAIN_UTF8
                    )
                ));
        }
    }

    // Helper methods
    private PaymentResponse toPaymentResponse(PaymentTransaction transaction) {
        return new PaymentResponse(
            transaction.transactionId(),
            transaction.status().name(),
            toMoneyResponse(transaction.amount()),
            transaction.merchantReference(),
            transaction.createdAt().toString(),
            transaction.completedAt() != null ? transaction.completedAt().toString() : null,
            transaction.failureReason()
        );
    }

    private MoneyResponse toMoneyResponse(Money money) {
        return new MoneyResponse(
            money.amount().toPlainString(),
            money.currency().name(),
            money.format()
        );
    }

    private RefundInfo toRefundInfo(Refund refund) {
        return new RefundInfo(
            refund.refundId(),
            toMoneyResponse(refund.amount()),
            refund.status().name(),
            refund.reason(),
            refund.createdAt().toString(),
            refund.completedAt() != null ? refund.completedAt().toString() : null
        );
    }

    private PaymentHistoryItem toPaymentHistoryItem(PaymentHistoryView.PaymentHistoryEntry entry) {
        var money = new Money(
            new java.math.BigDecimal(entry.amountValue()),
            Currency.valueOf(entry.currency())
        );
        return new PaymentHistoryItem(
            entry.transactionId(),
            entry.merchantReference(),
            toMoneyResponse(money),
            entry.status(),  // status is already a String
            entry.createdAt().toString(),
            entry.completedAt() != null ? entry.completedAt().toString() : null,
            entry.failureReason()
        );
    }
}
