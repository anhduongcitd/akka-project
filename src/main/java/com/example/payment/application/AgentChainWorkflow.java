package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.payment.agents.domain.AgentChainConfig;
import com.example.payment.agents.domain.AgentChainExecution;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofSeconds;

/**
 * Workflow for executing agent chains (sequential, parallel, conditional).
 */
@Component(id = "agent-chain-workflow")
public class AgentChainWorkflow extends Workflow<AgentChainWorkflow.State> {

    public record State(
        String executionId,
        String chainId,
        AgentChainConfig chainConfig,
        Map<String, Object> context,
        int currentStepIndex,
        String status,
        String errorMessage
    ) {
        public State withStepIndex(int index) {
            return new State(executionId, chainId, chainConfig, context, index, status, errorMessage);
        }

        public State withContext(Map<String, Object> newContext) {
            return new State(executionId, chainId, chainConfig, newContext, currentStepIndex, status, errorMessage);
        }

        public State withStatus(String newStatus) {
            return new State(executionId, chainId, chainConfig, context, currentStepIndex, newStatus, errorMessage);
        }

        public State withError(String error) {
            return new State(executionId, chainId, chainConfig, context, currentStepIndex, "FAILED", error);
        }

        public boolean hasMoreSteps() {
            return currentStepIndex < chainConfig.getStepCount();
        }

        public AgentChainConfig.ChainStep getCurrentStep() {
            return chainConfig.getStepAt(currentStepIndex);
        }
    }

    private final ComponentClient componentClient;

    public AgentChainWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
            .stepTimeout(AgentChainWorkflow::executeStepStep, ofSeconds(60))
            .stepTimeout(AgentChainWorkflow::executeParallelStep, ofSeconds(120))
            .defaultStepRecovery(RecoverStrategy.maxRetries(1)
                .failoverTo(AgentChainWorkflow::errorStep))
            .build();
    }

    /**
     * Start chain execution.
     */
    public Effect<Done> startChain(StartChainRequest request) {
        // Load chain configuration
        var chainConfig = componentClient
            .forKeyValueEntity(request.chainId())
            .method(AgentChainEntity::getChain)
            .invoke();

        // Initialize execution entity
        componentClient
            .forKeyValueEntity(request.executionId())
            .method(AgentChainExecutionEntity::startExecution)
            .invoke(new AgentChainExecutionEntity.StartExecution(
                request.executionId(),
                request.chainId(),
                request.initialContext()
            ));

        // Initialize workflow state
        var initialState = new State(
            request.executionId(),
            request.chainId(),
            chainConfig,
            new HashMap<>(request.initialContext()),
            0,
            "RUNNING",
            null
        );

        return effects()
            .updateState(initialState)
            .transitionTo(AgentChainWorkflow::routeExecutionStep)
            .thenReply(Done.getInstance());
    }

    /**
     * Route to appropriate execution strategy.
     */
    @StepName("route")
    private StepEffect routeExecutionStep() {
        return switch (currentState().chainConfig.executionMode()) {
            case SEQUENTIAL -> stepEffects()
                .thenTransitionTo(AgentChainWorkflow::executeStepStep);
            case PARALLEL -> stepEffects()
                .thenTransitionTo(AgentChainWorkflow::executeParallelStep);
            case CONDITIONAL -> stepEffects()
                .thenTransitionTo(AgentChainWorkflow::executeConditionalStep);
        };
    }

    /**
     * Execute single step in sequential chain.
     */
    @StepName("execute-step")
    private StepEffect executeStepStep() {
        if (!currentState().hasMoreSteps()) {
            return stepEffects()
                .thenTransitionTo(AgentChainWorkflow::finalizeStep);
        }

        var step = currentState().getCurrentStep();
        var startTime = Instant.now();

        // Check condition
        if (!step.shouldExecute(currentState().context)) {
            // Skip step, move to next
            return stepEffects()
                .updateState(currentState().withStepIndex(currentState().currentStepIndex + 1))
                .thenTransitionTo(AgentChainWorkflow::executeStepStep);
        }

        try {
            // Render input with context
            String input = step.renderInput(currentState().context);

            // Call agent dynamically
            Object rawOutput = componentClient
                .forAgent()
                .inSession(sessionId())
                .dynamicCall(step.agentId())
                .invoke(input);

            String output = String.valueOf(rawOutput);

            var endTime = Instant.now();

            // Record step result
            var stepResult = AgentChainExecution.StepResult.success(
                step.stepId(),
                step.agentId(),
                input,
                output,
                startTime,
                endTime
            );

            componentClient
                .forKeyValueEntity(currentState().executionId)
                .method(AgentChainExecutionEntity::addStepResult)
                .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

            // Update context with step output
            var newContext = new HashMap<>(currentState().context);
            newContext.put(step.outputKey(), output);

            // Move to next step
            return stepEffects()
                .updateState(currentState()
                    .withContext(newContext)
                    .withStepIndex(currentState().currentStepIndex + 1))
                .thenTransitionTo(AgentChainWorkflow::executeStepStep);

        } catch (Exception e) {
            // Record failure
            var stepResult = AgentChainExecution.StepResult.failure(
                step.stepId(),
                step.agentId(),
                step.renderInput(currentState().context),
                e.getMessage(),
                startTime,
                Instant.now()
            );

            componentClient
                .forKeyValueEntity(currentState().executionId)
                .method(AgentChainExecutionEntity::addStepResult)
                .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

            // Check if should continue on error
            if (currentState().chainConfig.continueOnError()) {
                return stepEffects()
                    .updateState(currentState().withStepIndex(currentState().currentStepIndex + 1))
                    .thenTransitionTo(AgentChainWorkflow::executeStepStep);
            } else {
                return stepEffects()
                    .updateState(currentState().withError(e.getMessage()))
                    .thenTransitionTo(AgentChainWorkflow::errorStep);
            }
        }
    }

    /**
     * Execute all steps in parallel.
     */
    @StepName("execute-parallel")
    private StepEffect executeParallelStep() {
        var steps = currentState().chainConfig.steps();
        var startTime = Instant.now();
        var outputs = new HashMap<String, String>();

        // Execute all steps in parallel
        var futures = steps.stream()
            .filter(step -> step.shouldExecute(currentState().context))
            .map(step -> CompletableFuture.supplyAsync(() -> {
                try {
                    String input = step.renderInput(currentState().context);
                    Object rawOutput = componentClient
                        .forAgent()
                        .inSession(sessionId())
                        .dynamicCall(step.agentId())
                        .invoke(input);

                    String output = String.valueOf(rawOutput);

                    var stepResult = AgentChainExecution.StepResult.success(
                        step.stepId(), step.agentId(), input, output, startTime, Instant.now()
                    );

                    componentClient
                        .forKeyValueEntity(currentState().executionId)
                        .method(AgentChainExecutionEntity::addStepResult)
                        .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

                    return Map.entry(step.outputKey(), output);
                } catch (Exception e) {
                    var stepResult = AgentChainExecution.StepResult.failure(
                        step.stepId(), step.agentId(),
                        step.renderInput(currentState().context),
                        e.getMessage(), startTime, Instant.now()
                    );

                    componentClient
                        .forKeyValueEntity(currentState().executionId)
                        .method(AgentChainExecutionEntity::addStepResult)
                        .invoke(new AgentChainExecutionEntity.AddStepResult(stepResult));

                    if (!currentState().chainConfig.continueOnError()) {
                        throw new RuntimeException(e);
                    }
                    return Map.entry(step.outputKey(), "ERROR: " + e.getMessage());
                }
            }))
            .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results
        futures.forEach(f -> {
            var entry = f.join();
            outputs.put(entry.getKey(), entry.getValue());
        });

        // Update context with all outputs
        var newContext = new HashMap<>(currentState().context);
        newContext.putAll(outputs);

        return stepEffects()
            .updateState(currentState().withContext(newContext))
            .thenTransitionTo(AgentChainWorkflow::finalizeStep);
    }

    /**
     * Execute conditional step chain.
     */
    @StepName("execute-conditional")
    private StepEffect executeConditionalStep() {
        // Similar to sequential but with condition checks
        return executeStepStep();
    }

    /**
     * Finalize execution.
     */
    @StepName("finalize")
    private StepEffect finalizeStep() {
        // Get final output from context
        var lastStep = currentState().chainConfig.getStepAt(
            currentState().chainConfig.getStepCount() - 1
        );

        String finalOutput = lastStep != null
            ? (String) currentState().context.get(lastStep.outputKey())
            : "No output";

        // Mark execution as complete
        componentClient
            .forKeyValueEntity(currentState().executionId)
            .method(AgentChainExecutionEntity::completeExecution)
            .invoke(new AgentChainExecutionEntity.CompleteExecution(finalOutput));

        return stepEffects()
            .updateState(currentState().withStatus("COMPLETED"))
            .thenEnd();
    }

    /**
     * Handle execution error.
     */
    @StepName("error")
    private StepEffect errorStep() {
        String errorMessage = currentState().errorMessage != null
            ? currentState().errorMessage
            : "Chain execution failed";

        componentClient
            .forKeyValueEntity(currentState().executionId)
            .method(AgentChainExecutionEntity::failExecution)
            .invoke(new AgentChainExecutionEntity.FailExecution(errorMessage));

        return stepEffects().thenEnd();
    }

    /**
     * Get execution result.
     */
    public Effect<AgentChainExecution> getResult() {
        if (currentState() == null) {
            return effects().error("Workflow not started");
        }

        var execution = componentClient
            .forKeyValueEntity(currentState().executionId)
            .method(AgentChainExecutionEntity::getExecution)
            .invoke();

        return effects().reply(execution);
    }

    private String sessionId() {
        return commandContext().workflowId();
    }

    // Request record

    public record StartChainRequest(
        String executionId,
        String chainId,
        Map<String, Object> initialContext
    ) {}
}
