package com.example.payment.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.CustomerSupportAgent;
import com.example.payment.agents.FraudAnalystAgent;
import com.example.payment.agents.PaymentAssistantAgent;

import java.util.UUID;

/**
 * Agent Endpoint - HTTP API for AI agent interactions.
 *
 * Provides:
 * - Customer support chat
 * - Fraud detection analysis
 * - Payment failure resolution
 *
 * All endpoints use agents with guardrails (PII detection, output validation, audit logging).
 */
@HttpEndpoint("/agents")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentEndpoint {

    private final ComponentClient componentClient;

    public AgentEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record SupportQueryRequest(
        String customerId,
        String query,
        String sessionId  // Optional: reuse session for conversation continuity
    ) {}

    public record SupportQueryResponse(
        String sessionId,
        String answer,
        String action,           // INFORM, REFUND_ELIGIBLE, ESCALATE
        String confidence,       // HIGH, MEDIUM, LOW
        String transactionId,
        RefundInfo refund
    ) {}

    public record RefundInfo(
        String amount,
        String reason,
        boolean autoApprove
    ) {}

    public record FraudAnalysisRequest(
        String customerId,
        String transactionId,
        String amount,
        String currency,
        String merchantReference
    ) {}

    public record FraudAnalysisResponse(
        String sessionId,
        boolean isSuspicious,
        String riskLevel,           // LOW, MEDIUM, HIGH, CRITICAL
        double confidenceScore,
        String[] riskFactors,
        String recommendation,      // APPROVE, REVIEW, DECLINE
        String reasoning
    ) {}

    public record FailureAnalysisRequest(
        String customerId,
        String transactionId
    ) {}

    public record FailureAnalysisResponse(
        String sessionId,
        String failureType,         // TRANSIENT, CARD_EXPIRED, INSUFFICIENT_FUNDS, GATEWAY_ERROR
        String severity,            // TEMPORARY, RECOVERABLE, TERMINAL
        RecoveryActionInfo[] actions,
        String customerMessage
    ) {}

    public record RecoveryActionInfo(
        String action,              // RETRY, CHANGE_CARD, CONTACT_BANK, UPDATE_CARD, DISPUTE
        String description,
        int priority
    ) {}

    /**
     * Customer support chat endpoint.
     * Use session ID to maintain conversation context across multiple queries.
     */
    @Post("/support/query")
    public SupportQueryResponse querySupportAgent(SupportQueryRequest request) {
        // Generate or reuse session ID
        String sessionId = request.sessionId() != null && !request.sessionId().isEmpty()
            ? request.sessionId()
            : UUID.randomUUID().toString();

        var supportRequest = new CustomerSupportAgent.SupportRequest(
            request.customerId(),
            request.query()
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(CustomerSupportAgent::handleQuery)
            .invoke(supportRequest);

        // Convert to API response
        RefundInfo refund = null;
        if (result.refund() != null) {
            refund = new RefundInfo(
                result.refund().amount(),
                result.refund().reason(),
                result.refund().autoApprove()
            );
        }

        return new SupportQueryResponse(
            sessionId,
            result.answer(),
            result.action(),
            result.confidence(),
            result.transactionId(),
            refund
        );
    }

    /**
     * Fraud detection analysis endpoint.
     * Analyzes transaction for fraud patterns beyond rule-based checks.
     */
    @Post("/fraud/analyze")
    public FraudAnalysisResponse analyzeFraud(FraudAnalysisRequest request) {
        String sessionId = UUID.randomUUID().toString();

        var fraudRequest = new FraudAnalystAgent.FraudCheckRequest(
            request.customerId(),
            request.transactionId(),
            request.amount(),
            request.currency(),
            request.merchantReference()
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(FraudAnalystAgent::analyzeTransaction)
            .invoke(fraudRequest);

        return new FraudAnalysisResponse(
            sessionId,
            result.isSuspicious(),
            result.riskLevel(),
            result.confidenceScore(),
            result.riskFactors().toArray(new String[0]),
            result.recommendation(),
            result.reasoning()
        );
    }

    /**
     * Payment failure analysis endpoint.
     * Provides intelligent recovery suggestions for failed payments.
     */
    @Post("/failures/analyze")
    public FailureAnalysisResponse analyzeFailure(FailureAnalysisRequest request) {
        String sessionId = UUID.randomUUID().toString();

        var failureRequest = new PaymentAssistantAgent.FailureRequest(
            request.transactionId(),
            request.customerId()
        );

        var result = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(PaymentAssistantAgent::analyzeFailure)
            .invoke(failureRequest);

        // Convert recovery actions
        var actions = result.actions().stream()
            .map(a -> new RecoveryActionInfo(a.action(), a.description(), a.priority()))
            .toArray(RecoveryActionInfo[]::new);

        return new FailureAnalysisResponse(
            sessionId,
            result.failureType(),
            result.severity(),
            actions,
            result.customerMessage()
        );
    }
}
