package com.example.payment.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateLibrary.
 */
public class TemplateLibraryTest {

    @Test
    public void shouldHaveEightTemplates() {
        var templates = TemplateLibrary.getAllTemplates();
        assertThat(templates).hasSize(8);
    }

    @Test
    public void shouldHaveCustomerSupportTemplate() {
        var template = TemplateLibrary.customerSupport();

        assertThat(template.templateId()).isEqualTo("template-customer-support-v1");
        assertThat(template.name()).isEqualTo("Customer Support Agent");
        assertThat(template.category()).isEqualTo("customer-support");
        assertThat(template.config().systemPrompt()).contains("customer support agent");
        assertThat(template.config().tools()).hasSize(3);
        assertThat(template.config().guardrails().enabledGuardrails())
            .contains("pii-guard", "output-validation");
        assertThat(template.tags()).contains("support", "payments", "refunds");
    }

    @Test
    public void shouldHaveFraudDetectionTemplate() {
        var template = TemplateLibrary.fraudDetection();

        assertThat(template.templateId()).isEqualTo("template-fraud-detection-v1");
        assertThat(template.name()).isEqualTo("Fraud Detection Analyst");
        assertThat(template.category()).isEqualTo("fraud-detection");
        assertThat(template.config().systemPrompt()).contains("fraud detection analyst");
        assertThat(template.config().tools()).hasSize(3);
        assertThat(template.config().model().temperature()).isEqualTo(0.3); // Lower temperature
        assertThat(template.tags()).contains("fraud", "security", "risk-analysis");
    }

    @Test
    public void shouldHavePaymentAssistantTemplate() {
        var template = TemplateLibrary.paymentAssistant();

        assertThat(template.templateId()).isEqualTo("template-payment-assistant-v1");
        assertThat(template.name()).isEqualTo("Payment Failure Assistant");
        assertThat(template.category()).isEqualTo("payment-support");
        assertThat(template.config().systemPrompt()).contains("payment failure resolution");
        assertThat(template.config().tools()).hasSize(3);
        assertThat(template.tags()).contains("payments", "failures", "troubleshooting");
    }

    @Test
    public void shouldHaveGeneralQATemplate() {
        var template = TemplateLibrary.generalQA();

        assertThat(template.templateId()).isEqualTo("template-general-qa-v1");
        assertThat(template.name()).isEqualTo("General Q&A Assistant");
        assertThat(template.category()).isEqualTo("general");
        assertThat(template.config().systemPrompt()).contains("general payment service questions");
        assertThat(template.config().tools()).isEmpty(); // No tools for general Q&A
        assertThat(template.tags()).contains("qa", "help", "information");
    }

    @Test
    public void shouldHaveDataAnalysisTemplate() {
        var template = TemplateLibrary.dataAnalysis();

        assertThat(template.templateId()).isEqualTo("template-data-analysis-v1");
        assertThat(template.name()).isEqualTo("Data Analysis Specialist");
        assertThat(template.category()).isEqualTo("analytics");
        assertThat(template.config().systemPrompt()).contains("data analysis specialist");
        assertThat(template.config().tools()).hasSize(2);
        assertThat(template.config().model().temperature()).isEqualTo(0.2); // Lower temperature
        assertThat(template.tags()).contains("analytics", "data", "insights");
    }

    @Test
    public void shouldHaveEmailGeneratorTemplate() {
        var template = TemplateLibrary.emailGenerator();

        assertThat(template.templateId()).isEqualTo("template-email-generator-v1");
        assertThat(template.name()).isEqualTo("Email Composition Assistant");
        assertThat(template.category()).isEqualTo("communication");
        assertThat(template.config().systemPrompt()).contains("email composition assistant");
        assertThat(template.config().guardrails().enabledGuardrails())
            .contains("pii-guard", "output-validation", "toxic-language");
        assertThat(template.tags()).contains("email", "communication", "customer-service");
    }

    @Test
    public void shouldHaveContentModeratorTemplate() {
        var template = TemplateLibrary.contentModerator();

        assertThat(template.templateId()).isEqualTo("template-content-moderator-v1");
        assertThat(template.name()).isEqualTo("Content Moderation Specialist");
        assertThat(template.category()).isEqualTo("moderation");
        assertThat(template.config().systemPrompt()).contains("content moderation specialist");
        assertThat(template.config().guardrails().enabledGuardrails())
            .contains("toxic-language", "similarity-guard");
        assertThat(template.config().model().temperature()).isEqualTo(0.1); // Very low temperature
        assertThat(template.tags()).contains("moderation", "safety", "compliance");
    }

    @Test
    public void shouldHaveTransactionAnalyzerTemplate() {
        var template = TemplateLibrary.transactionAnalyzer();

        assertThat(template.templateId()).isEqualTo("template-transaction-analyzer-v1");
        assertThat(template.name()).isEqualTo("Transaction Analysis Expert");
        assertThat(template.category()).isEqualTo("analytics");
        assertThat(template.config().systemPrompt()).contains("transaction analysis expert");
        assertThat(template.config().tools()).hasSize(3);
        assertThat(template.config().model().temperature()).isEqualTo(0.2); // Lower temperature
        assertThat(template.tags()).contains("transactions", "audit", "reconciliation");
    }

    @Test
    public void shouldHaveUniqueTemplateIds() {
        var templates = TemplateLibrary.getAllTemplates();
        var ids = templates.stream().map(TemplateLibrary.TemplateDefinition::templateId).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    public void shouldHaveUniqueTemplateNames() {
        var templates = TemplateLibrary.getAllTemplates();
        var names = templates.stream().map(TemplateLibrary.TemplateDefinition::name).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    public void allTemplatesShouldHaveValidConfig() {
        var templates = TemplateLibrary.getAllTemplates();

        for (var template : templates) {
            assertThat(template.templateId()).isNotBlank();
            assertThat(template.name()).isNotBlank();
            assertThat(template.description()).isNotBlank();
            assertThat(template.category()).isNotBlank();
            assertThat(template.author()).isNotBlank();
            assertThat(template.version()).isNotBlank();
            assertThat(template.config()).isNotNull();
            assertThat(template.config().systemPrompt()).isNotBlank();
            assertThat(template.config().tools()).isNotNull();
            assertThat(template.config().settings()).isNotNull();
            assertThat(template.tags()).isNotEmpty();
        }
    }
}
