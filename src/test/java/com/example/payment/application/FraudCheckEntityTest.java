package com.example.payment.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class FraudCheckEntityTest {

    @Test
    public void shouldAllowFirstPayment() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_1", FraudCheckEntity::new);

        var request = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-001",
            new BigDecimal("100.00"),
            "USD",
            5, 10,
            new BigDecimal("5000.00"), 60,
            5
        );

        var result = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request);

        assertThat(result.getReply().passed()).isTrue();
        assertThat(result.getReply().reason()).isNull();
    }

    @Test
    public void shouldDetectVelocityExceeded() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_2", FraudCheckEntity::new);

        // Make 3 payments (at limit) with unique merchant refs to avoid duplicate detection
        for (int i = 1; i <= 3; i++) {
            var request = new FraudCheckEntity.CheckFraudRequest(
                "ORDER-VEL-" + i,
                new BigDecimal("50.00"),
                "USD",
                3, 10,  // Max 3 payments in 10 min
                new BigDecimal("10000.00"), 60,
                5
            );
            testKit.method(FraudCheckEntity::checkAndRecord).invoke(request);
        }

        // 4th payment should be blocked (velocity exceeded)
        var request4 = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-VEL-4",
            new BigDecimal("50.00"),
            "USD",
            3, 10,
            new BigDecimal("10000.00"), 60,
            5
        );

        var result = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request4);

        assertThat(result.getReply().passed()).isFalse();
        assertThat(result.getReply().reason()).contains("VELOCITY_EXCEEDED");
    }

    @Test
    public void shouldDetectHighValue() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_3", FraudCheckEntity::new);

        // Make 3 payments totaling $3000 with unique merchant refs
        for (int i = 1; i <= 3; i++) {
            var request = new FraudCheckEntity.CheckFraudRequest(
                "ORDER-HV-" + i,
                new BigDecimal("1000.00"),
                "USD",
                10, 10,
                new BigDecimal("2500.00"), 60,  // Max $2500 in 1 hour
                5
            );
            testKit.method(FraudCheckEntity::checkAndRecord).invoke(request);
        }

        // Total is now $3000, exceeds $2500 limit
        var request4 = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-HV-4",
            new BigDecimal("1000.00"),
            "USD",
            10, 10,
            new BigDecimal("2500.00"), 60,
            5
        );

        var result = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request4);

        assertThat(result.getReply().passed()).isFalse();
        assertThat(result.getReply().reason()).contains("HIGH_VALUE");
    }

    @Test
    public void shouldDetectDuplicateTransaction() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_4", FraudCheckEntity::new);

        var request = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-DUP-001",
            new BigDecimal("100.00"),
            "USD",
            10, 10,
            new BigDecimal("10000.00"), 60,
            5  // 5 minute window for duplicates
        );

        // First payment - should pass
        var result1 = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request);
        assertThat(result1.getReply().passed()).isTrue();

        // Exact same payment immediately after - should be blocked
        var result2 = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request);
        assertThat(result2.getReply().passed()).isFalse();
        assertThat(result2.getReply().reason()).contains("DUPLICATE_TRANSACTION");
    }

    @Test
    public void shouldAllowDifferentMerchantReference() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_5", FraudCheckEntity::new);

        var request1 = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-001",
            new BigDecimal("100.00"),
            "USD",
            10, 10,
            new BigDecimal("10000.00"), 60,
            5
        );

        var request2 = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-002",  // Different merchant ref
            new BigDecimal("100.00"),
            "USD",
            10, 10,
            new BigDecimal("10000.00"), 60,
            5
        );

        // First payment
        var result1 = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request1);
        assertThat(result1.getReply().passed()).isTrue();

        // Same amount but different merchant ref - should pass
        var result2 = testKit.method(FraudCheckEntity::checkAndRecord)
            .invoke(request2);
        assertThat(result2.getReply().passed()).isTrue();
    }

    @Test
    public void shouldGetRecentPaymentCount() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_6", FraudCheckEntity::new);

        // Make 3 payments with unique merchant refs
        for (int i = 1; i <= 3; i++) {
            var request = new FraudCheckEntity.CheckFraudRequest(
                "ORDER-COUNT-" + i,
                new BigDecimal("50.00"),
                "USD",
                10, 10,
                new BigDecimal("10000.00"), 60,
                5
            );
            testKit.method(FraudCheckEntity::checkAndRecord).invoke(request);
        }

        // Get count
        var count = testKit.method(FraudCheckEntity::getRecentPaymentCount)
            .invoke(10);

        assertThat(count.getReply()).isEqualTo(3);
    }

    @Test
    public void shouldGetRecentPaymentTotal() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_7", FraudCheckEntity::new);

        // Make 2 payments of $150 each with unique merchant refs
        for (int i = 1; i <= 2; i++) {
            var request = new FraudCheckEntity.CheckFraudRequest(
                "ORDER-TOTAL-" + i,
                new BigDecimal("150.00"),
                "USD",
                10, 10,
                new BigDecimal("10000.00"), 60,
                5
            );
            testKit.method(FraudCheckEntity::checkAndRecord).invoke(request);
        }

        // Get total
        var total = testKit.method(FraudCheckEntity::getRecentPaymentTotal)
            .invoke(60);

        assertThat(new BigDecimal(total.getReply())).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    public void shouldResetFraudCheck() {
        var testKit = KeyValueEntityTestKit.of("cust_fraud_8", FraudCheckEntity::new);

        var request = new FraudCheckEntity.CheckFraudRequest(
            "ORDER-RESET",
            new BigDecimal("100.00"),
            "USD",
            10, 10,
            new BigDecimal("10000.00"), 60,
            5
        );

        // Make a payment
        testKit.method(FraudCheckEntity::checkAndRecord).invoke(request);
        assertThat(testKit.getState()).isNotNull();

        // Reset
        var result = testKit.method(FraudCheckEntity::reset).invoke();
        assertThat(result.getReply()).isEqualTo("RESET");
        assertThat(testKit.getState()).isNull();
    }
}
