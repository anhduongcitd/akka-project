package com.example.payment.agents.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

import java.util.regex.Pattern;

/**
 * PII Detection Guardrail - Prevents exposure of sensitive payment data.
 *
 * Detects:
 * - Credit card numbers (various formats)
 * - CVV codes
 * - Full SSN patterns
 * - Email addresses (when not the requesting customer's)
 *
 * Aligned with PCI DSS compliance requirements.
 */
public class PaymentPIIGuard implements TextGuardrail {

    // Credit card pattern: 4 groups of 4 digits with optional separators
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile(
        "\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"
    );

    // CVV pattern: 3-4 digits (context-aware)
    private static final Pattern CVV_PATTERN = Pattern.compile(
        "\\b(cvv|cvc|security code|card code)[:\\s]*\\d{3,4}\\b",
        Pattern.CASE_INSENSITIVE
    );

    // SSN pattern: XXX-XX-XXXX
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // Email pattern (basic)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    private final boolean reportOnly;

    public PaymentPIIGuard(GuardrailContext context) {
        this.reportOnly = context.config().hasPath("report-only")
            ? context.config().getBoolean("report-only")
            : false;
    }

    @Override
    public Result evaluate(String text) {
        // Check for credit card numbers
        if (CARD_NUMBER_PATTERN.matcher(text).find()) {
            return new Result(false, "PII detected: Credit card number");
        }

        // Check for CVV codes
        if (CVV_PATTERN.matcher(text).find()) {
            return new Result(false, "PII detected: CVV/CVC code");
        }

        // Check for SSN
        if (SSN_PATTERN.matcher(text).find()) {
            return new Result(false, "PII detected: Social Security Number");
        }

        // Check for email addresses (should allow customer's own email)
        // This is a simplified check - production would need context awareness
        var emailMatcher = EMAIL_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            String email = emailMatcher.group();
            // Allow common test emails
            if (!email.contains("example.com") && !email.contains("test.com")) {
                return new Result(false, "PII detected: Email address - " + maskEmail(email));
            }
        }

        return Result.OK;
    }

    /**
     * Mask email for logging (show first char + domain).
     */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return "***";
    }
}
