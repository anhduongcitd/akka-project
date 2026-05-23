package com.example.payment.agents.domain;

/**
 * Test Case - Definition for evaluating an agent.
 *
 * Contains:
 * - Input query
 * - Expected behavior description
 * - Success criteria
 * - Optional context
 */
public record TestCase(
    String testCaseId,
    String name,
    String description,
    String query,
    String expectedBehavior,
    String successCriteria,
    String context
) {
    public TestCase {
        if (testCaseId == null || testCaseId.isBlank()) {
            throw new IllegalArgumentException("Test case ID cannot be null or blank");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }
        if (expectedBehavior == null || expectedBehavior.isBlank()) {
            throw new IllegalArgumentException("Expected behavior cannot be null or blank");
        }
    }

    /**
     * Create a payment status test case.
     */
    public static TestCase paymentStatusCheck(String testId) {
        return new TestCase(
            testId,
            "Payment Status Check",
            "User asks about payment status",
            "Where is my payment for transaction txn_abc123?",
            "Agent should check transaction status and provide clear status update",
            "Response includes transaction ID, status, and expected timeline",
            "txn_abc123"
        );
    }

    /**
     * Create a refund eligibility test case.
     */
    public static TestCase refundEligibility(String testId) {
        return new TestCase(
            testId,
            "Refund Eligibility Check",
            "User asks if refund is possible",
            "Can I get a refund for my purchase?",
            "Agent should check refund policy and provide clear yes/no answer with explanation",
            "Response includes eligibility status, reason, and next steps if eligible",
            null
        );
    }

    /**
     * Create a payment failure test case.
     */
    public static TestCase paymentFailureAnalysis(String testId) {
        return new TestCase(
            testId,
            "Payment Failure Analysis",
            "User asks why payment failed",
            "Why did my payment fail?",
            "Agent should analyze failure reason and suggest recovery actions",
            "Response includes failure cause, suggested fixes, and clear action items",
            "txn_failed123"
        );
    }

    /**
     * Create a fraud concern test case.
     */
    public static TestCase fraudConcern(String testId) {
        return new TestCase(
            testId,
            "Fraud Concern",
            "User reports suspicious transaction",
            "I see a charge I didn't make for $500",
            "Agent should take fraud seriously, verify transaction, and provide security guidance",
            "Response shows empathy, confirms transaction details, explains dispute process",
            "txn_suspicious789"
        );
    }
}
