package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.PaymentProcessingWorkflow;
import com.example.payment.application.PaymentTransactionEntity;
import com.example.payment.application.RefundWorkflow;
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
public class PaymentEndpoint {

    private final ComponentClient componentClient;

    public PaymentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record CreatePaymentRequest(
        MoneyRequest amount,
        String cardToken,
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

    // API Methods
    @Post("/transactions")
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Validate request
        if (request.amount == null || request.cardToken == null) {
            throw new IllegalArgumentException("Amount and card token are required");
        }

        if (request.customer == null) {
            throw new IllegalArgumentException("Customer information is required");
        }

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
            request.cardToken
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

        // Start refund workflow
        var startCommand = new RefundWorkflow.StartRefund(
            transactionId,
            refundAmount,
            request.reason != null ? request.reason : "Customer requested refund"
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
            request.reason
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
}
