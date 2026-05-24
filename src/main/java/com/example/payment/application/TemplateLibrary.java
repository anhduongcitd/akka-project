package com.example.payment.application;

import com.example.payment.agents.domain.AgentConfig;

import java.util.List;
import java.util.Map;

/**
 * Library of pre-built agent templates for the marketplace.
 */
public class TemplateLibrary {

    /**
     * Customer Support Agent Template
     */
    public static TemplateDefinition customerSupport() {
        var config = new AgentConfig(
            """
            You are a professional customer support agent for an online payment service.
            Your role is to help customers with:
            - Payment status inquiries
            - Transaction history questions
            - Refund eligibility and processing
            - Payment method management
            - General payment assistance

            Always be polite, professional, and empathetic. If you cannot help with a request,
            escalate to human support. Never make assumptions about transaction details - always
            verify using the available tools.
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentHistoryView", Map.of("maxResults", "10")),
                new AgentConfig.ToolConfig("PaymentTransactionEntity", Map.of()),
                new AgentConfig.ToolConfig("RefundToolkit", Map.of())
            ),
            Map.of(
                "maxConversationTurns", "10",
                "escalationKeywords", "complaint,urgent,manager"
            ),
            new AgentConfig.GuardrailConfig(List.of("pii-guard", "output-validation")),
            AgentConfig.ModelConfig.DEFAULT
        );

        return new TemplateDefinition(
            "template-customer-support-v1",
            "Customer Support Agent",
            "24/7 AI assistant for payment inquiries, refunds, and account management",
            "customer-support",
            config,
            List.of("support", "payments", "refunds", "customer-service"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Fraud Detection Agent Template
     */
    public static TemplateDefinition fraudDetection() {
        var config = new AgentConfig(
            """
            You are an expert fraud detection analyst for payment transactions.
            Your role is to:
            - Analyze transaction patterns for suspicious activity
            - Identify velocity attacks and unusual spending patterns
            - Detect duplicate or potentially fraudulent transactions
            - Provide risk scores with clear reasoning

            Always explain your analysis with specific risk factors. Be thorough but avoid
            false positives. Consider customer history and transaction context.
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentHistoryView", Map.of()),
                new AgentConfig.ToolConfig("FraudCheckEntity", Map.of()),
                new AgentConfig.ToolConfig("AuditLogEntity", Map.of())
            ),
            Map.of(
                "riskThresholds", "low:0.3,medium:0.6,high:0.8",
                "velocityWindow", "10"
            ),
            new AgentConfig.GuardrailConfig(List.of("pii-guard", "output-validation")),
            new AgentConfig.ModelConfig("openai", "gpt-4o", 0.3) // Lower temperature for consistency
        );

        return new TemplateDefinition(
            "template-fraud-detection-v1",
            "Fraud Detection Analyst",
            "ML-enhanced fraud detection with confidence scoring and risk analysis",
            "fraud-detection",
            config,
            List.of("fraud", "security", "risk-analysis", "detection"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Payment Assistant Agent Template
     */
    public static TemplateDefinition paymentAssistant() {
        var config = new AgentConfig(
            """
            You are a payment failure resolution specialist.
            Your role is to:
            - Analyze payment failures and determine root causes
            - Suggest recovery actions (retry, change card, contact bank)
            - Provide user-friendly explanations of technical errors
            - Recommend alternative payment methods when appropriate

            Focus on actionable solutions. Classify failures as transient, recoverable, or terminal.
            Always provide clear next steps for customers.
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentTransactionEntity", Map.of()),
                new AgentConfig.ToolConfig("PaymentMethodEntity", Map.of()),
                new AgentConfig.ToolConfig("CustomerPaymentMethodsView", Map.of())
            ),
            Map.of(
                "retryableErrors", "insufficient_funds,card_declined,network_error",
                "terminalErrors", "card_expired,invalid_card,fraud_detected"
            ),
            new AgentConfig.GuardrailConfig(List.of("pii-guard", "output-validation")),
            AgentConfig.ModelConfig.DEFAULT
        );

        return new TemplateDefinition(
            "template-payment-assistant-v1",
            "Payment Failure Assistant",
            "Intelligent payment failure analysis and recovery recommendations",
            "payment-support",
            config,
            List.of("payments", "failures", "troubleshooting", "recovery"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * General Q&A Agent Template
     */
    public static TemplateDefinition generalQA() {
        var config = new AgentConfig(
            """
            You are a knowledgeable assistant for general payment service questions.
            Your role is to:
            - Answer questions about payment features and capabilities
            - Explain payment processes and workflows
            - Provide guidance on best practices
            - Direct users to appropriate resources or specialists

            You have broad knowledge but limited tools. For specific account or transaction
            questions, recommend contacting specialized agents or support.
            """,
            List.of(),
            Map.of(
                "knowledgeBase", "payments,refunds,security,compliance",
                "responseStyle", "friendly,informative"
            ),
            new AgentConfig.GuardrailConfig(List.of("output-validation")),
            AgentConfig.ModelConfig.DEFAULT
        );

        return new TemplateDefinition(
            "template-general-qa-v1",
            "General Q&A Assistant",
            "Versatile assistant for payment service information and guidance",
            "general",
            config,
            List.of("qa", "help", "information", "guidance"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Data Analysis Agent Template
     */
    public static TemplateDefinition dataAnalysis() {
        var config = new AgentConfig(
            """
            You are a data analysis specialist for payment analytics.
            Your role is to:
            - Analyze payment trends and patterns
            - Generate insights from transaction data
            - Identify anomalies and outliers
            - Provide statistical summaries and visualizations

            Always cite data sources and show your reasoning. Distinguish between correlation
            and causation. Acknowledge limitations in the data.
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentHistoryView", Map.of()),
                new AgentConfig.ToolConfig("AgentAnalyticsView", Map.of())
            ),
            Map.of(
                "analysisTypes", "trend,distribution,anomaly,correlation",
                "timeRanges", "day,week,month,quarter"
            ),
            new AgentConfig.GuardrailConfig(List.of("output-validation")),
            new AgentConfig.ModelConfig("openai", "gpt-4o", 0.2) // Lower temperature for analysis
        );

        return new TemplateDefinition(
            "template-data-analysis-v1",
            "Data Analysis Specialist",
            "Advanced analytics and insights for payment transaction data",
            "analytics",
            config,
            List.of("analytics", "data", "insights", "reporting"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Email Generator Agent Template
     */
    public static TemplateDefinition emailGenerator() {
        var config = new AgentConfig(
            """
            You are a professional email composition assistant for payment communications.
            Your role is to:
            - Generate customer-facing emails (confirmations, notifications, updates)
            - Draft support responses with appropriate tone and clarity
            - Create refund acknowledgments and payment receipts
            - Compose escalation emails when needed

            Always maintain professional tone. Include relevant transaction details. Make emails
            clear, concise, and actionable. Adapt tone based on situation (success, failure, refund).
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentTransactionEntity", Map.of())
            ),
            Map.of(
                "emailTypes", "confirmation,notification,receipt,support,escalation",
                "toneOptions", "professional,friendly,apologetic,urgent"
            ),
            new AgentConfig.GuardrailConfig(List.of("pii-guard", "output-validation", "toxic-language")),
            AgentConfig.ModelConfig.DEFAULT
        );

        return new TemplateDefinition(
            "template-email-generator-v1",
            "Email Composition Assistant",
            "AI-powered email generation for payment communications",
            "communication",
            config,
            List.of("email", "communication", "customer-service", "notifications"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Content Moderator Agent Template
     */
    public static TemplateDefinition contentModerator() {
        var config = new AgentConfig(
            """
            You are a content moderation specialist for user-generated payment-related content.
            Your role is to:
            - Review customer messages and feedback for policy violations
            - Detect toxic language, spam, or abuse
            - Flag potential security concerns or social engineering attempts
            - Provide moderation recommendations with severity levels

            Be objective and fair. Consider context. Distinguish between genuine complaints and
            abuse. Provide clear reasoning for all moderation decisions.
            """,
            List.of(),
            Map.of(
                "severityLevels", "low,medium,high,critical",
                "categories", "toxic,spam,security,fraud,pii"
            ),
            new AgentConfig.GuardrailConfig(List.of("toxic-language", "similarity-guard")),
            new AgentConfig.ModelConfig("openai", "gpt-4o", 0.1) // Very low temperature for consistency
        );

        return new TemplateDefinition(
            "template-content-moderator-v1",
            "Content Moderation Specialist",
            "Automated content review and policy enforcement for user communications",
            "moderation",
            config,
            List.of("moderation", "safety", "compliance", "security"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Transaction Analyzer Agent Template
     */
    public static TemplateDefinition transactionAnalyzer() {
        var config = new AgentConfig(
            """
            You are a transaction analysis expert for payment reconciliation and auditing.
            Your role is to:
            - Analyze transaction details for completeness and accuracy
            - Identify discrepancies and reconciliation issues
            - Trace payment flows across systems
            - Generate audit reports and compliance summaries

            Be thorough and precise. Cross-reference data sources. Flag any inconsistencies.
            Provide actionable recommendations for resolving issues.
            """,
            List.of(
                new AgentConfig.ToolConfig("PaymentTransactionEntity", Map.of()),
                new AgentConfig.ToolConfig("PaymentHistoryView", Map.of()),
                new AgentConfig.ToolConfig("AuditLogEntity", Map.of())
            ),
            Map.of(
                "analysisDepth", "basic,detailed,comprehensive",
                "reconciliationRules", "amount,status,timestamp,method"
            ),
            new AgentConfig.GuardrailConfig(List.of("pii-guard", "output-validation")),
            new AgentConfig.ModelConfig("openai", "gpt-4o", 0.2) // Lower temperature for accuracy
        );

        return new TemplateDefinition(
            "template-transaction-analyzer-v1",
            "Transaction Analysis Expert",
            "Comprehensive transaction analysis for reconciliation and auditing",
            "analytics",
            config,
            List.of("transactions", "audit", "reconciliation", "analysis"),
            "Akka Team",
            "1.0.0"
        );
    }

    /**
     * Get all pre-built templates
     */
    public static List<TemplateDefinition> getAllTemplates() {
        return List.of(
            customerSupport(),
            fraudDetection(),
            paymentAssistant(),
            generalQA(),
            dataAnalysis(),
            emailGenerator(),
            contentModerator(),
            transactionAnalyzer()
        );
    }

    /**
     * Template definition record for easy creation
     */
    public record TemplateDefinition(
        String templateId,
        String name,
        String description,
        String category,
        AgentConfig config,
        List<String> tags,
        String author,
        String version
    ) {}
}
