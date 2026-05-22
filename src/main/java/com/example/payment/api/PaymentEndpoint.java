package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.PaymentProcessingWorkflow;
import com.example.payment.application.PaymentTransactionEntity;
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
}
