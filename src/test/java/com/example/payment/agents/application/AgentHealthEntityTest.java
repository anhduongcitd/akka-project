package com.example.payment.agents.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.AgentHealthStatus;
import com.example.payment.agents.domain.HealthCheckResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentHealthEntityTest {

    @Test
    public void shouldInitializeHealthStatus() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        var result = testKit
            .method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().agentId()).isEqualTo("agent-1");
        assertThat(testKit.getState().state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
    }

    @Test
    public void shouldRecordSuccessfulHealthCheck() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        testKit.method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        var healthCheck = new HealthCheckResult("agent-1", true, 150, "OK", Instant.now());
        var result = testKit
            .method(AgentHealthEntity::recordHealthCheck)
            .invoke(new AgentHealthEntity.RecordHealthCheck(healthCheck));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().totalChecks()).isEqualTo(1);
        assertThat(testKit.getState().successfulChecks()).isEqualTo(1);
    }

    @Test
    public void shouldRecordFailedHealthCheck() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        testKit.method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        var healthCheck = new HealthCheckResult("agent-1", false, 150, "Timeout", Instant.now());
        var result = testKit
            .method(AgentHealthEntity::recordHealthCheck)
            .invoke(new AgentHealthEntity.RecordHealthCheck(healthCheck));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().totalChecks()).isEqualTo(1);
        assertThat(testKit.getState().failedChecks()).isEqualTo(1);
        assertThat(testKit.getState().state()).isEqualTo(AgentHealthStatus.HealthState.UNHEALTHY);
    }

    @Test
    public void shouldGetHealthStatus() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        testKit.method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        var result = testKit.method(AgentHealthEntity::getHealth).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isNotNull();
        assertThat(result.getReply().agentId()).isEqualTo("agent-1");
    }

    @Test
    public void shouldResetHealthStatus() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        testKit.method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        var healthCheck = new HealthCheckResult("agent-1", false, 150, "Error", Instant.now());
        testKit.method(AgentHealthEntity::recordHealthCheck)
            .invoke(new AgentHealthEntity.RecordHealthCheck(healthCheck));

        var result = testKit.method(AgentHealthEntity::reset).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().totalChecks()).isEqualTo(0);
        assertThat(testKit.getState().state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
    }

    @Test
    public void shouldDeleteHealthStatus() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        testKit.method(AgentHealthEntity::initialize)
            .invoke(new AgentHealthEntity.Initialize("agent-1"));

        var result = testKit.method(AgentHealthEntity::deleteHealth).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void shouldErrorWhenNotInitialized() {
        var testKit = KeyValueEntityTestKit.of("agent-1", AgentHealthEntity::new);

        var healthCheck = new HealthCheckResult("agent-1", true, 150, "OK", Instant.now());
        var result = testKit
            .method(AgentHealthEntity::recordHealthCheck)
            .invoke(new AgentHealthEntity.RecordHealthCheck(healthCheck));

        assertThat(result.isError()).isTrue();
    }
}
