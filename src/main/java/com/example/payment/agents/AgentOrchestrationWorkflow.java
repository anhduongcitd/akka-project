package com.example.payment.agents;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.time.Duration.ofSeconds;

/**
 * Agent Orchestration Workflow - Supervisor pattern for multi-agent coordination.
 *
 * Orchestrates:
 * - CustomerSupportAgent - Customer inquiries and refund requests
 * - FraudAnalystAgent - Transaction fraud detection
 * - PaymentAssistantAgent - Payment failure analysis
 *
 * Pattern: Workflow acts as supervisor, no direct agent-to-agent calls
 * Session memory: Shared via session ID across all agents in the workflow
 */
@Component(id = "agent-orchestration")
public class AgentOrchestrationWorkflow extends Workflow<AgentOrchestrationWorkflow.State> {

    private final ComponentClient componentClient;

    public AgentOrchestrationWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // State record
    public record State(
        String sessionId,
        String requestType,          // SUPPORT_QUERY, FRAUD_CHECK, FAILURE_ANALYSIS
        String customerId,
        Map<String, String> context,
        CustomerSupportAgent.SupportResponse supportResult,
        FraudAnalystAgent.FraudAnalysis fraudResult,
        PaymentAssistantAgent.FailureAnalysis failureResult,
        String finalDecision,
        String errorMessage
    ) {
        State withSupportResult(CustomerSupportAgent.SupportResponse result) {
            return new State(sessionId, requestType, customerId, context, result, fraudResult, failureResult, finalDecision, errorMessage);
        }

        State withFraudResult(FraudAnalystAgent.FraudAnalysis result) {
            return new State(sessionId, requestType, customerId, context, supportResult, result, failureResult, finalDecision, errorMessage);
        }

        State withFailureResult(PaymentAssistantAgent.FailureAnalysis result) {
            return new State(sessionId, requestType, customerId, context, supportResult, fraudResult, result, finalDecision, errorMessage);
        }

        State withFinalDecision(String decision) {
            return new State(sessionId, requestType, customerId, context, supportResult, fraudResult, failureResult, decision, errorMessage);
        }

        State withError(String error) {
            return new State(sessionId, requestType, customerId, context, supportResult, fraudResult, failureResult, finalDecision, error);
        }
    }

    // Request records
    public record AgentRequest(
        String sessionId,
        String requestType,          // SUPPORT_QUERY, FRAUD_CHECK, FAILURE_ANALYSIS
        String customerId,
        Map<String, String> context  // Request-specific data (query, transactionId, etc.)
    ) {}

    public record AgentResult(
        String decision,
        String supportAnswer,        // From CustomerSupportAgent
        String fraudRiskLevel,       // From FraudAnalystAgent
        String failureSeverity,      // From PaymentAssistantAgent
        String details
    ) {}

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .stepTimeout(AgentOrchestrationWorkflow::callSupportAgentStep, ofSeconds(60))
            .stepTimeout(AgentOrchestrationWorkflow::callFraudAgentStep, ofSeconds(60))
            .stepTimeout(AgentOrchestrationWorkflow::callAssistantAgentStep, ofSeconds(60))
            .defaultStepRecovery(RecoverStrategy.maxRetries(2)
                .failoverTo(AgentOrchestrationWorkflow::errorStep))
            .build();
    }

    /**
     * Start workflow with agent request.
     */
    public Effect<Done> start(AgentRequest request) {
        return effects()
            .updateState(new State(
                request.sessionId(),
                request.requestType(),
                request.customerId(),
                request.context() != null ? request.context() : new HashMap<>(),
                null, null, null, null, null
            ))
            .transitionTo(AgentOrchestrationWorkflow::routeRequestStep)
            .thenReply(Done.getInstance());
    }

    /**
     * Get final result.
     */
    public Effect<AgentResult> getResult() {
        if (currentState() == null || currentState().finalDecision == null) {
            return effects().error("Workflow not completed or no result available");
        }

        // Build result from agent responses
        String decision = currentState().finalDecision;
        String supportAnswer = currentState().supportResult != null
            ? currentState().supportResult.answer()
            : null;
        String fraudRiskLevel = currentState().fraudResult != null
            ? currentState().fraudResult.riskLevel()
            : null;
        String failureSeverity = currentState().failureResult != null
            ? currentState().failureResult.severity()
            : null;

        String details = buildDetails();

        return effects().reply(new AgentResult(
            decision, supportAnswer, fraudRiskLevel, failureSeverity, details
        ));
    }

    @StepName("route")
    private StepEffect routeRequestStep() {
        return switch (currentState().requestType) {
            case "SUPPORT_QUERY" -> stepEffects()
                .thenTransitionTo(AgentOrchestrationWorkflow::callSupportAgentStep);
            case "FRAUD_CHECK" -> stepEffects()
                .thenTransitionTo(AgentOrchestrationWorkflow::callFraudAgentStep);
            case "FAILURE_ANALYSIS" -> stepEffects()
                .thenTransitionTo(AgentOrchestrationWorkflow::callAssistantAgentStep);
            default -> stepEffects()
                .updateState(currentState().withError("Unknown request type: " + currentState().requestType))
                .thenTransitionTo(AgentOrchestrationWorkflow::errorStep);
        };
    }

    @StepName("support")
    private StepEffect callSupportAgentStep() {
        String query = currentState().context.get("query");
        if (query == null) {
            return stepEffects()
                .updateState(currentState().withError("Missing 'query' in context"))
                .thenTransitionTo(AgentOrchestrationWorkflow::errorStep);
        }

        var supportRequest = new CustomerSupportAgent.SupportRequest(
            currentState().customerId,
            query
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(CustomerSupportAgent::handleQuery)
            .invoke(supportRequest);

        return stepEffects()
            .updateState(currentState().withSupportResult(result))
            .thenTransitionTo(AgentOrchestrationWorkflow::aggregateResultsStep);
    }

    @StepName("fraud")
    private StepEffect callFraudAgentStep() {
        String transactionId = currentState().context.get("transactionId");
        String amount = currentState().context.get("amount");
        String currency = currentState().context.get("currency");
        String merchantReference = currentState().context.get("merchantReference");

        if (transactionId == null || amount == null || currency == null) {
            return stepEffects()
                .updateState(currentState().withError("Missing fraud check parameters"))
                .thenTransitionTo(AgentOrchestrationWorkflow::errorStep);
        }

        var fraudRequest = new FraudAnalystAgent.FraudCheckRequest(
            currentState().customerId,
            transactionId,
            amount,
            currency,
            merchantReference
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(fraudRequest);

        return stepEffects()
            .updateState(currentState().withFraudResult(result))
            .thenTransitionTo(AgentOrchestrationWorkflow::aggregateResultsStep);
    }

    @StepName("assistant")
    private StepEffect callAssistantAgentStep() {
        String transactionId = currentState().context.get("transactionId");

        if (transactionId == null) {
            return stepEffects()
                .updateState(currentState().withError("Missing 'transactionId' in context"))
                .thenTransitionTo(AgentOrchestrationWorkflow::errorStep);
        }

        var failureRequest = new PaymentAssistantAgent.FailureRequest(
            transactionId,
            currentState().customerId
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(failureRequest);

        return stepEffects()
            .updateState(currentState().withFailureResult(result))
            .thenTransitionTo(AgentOrchestrationWorkflow::aggregateResultsStep);
    }

    @StepName("aggregate")
    private StepEffect aggregateResultsStep() {
        String decision = aggregateAgentDecisions(currentState());

        return stepEffects()
            .updateState(currentState().withFinalDecision(decision))
            .thenTransitionTo(AgentOrchestrationWorkflow::auditDecisionStep);
    }

    @StepName("audit")
    private StepEffect auditDecisionStep() {
        // In a real implementation, log to AuditLogEntity here
        // For now, just complete the workflow
        return stepEffects().thenEnd();
    }

    @StepName("error")
    private StepEffect errorStep() {
        String errorDecision = "ERROR: " + (currentState().errorMessage != null
            ? currentState().errorMessage
            : "Agent orchestration failed");

        return stepEffects()
            .updateState(currentState().withFinalDecision(errorDecision))
            .thenEnd();
    }

    private String aggregateAgentDecisions(State state) {
        if (state.supportResult != null) {
            return "SUPPORT: " + state.supportResult.action() + " - " + state.supportResult.confidence();
        }
        if (state.fraudResult != null) {
            return "FRAUD: " + state.fraudResult.recommendation() + " - " + state.fraudResult.riskLevel();
        }
        if (state.failureResult != null) {
            return "FAILURE: " + state.failureResult.severity() + " - " +
                (state.failureResult.actions().isEmpty() ? "No actions" : state.failureResult.actions().get(0).action());
        }
        return "NO_DECISION";
    }

    private String buildDetails() {
        StringBuilder details = new StringBuilder();

        if (currentState().supportResult != null) {
            details.append("Support: ").append(currentState().supportResult.answer()).append("\n");
        }
        if (currentState().fraudResult != null) {
            details.append("Fraud: ").append(currentState().fraudResult.reasoning()).append("\n");
        }
        if (currentState().failureResult != null) {
            details.append("Failure: ").append(currentState().failureResult.customerMessage()).append("\n");
        }

        return details.toString();
    }

    private String sessionId() {
        return commandContext().workflowId();
    }
}
