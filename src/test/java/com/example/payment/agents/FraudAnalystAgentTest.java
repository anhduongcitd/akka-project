package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FraudAnalystAgent using TestModelProvider.
 */
public class FraudAnalystAgentTest extends TestKitSupport {

    private final TestModelProvider fraudModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = test")
            .withModelProvider(FraudAnalystAgent.class, fraudModel);
    }

    @Test
    public void shouldDetectLowRiskTransaction() {
        var expectedResponse = new FraudAnalystAgent.FraudAnalysis(
            false,
            "LOW",
            0.95,
            List.of("Normal transaction amount", "Consistent with customer history"),
            "APPROVE",
            "Transaction appears legitimate based on customer spending patterns"
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new FraudAnalystAgent.FraudCheckRequest(
            "cust_123",
            "txn_abc",
            "50.00",
            "USD",
            "ORDER-123"
        );

        var result = componentClient
            .forAgent()
            .inSession("fraud-session-1")
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(request);

        assertThat(result.isSuspicious()).isFalse();
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.recommendation()).isEqualTo("APPROVE");
        assertThat(result.confidenceScore()).isGreaterThan(0.9);
    }

    @Test
    public void shouldDetectHighRiskTransaction() {
        var expectedResponse = new FraudAnalystAgent.FraudAnalysis(
            true,
            "HIGH",
            0.85,
            List.of("Amount 10x higher than average", "First transaction from new location"),
            "REVIEW",
            "Multiple fraud indicators detected. Manual review recommended."
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new FraudAnalystAgent.FraudCheckRequest(
            "cust_456",
            "txn_xyz",
            "5000.00",
            "USD",
            "ORDER-456"
        );

        var result = componentClient
            .forAgent()
            .inSession("fraud-session-2")
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(request);

        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.recommendation()).isEqualTo("REVIEW");
        assertThat(result.riskFactors()).hasSize(2);
    }

    @Test
    public void shouldRecommendDeclineForCriticalRisk() {
        var expectedResponse = new FraudAnalystAgent.FraudAnalysis(
            true,
            "CRITICAL",
            0.98,
            List.of("Customer has previous fraud alerts", "Duplicate transaction detected", "Velocity limit exceeded"),
            "DECLINE",
            "Clear fraud indicators. Block immediately to prevent loss."
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new FraudAnalystAgent.FraudCheckRequest(
            "cust_fraud",
            "txn_suspicious",
            "1000.00",
            "USD",
            "ORDER-999"
        );

        var result = componentClient
            .forAgent()
            .inSession("fraud-session-3")
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(request);

        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.riskLevel()).isEqualTo("CRITICAL");
        assertThat(result.recommendation()).isEqualTo("DECLINE");
        assertThat(result.riskFactors()).contains("Velocity limit exceeded");
    }

    @Test
    public void shouldHandleMediumRiskWithReview() {
        var expectedResponse = new FraudAnalystAgent.FraudAnalysis(
            true,
            "MEDIUM",
            0.65,
            List.of("Unusual merchant category"),
            "REVIEW",
            "Some unusual factors present but could be legitimate. Recommend manual review."
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new FraudAnalystAgent.FraudCheckRequest(
            "cust_789",
            "txn_medium",
            "250.00",
            "EUR",
            "ORDER-789"
        );

        var result = componentClient
            .forAgent()
            .inSession("fraud-session-4")
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(request);

        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.recommendation()).isEqualTo("REVIEW");
    }

    @Test
    public void shouldFallbackOnFailure() {
        // Don't mock response - trigger failure
        fraudModel.fixedResponse("invalid json");

        var request = new FraudAnalystAgent.FraudCheckRequest(
            "cust_fail",
            "txn_fail",
            "100.00",
            "USD",
            "ORDER-FAIL"
        );

        var result = componentClient
            .forAgent()
            .inSession("fraud-session-fail")
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(request);

        // onFailure handler should provide safe defaults
        assertThat(result.isSuspicious()).isFalse();
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.recommendation()).isEqualTo("APPROVE");
        assertThat(result.reasoning()).contains("rule-based");
    }
}
