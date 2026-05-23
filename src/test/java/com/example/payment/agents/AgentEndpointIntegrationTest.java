package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.payment.api.AgentEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgentEndpoint with mocked AI responses.
 */
public class AgentEndpointIntegrationTest extends TestKitSupport {

    private final TestModelProvider supportModel = new TestModelProvider();
    private final TestModelProvider fraudModel = new TestModelProvider();
    private final TestModelProvider assistantModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = test")
            .withModelProvider(CustomerSupportAgent.class, supportModel)
            .withModelProvider(FraudAnalystAgent.class, fraudModel)
            .withModelProvider(PaymentAssistantAgent.class, assistantModel);
    }

    @Test
    public void shouldHandleSupportQueryViaEndpoint() {
        var agentResponse = new CustomerSupportAgent.SupportResponse(
            "I apologize, but I'm having trouble processing your request right now. Please contact our support team for assistance.",
            "ESCALATE",
            "LOW",
            null,
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.SupportQueryRequest(
            "cust_123",
            "What's the status of my last payment?",
            null
        );

        var response = httpClient
            .POST("/agents/support/query")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.SupportQueryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().action()).isEqualTo("ESCALATE");
        assertThat(response.body().sessionId()).isNotNull();
    }

    @Test
    public void shouldHandleSupportQueryWithEscalation() {
        var agentResponse = new CustomerSupportAgent.SupportResponse(
            "I apologize, but I'm having trouble processing your request right now. Please contact our support team for assistance.",
            "ESCALATE",
            "LOW",
            null,
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.SupportQueryRequest(
            "cust_456",
            "I want a partial refund",
            null
        );

        var response = httpClient
            .POST("/agents/support/query")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.SupportQueryResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().action()).isEqualTo("ESCALATE");
    }

    @Test
    public void shouldHandleFraudAnalysisViaEndpoint() {
        var agentResponse = new FraudAnalystAgent.FraudAnalysis(
            true,
            "HIGH",
            0.85,
            List.of("Amount 5x higher than average", "First transaction from new device"),
            "REVIEW",
            "Multiple fraud indicators detected. Manual review recommended."
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.FraudAnalysisRequest(
            "cust_789",
            "txn_suspicious",
            "2500.00",
            "USD",
            "ORDER-789"
        );

        var response = httpClient
            .POST("/agents/fraud/analyze")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.FraudAnalysisResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().isSuspicious()).isTrue();
        assertThat(response.body().riskLevel()).isEqualTo("HIGH");
        assertThat(response.body().recommendation()).isEqualTo("REVIEW");
        assertThat(response.body().riskFactors()).hasSize(2);
    }

    @Test
    public void shouldHandleLowRiskFraudAnalysis() {
        var agentResponse = new FraudAnalystAgent.FraudAnalysis(
            false,
            "LOW",
            0.95,
            List.of("Normal transaction pattern"),
            "APPROVE",
            "Transaction appears legitimate."
        );

        fraudModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.FraudAnalysisRequest(
            "cust_regular",
            "txn_normal",
            "50.00",
            "USD",
            "ORDER-100"
        );

        var response = httpClient
            .POST("/agents/fraud/analyze")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.FraudAnalysisResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().isSuspicious()).isFalse();
        assertThat(response.body().riskLevel()).isEqualTo("LOW");
        assertThat(response.body().recommendation()).isEqualTo("APPROVE");
    }

    @Test
    public void shouldHandleFailureAnalysisViaEndpoint() {
        var agentResponse = new PaymentAssistantAgent.FailureAnalysis(
            "CARD_EXPIRED",
            "RECOVERABLE",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("UPDATE_CARD", "Update your card expiration date", 1),
                new PaymentAssistantAgent.RecoveryAction("CHANGE_CARD", "Use a different payment method", 2)
            ),
            "Your card has expired. Please update your card information."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.FailureAnalysisRequest(
            "cust_expired",
            "txn_fail_expired"
        );

        var response = httpClient
            .POST("/agents/failures/analyze")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.FailureAnalysisResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().failureType()).isEqualTo("CARD_EXPIRED");
        assertThat(response.body().severity()).isEqualTo("RECOVERABLE");
        assertThat(response.body().actions()).hasSize(2);
        assertThat(response.body().actions()[0].action()).isEqualTo("UPDATE_CARD");
        assertThat(response.body().actions()[0].priority()).isEqualTo(1);
    }

    @Test
    public void shouldHandleTransientFailureAnalysis() {
        var agentResponse = new PaymentAssistantAgent.FailureAnalysis(
            "TRANSIENT",
            "TEMPORARY",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("RETRY", "Try your payment again in a few minutes", 1)
            ),
            "Temporary issue. Please retry your payment."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(agentResponse));

        var request = new AgentEndpoint.FailureAnalysisRequest(
            "cust_retry",
            "txn_fail_transient"
        );

        var response = httpClient
            .POST("/agents/failures/analyze")
            .withRequestBody(request)
            .responseBodyAs(AgentEndpoint.FailureAnalysisResponse.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
        assertThat(response.body().failureType()).isEqualTo("TRANSIENT");
        assertThat(response.body().severity()).isEqualTo("TEMPORARY");
        assertThat(response.body().customerMessage()).contains("retry");
    }

    @Test
    public void shouldMaintainSessionInSupportChat() {
        var response1 = new CustomerSupportAgent.SupportResponse(
            "I see your payment for $100.00",
            "INFORM",
            "HIGH",
            "txn_conversation",
            null
        );

        var response2 = new CustomerSupportAgent.SupportResponse(
            "Yes, that payment was successful",
            "INFORM",
            "HIGH",
            "txn_conversation",
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(response1));

        var request1 = new AgentEndpoint.SupportQueryRequest(
            "cust_chat",
            "Show my last payment",
            null
        );

        var result1 = httpClient
            .POST("/agents/support/query")
            .withRequestBody(request1)
            .responseBodyAs(AgentEndpoint.SupportQueryResponse.class)
            .invoke();

        assertThat(result1.body().sessionId()).isNotNull();
        String sessionId = result1.body().sessionId();

        // Continue conversation with same session
        supportModel.fixedResponse(JsonSupport.encodeToString(response2));

        var request2 = new AgentEndpoint.SupportQueryRequest(
            "cust_chat",
            "Did it succeed?",
            sessionId  // Reuse session
        );

        var result2 = httpClient
            .POST("/agents/support/query")
            .withRequestBody(request2)
            .responseBodyAs(AgentEndpoint.SupportQueryResponse.class)
            .invoke();

        assertThat(result2.body().sessionId()).isEqualTo(sessionId);
        assertThat(result2.body().transactionId()).isEqualTo("txn_conversation");
    }
}
