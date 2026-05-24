package com.example.payment.agents.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentHealthStatusTest {

    @Test
    public void shouldCreateHealthyStatus() {
        var status = AgentHealthStatus.create("agent-1");

        assertThat(status.agentId()).isEqualTo("agent-1");
        assertThat(status.state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
        assertThat(status.errorRate()).isEqualTo(0.0);
        assertThat(status.availability()).isEqualTo(100.0);
    }

    @Test
    public void shouldRecordSuccessfulCheck() {
        var status = AgentHealthStatus.create("agent-1");
        var updated = status.recordSuccess(150);

        assertThat(updated.totalChecks()).isEqualTo(1);
        assertThat(updated.successfulChecks()).isEqualTo(1);
        assertThat(updated.failedChecks()).isEqualTo(0);
        assertThat(updated.errorRate()).isEqualTo(0.0);
        assertThat(updated.state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
    }

    @Test
    public void shouldRecordFailedCheck() {
        var status = AgentHealthStatus.create("agent-1");
        var updated = status.recordFailure(150);

        assertThat(updated.totalChecks()).isEqualTo(1);
        assertThat(updated.successfulChecks()).isEqualTo(0);
        assertThat(updated.failedChecks()).isEqualTo(1);
        assertThat(updated.errorRate()).isEqualTo(100.0);
        assertThat(updated.state()).isEqualTo(AgentHealthStatus.HealthState.UNHEALTHY);
    }

    @Test
    public void shouldTransitionToDegradedState() {
        var status = AgentHealthStatus.create("agent-1");

        // 3 successes, 2 failures = 40% error rate (DEGRADED threshold is 30%)
        var updated = status
            .recordSuccess(100)
            .recordSuccess(100)
            .recordSuccess(100)
            .recordFailure(100)
            .recordFailure(100);

        assertThat(updated.errorRate()).isEqualTo(40.0);
        assertThat(updated.state()).isEqualTo(AgentHealthStatus.HealthState.DEGRADED);
    }

    @Test
    public void shouldCalculatePercentiles() {
        var latencies = Arrays.asList(100L, 150L, 200L, 250L, 300L, 350L, 400L, 450L, 500L, 1000L);
        var percentiles = AgentHealthStatus.calculatePercentiles(latencies);

        assertThat(percentiles.p50()).isGreaterThanOrEqualTo(250.0);
        assertThat(percentiles.p95()).isGreaterThanOrEqualTo(900.0);
        assertThat(percentiles.p99()).isGreaterThanOrEqualTo(950.0);
    }

    @Test
    public void shouldUpdateLatencies() {
        var status = AgentHealthStatus.create("agent-1");
        var updated = status.updateLatencies(250.0, 800.0, 950.0);

        assertThat(updated.latencyP50Ms()).isEqualTo(250.0);
        assertThat(updated.latencyP95Ms()).isEqualTo(800.0);
        assertThat(updated.latencyP99Ms()).isEqualTo(950.0);
    }

    @Test
    public void shouldResetHealthStatus() {
        var status = AgentHealthStatus.create("agent-1")
            .recordSuccess(100)
            .recordFailure(100);

        var reset = status.reset();

        assertThat(reset.totalChecks()).isEqualTo(0);
        assertThat(reset.successfulChecks()).isEqualTo(0);
        assertThat(reset.failedChecks()).isEqualTo(0);
        assertThat(reset.errorRate()).isEqualTo(0.0);
        assertThat(reset.state()).isEqualTo(AgentHealthStatus.HealthState.HEALTHY);
    }

    @Test
    public void shouldCalculateAvailability() {
        var status = AgentHealthStatus.create("agent-1")
            .recordSuccess(100)
            .recordSuccess(100)
            .recordSuccess(100)
            .recordFailure(100);

        assertThat(status.availability()).isEqualTo(75.0);
    }
}
