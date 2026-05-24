package com.example.payment.application;

import com.example.payment.agents.domain.AgentChainConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChainTemplateLibrary.
 */
public class ChainTemplateLibraryTest {

    @Test
    public void shouldHaveEightChainTemplates() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();
        assertThat(templates).hasSize(8);
    }

    @Test
    public void shouldHaveCustomerSupportChain() {
        var chain = ChainTemplateLibrary.customerSupportChain();

        assertThat(chain.chainId()).isEqualTo("chain-customer-support-v1");
        assertThat(chain.name()).isEqualTo("Customer Support Chain");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.SEQUENTIAL);
        assertThat(chain.getStepCount()).isEqualTo(3);
        assertThat(chain.continueOnError()).isTrue();

        // Verify steps
        var steps = chain.steps();
        assertThat(steps.get(0).stepId()).isEqualTo("triage");
        assertThat(steps.get(0).agentId()).isEqualTo("general-qa");
        assertThat(steps.get(1).stepId()).isEqualTo("support");
        assertThat(steps.get(1).agentId()).isEqualTo("customer-support");
        assertThat(steps.get(2).stepId()).isEqualTo("escalation");
        assertThat(steps.get(2).condition()).isNotNull();
    }

    @Test
    public void shouldHaveFraudDetectionChain() {
        var chain = ChainTemplateLibrary.fraudDetectionChain();

        assertThat(chain.chainId()).isEqualTo("chain-fraud-detection-v1");
        assertThat(chain.name()).isEqualTo("Fraud Detection Chain");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.PARALLEL);
        assertThat(chain.getStepCount()).isEqualTo(3);
        assertThat(chain.continueOnError()).isFalse();

        // Verify parallel steps
        var steps = chain.steps();
        assertThat(steps.get(0).stepId()).isEqualTo("velocity-check");
        assertThat(steps.get(1).stepId()).isEqualTo("pattern-analysis");
        assertThat(steps.get(2).stepId()).isEqualTo("risk-scoring");
        assertThat(steps).allMatch(s -> s.agentId().equals("fraud-analyst"));
    }

    @Test
    public void shouldHavePaymentAnalysisChain() {
        var chain = ChainTemplateLibrary.paymentAnalysisChain();

        assertThat(chain.chainId()).isEqualTo("chain-payment-analysis-v1");
        assertThat(chain.name()).isEqualTo("Payment Analysis Chain");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.SEQUENTIAL);
        assertThat(chain.getStepCount()).isEqualTo(3);

        var steps = chain.steps();
        assertThat(steps.get(0).agentId()).isEqualTo("transaction-analyzer");
        assertThat(steps.get(1).agentId()).isEqualTo("payment-assistant");
        assertThat(steps.get(2).agentId()).isEqualTo("payment-assistant");
    }

    @Test
    public void shouldHaveContentModerationChain() {
        var chain = ChainTemplateLibrary.contentModerationChain();

        assertThat(chain.chainId()).isEqualTo("chain-content-moderation-v1");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.PARALLEL);
        assertThat(chain.getStepCount()).isEqualTo(3);
        assertThat(chain.continueOnError()).isTrue(); // Get all moderation results

        var steps = chain.steps();
        assertThat(steps.get(0).outputKey()).isEqualTo("toxic_result");
        assertThat(steps.get(1).outputKey()).isEqualTo("pii_result");
        assertThat(steps.get(2).outputKey()).isEqualTo("spam_result");
    }

    @Test
    public void shouldHaveResearchChain() {
        var chain = ChainTemplateLibrary.researchChain();

        assertThat(chain.chainId()).isEqualTo("chain-research-v1");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.SEQUENTIAL);
        assertThat(chain.getStepCount()).isEqualTo(3);

        var steps = chain.steps();
        assertThat(steps.get(0).agentId()).isEqualTo("planner");
        assertThat(steps.get(1).agentId()).isEqualTo("data-analyst");
        assertThat(steps.get(2).agentId()).isEqualTo("summarizer");
    }

    @Test
    public void shouldHaveDecisionMakingChain() {
        var chain = ChainTemplateLibrary.decisionMakingChain();

        assertThat(chain.chainId()).isEqualTo("chain-decision-making-v1");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.CONDITIONAL);
        assertThat(chain.getStepCount()).isEqualTo(3);

        // Verify conditional execution
        var executeStep = chain.getStep("execute");
        assertThat(executeStep).isNotNull();
        assertThat(executeStep.condition()).isNotNull();
        assertThat(executeStep.condition().contextKey()).isEqualTo("recommendation");
        assertThat(executeStep.condition().operator())
            .isEqualTo(AgentChainConfig.Condition.ConditionOperator.CONTAINS);
    }

    @Test
    public void shouldHaveEmailResponseChain() {
        var chain = ChainTemplateLibrary.emailResponseChain();

        assertThat(chain.chainId()).isEqualTo("chain-email-response-v1");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.SEQUENTIAL);
        assertThat(chain.getStepCount()).isEqualTo(3);

        var steps = chain.steps();
        assertThat(steps.get(0).stepId()).isEqualTo("draft");
        assertThat(steps.get(1).stepId()).isEqualTo("review");
        assertThat(steps.get(2).stepId()).isEqualTo("finalize");
    }

    @Test
    public void shouldHaveTransactionAuditChain() {
        var chain = ChainTemplateLibrary.transactionAuditChain();

        assertThat(chain.chainId()).isEqualTo("chain-transaction-audit-v1");
        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.SEQUENTIAL);
        assertThat(chain.getStepCount()).isEqualTo(3);
        assertThat(chain.continueOnError()).isFalse();

        var steps = chain.steps();
        assertThat(steps.get(0).agentId()).isEqualTo("transaction-analyzer");
        assertThat(steps.get(1).agentId()).isEqualTo("fraud-analyst");
        assertThat(steps.get(2).agentId()).isEqualTo("summarizer");
    }

    @Test
    public void allChainsShouldBeValid() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();

        for (var chain : templates) {
            assertThat(chain.isValid())
                .withFailMessage("Chain %s is invalid", chain.chainId())
                .isTrue();
        }
    }

    @Test
    public void allChainsShouldHaveUniqueIds() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();
        var ids = templates.stream().map(AgentChainConfig::chainId).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    public void allChainsShouldHaveUniqueNames() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();
        var names = templates.stream().map(AgentChainConfig::name).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    public void allChainsShouldHaveValidSteps() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();

        for (var chain : templates) {
            assertThat(chain.getStepCount())
                .withFailMessage("Chain %s has no steps", chain.chainId())
                .isGreaterThan(0);

            for (var step : chain.steps()) {
                assertThat(step.stepId()).isNotBlank();
                assertThat(step.agentId()).isNotBlank();
                assertThat(step.inputTemplate()).isNotNull();
                assertThat(step.outputKey()).isNotBlank();
            }
        }
    }

    @Test
    public void allChainsShouldHaveGlobalContext() {
        var templates = ChainTemplateLibrary.getAllChainTemplates();

        for (var chain : templates) {
            assertThat(chain.globalContext())
                .withFailMessage("Chain %s has no global context", chain.chainId())
                .isNotNull();
        }
    }

    @Test
    public void sequentialChainsShouldHaveConnectedSteps() {
        var chain = ChainTemplateLibrary.customerSupportChain();

        // Verify output keys are used in next steps
        var steps = chain.steps();
        var firstOutputKey = steps.get(0).outputKey();
        var secondInput = steps.get(1).inputTemplate();

        assertThat(secondInput).contains("{{" + firstOutputKey + "}}");
    }

    @Test
    public void conditionalStepsShouldHaveConditions() {
        var chain = ChainTemplateLibrary.customerSupportChain();

        // Support step has condition
        var supportStep = chain.getStep("support");
        assertThat(supportStep.condition()).isNotNull();

        // Escalation step has condition
        var escalationStep = chain.getStep("escalation");
        assertThat(escalationStep.condition()).isNotNull();
    }

    @Test
    public void parallelChainsShouldHaveMultipleSteps() {
        var chain = ChainTemplateLibrary.fraudDetectionChain();

        assertThat(chain.executionMode()).isEqualTo(AgentChainConfig.ExecutionMode.PARALLEL);
        assertThat(chain.getStepCount()).isGreaterThanOrEqualTo(2);
    }
}
