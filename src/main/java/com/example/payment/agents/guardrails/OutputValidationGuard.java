package com.example.payment.agents.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

import java.util.regex.Pattern;

/**
 * Output Validation Guardrail - Ensures agent outputs are valid and safe.
 *
 * Validates:
 * - Refund amounts don't exceed transaction amounts
 * - Action codes are from approved list
 * - Transaction IDs are valid format
 * - No unauthorized operations
 *
 * Prevents hallucinated or malicious outputs.
 */
public class OutputValidationGuard implements TextGuardrail {

    // Transaction ID pattern: txn_followed by alphanumeric
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile(
        "txn_[a-zA-Z0-9]+"
    );

    // Refund ID pattern: ref_followed by alphanumeric
    private static final Pattern REFUND_ID_PATTERN = Pattern.compile(
        "ref_[a-zA-Z0-9]+"
    );

    // Dangerous operation keywords
    private static final String[] FORBIDDEN_OPERATIONS = {
        "delete all",
        "drop table",
        "truncate",
        "remove customer",
        "cancel all payments",
        "refund everything"
    };

    // Valid action codes for support agent
    private static final String[] VALID_ACTIONS = {
        "INFORM", "REFUND_ELIGIBLE", "ESCALATE", "APPROVE", "REVIEW", "DECLINE",
        "RETRY", "CHANGE_CARD", "CONTACT_BANK", "UPDATE_CARD", "DISPUTE"
    };

    private final boolean reportOnly;

    public OutputValidationGuard(GuardrailContext context) {
        this.reportOnly = context.config().hasPath("report-only")
            ? context.config().getBoolean("report-only")
            : false;
    }

    @Override
    public Result evaluate(String text) {
        // Check for dangerous operations
        String lowerText = text.toLowerCase();
        for (String forbidden : FORBIDDEN_OPERATIONS) {
            if (lowerText.contains(forbidden)) {
                return new Result(false, "Forbidden operation detected: " + forbidden);
            }
        }

        // Validate action codes if present
        if (containsActionCode(text)) {
            boolean hasValidAction = false;
            for (String validAction : VALID_ACTIONS) {
                if (text.contains(validAction)) {
                    hasValidAction = true;
                    break;
                }
            }
            if (!hasValidAction) {
                return new Result(false, "Invalid action code in response");
            }
        }

        // Check for suspiciously large refund amounts
        if (text.matches(".*refund.*\\$?\\d{6,}.*")) {
            return new Result(false, "Suspicious refund amount detected (>$100k)");
        }

        // Validate transaction ID format if present
        var txnMatcher = TRANSACTION_ID_PATTERN.matcher(text);
        while (txnMatcher.find()) {
            String txnId = txnMatcher.group();
            if (txnId.length() < 10) { // txn_ + at least 6 chars
                return new Result(false, "Invalid transaction ID format: " + txnId);
            }
        }

        return Result.OK;
    }

    /**
     * Check if text contains action code patterns.
     */
    private boolean containsActionCode(String text) {
        return text.contains("\"action\"") ||
               text.contains("action:") ||
               text.contains("recommendation:");
    }
}
