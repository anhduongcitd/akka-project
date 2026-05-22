package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.PaymentHistoryView;
import com.example.payment.application.PaymentProcessingWorkflow;
import com.example.payment.application.PaymentTransactionEntity;
import com.example.payment.application.ReceiptGenerator;
import com.example.payment.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payment REST API Endpoint.
 * Provides payment transaction operations.
 * Aligned with FR-001 to FR-021: All payment functional requirements.
 */
@HttpEndpoint("/payment")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class PaymentEndpoint {

    private final ComponentClient componentClient;
    private final ReceiptGenerator receiptGenerator;

    public PaymentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
        this.receiptGenerator = new ReceiptGenerator();
    }

    // Request/Response records
    public record CreatePaymentRequest(
        MoneyRequest amount,
        String cardToken,
        String paymentMethodId,  // Optional: use saved payment method
        String merchantReference,
        CustomerRequest customer,
        boolean savePaymentMethod
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

    // API Methods
    @Post("/transactions")
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Validate request
        if (request.amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        // Either cardToken OR paymentMethodId must be provided
        if (request.cardToken == null && request.paymentMethodId == null) {
            throw new IllegalArgumentException("Either card token or payment method ID is required");
        }

        if (request.customer == null) {
            throw new IllegalArgumentException("Customer information is required");
        }

        // Determine which token to use
        String token = request.cardToken != null
            ? request.cardToken
            : request.paymentMethodId; // Use saved payment method token

        // Generate transaction ID
        String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        // Convert request to domain objects
        Money amount = request.amount.toMoney();
        Customer customer = request.customer.toCustomer();

        // Start payment workflow
        var startCommand = new PaymentProcessingWorkflow.StartPayment(
            transactionId,
            customer,
            amount,
            request.merchantReference,
            token
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

    @Get("/history/{customerId}")
    public PaymentHistoryResponse getPaymentHistory(String customerId) {
        // Query payment history view
        var entries = componentClient
            .forView()
            .method(PaymentHistoryView::getByCustomerId)
            .invoke(customerId);

        var transactions = entries.transactions().stream()
            .map(this::toHistoryEntryResponse)
            .collect(Collectors.toList());

        return new PaymentHistoryResponse(transactions, transactions.size());
    }

    @Get("/history/{customerId}/status/{status}")
    public PaymentHistoryResponse getPaymentHistoryByStatus(String customerId, String status) {
        // Query filtered history
        var filter = new PaymentHistoryView.StatusFilter(customerId, status);
        var entries = componentClient
            .forView()
            .method(PaymentHistoryView::getByCustomerIdAndStatus)
            .invoke(filter);

        var transactions = entries.transactions().stream()
            .map(this::toHistoryEntryResponse)
            .collect(Collectors.toList());

        return new PaymentHistoryResponse(transactions, transactions.size());
    }

    @Get("/transactions/{transactionId}/receipt")
    public String getReceipt(String transactionId) {
        // Get transaction details
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        // Generate HTML receipt
        return receiptGenerator.generateReceiptHtml(transaction);
    }

    @Get("/transactions/{transactionId}/receipt/text")
    public String getReceiptText(String transactionId) {
        // Get transaction details
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        // Generate text receipt
        return receiptGenerator.generateReceiptText(transaction);
    }

    // Response records for history
    public record PaymentHistoryResponse(
        List<HistoryEntryResponse> transactions,
        int total
    ) {}

    public record HistoryEntryResponse(
        String transactionId,
        String status,
        MoneyResponse amount,
        String merchantReference,
        String createdAt,
        String completedAt,
        boolean hasRefunds
    ) {}

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

    private HistoryEntryResponse toHistoryEntryResponse(PaymentHistoryView.PaymentHistoryEntry entry) {
        var money = new Money(entry.amount(), Currency.valueOf(entry.currency()));
        return new HistoryEntryResponse(
            entry.transactionId(),
            entry.status().name(),
            toMoneyResponse(money),
            entry.merchantReference(),
            entry.createdAt().toString(),
            entry.completedAt() != null ? entry.completedAt().toString() : null,
            entry.hasRefunds()
        );
    }
}
