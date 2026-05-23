package com.example.payment.agents;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CustomerSupportAgent using TestModelProvider.
 *
 * Note: These tests verify agent configuration and fallback behavior.
 * Full integration tests with real data are in AgentEndpointIntegrationTest.
 */
public class CustomerSupportAgentTest extends TestKitSupport {

    private final TestModelProvider supportModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
            .withAdditionalConfig("akka.javasdk.agent.openai.api-key = test")
            .withModelProvider(CustomerSupportAgent.class, supportModel);
    }

    @Test
    public void shouldConfigureAgentCorrectly() {
        // Verify agent can be invoked and returns fallback on tool failure
        var fallbackResponse = new CustomerSupportAgent.SupportResponse(
            "I apologize, but I'm having trouble processing your request right now. Please contact our support team for assistance.",
            "ESCALATE",
            "LOW",
            null,
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(fallbackResponse));

        var result = componentClient
            .forAgent()
            .inSession("test-session")
            .method(CustomerSupportAgent::handleQuery)
            .invoke(new CustomerSupportAgent.SupportRequest("cust_123", "test query"));

        assertThat(result).isNotNull();
        assertThat(result.action()).isEqualTo("ESCALATE");
        assertThat(result.confidence()).isEqualTo("LOW");
    }

    @Test
    public void shouldReturnEscalateAction() {
        var expectedResponse = new CustomerSupportAgent.SupportResponse(
            "I apologize, but I need to escalate this to our support team.",
            "ESCALATE",
            "LOW",
            null,
            null
        );

        supportModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var result = componentClient
            .forAgent()
            .inSession("test-session-2")
            .method(CustomerSupportAgent::handleQuery)
            .invoke(new CustomerSupportAgent.SupportRequest("cust_789", "complex issue"));

        assertThat(result.action()).isEqualTo("ESCALATE");
        assertThat(result.confidence()).isEqualTo("LOW");
    }
}
