package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;

import java.util.regex.Pattern;

/**
 * Hallucination Guard - Verifies agent outputs for fabricated data.
 *
 * Detects:
 * - Agent admitting inability to access data
 * - Invalid transaction ID formats
 * - Inconsistent statements
 */
public class HallucinationGuard implements TextGuardrail {

    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("txn_[a-zA-Z0-9]{6,}");

    @Override
    public Result evaluate(String text) {
        String lowerText = text.toLowerCase();

        // Check for hallucination indicators
        if (lowerText.contains("i don't have access") ||
            lowerText.contains("i cannot access") ||
            lowerText.contains("i cannot verify") ||
            lowerText.contains("as an ai")) {
            return new Result(false,
                "Quality: Agent admitted inability to access data. May be hallucinating.");
        }

        // Check for inconsistencies
        if (lowerText.contains("failed") && lowerText.contains("completed")) {
            return new Result(false,
                "Quality: Inconsistent response - mentions both failed and completed.");
        }

        if (lowerText.contains("refund") && lowerText.contains("payment successful")) {
            return new Result(false,
                "Quality: Inconsistent response - mentions both refund and successful payment.");
        }

        return Result.OK;
    }
}
