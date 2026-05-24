package com.example.payment.agents.api;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.agents.domain.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentOperationsEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void shouldInitializeAndGetHealth() {
        var agentId = "agent-test-1";

        // Initialize health
        var initResponse = httpClient
            .POST("/agent-ops/health/" + agentId + "/initialize")
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(initResponse.status().isSuccess()).isTrue();

        // Get health status
        var healthResponse = httpClient
            .GET("/agent-ops/health/" + agentId)
            .responseBodyAs(AgentHealthStatus.class)
            .invoke();

        assertThat(healthResponse.status().isSuccess()).isTrue();
        assertThat(healthResponse.body().agentId()).isEqualTo(agentId);
        assertThat(healthResponse.body().state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
    }

    @Test
    public void shouldRecordHealthCheck() {
        var agentId = "agent-test-2";

        httpClient.POST("/agent-ops/health/" + agentId + "/initialize")
            .responseBodyAs(akka.Done.class)
            .invoke();

        var checkRequest = new AgentOperationsEndpoint.HealthCheckRequest(
            true,
            150L,
            "OK"
        );

        var response = httpClient
            .POST("/agent-ops/health/" + agentId + "/check")
            .withRequestBody(checkRequest)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldCreateAndGetAlert() {
        var alertId = "alert-test-1";

        var condition = new AgentOperationsEndpoint.AlertConditionRequest(
            "error_rate",
            AlertCondition.ConditionOperator.GREATER_THAN,
            0.3,
            5
        );

        var request = new AgentOperationsEndpoint.CreateAlertRequest(
            alertId,
            "High Error Rate Alert",
            "Alert when error rate exceeds 30%",
            Alert.AlertSeverity.WARNING,
            Alert.AlertType.HIGH_ERROR_RATE,
            condition,
            List.of(Alert.NotificationChannel.EMAIL),
            true
        );

        var createResponse = httpClient
            .POST("/agent-ops/alerts")
            .withRequestBody(request)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();

        // Get alert
        var getResponse = httpClient
            .GET("/agent-ops/alerts/" + alertId)
            .responseBodyAs(Alert.class)
            .invoke();

        assertThat(getResponse.status().isSuccess()).isTrue();
        assertThat(getResponse.body().alertId()).isEqualTo(alertId);
    }

    @Test
    public void shouldCreateAndRecordCost() {
        var entityId = "cost-test-1";

        var request = new AgentOperationsEndpoint.RecordCostRequest(
            "agent-1",
            "session-123",
            1000,
            500,
            2500L,
            "gpt-4o"
        );

        var response = httpClient
            .POST("/agent-ops/costs/" + entityId + "/record")
            .withRequestBody(request)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(response.status().isSuccess()).isTrue();
    }

    @Test
    public void shouldCreateAndManageBudget() {
        var budgetId = "budget-test-1";

        var request = new AgentOperationsEndpoint.CreateBudgetRequest(
            budgetId,
            "agent-1",
            CostBudget.BudgetPeriod.DAILY,
            100.0,
            CostBudget.BudgetAction.ALERT,
            0.8
        );

        var createResponse = httpClient
            .POST("/agent-ops/budgets")
            .withRequestBody(request)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();

        // Record spending
        var spendRequest = new AgentOperationsEndpoint.RecordSpendRequest(25.0);

        var spendResponse = httpClient
            .POST("/agent-ops/budgets/" + budgetId + "/spend")
            .withRequestBody(spendRequest)
            .responseBodyAs(com.example.payment.application.CostBudgetEntity.BudgetCheckResult.class)
            .invoke();

        assertThat(spendResponse.status().isSuccess()).isTrue();
        assertThat(spendResponse.body().allowed()).isTrue();
    }

    @Test
    public void shouldCreateAndUseCircuitBreaker() {
        var agentId = "agent-test-cb-1";

        var request = new AgentOperationsEndpoint.CreateCircuitBreakerRequest(
            agentId,
            3,
            2,
            5000
        );

        var createResponse = httpClient
            .POST("/agent-ops/circuit-breakers")
            .withRequestBody(request)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();

        // Check if request allowed
        var checkResponse = httpClient
            .GET("/agent-ops/circuit-breakers/" + agentId + "/check")
            .responseBodyAs(com.example.payment.agents.application.CircuitBreakerEntity.RequestAllowed.class)
            .invoke();

        assertThat(checkResponse.status().isSuccess()).isTrue();
        assertThat(checkResponse.body().allowed()).isTrue();
    }

    @Test
    public void shouldCreateAndCheckRateLimit() {
        var agentId = "agent-test-rl-1";

        var request = new AgentOperationsEndpoint.CreateRateLimitRequest(
            agentId,
            10,
            100,
            1000,
            RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
        );

        var createResponse = httpClient
            .POST("/agent-ops/rate-limits")
            .withRequestBody(request)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(createResponse.status().isSuccess()).isTrue();

        // Check rate limit
        var checkResponse = httpClient
            .POST("/agent-ops/rate-limits/" + agentId + "/check")
            .responseBodyAs(com.example.payment.agents.application.RateLimitEntity.RateLimitCheck.class)
            .invoke();

        assertThat(checkResponse.status().isSuccess()).isTrue();
        assertThat(checkResponse.body().allowed()).isTrue();
    }

    @Test
    public void shouldCreateCacheConfigAndStoreEntry() {
        var agentId = "agent-test-cache-1";
        var cacheKey = "cache-key-test-1";

        var configRequest = new AgentOperationsEndpoint.CreateCacheConfigRequest(
            agentId,
            3600L,
            1000,
            CacheConfig.CacheStrategy.LRU
        );

        var configResponse = httpClient
            .POST("/agent-ops/cache/config")
            .withRequestBody(configRequest)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(configResponse.status().isSuccess()).isTrue();

        // Store cache entry
        var storeRequest = new AgentOperationsEndpoint.StoreCacheRequest(
            agentId,
            "request-hash-123",
            "cached response",
            3600L
        );

        var storeResponse = httpClient
            .POST("/agent-ops/cache/" + cacheKey + "/store")
            .withRequestBody(storeRequest)
            .responseBodyAs(akka.Done.class)
            .invoke();

        assertThat(storeResponse.status().isSuccess()).isTrue();

        // Get cache entry
        var getResponse = httpClient
            .GET("/agent-ops/cache/" + cacheKey)
            .responseBodyAs(com.example.payment.agents.application.CacheEntryEntity.CacheHit.class)
            .invoke();

        assertThat(getResponse.status().isSuccess()).isTrue();
        assertThat(getResponse.body().hit()).isTrue();
    }
}
