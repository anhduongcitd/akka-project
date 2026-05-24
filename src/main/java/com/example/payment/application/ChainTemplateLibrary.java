package com.example.payment.application;

import com.example.payment.agents.domain.AgentChainConfig;

import java.util.List;
import java.util.Map;

/**
 * Library of pre-built agent chain templates.
 */
public class ChainTemplateLibrary {

    /**
     * Customer Support Chain - Sequential triage and resolution.
     */
    public static AgentChainConfig customerSupportChain() {
        return new AgentChainConfig(
            "chain-customer-support-v1",
            "Customer Support Chain",
            "Sequential chain for customer inquiries: triage → support → escalation if needed",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "triage",
                    "general-qa",
                    "Customer query: {{query}}. Determine if this requires specialized support.",
                    Map.of(),
                    "triage_result"
                ),
                new AgentChainConfig.ChainStep(
                    "support",
                    "customer-support",
                    "Customer query: {{query}}\nTriage assessment: {{triage_result}}\nProvide detailed support.",
                    Map.of(),
                    "support_response",
                    new AgentChainConfig.Condition(
                        "triage_result",
                        AgentChainConfig.Condition.ConditionOperator.CONTAINS,
                        "support"
                    )
                ),
                new AgentChainConfig.ChainStep(
                    "escalation",
                    "summarizer",
                    "Summarize escalation: Query={{query}}, Triage={{triage_result}}, Support={{support_response}}",
                    Map.of(),
                    "final_response",
                    new AgentChainConfig.Condition(
                        "support_response",
                        AgentChainConfig.Condition.ConditionOperator.CONTAINS,
                        "escalate"
                    )
                )
            ),
            Map.of("domain", "customer-support", "priority", "normal"),
            true // Continue on error
        );
    }

    /**
     * Fraud Detection Chain - Parallel analysis for comprehensive fraud detection.
     */
    public static AgentChainConfig fraudDetectionChain() {
        return new AgentChainConfig(
            "chain-fraud-detection-v1",
            "Fraud Detection Chain",
            "Parallel chain for fraud analysis: velocity + pattern + risk scoring",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "velocity-check",
                    "fraud-analyst",
                    "Analyze transaction velocity for customer {{customerId}}: {{transactionDetails}}",
                    Map.of(),
                    "velocity_result"
                ),
                new AgentChainConfig.ChainStep(
                    "pattern-analysis",
                    "fraud-analyst",
                    "Analyze spending patterns for customer {{customerId}}: {{transactionDetails}}",
                    Map.of(),
                    "pattern_result"
                ),
                new AgentChainConfig.ChainStep(
                    "risk-scoring",
                    "fraud-analyst",
                    "Calculate risk score for transaction {{transactionId}}: {{transactionDetails}}",
                    Map.of(),
                    "risk_score"
                )
            ),
            Map.of("domain", "fraud-detection", "threshold", "0.7"),
            false // Stop on error
        );
    }

    /**
     * Payment Analysis Chain - Sequential failure analysis and recovery.
     */
    public static AgentChainConfig paymentAnalysisChain() {
        return new AgentChainConfig(
            "chain-payment-analysis-v1",
            "Payment Analysis Chain",
            "Sequential chain for payment issues: transaction review → failure analysis → recovery",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "transaction-review",
                    "transaction-analyzer",
                    "Review transaction {{transactionId}}: {{transactionDetails}}",
                    Map.of(),
                    "review_result"
                ),
                new AgentChainConfig.ChainStep(
                    "failure-analysis",
                    "payment-assistant",
                    "Analyze failure: {{review_result}}\nTransaction: {{transactionDetails}}",
                    Map.of(),
                    "failure_analysis"
                ),
                new AgentChainConfig.ChainStep(
                    "recovery-plan",
                    "payment-assistant",
                    "Create recovery plan based on: {{failure_analysis}}",
                    Map.of(),
                    "recovery_plan"
                )
            ),
            Map.of("domain", "payment-recovery", "autoRetry", "false"),
            true // Continue on error
        );
    }

    /**
     * Content Moderation Chain - Parallel content safety checks.
     */
    public static AgentChainConfig contentModerationChain() {
        return new AgentChainConfig(
            "chain-content-moderation-v1",
            "Content Moderation Chain",
            "Parallel chain for content safety: toxic language + PII + spam detection",
            AgentChainConfig.ExecutionMode.PARALLEL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "toxic-check",
                    "content-moderator",
                    "Check for toxic language: {{content}}",
                    Map.of(),
                    "toxic_result"
                ),
                new AgentChainConfig.ChainStep(
                    "pii-check",
                    "content-moderator",
                    "Check for PII exposure: {{content}}",
                    Map.of(),
                    "pii_result"
                ),
                new AgentChainConfig.ChainStep(
                    "spam-check",
                    "content-moderator",
                    "Check for spam patterns: {{content}}",
                    Map.of(),
                    "spam_result"
                )
            ),
            Map.of("domain", "content-safety", "autoBlock", "true"),
            true // Continue on error to get all results
        );
    }

    /**
     * Multi-Agent Research Chain - Sequential research with planning and synthesis.
     */
    public static AgentChainConfig researchChain() {
        return new AgentChainConfig(
            "chain-research-v1",
            "Multi-Agent Research Chain",
            "Sequential chain for complex queries: planner → data analysis → summarizer",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "planning",
                    "planner",
                    "Create research plan for: {{query}}",
                    Map.of(),
                    "research_plan"
                ),
                new AgentChainConfig.ChainStep(
                    "data-analysis",
                    "data-analyst",
                    "Execute research plan: {{research_plan}}\nQuery: {{query}}",
                    Map.of(),
                    "analysis_results"
                ),
                new AgentChainConfig.ChainStep(
                    "synthesis",
                    "summarizer",
                    "Synthesize findings:\nPlan: {{research_plan}}\nResults: {{analysis_results}}",
                    Map.of(),
                    "final_report"
                )
            ),
            Map.of("domain", "research", "depth", "comprehensive"),
            false // Stop on error
        );
    }

    /**
     * Decision Making Chain - Conditional chain with approval gates.
     */
    public static AgentChainConfig decisionMakingChain() {
        return new AgentChainConfig(
            "chain-decision-making-v1",
            "Decision Making Chain",
            "Conditional chain for decisions: analyze → recommend → execute if approved",
            AgentChainConfig.ExecutionMode.CONDITIONAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "analyze",
                    "data-analyst",
                    "Analyze decision factors: {{decisionContext}}",
                    Map.of(),
                    "analysis"
                ),
                new AgentChainConfig.ChainStep(
                    "recommend",
                    "general-qa",
                    "Recommend action based on: {{analysis}}",
                    Map.of(),
                    "recommendation"
                ),
                new AgentChainConfig.ChainStep(
                    "execute",
                    "customer-support",
                    "Execute recommendation: {{recommendation}}",
                    Map.of(),
                    "execution_result",
                    new AgentChainConfig.Condition(
                        "recommendation",
                        AgentChainConfig.Condition.ConditionOperator.CONTAINS,
                        "approve"
                    )
                )
            ),
            Map.of("domain", "decision-making", "requireApproval", "true"),
            false // Stop on error
        );
    }

    /**
     * Email Response Chain - Sequential email composition with review.
     */
    public static AgentChainConfig emailResponseChain() {
        return new AgentChainConfig(
            "chain-email-response-v1",
            "Email Response Chain",
            "Sequential chain for email replies: draft → review → finalize",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "draft",
                    "email-generator",
                    "Draft email response to: {{customerMessage}}\nContext: {{context}}",
                    Map.of(),
                    "draft_email"
                ),
                new AgentChainConfig.ChainStep(
                    "review",
                    "content-moderator",
                    "Review email for tone and compliance: {{draft_email}}",
                    Map.of(),
                    "review_result"
                ),
                new AgentChainConfig.ChainStep(
                    "finalize",
                    "email-generator",
                    "Finalize email:\nDraft: {{draft_email}}\nReview feedback: {{review_result}}",
                    Map.of(),
                    "final_email"
                )
            ),
            Map.of("domain", "communication", "tone", "professional"),
            true // Continue on error
        );
    }

    /**
     * Transaction Audit Chain - Sequential audit with compliance checks.
     */
    public static AgentChainConfig transactionAuditChain() {
        return new AgentChainConfig(
            "chain-transaction-audit-v1",
            "Transaction Audit Chain",
            "Sequential chain for transaction auditing: review → compliance → report",
            AgentChainConfig.ExecutionMode.SEQUENTIAL,
            List.of(
                new AgentChainConfig.ChainStep(
                    "transaction-review",
                    "transaction-analyzer",
                    "Review transaction {{transactionId}}: {{transactionData}}",
                    Map.of(),
                    "review"
                ),
                new AgentChainConfig.ChainStep(
                    "compliance-check",
                    "fraud-analyst",
                    "Check compliance for: {{review}}",
                    Map.of(),
                    "compliance_result"
                ),
                new AgentChainConfig.ChainStep(
                    "audit-report",
                    "summarizer",
                    "Generate audit report:\nReview: {{review}}\nCompliance: {{compliance_result}}",
                    Map.of(),
                    "audit_report"
                )
            ),
            Map.of("domain", "audit", "regulatory", "pci-dss"),
            false // Stop on error
        );
    }

    /**
     * Get all pre-built chain templates.
     */
    public static List<AgentChainConfig> getAllChainTemplates() {
        return List.of(
            customerSupportChain(),
            fraudDetectionChain(),
            paymentAnalysisChain(),
            contentModerationChain(),
            researchChain(),
            decisionMakingChain(),
            emailResponseChain(),
            transactionAuditChain()
        );
    }
}
