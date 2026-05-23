package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.JudgeAgent;
import com.example.payment.agents.domain.EvaluationCriteria;
import com.example.payment.agents.domain.EvaluationResult;
import com.example.payment.agents.domain.TestCase;
import com.example.payment.application.AgentEvaluationEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agent Evaluation Endpoint - API for LLM-as-Judge evaluation system.
 *
 * Endpoints:
 * - POST /evaluation/run - Run evaluation on agent response
 * - POST /evaluation/test-case - Run predefined test case
 * - GET /evaluation/history/{agentId} - Get evaluation history
 * - GET /evaluation/stats/{agentId} - Get evaluation statistics
 */
@HttpEndpoint("/evaluation")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentEvaluationEndpoint {

    private final ComponentClient componentClient;

    public AgentEvaluationEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record RunEvaluationRequest(
        String targetAgentId,
        String testCaseId,
        String query,
        String response,
        String expectedBehavior,
        String successCriteria
    ) {}

    public record TestCaseEvaluationRequest(
        String targetAgentId,
        String testCaseType,  // "payment-status", "refund-eligibility", "payment-failure", "fraud-concern"
        String agentResponse
    ) {}

    public record EvaluationResponse(
        String evaluationId,
        String targetAgentId,
        EvaluationCriteria scores,
        String reasoning,
        boolean passed,
        String qualityRating,
        double overallScore,
        String summary
    ) {}

    public record HistoryResponse(
        String agentId,
        List<EvaluationSummary> evaluations,
        Statistics stats
    ) {}

    public record EvaluationSummary(
        String evaluationId,
        String testCaseId,
        double overallScore,
        boolean passed,
        String qualityRating,
        String timestamp
    ) {}

    public record Statistics(
        int totalEvaluations,
        int passedEvaluations,
        int failedEvaluations,
        double passRate,
        double averageScore,
        String lastEvaluated
    ) {}

    /**
     * Run evaluation on an agent response.
     */
    @Post("/run")
    public EvaluationResponse runEvaluation(RunEvaluationRequest request) {
        // Call JudgeAgent to evaluate
        var judgeRequest = new JudgeAgent.EvaluationRequest(
            request.targetAgentId(),
            request.testCaseId(),
            request.query(),
            request.response(),
            request.expectedBehavior(),
            request.successCriteria()
        );

        var judgeResponse = componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(JudgeAgent::evaluate)
            .invoke(judgeRequest);

        // Create evaluation result
        String evaluationId = "eval_" + UUID.randomUUID().toString().substring(0, 8);
        var result = new EvaluationResult(
            evaluationId,
            request.targetAgentId(),
            request.testCaseId(),
            request.query(),
            request.response(),
            judgeResponse.scores(),
            judgeResponse.reasoning(),
            judgeResponse.passed(),
            Instant.now()
        );

        // Store result
        componentClient
            .forKeyValueEntity(request.targetAgentId())
            .method(AgentEvaluationEntity::recordEvaluation)
            .invoke(new AgentEvaluationEntity.RecordEvaluation(result));

        // Return response
        return toApi(result);
    }

    /**
     * Run evaluation using predefined test case.
     */
    @Post("/test-case")
    public EvaluationResponse runTestCase(TestCaseEvaluationRequest request) {
        // Get test case
        TestCase testCase = getTestCase(request.testCaseType());

        // Run evaluation
        var evalRequest = new RunEvaluationRequest(
            request.targetAgentId(),
            testCase.testCaseId(),
            testCase.query(),
            request.agentResponse(),
            testCase.expectedBehavior(),
            testCase.successCriteria()
        );

        return runEvaluation(evalRequest);
    }

    /**
     * Get evaluation history for an agent.
     */
    @Get("/history/{agentId}")
    public HistoryResponse getHistory(String agentId) {
        var history = componentClient
            .forKeyValueEntity(agentId)
            .method(AgentEvaluationEntity::getHistory)
            .invoke(new AgentEvaluationEntity.GetHistory(20)); // Last 20 evaluations

        var evaluations = history.evaluations().stream()
            .map(this::toSummary)
            .toList();

        var stats = new Statistics(
            history.totalEvaluations(),
            (int) (history.totalEvaluations() * history.passRate()),
            (int) (history.totalEvaluations() * (1 - history.passRate())),
            Math.round(history.passRate() * 100.0) / 100.0,
            Math.round(history.averageScore() * 100.0) / 100.0,
            evaluations.isEmpty() ? null : evaluations.get(evaluations.size() - 1).timestamp()
        );

        return new HistoryResponse(agentId, evaluations, stats);
    }

    /**
     * Get evaluation statistics for an agent.
     */
    @Get("/stats/{agentId}")
    public Statistics getStats(String agentId) {
        var state = componentClient
            .forKeyValueEntity(agentId)
            .method(AgentEvaluationEntity::getStats)
            .invoke();

        return new Statistics(
            state.totalEvaluations(),
            state.passedEvaluations(),
            state.failedEvaluations(),
            Math.round(state.getPassRate() * 100.0) / 100.0,
            Math.round(state.averageScore() * 100.0) / 100.0,
            state.lastEvaluated() != null ? state.lastEvaluated().toString() : null
        );
    }

    /**
     * Convert EvaluationResult to API response.
     */
    private EvaluationResponse toApi(EvaluationResult result) {
        return new EvaluationResponse(
            result.evaluationId(),
            result.targetAgentId(),
            result.scores(),
            result.reasoning(),
            result.passed(),
            result.getQualityRating(),
            Math.round(result.getOverallScore() * 100.0) / 100.0,
            result.getSummary()
        );
    }

    /**
     * Convert EvaluationResult to summary.
     */
    private EvaluationSummary toSummary(EvaluationResult result) {
        return new EvaluationSummary(
            result.evaluationId(),
            result.testCaseId(),
            Math.round(result.getOverallScore() * 100.0) / 100.0,
            result.passed(),
            result.getQualityRating(),
            result.timestamp().toString()
        );
    }

    /**
     * Get predefined test case by type.
     */
    private TestCase getTestCase(String type) {
        String testId = "test_" + type + "_" + System.currentTimeMillis();

        return switch (type) {
            case "payment-status" -> TestCase.paymentStatusCheck(testId);
            case "refund-eligibility" -> TestCase.refundEligibility(testId);
            case "payment-failure" -> TestCase.paymentFailureAnalysis(testId);
            case "fraud-concern" -> TestCase.fraudConcern(testId);
            default -> throw new IllegalArgumentException("Unknown test case type: " + type);
        };
    }
}
