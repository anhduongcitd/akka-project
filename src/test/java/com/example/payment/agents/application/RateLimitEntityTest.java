package com.example.payment.agents.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.agents.domain.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitEntityTest {

    @Test
    public void shouldCreateRateLimit() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        var result = testKit
            .method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1",
                100,
                1000,
                10000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().config().requestsPerMinute()).isEqualTo(100);
    }

    @Test
    public void shouldAllowRequestUnderLimit() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 10, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        var result = testKit.method(RateLimitEntity::checkLimit).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isTrue();
        assertThat(result.getReply().currentRequests()).isEqualTo(1);
    }

    @Test
    public void shouldBlockRequestOverLimit() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 3, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        // Make 3 requests (at limit)
        testKit.method(RateLimitEntity::checkLimit).invoke();
        testKit.method(RateLimitEntity::checkLimit).invoke();
        testKit.method(RateLimitEntity::checkLimit).invoke();

        // 4th request should be blocked
        var result = testKit.method(RateLimitEntity::checkLimit).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().allowed()).isFalse();
        assertThat(result.getReply().message()).contains("Rate limit exceeded");
    }

    @Test
    public void shouldUpdateLimits() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 10, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        var result = testKit
            .method(RateLimitEntity::updateLimits)
            .invoke(new RateLimitEntity.UpdateLimits(50, 500, 5000));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().config().requestsPerMinute()).isEqualTo(50);
    }

    @Test
    public void shouldEnableAndDisable() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 10, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        testKit.method(RateLimitEntity::disable).invoke();
        assertThat(testKit.getState().config().enabled()).isFalse();

        testKit.method(RateLimitEntity::enable).invoke();
        assertThat(testKit.getState().config().enabled()).isTrue();
    }

    @Test
    public void shouldResetState() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 10, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        testKit.method(RateLimitEntity::checkLimit).invoke();
        testKit.method(RateLimitEntity::checkLimit).invoke();

        var result = testKit.method(RateLimitEntity::reset).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState().state().currentMinuteRequests()).isEqualTo(0);
    }

    @Test
    public void shouldGetInfo() {
        var testKit = KeyValueEntityTestKit.of("agent-1", RateLimitEntity::new);

        testKit.method(RateLimitEntity::createRateLimit)
            .invoke(new RateLimitEntity.CreateRateLimit(
                "agent-1", 10, 100, 1000,
                RateLimitConfig.RateLimitStrategy.FIXED_WINDOW
            ));

        var result = testKit.method(RateLimitEntity::getInfo).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isNotNull();
    }
}
