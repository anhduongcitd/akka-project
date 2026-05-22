package com.example.payment.domain;

/**
 * Customer reference for payment transactions.
 * Contains minimal customer information required for payment processing.
 */
public record Customer(
    String customerId,
    String email,
    String name
) {
    public Customer {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
    }
}
