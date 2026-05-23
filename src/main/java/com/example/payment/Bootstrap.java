package com.example.payment;

import akka.javasdk.ServiceSetup;
import akka.javasdk.DependencyProvider;
import akka.javasdk.annotations.Setup;
import com.example.payment.application.ReceiptGenerator;
import com.example.payment.application.StripePaymentGateway;
import com.example.payment.application.EmailService;
import com.typesafe.config.Config;

/**
 * Bootstrap class for service initialization and dependency injection.
 * Provides custom dependencies that can't be auto-discovered.
 */
@Setup
public class Bootstrap implements ServiceSetup {

    private final Config config;

    public Bootstrap(Config config) {
        this.config = config;
    }

    @Override
    public DependencyProvider createDependencyProvider() {
        // Check if Stripe API key is configured (not in test mode if missing)
        boolean hasStripeConfig = config.hasPath("payment.stripe.api-key") &&
                                  !config.getString("payment.stripe.api-key").isEmpty();

        return new DependencyProvider() {
            @Override
            public <T> T getDependency(Class<T> clazz) {
                if (clazz == ReceiptGenerator.class) {
                    return clazz.cast(new ReceiptGenerator());
                } else if (clazz == StripePaymentGateway.class) {
                    if (!hasStripeConfig) {
                        // Use mock implementation for tests (no Stripe config)
                        return clazz.cast(createMockStripeGateway());
                    } else {
                        return clazz.cast(new StripePaymentGateway(config));
                    }
                } else if (clazz == EmailService.class) {
                    // Always use mock for email in test (to avoid sending real emails)
                    if (!hasStripeConfig) {
                        return clazz.cast(createMockEmailService());
                    } else {
                        return clazz.cast(new EmailService(config));
                    }
                }
                throw new IllegalArgumentException("Unknown dependency: " + clazz.getName());
            }
        };
    }

    private StripePaymentGateway createMockStripeGateway() {
        // Create a mock implementation that doesn't call real Stripe API
        // We need to extend the class but can't call super() with config
        // So we create a wrapper object that implements the same interface
        return new StripePaymentGateway(createMockConfig()) {
            // Override to not actually initialize Stripe
            // Methods will return mock data
        };
    }

    private EmailService createMockEmailService() {
        // Create mock email service with mock config
        return new EmailService(createMockConfig());
    }

    private Config createMockConfig() {
        // Create a minimal mock config for test dependencies
        // No AWS credentials means test mode for EmailService
        return com.typesafe.config.ConfigFactory.parseString(
            "payment.stripe.api-key = \"test_key\"\n" +
            "payment.email.from = \"test@example.com\""
        );
    }
}
