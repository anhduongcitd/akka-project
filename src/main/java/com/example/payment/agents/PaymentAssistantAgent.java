package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.CustomerPaymentMethodsView;
import com.example.payment.application.PaymentMethodEntity;
import com.example.payment.application.PaymentTransactionEntity;
import com.example.payment.domain.PaymentMethod;
import com.example.payment.domain.PaymentTransaction;

import java.util.List;

/**
 * Payment Assistant Agent - Intelligent payment failure resolution.
 *
 * Capabilities:
 * - Analyze payment failures and determine root cause
 * - Suggest recovery actions (retry, change card, contact bank)
 * - Check for alternative payment methods
 * - Provide user-friendly failure explanations
 *
 * Tools:
 * - PaymentTransactionEntity (get failure details)
 * - PaymentMethodEntity (check card status)
 * - CustomerPaymentMethodsView (find alternatives)
 */
@Component(id = "payment-assistant")
public class PaymentAssistantAgent extends Agent {

    private final ComponentClient componentClient;

    public PaymentAssistantAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record FailureRequest(
        String transactionId,
        String customerId
    ) {}

    public record FailureAnalysis(
        String failureType,         // Failure type: TRANSIENT, CARD_EXPIRED, INSUFFICIENT_FUNDS, GATEWAY_ERROR, FRAUD_BLOCK
        String severity,            // Severity: TEMPORARY, RECOVERABLE, TERMINAL
        List<RecoveryAction> actions, // List of recommended recovery actions in priority order
        String customerMessage      // User-friendly explanation of what went wrong
    ) {}

    public record RecoveryAction(
        String action,          // Action type: RETRY, CHANGE_CARD, CONTACT_BANK, DISPUTE, UPDATE_CARD
        String description,     // Detailed description of the action
        int priority            // Priority (1=highest, 5=lowest)
    ) {}

    // Internal tool records
    public record PaymentMethodStatus(
        String paymentMethodId,
        String brand,
        String last4,
        String expirationDate,
        boolean isExpired,
        boolean isExpiringSoon,
        boolean isDefault
    ) {}

    public record PaymentMethodInfo(
        String paymentMethodId,
        String brand,
        String last4Digits,
        boolean isDefault
    ) {}

    /**
     * Main agent command handler - analyzes payment failures.
     */
    public Effect<FailureAnalysis> analyzeFailure(FailureRequest request) {
        String systemPrompt = """
            You are a payment failure resolution expert helping customers recover from failed payments.

            Your role:
            - Diagnose payment failure root causes
            - Classify failure type and severity
            - Recommend specific recovery actions
            - Provide clear, empathetic customer explanations

            Available tools:
            - getTransactionFailureDetails: Get detailed failure information
            - checkPaymentMethod: Check if payment card has issues
            - getAlternativeMethods: Find other payment methods customer can use

            Failure classifications:
            - TRANSIENT: Temporary network/gateway issue, retry likely works
            - CARD_EXPIRED: Card expiration date passed
            - INSUFFICIENT_FUNDS: Issuer declined due to insufficient balance
            - GATEWAY_ERROR: Payment gateway unavailable or error
            - FRAUD_BLOCK: Blocked by fraud detection system

            Severity levels:
            - TEMPORARY: Will resolve quickly, retry in minutes
            - RECOVERABLE: Customer can fix (use different card, add funds)
            - TERMINAL: Cannot be recovered (fraud block, permanent decline)

            Recovery actions:
            1. RETRY: Try same payment method again (for transient issues)
            2. CHANGE_CARD: Use a different saved payment method
            3. CONTACT_BANK: Customer should call their bank/issuer
            4. UPDATE_CARD: Update card expiration date
            5. DISPUTE: Contact support to dispute fraud block

            Guidelines:
            - Be empathetic - payment failures are frustrating
            - Provide specific, actionable steps
            - Prioritize actions by likelihood of success
            - Explain technical issues in simple terms
            """;

        String userMessage = String.format(
            "Analyze this failed payment:\n" +
            "Transaction ID: %s\n" +
            "Customer ID: %s\n" +
            "Determine why it failed and recommend recovery actions.",
            request.transactionId(),
            request.customerId()
        );

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(userMessage)
            .responseConformsTo(FailureAnalysis.class)
            .onFailure(ex -> createDefaultAnalysis())
            .thenReply();
    }

    /**
     * Function tool: Get transaction failure details.
     */
    @FunctionTool(description = "Get detailed information about why a payment transaction failed")
    private String getTransactionFailureDetails(String transactionId) {
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            return "Transaction not found";
        }

        String status = transaction.status().name();
        String failureReason = transaction.failureReason();

        if ("FAILED".equals(status)) {
            return String.format(
                "Transaction failed with reason: %s\n" +
                "Amount: %s %s\n" +
                "Gateway Transaction ID: %s\n" +
                "Created: %s\n" +
                "Failed: %s",
                failureReason != null ? failureReason : "Unknown",
                transaction.amount().amount(),
                transaction.amount().currency().name(),
                transaction.gatewayTransactionId(),
                transaction.createdAt(),
                transaction.completedAt()
            );
        } else {
            return String.format("Transaction status: %s (not failed)", status);
        }
    }

    /**
     * Function tool: Check payment method status.
     */
    @FunctionTool(description = "Check if a payment method has issues like expiration")
    private PaymentMethodStatus checkPaymentMethod(String paymentMethodId) {
        PaymentMethod paymentMethod = componentClient
            .forEventSourcedEntity(paymentMethodId)
            .method(PaymentMethodEntity::getPaymentMethod)
            .invoke();

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method not found: " + paymentMethodId);
        }

        return new PaymentMethodStatus(
            paymentMethodId,
            paymentMethod.brand().name(),
            paymentMethod.last4Digits(),
            paymentMethod.expirationDate().toString(),
            paymentMethod.isExpired(),
            paymentMethod.isExpiringSoon(),
            paymentMethod.isDefault()
        );
    }

    /**
     * Function tool: Get alternative payment methods.
     */
    @FunctionTool(description = "Get other payment methods the customer has saved to suggest alternatives")
    private List<PaymentMethodInfo> getAlternativeMethods(String customerId) {
        var methods = componentClient
            .forView()
            .method(CustomerPaymentMethodsView::getByCustomer)
            .invoke(customerId);

        return methods.methods().stream()
            .map(method -> new PaymentMethodInfo(
                method.paymentMethodId(),
                method.brand().name(),
                method.last4Digits(),
                method.isDefault()
            ))
            .toList();
    }

    /**
     * Create default failure analysis when agent fails.
     */
    private FailureAnalysis createDefaultAnalysis() {
        return new FailureAnalysis(
            "GATEWAY_ERROR",
            "TEMPORARY",
            List.of(
                new RecoveryAction(
                    "RETRY",
                    "Try your payment again in a few minutes",
                    1
                ),
                new RecoveryAction(
                    "CONTACT_BANK",
                    "If the issue persists, contact your bank to verify your card is active",
                    2
                )
            ),
            "We're sorry, but your payment couldn't be processed. " +
            "This is usually temporary. Please try again in a few minutes."
        );
    }
}
