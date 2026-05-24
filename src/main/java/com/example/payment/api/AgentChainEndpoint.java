package com.example.payment.api;

import akka.Done;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.payment.agents.domain.AgentChainConfig;
import com.example.payment.agents.domain.AgentChainExecution;
import com.example.payment.application.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Endpoint for Agent Chaining.
 */
@HttpEndpoint("/agent-chains")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentChainEndpoint {

    private final ComponentClient componentClient;

    public AgentChainEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // ===== Chain Configuration Management =====

    /**
     * Create a new agent chain.
     */
    @Post("/")
    public akka.http.javadsl.model.HttpResponse createChain(CreateChainRequest request) {
        try {
            var config = new AgentChainConfig(
                request.chainId(),
                request.name(),
                request.description(),
                request.executionMode(),
                request.steps(),
                request.globalContext(),
                request.continueOnError()
            );

            if (!config.isValid()) {
                return HttpResponses.badRequest("Invalid chain configuration");
            }

            componentClient
                .forKeyValueEntity(request.chainId())
                .method(AgentChainEntity::createChain)
                .invoke(new AgentChainEntity.CreateChain(request.chainId(), config));

            return HttpResponses.created();
        } catch (Exception e) {
            return HttpResponses.badRequest(e.getMessage());
        }
    }

    /**
     * Get chain configuration.
     */
    @Get("/{chainId}")
    public ChainResponse getChain(String chainId) {
        var config = componentClient
            .forKeyValueEntity(chainId)
            .method(AgentChainEntity::getChain)
            .invoke();

        return toResponse(config);
    }

    /**
     * Update chain configuration.
     */
    @Put("/{chainId}")
    public akka.http.javadsl.model.HttpResponse updateChain(String chainId, UpdateChainRequest request) {
        try {
            var config = new AgentChainConfig(
                chainId,
                request.name(),
                request.description(),
                request.executionMode(),
                request.steps(),
                request.globalContext(),
                request.continueOnError()
            );

            if (!config.isValid()) {
                return HttpResponses.badRequest("Invalid chain configuration");
            }

            componentClient
                .forKeyValueEntity(chainId)
                .method(AgentChainEntity::updateChain)
                .invoke(new AgentChainEntity.UpdateChain(config));

            return HttpResponses.ok();
        } catch (Exception e) {
            return HttpResponses.badRequest(e.getMessage());
        }
    }

    /**
     * Delete chain.
     */
    @Delete("/{chainId}")
    public Done deleteChain(String chainId) {
        return componentClient
            .forKeyValueEntity(chainId)
            .method(AgentChainEntity::deleteChain)
            .invoke();
    }

    /**
     * List all chains.
     */
    @Get("/")
    public ChainListResponse listChains() {
        var result = componentClient.forView()
            .method(AgentChainView::getAllChains)
            .invoke();

        return new ChainListResponse(result.chains());
    }

    /**
     * Search chains by name.
     */
    @Get("/search")
    public ChainListResponse searchChains(String query) {
        var result = componentClient.forView()
            .method(AgentChainView::searchChains)
            .invoke("%" + query + "%");

        return new ChainListResponse(result.chains());
    }

    // ===== Chain Execution =====

    /**
     * Execute a chain.
     */
    @Post("/{chainId}/execute")
    public ExecutionResponse executeChain(String chainId, ExecuteChainRequest request) {
        String executionId = UUID.randomUUID().toString();

        componentClient
            .forWorkflow(executionId)
            .method(AgentChainWorkflow::startChain)
            .invoke(new AgentChainWorkflow.StartChainRequest(
                executionId,
                chainId,
                request.initialContext()
            ));

        return new ExecutionResponse(executionId, chainId, "RUNNING", null);
    }

    /**
     * Get execution status.
     */
    @Get("/executions/{executionId}")
    public ExecutionDetailResponse getExecution(String executionId) {
        var execution = componentClient
            .forKeyValueEntity(executionId)
            .method(AgentChainExecutionEntity::getExecution)
            .invoke();

        return toDetailResponse(execution);
    }

    /**
     * Cancel execution.
     */
    @Post("/executions/{executionId}/cancel")
    public Done cancelExecution(String executionId) {
        return componentClient
            .forKeyValueEntity(executionId)
            .method(AgentChainExecutionEntity::cancelExecution)
            .invoke();
    }

    /**
     * Get executions by chain.
     */
    @Get("/{chainId}/executions")
    public ExecutionListResponse getChainExecutions(String chainId) {
        var result = componentClient.forView()
            .method(AgentChainExecutionView::getByChainId)
            .invoke(chainId);

        return new ExecutionListResponse(result.executions());
    }

    /**
     * Get recent executions.
     */
    @Get("/executions")
    public ExecutionListResponse getRecentExecutions() {
        var result = componentClient.forView()
            .method(AgentChainExecutionView::getRecent)
            .invoke(50);

        return new ExecutionListResponse(result.executions());
    }

    /**
     * Get running executions.
     */
    @Get("/executions/running")
    public ExecutionListResponse getRunningExecutions() {
        var result = componentClient.forView()
            .method(AgentChainExecutionView::getRunningExecutions)
            .invoke();

        return new ExecutionListResponse(result.executions());
    }

    /**
     * Get failed executions.
     */
    @Get("/executions/failed")
    public ExecutionListResponse getFailedExecutions() {
        var result = componentClient.forView()
            .method(AgentChainExecutionView::getFailedExecutions)
            .invoke();

        return new ExecutionListResponse(result.executions());
    }

    // ===== Helper Methods =====

    private ChainResponse toResponse(AgentChainConfig config) {
        return new ChainResponse(
            config.chainId(),
            config.name(),
            config.description(),
            config.executionMode().name(),
            config.getStepCount(),
            config.steps().stream()
                .map(s -> new StepResponse(s.stepId(), s.agentId(), s.outputKey()))
                .toList(),
            config.continueOnError()
        );
    }

    private ExecutionDetailResponse toDetailResponse(AgentChainExecution execution) {
        return new ExecutionDetailResponse(
            execution.executionId(),
            execution.chainId(),
            execution.status().name(),
            execution.stepResults().stream()
                .map(r -> new StepResultResponse(
                    r.stepId(),
                    r.agentId(),
                    r.status().name(),
                    r.output(),
                    r.getDurationMs(),
                    r.errorMessage()
                ))
                .toList(),
            execution.finalOutput(),
            execution.getDurationMs(),
            execution.startedAt().toString(),
            execution.completedAt() != null ? execution.completedAt().toString() : null,
            execution.errorMessage()
        );
    }

    // ===== Request/Response Records =====

    public record CreateChainRequest(
        String chainId,
        String name,
        String description,
        AgentChainConfig.ExecutionMode executionMode,
        List<AgentChainConfig.ChainStep> steps,
        Map<String, String> globalContext,
        boolean continueOnError
    ) {}

    public record UpdateChainRequest(
        String name,
        String description,
        AgentChainConfig.ExecutionMode executionMode,
        List<AgentChainConfig.ChainStep> steps,
        Map<String, String> globalContext,
        boolean continueOnError
    ) {}

    public record ExecuteChainRequest(
        Map<String, Object> initialContext
    ) {}

    public record ChainResponse(
        String chainId,
        String name,
        String description,
        String executionMode,
        int stepCount,
        List<StepResponse> steps,
        boolean continueOnError
    ) {}

    public record StepResponse(
        String stepId,
        String agentId,
        String outputKey
    ) {}

    public record ChainListResponse(
        List<AgentChainView.ChainEntry> chains
    ) {}

    public record ExecutionResponse(
        String executionId,
        String chainId,
        String status,
        String message
    ) {}

    public record ExecutionDetailResponse(
        String executionId,
        String chainId,
        String status,
        List<StepResultResponse> stepResults,
        String finalOutput,
        long durationMs,
        String startedAt,
        String completedAt,
        String errorMessage
    ) {}

    public record StepResultResponse(
        String stepId,
        String agentId,
        String status,
        String output,
        long durationMs,
        String errorMessage
    ) {}

    public record ExecutionListResponse(
        List<AgentChainExecutionView.ExecutionEntry> executions
    ) {}
}
