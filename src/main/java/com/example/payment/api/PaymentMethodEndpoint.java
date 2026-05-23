package com.example.payment.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.payment.application.CustomerPaymentMethodsView;
import com.example.payment.application.PaymentMethodEntity;
import com.example.payment.domain.CardBrand;
import com.example.payment.domain.PaymentMethod;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Payment Method HTTP Endpoint.
 * RESTful API for managing saved payment methods.
 * Aligned with FR-010: Allow users to save payment methods for faster checkout.
 */
@HttpEndpoint("/payment/methods")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class PaymentMethodEndpoint extends AbstractHttpEndpoint {

    private final ComponentClient componentClient;

    public PaymentMethodEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records

    public record SavePaymentMethodRequest(
        String customerId,
        String token,           // PCI-compliant token from Stripe
        String brand,           // Card brand as string
        String last4Digits,
        String expirationDate,  // Format: "yyyy-MM" (e.g., "2025-12")
        boolean isDefault
    ) {}

    public record PaymentMethodResponse(
        String paymentMethodId,
        String customerId,
        String brand,
        String last4Digits,
        String expirationDate,
        String maskedNumber,
        boolean isDefault,
        boolean isExpired,
        boolean isExpiringSoon,
        String createdAt
    ) {}

    public record PaymentMethodsResponse(
        java.util.List<PaymentMethodResponse> methods
    ) {}

    // POST /payment/methods - Save a new payment method
    @Post
    public PaymentMethodResponse savePaymentMethod(SavePaymentMethodRequest request) {
        // Validate request
        if (request.customerId == null || request.customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request.token == null || request.token.isBlank()) {
            throw new IllegalArgumentException("Payment method token is required");
        }
        if (request.brand == null || request.brand.isBlank()) {
            throw new IllegalArgumentException("Card brand is required");
        }
        if (request.last4Digits == null || !request.last4Digits.matches("\\d{4}")) {
            throw new IllegalArgumentException("Last 4 digits must be exactly 4 digits");
        }
        if (request.expirationDate == null || !request.expirationDate.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Expiration date must be in format yyyy-MM");
        }

        // Parse expiration date
        YearMonth expirationDate;
        try {
            expirationDate = YearMonth.parse(request.expirationDate);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid expiration date format");
        }

        // Parse card brand
        CardBrand cardBrand;
        try {
            cardBrand = CardBrand.valueOf(request.brand.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid card brand: " + request.brand);
        }

        // Generate unique payment method ID
        String paymentMethodId = UUID.randomUUID().toString();

        // Save payment method via entity
        var command = new PaymentMethodEntity.SavePaymentMethodCommand(
            request.customerId,
            request.token,
            cardBrand,
            request.last4Digits,
            expirationDate,
            request.isDefault
        );

        componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::savePaymentMethod)
            .invoke(command);

        // Retrieve saved payment method
        var savedMethod = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::getPaymentMethod)
            .invoke();

        return toApiResponse(savedMethod);
    }

    // GET /payment/methods?customerId={customerId} - List customer's payment methods
    @Get
    public PaymentMethodsResponse listPaymentMethods() {
        var customerId = requestContext().queryParams().getString("customerId")
            .orElseThrow(() -> new IllegalArgumentException("customerId query parameter is required"));

        var viewResult = componentClient
            .forView()
            .method(CustomerPaymentMethodsView::getByCustomer)
            .invoke(customerId);

        return new PaymentMethodsResponse(
            viewResult.methods().stream()
                .map(this::toApiResponse)
                .toList()
        );
    }

    // DELETE /payment/methods/{id} - Delete a payment method
    @Delete("/{paymentMethodId}")
    public Done deletePaymentMethod(String paymentMethodId) {
        componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::delete)
            .invoke();

        return Done.getInstance();
    }

    // PUT /payment/methods/{id}/default - Set payment method as default
    @Put("/{paymentMethodId}/default")
    public PaymentMethodResponse setAsDefault(String paymentMethodId) {
        componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::setAsDefault)
            .invoke();

        // Retrieve updated payment method
        var updatedMethod = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::getPaymentMethod)
            .invoke();

        return toApiResponse(updatedMethod);
    }

    // Private helper methods

    private PaymentMethodResponse toApiResponse(PaymentMethod method) {
        return new PaymentMethodResponse(
            method.paymentMethodId(),
            method.customerId(),
            method.brand().name(),
            method.last4Digits(),
            method.expirationDate().toString(),
            method.getMaskedNumber(),
            method.isDefault(),
            method.isExpired(),
            method.isExpiringSoon(),
            method.createdAt().toString()
        );
    }

    private PaymentMethodResponse toApiResponse(CustomerPaymentMethodsView.PaymentMethodEntry entry) {
        return new PaymentMethodResponse(
            entry.paymentMethodId(),
            entry.customerId(),
            entry.brand().name(),
            entry.last4Digits(),
            entry.expirationDate(),
            "**** " + entry.last4Digits(),
            entry.isDefault(),
            entry.isExpired(),
            entry.isExpiringSoon(),
            entry.createdAt().toString()
        );
    }
}
