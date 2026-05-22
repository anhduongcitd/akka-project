package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.CustomerPaymentMethodsView;
import com.example.payment.application.PaymentMethodEntity;
import com.example.payment.domain.CardBrand;
import com.example.payment.domain.PaymentMethod;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payment Method REST API Endpoint.
 * Manages saved payment methods for customers.
 * Aligned with FR-010, FR-011: Save and delete payment methods.
 */
@HttpEndpoint("/payment/methods")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class PaymentMethodEndpoint {

    private final ComponentClient componentClient;

    public PaymentMethodEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record SavePaymentMethodRequest(
        String customerId,
        String token,
        String brand,
        String last4Digits,
        String expirationDate, // Format: "YYYY-MM"
        boolean isDefault
    ) {}

    public record PaymentMethodResponse(
        String paymentMethodId,
        String customerId,
        String brand,
        String maskedNumber,
        String expirationDate,
        boolean isDefault,
        boolean isExpired,
        boolean isExpiringSoon,
        String createdAt
    ) {}

    public record PaymentMethodsListResponse(
        List<PaymentMethodResponse> methods,
        int total
    ) {}

    // API Methods
    @Post
    public PaymentMethodResponse savePaymentMethod(SavePaymentMethodRequest request) {
        // Validate request
        if (request.customerId == null || request.token == null) {
            throw new IllegalArgumentException("Customer ID and token are required");
        }

        if (request.last4Digits == null || !request.last4Digits.matches("\\d{4}")) {
            throw new IllegalArgumentException("Last 4 digits must be exactly 4 digits");
        }

        // Parse expiration date
        YearMonth expirationDate;
        try {
            expirationDate = YearMonth.parse(request.expirationDate);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid expiration date format. Use YYYY-MM");
        }

        // Parse card brand
        CardBrand brand;
        try {
            brand = CardBrand.valueOf(request.brand.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid card brand: " + request.brand);
        }

        // Generate payment method ID
        String paymentMethodId = "pm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        // Save payment method
        var command = new PaymentMethodEntity.SavePaymentMethod(
            request.customerId,
            request.token,
            brand,
            request.last4Digits,
            expirationDate,
            request.isDefault
        );

        componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        // Return response
        return new PaymentMethodResponse(
            paymentMethodId,
            request.customerId,
            brand.name(),
            "**** " + request.last4Digits,
            expirationDate.toString(),
            request.isDefault,
            false, // Not expired (validated during save)
            false, // Will be calculated by view
            java.time.Instant.now().toString()
        );
    }

    @Get("/customer/{customerId}")
    public PaymentMethodsListResponse listPaymentMethods(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        // Query view
        var entries = componentClient
            .forView()
            .method(CustomerPaymentMethodsView::getByCustomerId)
            .invoke(customerId);

        var methods = entries.methods().stream()
            .map(this::toPaymentMethodResponse)
            .collect(Collectors.toList());

        return new PaymentMethodsListResponse(methods, methods.size());
    }

    @Delete("/{paymentMethodId}")
    public String deletePaymentMethod(String paymentMethodId) {
        var command = new PaymentMethodEntity.DeletePaymentMethod();

        String result = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::deletePaymentMethod)
            .invoke(command);

        return result;
    }

    @Put("/{paymentMethodId}/default")
    public String setDefaultPaymentMethod(String paymentMethodId) {
        var command = new PaymentMethodEntity.SetDefaultPaymentMethod();

        String result = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::setDefault)
            .invoke(command);

        return result;
    }

    @Get("/{paymentMethodId}")
    public PaymentMethodResponse getPaymentMethod(String paymentMethodId) {
        PaymentMethod paymentMethod = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::getPaymentMethod)
            .invoke();

        return new PaymentMethodResponse(
            paymentMethod.paymentMethodId(),
            paymentMethod.customerId(),
            paymentMethod.brand().name(),
            paymentMethod.getMaskedNumber(),
            paymentMethod.expirationDate().toString(),
            paymentMethod.isDefault(),
            paymentMethod.isExpired(),
            paymentMethod.isExpiringSoon(),
            paymentMethod.createdAt().toString()
        );
    }

    // Helper methods
    private PaymentMethodResponse toPaymentMethodResponse(CustomerPaymentMethodsView.PaymentMethodEntry entry) {
        return new PaymentMethodResponse(
            entry.paymentMethodId(),
            entry.customerId(),
            entry.brand().name(),
            "**** " + entry.last4Digits(),
            entry.expirationDate(),
            entry.isDefault(),
            entry.isExpired(),
            entry.isExpiringSoon(),
            entry.createdAt().toString()
        );
    }
}
