package com.example.payment.agents.guardrails;

import akka.javasdk.agent.TextGuardrail;

import java.util.Arrays;
import java.util.List;

/**
 * Similarity Guard - Detects jailbreak attempts and prompt injections.
 *
 * Blocks malicious prompts that try to:
 * - Override system instructions
 * - Roleplay as different entities  
 * - Manipulate context
 */
public class SimilarityGuard implements TextGuardrail {

    private static final List<String> JAILBREAK_PATTERNS = Arrays.asList(
        "ignore previous instructions",
        "ignore all previous instructions",
        "disregard previous instructions",
        "pretend you are",
        "act as if you are",
        "from now on you are",
        "do anything now",
        "show me your prompt",
        "developer mode"
    );

    @Override
    public Result evaluate(String text) {
        String lowerText = text.toLowerCase();

        for (String pattern : JAILBREAK_PATTERNS) {
            if (lowerText.contains(pattern)) {
                return new Result(false, 
                    "Security: Potential jailbreak attempt detected. Request blocked.");
            }
        }

        if (text.length() > 5000) {
            return new Result(false, 
                "Security: Request exceeds maximum allowed length. Possible prompt stuffing.");
        }

        return Result.OK;
    }
}
