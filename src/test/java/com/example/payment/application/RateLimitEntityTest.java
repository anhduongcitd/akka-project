package com.example.payment.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.domain.RateLimitRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitEntityTest {

    @Test
    public void shouldAllowFirstRequest() {
        var testKit = KeyValueEntityTestKit.of("test-ip", RateLimitEntity::new);

        var request = new RateLimitEntity.CheckRateLimitRequest(
            "192.168.1.1",
            RateLimitRecord.RateLimitType.IP,
            100,
            1
        );

        var result = testKit.method(RateLimitEntity::checkAndRecord)
            .invoke(request);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("ALLOWED");
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().requestCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementCountOnSubsequentRequests() {
        var testKit = KeyValueEntityTestKit.of("test-customer", RateLimitEntity::new);

        var request = new RateLimitEntity.CheckRateLimitRequest(
            "cust_123",
            RateLimitRecord.RateLimitType.CUSTOMER,
            50,
            60
        );

        // First request
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);

        // Second request
        var result = testKit.method(RateLimitEntity::checkAndRecord)
            .invoke(request);

        assertThat(result.getReply()).isEqualTo("ALLOWED");
        assertThat(testKit.getState().requestCount()).isEqualTo(2);
    }

    @Test
    public void shouldExceedRateLimitAfterMaxRequests() {
        var testKit = KeyValueEntityTestKit.of("test-ip-limit", RateLimitEntity::new);

        var request = new RateLimitEntity.CheckRateLimitRequest(
            "192.168.1.100",
            RateLimitRecord.RateLimitType.IP,
            3,  // Low limit for testing
            1
        );

        // Make 3 requests (at limit)
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);

        assertThat(testKit.getState().requestCount()).isEqualTo(3);

        // 4th request should be rejected
        var result = testKit.method(RateLimitEntity::checkAndRecord)
            .invoke(request);

        assertThat(result.getReply()).isEqualTo("EXCEEDED");
        assertThat(testKit.getState().requestCount()).isEqualTo(3); // Count not incremented
    }

    @Test
    public void shouldGetCurrentCount() {
        var testKit = KeyValueEntityTestKit.of("test-count", RateLimitEntity::new);

        var request = new RateLimitEntity.CheckRateLimitRequest(
            "test-id",
            RateLimitRecord.RateLimitType.IP,
            100,
            1
        );

        // Make 2 requests
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);

        var countResult = testKit.method(RateLimitEntity::getCurrentCount)
            .invoke();

        assertThat(countResult.getReply()).isEqualTo(2);
    }

    @Test
    public void shouldReturnZeroCountForNewEntity() {
        var testKit = KeyValueEntityTestKit.of("test-zero", RateLimitEntity::new);

        var result = testKit.method(RateLimitEntity::getCurrentCount)
            .invoke();

        assertThat(result.getReply()).isEqualTo(0);
    }

    @Test
    public void shouldResetRateLimit() {
        var testKit = KeyValueEntityTestKit.of("test-reset", RateLimitEntity::new);

        var request = new RateLimitEntity.CheckRateLimitRequest(
            "test-id",
            RateLimitRecord.RateLimitType.IP,
            100,
            1
        );

        // Make requests
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);
        testKit.method(RateLimitEntity::checkAndRecord).invoke(request);

        assertThat(testKit.getState().requestCount()).isEqualTo(2);

        // Reset
        var resetResult = testKit.method(RateLimitEntity::reset).invoke();

        assertThat(resetResult.getReply()).isEqualTo("RESET");
        assertThat(testKit.getState()).isNull();
    }
}
