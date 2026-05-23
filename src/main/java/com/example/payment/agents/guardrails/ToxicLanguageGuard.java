package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;

import java.util.Arrays;
import java.util.List;

/**
 * Toxic Language Guard - Filters offensive and harmful content.
 *
 * Blocks content containing:
 * - Profanity
 * - Hate speech
 * - Threats
 * - Harassment
 */
public class ToxicLanguageGuard implements TextGuardrail {

    private static final List<String> TOXIC_PATTERNS = Arrays.asList(
        "i hate",
        "kill all",
        "deserve to die",
        "should die",
        "kill yourself",
        "shut up idiot",
        "you're stupid"
    );

    private static final int SEVERITY_THRESHOLD = 1;

    @Override
    public Result evaluate(String text) {
        String lowerText = text.toLowerCase();
        int toxicityScore = 0;
        StringBuilder violations = new StringBuilder();

        for (String pattern : TOXIC_PATTERNS) {
            if (lowerText.contains(pattern)) {
                toxicityScore++;
                violations.append(pattern).append("; ");
            }
        }

        if (toxicityScore >= SEVERITY_THRESHOLD) {
            return new Result(false,
                "Policy: Toxic content detected. Please maintain respectful communication.");
        }

        return Result.OK;
    }
}
