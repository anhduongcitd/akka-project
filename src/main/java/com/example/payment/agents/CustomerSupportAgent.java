package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.PaymentHistoryView;
import com.example.payment.application.PaymentTransactionEntity;
import com.example.payment.domain.PaymentTransaction;

import java.time.Instant;
import java.util.List;

/**
 * Customer Support Agent - Conversational AI for payment inquiries.
 *
 * Capabilities:
 * - Answer payment status questions
 * - Provide refund eligibility information
 * - Trace transaction history
 * - Escalate to human support when needed
 *
 * Tools:
 * - PaymentHistoryView (query customer transactions)
 * - PaymentTransactionEntity (get transaction details)
 * - Function tools for refund eligibility checks
 */
@Component(id = "customer-support")
public class CustomerSupportAgent extends Agent {

    private final ComponentClient componentClient;

    public CustomerSupportAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record SupportRequest(
        String customerId,
        String query
    ) {}

    public record SupportResponse(
        String answer,              // Human-readable answer to customer query
        String action,              // Action type: INFORM, REFUND_ELIGIBLE, ESCALATE
        String confidence,          // Confidence level: HIGH, MEDIUM, LOW
        String transactionId,       // Transaction ID if discussing specific transaction
        RefundRecommendation refund // Refund recommendation if action is REFUND_ELIGIBLE
    ) {}

    public record RefundRecommendation(
        String amount,              // Refund amount in decimal format
        String reason,              // Reason for refund
        boolean autoApprove         // Whether refund can be auto-approved
    ) {}

    // Internal tool records
    public record PaymentSummary(
        String transactionId,
        String amount,
        String currency,
        String status,
        String createdAt,
        String merchantReference
    ) {}

    public record RefundEligibility(
        boolean eligible,
        String reason,
        String maxRefundAmount,
        boolean hasExistingRefunds
    ) {}

    /**
     * Main agent command handler - processes customer support queries.
     */
    public Effect<SupportResponse> handleQuery(SupportRequest request) {
        String systemPrompt = """
            You are a helpful payment support agent for an online payment service.

            Your role:
            - Answer customer questions about their payments, refunds, and transaction status
            - Check refund eligibility and provide recommendations
            - Trace transaction history to help customers understand their payments
            - Escalate to human support when you cannot help or lack confidence

            Available tools:
            - getPaymentHistory: Get customer's recent transactions
            - getTransactionDetails: Get specific transaction information
            - checkRefundEligibility: Check if a transaction can be refunded

            Response guidelines:
            - Be friendly, professional, and empathetic
            - Provide specific transaction IDs and amounts when available
            - If you cannot find information, suggest escalation
            - For refund requests, always check eligibility first
            - Use INFORM action for informational responses
            - Use REFUND_ELIGIBLE action when customer can get a refund
            - Use ESCALATE action when human intervention is needed

            Confidence levels:
            - HIGH: You found exact information and can answer definitively
            - MEDIUM: You found partial information or are making reasonable inference
            - LOW: You're uncertain or recommending escalation
            """;

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(request.query())
            .responseConformsTo(SupportResponse.class)
            .onFailure(ex -> new SupportResponse(
                "I apologize, but I'm having trouble processing your request right now. " +
                "Please contact our support team for assistance.",
                "ESCALATE",
                "LOW",
                null,
                null
            ))
            .thenReply();
    }

    /**
     * Function tool: Get customer's payment history.
     */
    @FunctionTool(description = "Get customer's recent payment transactions to answer questions about their payment history")
    private List<PaymentSummary> getPaymentHistory(String customerId) {
        var history = componentClient
            .forView()
            .method(PaymentHistoryView::getByCustomer)
            .invoke(customerId);

        return history.transactions().stream()
            .map(entry -> new PaymentSummary(
                entry.transactionId(),
                entry.amountValue(),
                entry.currency(),
                entry.status(),
                entry.createdAt().toString(),
                entry.merchantReference()
            ))
            .toList();
    }

    /**
     * Function tool: Get specific transaction details.
     */
    @FunctionTool(description = "Get detailed information about a specific transaction by transaction ID")
    private PaymentSummary getTransactionDetails(String transactionId) {
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }

        return new PaymentSummary(
            transactionId,
            transaction.amount().amount().toString(),
            transaction.amount().currency().name(),
            transaction.status().name(),
            transaction.createdAt().toString(),
            transaction.merchantReference()
        );
    }

    /**
     * Function tool: Check if a transaction is eligible for refund.
     */
    @FunctionTool(description = "Check if a transaction is eligible for refund and get refund details")
    private RefundEligibility checkRefundEligibility(String transactionId) {
        PaymentTransaction transaction = componentClient
            .forEventSourcedEntity(transactionId)
            .method(PaymentTransactionEntity::getPayment)
            .invoke();

        if (transaction == null) {
            return new RefundEligibility(
                false,
                "Transaction not found",
                "0.00",
                false
            );
        }

        // Check if payment succeeded
        if (!transaction.status().name().equals("SUCCEEDED")) {
            return new RefundEligibility(
                false,
                "Only successful payments can be refunded. Current status: " + transaction.status(),
                "0.00",
                false
            );
        }

        // Calculate remaining refundable amount
        var totalRefunded = transaction.refunds().stream()
            .map(refund -> refund.amount().amount())
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        var remainingAmount = transaction.amount().amount().subtract(totalRefunded);

        boolean eligible = remainingAmount.compareTo(java.math.BigDecimal.ZERO) > 0;
        boolean hasExistingRefunds = !transaction.refunds().isEmpty();

        String reason;
        if (!eligible) {
            reason = "Transaction has been fully refunded already";
        } else if (hasExistingRefunds) {
            reason = "Partial refund available. Already refunded: $" + totalRefunded;
        } else {
            reason = "Full refund available";
        }

        return new RefundEligibility(
            eligible,
            reason,
            remainingAmount.toString(),
            hasExistingRefunds
        );
    }
}
