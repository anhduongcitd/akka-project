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
    private final ExchangeRateService exchangeRateService;

    // Rate limit configuration
    private static final int IP_MAX_REQUESTS = 100;
    private static final int IP_WINDOW_MINUTES = 1;
    private static final int CUSTOMER_MAX_PAYMENTS = 50;
    private static final int CUSTOMER_WINDOW_MINUTES = 60;

    // Fraud detection configuration
    private static final int FRAUD_VELOCITY_LIMIT = 5;      // Max 5 payments
    private static final int FRAUD_VELOCITY_WINDOW = 10;    // in 10 minutes
    private static final BigDecimal FRAUD_HIGH_VALUE = new BigDecimal("5000.00");  // $5000
    private static final int FRAUD_HIGH_VALUE_WINDOW = 60;  // in 1 hour
    private static final int FRAUD_DUPLICATE_WINDOW = 5;    // 5 minutes

    public PaymentEndpoint(ComponentClient componentClient, ReceiptGenerator receiptGenerator, ExchangeRateService exchangeRateService) {
        this.componentClient = componentClient;
        this.receiptGenerator = receiptGenerator;
        this.exchangeRateService = exchangeRateService;
    }

    // Request/Response records
    public record CreatePaymentRequest(
        MoneyRequest amount,
        String cardToken,              // For new card payments (mutually exclusive with paymentMethodId)
        String paymentMethodId,        // For saved payment method (mutually exclusive with cardToken)
        String merchantReference,
        CustomerRequest customer,
        boolean savePaymentMethod,     // Only applies when using cardToken
        String idempotencyKey          // Optional: prevent duplicate payments
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

    public record ExchangeRatesResponse(
        java.util.Map<String, String> rates,
        String baseCurrency,
        String timestamp
    ) {}

    public record CurrencyConversionRequest(
        String amount,
        String fromCurrency,
        String toCurrency
    ) {}

    public record CurrencyConversionResponse(
        MoneyResponse originalAmount,
        MoneyResponse convertedAmount,
        String exchangeRate,
        String timestamp
    ) {}

    // API Methods
    @Post("/transactions")
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        // Rate limiting - check IP limit
        String clientIp = requestContext().requestHeader("X-Forwarded-For")
            .map(akka.http.javadsl.model.HttpHeader::value)
            .orElse(requestContext().requestHeader("X-Real-IP")
                .map(akka.http.javadsl.model.HttpHeader::value)
                .orElse("unknown"));

        var ipCheckRequest = new RateLimitEntity.CheckRateLimitRequest(
            clientIp,
            RateLimitRecord.RateLimitType.IP,
            IP_MAX_REQUESTS,
            IP_WINDOW_MINUTES
        );

        String ipResult = componentClient
            .forKeyValueEntity("ip:" + clientIp)
            .method(RateLimitEntity::checkAndRecord)
            .invoke(ipCheckRequest);

        if ("EXCEEDED".equals(ipResult)) {
            throw new IllegalArgumentException("Rate limit exceeded for IP address. Please try again later.");
        }

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

        // Rate limiting - check customer payment limit
        var customerCheckRequest = new RateLimitEntity.CheckRateLimitRequest(
            request.customer.customerId(),
            RateLimitRecord.RateLimitType.CUSTOMER,
            CUSTOMER_MAX_PAYMENTS,
            CUSTOMER_WINDOW_MINUTES
        );

        String customerResult = componentClient
            .forKeyValueEntity("customer:" + request.customer.customerId())
            .method(RateLimitEntity::checkAndRecord)
            .invoke(customerCheckRequest);

        if ("EXCEEDED".equals(customerResult)) {
            throw new IllegalArgumentException("Payment limit exceeded for customer. Please try again later.");
        }

        // Fraud detection - check for suspicious patterns
        Money amount = request.amount.toMoney();
        var fraudCheck = new FraudCheckEntity.CheckFraudRequest(
            request.merchantReference,
            amount.amount(),
            amount.currency().name(),
            FRAUD_VELOCITY_LIMIT,
            FRAUD_VELOCITY_WINDOW,
            FRAUD_HIGH_VALUE,
            FRAUD_HIGH_VALUE_WINDOW,
            FRAUD_DUPLICATE_WINDOW
        );

        var fraudResult = componentClient
            .forKeyValueEntity("fraud:" + request.customer.customerId())
            .method(FraudCheckEntity::checkAndRecord)
            .invoke(fraudCheck);

        if (!fraudResult.passed()) {
            throw new IllegalArgumentException("Payment blocked: " + fraudResult.reason());
        }

        // Check idempotency key if provided
        String transactionId;
        if (request.idempotencyKey != null && !request.idempotencyKey.isBlank()) {
            // Check if this idempotency key already has a transaction
            String existingTxnId = componentClient
                .forKeyValueEntity(request.idempotencyKey)
                .method(IdempotencyEntity::getTransactionId)
                .invoke();

            if (existingTxnId != null && !existingTxnId.isEmpty()) {
                // Return existing transaction (idempotent response)
                PaymentTransaction transaction = componentClient
                    .forEventSourcedEntity(existingTxnId)
                    .method(PaymentTransactionEntity::getPayment)
                    .invoke();
                return toPaymentResponse(transaction);
            }

            // Generate new transaction ID
            transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

            // Register idempotency key with new transaction ID
            componentClient
                .forKeyValueEntity(request.idempotencyKey)
                .method(IdempotencyEntity::register)
                .invoke(transactionId);
        } else {
            // No idempotency key - generate transaction ID normally
            transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }

        // Validate amount is greater than zero (amount already created for fraud check)
        if (amount.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

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

        // Verify transaction exists
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        Money refundAmount = request.amount.toMoney();

        // Validate refund amount is greater than zero
        if (refundAmount.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }

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

    @Get("/exchange-rates")
    public ExchangeRatesResponse getExchangeRates() {
        var rates = exchangeRateService.getExchangeRates();

        // Convert rates to String format for JSON response
        java.util.Map<String, String> ratesMap = new java.util.HashMap<>();
        rates.rates().forEach((currency, rate) ->
            ratesMap.put(currency.name(), rate.toPlainString())
        );

        return new ExchangeRatesResponse(
            ratesMap,
            rates.baseCurrency().name(),
            rates.timestamp().toString()
        );
    }

    @Post("/convert")
    public CurrencyConversionResponse convertCurrency(CurrencyConversionRequest request) {
        // Validate request
        if (request.amount == null || request.fromCurrency == null || request.toCurrency == null) {
            throw new IllegalArgumentException("amount, fromCurrency, and toCurrency are required");
        }

        BigDecimal amount = new BigDecimal(request.amount);
        Currency fromCurrency = Currency.valueOf(request.fromCurrency.toUpperCase());
        Currency toCurrency = Currency.valueOf(request.toCurrency.toUpperCase());

        var result = exchangeRateService.convertCurrency(amount, fromCurrency, toCurrency);

        var originalMoney = new Money(result.originalAmount(), result.fromCurrency());
        var convertedMoney = new Money(result.convertedAmount(), result.toCurrency());

        return new CurrencyConversionResponse(
            toMoneyResponse(originalMoney),
            toMoneyResponse(convertedMoney),
            result.exchangeRate().toPlainString(),
            result.timestamp().toString()
        );
    }
}
