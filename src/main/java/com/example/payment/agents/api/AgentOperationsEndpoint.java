package com.example.payment.agents.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.application.*;
import com.example.payment.agents.domain.*;

import java.util.List;

/**
 * HTTP Endpoint for agent operations and monitoring.
 */
@HttpEndpoint("/agent-ops")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentOperationsEndpoint {

    private final ComponentClient componentClient;

    public AgentOperationsEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // ========== Health Monitoring ==========

    @Post("/health/{agentId}/initialize")
    public Done initializeHealth(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.application.AgentHealthEntity::initialize)
            .invoke(new com.example.payment.application.AgentHealthEntity.Initialize(agentId));
    }

    @Post("/health/{agentId}/check")
    public Done recordHealthCheck(String agentId, HealthCheckRequest request) {
        var result = new HealthCheckResult(
            agentId,
            request.success(),
            request.durationMs(),
            request.message(),
            java.time.Instant.now()
        );

        return componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.application.AgentHealthEntity::recordHealthCheck)
            .invoke(new com.example.payment.application.AgentHealthEntity.RecordHealthCheck(result));
    }

    @Get("/health/{agentId}")
    public AgentHealthStatus getHealth(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.application.AgentHealthEntity::getHealth)
            .invoke();
    }

    @Get("/health")
    public com.example.payment.application.AgentHealthView.HealthList getAllHealth() {
        return componentClient
            .forView()
            .method(com.example.payment.application.AgentHealthView::getAllHealth)
            .invoke();
    }

    // ========== Alerts ==========

    @Post("/alerts")
    public Done createAlert(CreateAlertRequest request) {
        var condition = new AlertCondition(
            request.condition().metric(),
            request.condition().operator(),
            request.condition().threshold(),
            request.condition().durationMinutes()
        );

        var command = new com.example.payment.application.AlertEntity.CreateAlert(
            request.alertId(),
            request.name(),
            request.description(),
            request.severity(),
            request.type(),
            condition,
            request.channels()
        );

        return componentClient
            .forKeyValueEntity(request.alertId())
            .method(com.example.payment.application.AlertEntity::createAlert)
            .invoke(command);
    }

    @Get("/alerts/{alertId}")
    public Alert getAlert(String alertId) {
        return componentClient
            .forKeyValueEntity(alertId)
            .method(com.example.payment.application.AlertEntity::getAlert)
            .invoke();
    }

    @Put("/alerts/{alertId}/enable")
    public Done enableAlert(String alertId) {
        return componentClient
            .forKeyValueEntity(alertId)
            .method(com.example.payment.application.AlertEntity::enableAlert)
            .invoke();
    }

    @Put("/alerts/{alertId}/disable")
    public Done disableAlert(String alertId) {
        return componentClient
            .forKeyValueEntity(alertId)
            .method(com.example.payment.application.AlertEntity::disableAlert)
            .invoke();
    }

    // ========== Cost Management ==========

    @Post("/costs/{entityId}/record")
    public Done recordCost(String entityId, RecordCostRequest request) {
        return componentClient
            .forKeyValueEntity(entityId)
            .method(com.example.payment.application.AgentCostEntity::recordCost)
            .invoke(new com.example.payment.application.AgentCostEntity.RecordCost(
                request.agentId(),
                request.sessionId(),
                request.inputTokens(),
                request.outputTokens(),
                request.durationMs(),
                request.modelId()
            ));
    }

    @Get("/costs/agent/{agentId}")
    public com.example.payment.application.CostAnalyticsView.CostList getCostsByAgent(String agentId) {
        return componentClient
            .forView()
            .method(com.example.payment.application.CostAnalyticsView::getByAgent)
            .invoke(agentId);
    }

    @Get("/costs/recent")
    public com.example.payment.application.CostAnalyticsView.CostList getRecentCosts(int limit) {
        return componentClient
            .forView()
            .method(com.example.payment.application.CostAnalyticsView::getRecent)
            .invoke(limit);
    }

    // ========== Budgets ==========

    @Post("/budgets")
    public Done createBudget(CreateBudgetRequest request) {
        return componentClient
            .forKeyValueEntity(request.budgetId())
            .method(com.example.payment.application.CostBudgetEntity::createBudget)
            .invoke(new com.example.payment.application.CostBudgetEntity.CreateBudget(
                request.budgetId(),
                request.agentId(),
                request.period(),
                request.limitUsd(),
                request.action(),
                request.alertThreshold()
            ));
    }

    @Post("/budgets/{budgetId}/spend")
    public com.example.payment.application.CostBudgetEntity.BudgetCheckResult recordSpend(
            String budgetId, RecordSpendRequest request) {
        return componentClient
            .forKeyValueEntity(budgetId)
            .method(com.example.payment.application.CostBudgetEntity::recordSpend)
            .invoke(new com.example.payment.application.CostBudgetEntity.RecordSpend(request.costUsd()));
    }

    @Get("/budgets/{budgetId}")
    public CostBudget getBudget(String budgetId) {
        return componentClient
            .forKeyValueEntity(budgetId)
            .method(com.example.payment.application.CostBudgetEntity::getBudget)
            .invoke();
    }

    @Get("/budgets/agent/{agentId}")
    public com.example.payment.agents.application.CostBudgetView.BudgetList getBudgetsByAgent(String agentId) {
        return componentClient
            .forView()
            .method(com.example.payment.agents.application.CostBudgetView::getByAgent)
            .invoke(agentId);
    }

    // ========== Circuit Breakers ==========

    @Post("/circuit-breakers")
    public Done createCircuitBreaker(CreateCircuitBreakerRequest request) {
        return componentClient
            .forKeyValueEntity(request.agentId())
            .method(CircuitBreakerEntity::createCircuitBreaker)
            .invoke(new CircuitBreakerEntity.CreateCircuitBreaker(
                request.agentId(),
                request.failureThreshold(),
                request.successThreshold(),
                request.timeoutMs()
            ));
    }

    @Post("/circuit-breakers/{agentId}/success")
    public CircuitBreakerConfig recordSuccess(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(CircuitBreakerEntity::recordSuccess)
            .invoke();
    }

    @Post("/circuit-breakers/{agentId}/failure")
    public CircuitBreakerConfig recordFailure(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(CircuitBreakerEntity::recordFailure)
            .invoke();
    }

    @Get("/circuit-breakers/{agentId}/check")
    public CircuitBreakerEntity.RequestAllowed checkCircuitBreaker(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(CircuitBreakerEntity::allowsRequest)
            .invoke();
    }

    // ========== Rate Limiting ==========

    @Post("/rate-limits")
    public Done createRateLimit(CreateRateLimitRequest request) {
        return componentClient
            .forKeyValueEntity(request.agentId())
            .method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                request.agentId(),
                request.requestsPerMinute(),
                request.requestsPerHour(),
                request.requestsPerDay(),
                request.strategy()
            ));
    }

    @Post("/rate-limits/{agentId}/check")
    public com.example.payment.agents.application.RateLimitEntity.RateLimitCheck checkRateLimit(String agentId) {
        return componentClient
            .forKeyValueEntity(agentId)
            .method(com.example.payment.agents.application.RateLimitEntity::checkLimit)
            .invoke();
    }

    // ========== Cache Management ==========

    @Post("/cache/config")
    public Done createCacheConfig(CreateCacheConfigRequest request) {
        return componentClient
            .forKeyValueEntity(request.agentId())
            .method(CacheConfigEntity::createConfig)
            .invoke(new CacheConfigEntity.CreateConfig(
                request.agentId(),
                request.ttlSeconds(),
                request.maxSize(),
                request.strategy()
            ));
    }

    @Post("/cache/{cacheKey}/store")
    public Done storeCache(String cacheKey, StoreCacheRequest request) {
        return componentClient
            .forKeyValueEntity(cacheKey)
            .method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                cacheKey,
                request.agentId(),
                request.requestHash(),
                request.response(),
                request.ttlSeconds()
            ));
    }

    @Get("/cache/{cacheKey}")
    public com.example.payment.agents.application.CacheEntryEntity.CacheHit getCache(String cacheKey) {
        return componentClient
            .forKeyValueEntity(cacheKey)
            .method(com.example.payment.agents.application.CacheEntryEntity::get)
            .invoke();
    }

    // Request/Response records

    public record HealthCheckRequest(
        boolean success,
        long durationMs,
        String message
    ) {}

    public record CreateAlertRequest(
        String alertId,
        String name,
        String description,
        Alert.AlertSeverity severity,
        Alert.AlertType type,
        AlertConditionRequest condition,
        List<Alert.NotificationChannel> channels,
        boolean enabled
    ) {}

    public record AlertConditionRequest(
        String metric,
        AlertCondition.ConditionOperator operator,
        double threshold,
        int durationMinutes
    ) {}

    public record RecordCostRequest(
        String agentId,
        String sessionId,
        int inputTokens,
        int outputTokens,
        long durationMs,
        String modelId
    ) {}

    public record CreateBudgetRequest(
        String budgetId,
        String agentId,
        CostBudget.BudgetPeriod period,
        double limitUsd,
        CostBudget.BudgetAction action,
        double alertThreshold
    ) {}

    public record RecordSpendRequest(double costUsd) {}

    public record CreateCircuitBreakerRequest(
        String agentId,
        int failureThreshold,
        int successThreshold,
        long timeoutMs
    ) {}

    public record CreateRateLimitRequest(
        String agentId,
        int requestsPerMinute,
        int requestsPerHour,
        int requestsPerDay,
        com.example.payment.agents.domain.RateLimitConfig.RateLimitStrategy strategy
    ) {}

    public record CreateCacheConfigRequest(
        String agentId,
        long ttlSeconds,
        int maxSize,
        com.example.payment.agents.domain.CacheConfig.CacheStrategy strategy
    ) {}

    public record StoreCacheRequest(
        String agentId,
        String requestHash,
        String response,
        long ttlSeconds
    ) {}
}
