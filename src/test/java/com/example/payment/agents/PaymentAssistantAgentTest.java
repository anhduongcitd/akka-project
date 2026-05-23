package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PaymentAssistantAgent using TestModelProvider.
 */
public class PaymentAssistantAgentTest extends TestKitSupport {

    private final TestModelProvider assistantModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = test")
            .withModelProvider(PaymentAssistantAgent.class, assistantModel);
    }

    @Test
    public void shouldAnalyzeTransientFailure() {
        var expectedResponse = new PaymentAssistantAgent.FailureAnalysis(
            "TRANSIENT",
            "TEMPORARY",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("RETRY", "Try your payment again in a few minutes", 1),
                new PaymentAssistantAgent.RecoveryAction("CONTACT_BANK", "If issue persists, contact your bank", 2)
            ),
            "We're sorry, but your payment couldn't be processed due to a temporary issue. Please try again."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new PaymentAssistantAgent.FailureRequest("txn_fail_transient", "cust_123");

        var result = componentClient
            .forAgent()
            .inSession("assistant-session-1")
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(request);

        assertThat(result.failureType()).isEqualTo("TRANSIENT");
        assertThat(result.severity()).isEqualTo("TEMPORARY");
        assertThat(result.actions()).hasSize(2);
        assertThat(result.actions().get(0).action()).isEqualTo("RETRY");
        assertThat(result.actions().get(0).priority()).isEqualTo(1);
    }

    @Test
    public void shouldAnalyzeCardExpiredFailure() {
        var expectedResponse = new PaymentAssistantAgent.FailureAnalysis(
            "CARD_EXPIRED",
            "RECOVERABLE",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("UPDATE_CARD", "Update your card expiration date", 1),
                new PaymentAssistantAgent.RecoveryAction("CHANGE_CARD", "Use a different payment method", 2)
            ),
            "Your card has expired. Please update your card information or use a different payment method."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new PaymentAssistantAgent.FailureRequest("txn_fail_expired", "cust_456");

        var result = componentClient
            .forAgent()
            .inSession("assistant-session-2")
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(request);

        assertThat(result.failureType()).isEqualTo("CARD_EXPIRED");
        assertThat(result.severity()).isEqualTo("RECOVERABLE");
        assertThat(result.actions()).anyMatch(a -> a.action().equals("UPDATE_CARD"));
    }

    @Test
    public void shouldAnalyzeInsufficientFundsFailure() {
        var expectedResponse = new PaymentAssistantAgent.FailureAnalysis(
            "INSUFFICIENT_FUNDS",
            "RECOVERABLE",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("CONTACT_BANK", "Contact your bank to add funds", 1),
                new PaymentAssistantAgent.RecoveryAction("CHANGE_CARD", "Use a different payment method", 2)
            ),
            "Your payment was declined due to insufficient funds. Please ensure your account has enough balance."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new PaymentAssistantAgent.FailureRequest("txn_fail_insufficient", "cust_789");

        var result = componentClient
            .forAgent()
            .inSession("assistant-session-3")
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(request);

        assertThat(result.failureType()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.customerMessage()).contains("insufficient funds");
    }

    @Test
    public void shouldAnalyzeFraudBlockFailure() {
        var expectedResponse = new PaymentAssistantAgent.FailureAnalysis(
            "FRAUD_BLOCK",
            "TERMINAL",
            List.of(
                new PaymentAssistantAgent.RecoveryAction("DISPUTE", "Contact support to dispute fraud block", 1),
                new PaymentAssistantAgent.RecoveryAction("CONTACT_BANK", "Contact your bank to verify the charge", 2)
            ),
            "Your payment was blocked by our fraud detection system. Please contact support if you believe this is an error."
        );

        assistantModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var request = new PaymentAssistantAgent.FailureRequest("txn_fail_fraud", "cust_fraud");

        var result = componentClient
            .forAgent()
            .inSession("assistant-session-4")
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(request);

        assertThat(result.failureType()).isEqualTo("FRAUD_BLOCK");
        assertThat(result.severity()).isEqualTo("TERMINAL");
        assertThat(result.actions()).anyMatch(a -> a.action().equals("DISPUTE"));
    }

    @Test
    public void shouldFallbackOnFailure() {
        // Don't mock response - trigger failure
        assistantModel.fixedResponse("invalid json");

        var request = new PaymentAssistantAgent.FailureRequest("txn_fail", "cust_fail");

        var result = componentClient
            .forAgent()
            .inSession("assistant-session-fail")
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(request);

        // onFailure handler should provide default analysis
        assertThat(result.failureType()).isEqualTo("GATEWAY_ERROR");
        assertThat(result.severity()).isEqualTo("TEMPORARY");
        assertThat(result.actions()).isNotEmpty();
        assertThat(result.actions().get(0).action()).isEqualTo("RETRY");
    }
}
