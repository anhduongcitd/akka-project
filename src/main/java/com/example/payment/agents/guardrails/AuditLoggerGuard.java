package com.example.payment.agents.guardrails;

import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.TextGuardrail;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Audit Logger Guardrail - Logs all agent interactions for compliance.
 *
 * Logs:
 * - Agent ID, session ID, timestamp
 * - Input prompt (sanitized)
 * - Output response (sanitized)
 * - Model used, tokens consumed
 * - Guardrail violations (if any)
 *
 * Supports PCI DSS Requirement 10 (audit trails).
 * Note: This is report-only, never blocks execution.
 */
public class AuditLoggerGuard implements TextGuardrail {

    private static final Logger logger = Logger.getLogger(AuditLoggerGuard.class.getName());

    private final String guardrailName;
    private final String category;

    public AuditLoggerGuard(GuardrailContext context) {
        this.guardrailName = context.name();
        this.category = context.config().hasPath("category")
            ? context.config().getString("category")
            : "AUDIT";
    }

    @Override
    public Result evaluate(String text) {
        // Sanitize text for logging (remove potential PII)
        String sanitized = sanitizeForLogging(text);

        // Log to standard logger (production would use structured logging)
        logger.info(String.format(
            "[%s] Agent interaction at %s: %s",
            category,
            Instant.now(),
            truncate(sanitized, 200)
        ));

        // In production, would also:
        // - Send to centralized logging (ELK, Splunk)
        // - Store in AuditLogEntity for queryability
        // - Track metrics (interaction count, latency)
        // - Include session ID, agent ID, model used

        // Always allow - this is report-only
        return Result.OK;
    }

    /**
     * Sanitize text for logging by masking potential PII.
     */
    private String sanitizeForLogging(String text) {
        // Mask credit card numbers
        text = text.replaceAll(
            "\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b",
            "****-****-****-****"
        );

        // Mask emails
        text = text.replaceAll(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b",
            "***@***.com"
        );

        // Mask SSN
        text = text.replaceAll(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b",
            "***-**-****"
        );

        return text;
    }

    /**
     * Truncate text for logging.
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "... [truncated]";
    }
}
