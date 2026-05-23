package com.example.payment.agents;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.payment.agents.domain.ExecutionPlan;
import com.example.payment.agents.domain.PlanStep;
import com.example.payment.application.AgentPerformanceEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.time.Duration.ofSeconds;

/**
 * Dynamic Agent Workflow - Executes multi-agent collaboration plans.
 *
 * Orchestration Pattern:
 * 1. PlannerAgent creates execution plan
 * 2. Execute plan steps (sequential/parallel/hybrid)
 * 3. Track performance for each agent call
 * 4. SummarizerAgent combines results
 * 5. Return unified response
 *
 * Supports dynamic agent invocation - agents not known at compile time.
 */
@Component(id = "dynamic-agent-workflow")
public class DynamicAgentWorkflow extends Workflow<DynamicAgentWorkflow.State> {

    private final ComponentClient componentClient;

    public DynamicAgentWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // State record
    public record State(
        String sessionId,
        String userQuery,
        String customerId,
        String context,
        ExecutionPlan plan,
        Map<String, String> agentResults,      // agentId -> response
        Map<String, Long> agentLatencies,      // agentId -> latency ms
        int currentStepIndex,
        String finalResponse,
        String errorMessage
    ) {
        State withPlan(ExecutionPlan p) {
            return new State(sessionId, userQuery, customerId, context, p, agentResults, agentLatencies, currentStepIndex, finalResponse, errorMessage);
        }

        State addAgentResult(String agentId, String result, long latencyMs) {
            var newResults = new HashMap<>(agentResults);
            newResults.put(agentId, result);
            var newLatencies = new HashMap<>(agentLatencies);
            newLatencies.put(agentId, latencyMs);
            return new State(sessionId, userQuery, customerId, context, plan, newResults, newLatencies, currentStepIndex, finalResponse, errorMessage);
        }

        State incrementStep() {
            return new State(sessionId, userQuery, customerId, context, plan, agentResults, agentLatencies, currentStepIndex + 1, finalResponse, errorMessage);
        }

        State withFinalResponse(String response) {
            return new State(sessionId, userQuery, customerId, context, plan, agentResults, agentLatencies, currentStepIndex, response, errorMessage);
        }

        State withError(String error) {
            return new State(sessionId, userQuery, customerId, context, plan, agentResults, agentLatencies, currentStepIndex, finalResponse, error);
        }

        boolean hasMoreSteps() {
            return plan != null && currentStepIndex < plan.steps().size();
        }

        PlanStep currentStep() {
            return plan.steps().get(currentStepIndex);
        }
    }

    // Request/Response records
    public record CollaborationRequest(
        String sessionId,       // Session for shared memory across agents
        String userQuery,       // User's question
        String customerId,      // Customer ID
        String context          // Additional context (transaction ID, etc.)
    ) {}

    public record CollaborationResponse(
        String answer,              // Unified response
        String confidence,          // Confidence level
        ExecutionPlan planUsed,     // Plan that was executed
        Map<String, String> agentContributions  // What each agent said
    ) {}

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .stepTimeout(DynamicAgentWorkflow::createPlanStep, ofSeconds(60))
            .stepTimeout(DynamicAgentWorkflow::executeAgentStep, ofSeconds(60))
            .stepTimeout(DynamicAgentWorkflow::summarizeResultsStep, ofSeconds(60))
            .defaultStepRecovery(RecoverStrategy.maxRetries(2)
                .failoverTo(DynamicAgentWorkflow::errorStep))
            .build();
    }

    /**
     * Start workflow with collaboration request.
     */
    public Effect<Done> start(CollaborationRequest request) {
        return effects()
            .updateState(new State(
                request.sessionId(),
                request.userQuery(),
                request.customerId(),
                request.context(),
                null,
                new HashMap<>(),
                new HashMap<>(),
                0,
                null,
                null
            ))
            .transitionTo(DynamicAgentWorkflow::createPlanStep)
            .thenReply(Done.getInstance());
    }

    /**
     * Get final collaboration result.
     */
    public Effect<CollaborationResponse> getResult() {
        if (currentState() == null || currentState().finalResponse == null) {
            return effects().error("Collaboration not completed or no result available");
        }

        // Parse final response from SummarizerAgent
        // In production, this would deserialize the SummarizedResponse
        return effects().reply(new CollaborationResponse(
            currentState().finalResponse,
            "HIGH",  // Would come from SummarizerAgent
            currentState().plan,
            currentState().agentResults
        ));
    }

    @StepName("create-plan")
    private StepEffect createPlanStep() {
        var planRequest = new PlannerAgent.PlanningRequest(
            currentState().userQuery,
            currentState().customerId,
            currentState().context
        );

        var plan = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(PlannerAgent::createPlan)
            .invoke(planRequest);

        if (plan.steps().isEmpty()) {
            return stepEffects()
                .updateState(currentState().withError("Planning failed: no steps generated"))
                .thenTransitionTo(DynamicAgentWorkflow::errorStep);
        }

        return stepEffects()
            .updateState(currentState().withPlan(plan))
            .thenTransitionTo(DynamicAgentWorkflow::executeAgentStep);
    }

    @StepName("execute-agent")
    private StepEffect executeAgentStep() {
        if (!currentState().hasMoreSteps()) {
            // All steps executed, move to summarization
            return stepEffects()
                .thenTransitionTo(DynamicAgentWorkflow::summarizeResultsStep);
        }

        PlanStep step = currentState().currentStep();
        long startTime = System.currentTimeMillis();

        try {
            // Dynamic agent invocation - call agent by ID at runtime
            String result = callAgentDynamically(step.agentId(), step.query());
            long latency = System.currentTimeMillis() - startTime;

            // Track performance metrics
            trackAgentPerformance(step.agentId(), true, latency, 1000.0, 0.002);

            // Store result and continue to next step
            return stepEffects()
                .updateState(currentState()
                    .addAgentResult(step.agentId(), result, latency)
                    .incrementStep())
                .thenTransitionTo(DynamicAgentWorkflow::executeAgentStep);

        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - startTime;
            trackAgentPerformance(step.agentId(), false, latency, 0, 0);

            // If step is required and failed, abort workflow
            if (step.required()) {
                return stepEffects()
                    .updateState(currentState().withError(
                        "Required agent " + step.agentId() + " failed: " + ex.getMessage()))
                    .thenTransitionTo(DynamicAgentWorkflow::errorStep);
            }

            // Optional step failed, skip and continue
            return stepEffects()
                .updateState(currentState().incrementStep())
                .thenTransitionTo(DynamicAgentWorkflow::executeAgentStep);
        }
    }

    @StepName("summarize")
    private StepEffect summarizeResultsStep() {
        if (currentState().agentResults.isEmpty()) {
            return stepEffects()
                .updateState(currentState().withError("No agent results to summarize"))
                .thenTransitionTo(DynamicAgentWorkflow::errorStep);
        }

        var summaryRequest = new SummarizerAgent.SummarizationRequest(
            currentState().userQuery,
            currentState().agentResults,
            currentState().plan.strategy()
        );

        var summary = componentClient
            .forAgent()
            .inSession(sessionId())
            .method(SummarizerAgent::summarize)
            .invoke(summaryRequest);

        return stepEffects()
            .updateState(currentState().withFinalResponse(summary.answer()))
            .thenEnd();
    }

    @StepName("error")
    private StepEffect errorStep() {
        String errorResponse = currentState().errorMessage != null
            ? "I encountered an issue: " + currentState().errorMessage
            : "I'm unable to process this request. Please try again or contact support.";

        return stepEffects()
            .updateState(currentState().withFinalResponse(errorResponse))
            .thenEnd();
    }

    /**
     * Call agent dynamically by ID.
     * Maps agent IDs to their command handlers.
     */
    private String callAgentDynamically(String agentId, String query) {
        return switch (agentId) {
            case "customer-support" -> {
                var request = new CustomerSupportAgent.SupportRequest(
                    currentState().customerId,
                    query
                );
                var result = componentClient
                    .forAgent()
                    .inSession(sessionId())
                    .method(CustomerSupportAgent::handleQuery)
                    .invoke(request);
                yield result.answer();
            }
            case "fraud-analyst" -> {
                // Parse context for transaction details
                var request = new FraudAnalystAgent.FraudCheckRequest(
                    currentState().customerId,
                    extractTransactionId(currentState().context),
                    "0.00",  // Amount would be extracted from context
                    "USD",
                    ""
                );
                var result = componentClient
                    .forAgent()
                    .inSession(sessionId())
                    .method(FraudAnalystAgent::analyzeTransaction)
                    .invoke(request);
                yield result.reasoning();
            }
            case "payment-assistant" -> {
                var request = new PaymentAssistantAgent.FailureRequest(
                    extractTransactionId(currentState().context),
                    currentState().customerId
                );
                var result = componentClient
                    .forAgent()
                    .inSession(sessionId())
                    .method(PaymentAssistantAgent::analyzeFailure)
                    .invoke(request);
                yield result.customerMessage();
            }
            default -> throw new IllegalArgumentException("Unknown agent: " + agentId);
        };
    }

    /**
     * Track agent performance metrics.
     */
    private void trackAgentPerformance(String agentId, boolean success, long latencyMs, double tokens, double cost) {
        try {
            if (success) {
                componentClient
                    .forKeyValueEntity(agentId)
                    .method(AgentPerformanceEntity::recordSuccess)
                    .invoke(new AgentPerformanceEntity.RecordSuccess(latencyMs, tokens, cost));
            } else {
                componentClient
                    .forKeyValueEntity(agentId)
                    .method(AgentPerformanceEntity::recordFailure)
                    .invoke(new AgentPerformanceEntity.RecordFailure(latencyMs));
            }
        } catch (Exception ex) {
            // Don't fail workflow if metrics tracking fails
            System.err.println("Failed to track performance for " + agentId + ": " + ex.getMessage());
        }
    }

    /**
     * Extract transaction ID from context string.
     */
    private String extractTransactionId(String context) {
        if (context == null || context.isBlank()) {
            return "unknown";
        }
        // Simple extraction - in production would use regex
        if (context.startsWith("txn_")) {
            return context.split(" ")[0];
        }
        return "unknown";
    }

    private String sessionId() {
        return commandContext().workflowId();
    }
}
