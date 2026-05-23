package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;

import java.util.regex.Pattern;

/**
 * Enhanced PII Guard - Extended PII detection.
 *
 * Detects:
 * - Credit card numbers
 * - Bank account numbers
 * - SSN
 * - Phone numbers
 * - Email addresses
 */
public class EnhancedPIIGuard implements TextGuardrail {

    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"
    );

    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}[-\\s]\\d{2}[-\\s]\\d{4}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b\\d{3}[-\\s]\\d{3}[-\\s]\\d{4}\\b"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    @Override
    public Result evaluate(String text) {
        if (CREDIT_CARD_PATTERN.matcher(text).find()) {
            return new Result(false, "PII: Credit card number detected.");
        }

        if (SSN_PATTERN.matcher(text).find()) {
            return new Result(false, "PII: Social Security Number detected.");
        }

        if (PHONE_PATTERN.matcher(text).find() && 
            !text.contains("555-0100") && !text.contains("123-456-7890")) {
            return new Result(false, "PII: Phone number detected.");
        }

        if (EMAIL_PATTERN.matcher(text).find()) {
            String email = extractEmail(text);
            if (email != null && !isTestEmail(email)) {
                return new Result(false, "PII: Email address detected.");
            }
        }

        return Result.OK;
    }

    private String extractEmail(String text) {
        var matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean isTestEmail(String email) {
        return email.endsWith("@example.com") ||
               email.endsWith("@test.com");
    }
}
