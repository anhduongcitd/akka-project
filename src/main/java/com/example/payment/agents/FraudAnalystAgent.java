package com.example.payment.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;
import com.example.payment.application.AuditLogEntity;
import com.example.payment.application.PaymentHistoryView;
import com.example.payment.domain.AuditEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fraud Analyst Agent - ML-enhanced fraud detection with confidence scoring.
 *
 * Capabilities:
 * - Analyze transaction patterns for fraud indicators
 * - Provide risk scores with detailed reasoning
 * - Learn from customer spending behavior
 * - Flag suspicious patterns beyond rule-based checks
 *
 * Tools:
 * - PaymentHistoryView (analyze spending patterns)
 * - AuditLogEntity (check fraud alert history)
 * - Function tools for pattern analysis
 */
@Component(id = "fraud-analyst")
public class FraudAnalystAgent extends Agent {

    private final ComponentClient componentClient;

    public FraudAnalystAgent(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record FraudCheckRequest(
        String customerId,
        String transactionId,
        String amount,
        String currency,
        String merchantReference
    ) {}

    public record FraudAnalysis(
        boolean isSuspicious,       // Whether the transaction appears suspicious
        String riskLevel,           // Risk level: LOW, MEDIUM, HIGH, CRITICAL
        double confidenceScore,     // Confidence score from 0.0 to 1.0
        List<String> riskFactors,   // List of specific risk factors detected
        String recommendation,      // Recommendation: APPROVE, REVIEW, DECLINE
        String reasoning            // Detailed reasoning for the decision
    ) {}

    // Internal tool records
    public record SpendingPattern(
        String customerId,
        double averageTransactionAmount,
        int transactionCount,
        String mostCommonCurrency,
        boolean hasHistoricalPatterns
    ) {}

    public record FraudAlert(
        String alertType,
        String transactionId,
        String timestamp,
        String details
    ) {}

    /**
     * Main agent command handler - analyzes transactions for fraud.
     */
    public Effect<FraudAnalysis> analyzeTransaction(FraudCheckRequest request) {
        String systemPrompt = """
            You are an expert fraud detection analyst for a payment processing system.

            Your role:
            - Analyze transactions for fraud indicators
            - Consider customer spending patterns and history
            - Provide detailed risk assessment with confidence scores
            - Balance fraud prevention with customer experience

            Available tools:
            - getSpendingPattern: Analyze customer's historical spending behavior
            - getFraudHistory: Check if customer has previous fraud alerts
            - analyzeTransactionAmount: Compare transaction to customer norms

            Fraud indicators to consider:
            1. Unusual transaction amounts (much higher/lower than average)
            2. Suspicious merchant references or patterns
            3. Multiple transactions in short time (velocity)
            4. First transaction from new customer (high risk but may be legitimate)
            5. Previous fraud alerts for this customer
            6. Unusual currency usage

            Risk levels:
            - LOW: Normal transaction, consistent with patterns
            - MEDIUM: Some unusual factors, but could be legitimate
            - HIGH: Multiple red flags, likely fraud
            - CRITICAL: Clear fraud indicators, decline immediately

            Recommendations:
            - APPROVE: Low risk, process normally
            - REVIEW: Medium/high risk, require manual review
            - DECLINE: Critical risk, block immediately

            Important: Provide specific, actionable reasoning. Explain WHY you flagged it.
            """;

        String userMessage = String.format(
            "Analyze this transaction for fraud:\n" +
            "Customer: %s\n" +
            "Transaction: %s\n" +
            "Amount: %s %s\n" +
            "Merchant: %s",
            request.customerId(),
            request.transactionId(),
            request.amount(),
            request.currency(),
            request.merchantReference()
        );

        return effects()
            .systemMessage(systemPrompt)
            .userMessage(userMessage)
            .responseConformsTo(FraudAnalysis.class)
            .onFailure(ex -> new FraudAnalysis(
                false,
                "LOW",
                0.0,
                List.of("Analysis failed - defaulting to rule-based check"),
                "APPROVE",
                "Agent analysis failed. Falling back to rule-based fraud detection."
            ))
            .thenReply();
    }

    /**
     * Function tool: Get customer's spending pattern analysis.
     */
    @FunctionTool(description = "Analyze customer's historical spending pattern including average amount and frequency")
    private SpendingPattern getSpendingPattern(String customerId) {
        var history = componentClient
            .forView()
            .method(PaymentHistoryView::getByCustomer)
            .invoke(customerId);

        if (history.transactions().isEmpty()) {
            return new SpendingPattern(
                customerId,
                0.0,
                0,
                "USD",
                false
            );
        }

        // Calculate average transaction amount
        double totalAmount = 0.0;
        int successfulCount = 0;
        java.util.Map<String, Integer> currencyCount = new java.util.HashMap<>();

        for (var txn : history.transactions()) {
            if ("SUCCEEDED".equals(txn.status())) {
                totalAmount += Double.parseDouble(txn.amountValue());
                successfulCount++;
                currencyCount.put(txn.currency(), currencyCount.getOrDefault(txn.currency(), 0) + 1);
            }
        }

        double averageAmount = successfulCount > 0 ? totalAmount / successfulCount : 0.0;

        // Find most common currency
        String mostCommonCurrency = currencyCount.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("USD");

        return new SpendingPattern(
            customerId,
            averageAmount,
            successfulCount,
            mostCommonCurrency,
            successfulCount > 0
        );
    }

    /**
     * Function tool: Get customer's fraud alert history.
     */
    @FunctionTool(description = "Get past fraud alerts for a customer to identify repeat offenders")
    private List<FraudAlert> getFraudHistory(String customerId) {
        // Query audit log for fraud alerts
        var eventList = componentClient
            .forEventSourcedEntity(customerId)
            .method(AuditLogEntity::getEventsByType)
            .invoke("FRAUD_ALERT");

        return eventList.events().stream()
            .filter(event -> event instanceof AuditEvent.FraudAlertTriggered)
            .map(event -> {
                var fraudEvent = (AuditEvent.FraudAlertTriggered) event;
                return new FraudAlert(
                    fraudEvent.fraudType(),
                    fraudEvent.transactionId(),
                    fraudEvent.timestamp().toString(),
                    fraudEvent.details()
                );
            })
            .toList();
    }

    /**
     * Function tool: Analyze if transaction amount is unusual for customer.
     */
    @FunctionTool(description = "Compare transaction amount against customer's normal spending to detect anomalies")
    private String analyzeTransactionAmount(String customerId, String amountStr) {
        SpendingPattern pattern = getSpendingPattern(customerId);

        if (!pattern.hasHistoricalPatterns()) {
            return "First transaction for customer - no baseline to compare. Higher risk.";
        }

        double amount = Double.parseDouble(amountStr);
        double average = pattern.averageTransactionAmount();

        if (amount > average * 5) {
            return String.format(
                "ALERT: Transaction amount ($%.2f) is 5x higher than customer average ($%.2f). Possible fraud.",
                amount, average
            );
        } else if (amount > average * 2) {
            return String.format(
                "WARNING: Transaction amount ($%.2f) is 2x higher than customer average ($%.2f). Review recommended.",
                amount, average
            );
        } else if (amount < average * 0.1 && amount > 0) {
            return String.format(
                "NOTE: Transaction amount ($%.2f) is much lower than average ($%.2f). Could be card testing.",
                amount, average
            );
        } else {
            return String.format(
                "Normal: Transaction amount ($%.2f) is consistent with customer average ($%.2f).",
                amount, average
            );
        }
    }
}
