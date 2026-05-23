package com.example.payment.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.payment.domain.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditLogEntityTest {

    @Test
    public void shouldLogPaymentCreatedEvent() {
        var testKit = EventSourcedTestKit.of("cust_audit_1", AuditLogEntity::new);

        var event = new AuditEvent.PaymentCreated(
            "cust_audit_1",
            "txn_123",
            "100.00",
            "USD",
            "ORDER-001",
            Instant.now(),
            "Payment created"
        );

        var result = testKit.method(AuditLogEntity::logEvent)
            .invoke(event);

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("LOGGED");
        assertThat(testKit.getState().events()).hasSize(1);
        assertThat(testKit.getState().events().get(0)).isEqualTo(event);
    }

    @Test
    public void shouldGetAllEvents() {
        var testKit = EventSourcedTestKit.of("cust_audit_2", AuditLogEntity::new);

        var event1 = new AuditEvent.PaymentCreated(
            "cust_audit_2",
            "txn_1",
            "50.00",
            "USD",
            "ORDER-1",
            Instant.now(),
            "Payment 1"
        );

        var event2 = new AuditEvent.PaymentCompleted(
            "cust_audit_2",
            "txn_1",
            "50.00",
            "USD",
            "gw_123",
            Instant.now(),
            "Payment 1 completed"
        );

        testKit.method(AuditLogEntity::logEvent).invoke(event1);
        testKit.method(AuditLogEntity::logEvent).invoke(event2);

        var result = testKit.method(AuditLogEntity::getAllEvents).invoke();

        assertThat(result.getReply().events()).hasSize(2);
        assertThat(result.getReply().events()).containsExactly(event1, event2);
    }

    @Test
    public void shouldGetEventsByType() {
        var testKit = EventSourcedTestKit.of("cust_audit_3", AuditLogEntity::new);

        var event1 = new AuditEvent.PaymentCreated(
            "cust_audit_3",
            "txn_1",
            "100.00",
            "USD",
            "ORDER-1",
            Instant.now(),
            "Payment 1"
        );

        var event2 = new AuditEvent.PaymentCompleted(
            "cust_audit_3",
            "txn_1",
            "100.00",
            "USD",
            "gw_123",
            Instant.now(),
            "Payment 1 completed"
        );

        var event3 = new AuditEvent.PaymentCreated(
            "cust_audit_3",
            "txn_2",
            "200.00",
            "USD",
            "ORDER-2",
            Instant.now(),
            "Payment 2"
        );

        testKit.method(AuditLogEntity::logEvent).invoke(event1);
        testKit.method(AuditLogEntity::logEvent).invoke(event2);
        testKit.method(AuditLogEntity::logEvent).invoke(event3);

        var result = testKit.method(AuditLogEntity::getEventsByType)
            .invoke("PAYMENT_CREATED");

        assertThat(result.getReply().events()).hasSize(2);
        assertThat(result.getReply().events()).containsExactly(event1, event3);
    }

    @Test
    public void shouldGetEventsInTimeRange() {
        var testKit = EventSourcedTestKit.of("cust_audit_4", AuditLogEntity::new);

        Instant now = Instant.now();
        Instant past = now.minus(2, ChronoUnit.HOURS);
        Instant future = now.plus(1, ChronoUnit.HOURS);

        var event1 = new AuditEvent.PaymentCreated(
            "cust_audit_4",
            "txn_1",
            "100.00",
            "USD",
            "ORDER-1",
            past,
            "Old payment"
        );

        var event2 = new AuditEvent.PaymentCreated(
            "cust_audit_4",
            "txn_2",
            "200.00",
            "USD",
            "ORDER-2",
            now,
            "Recent payment"
        );

        testKit.method(AuditLogEntity::logEvent).invoke(event1);
        testKit.method(AuditLogEntity::logEvent).invoke(event2);

        var query = new AuditLogEntity.TimeRangeQuery(
            now.minus(1, ChronoUnit.HOURS),
            future
        );

        var result = testKit.method(AuditLogEntity::getEventsInRange)
            .invoke(query);

        assertThat(result.getReply().events()).hasSize(1);
        assertThat(result.getReply().events().get(0)).isEqualTo(event2);
    }

    @Test
    public void shouldGetRecentEvents() {
        var testKit = EventSourcedTestKit.of("cust_audit_5", AuditLogEntity::new);

        // Add 5 events
        for (int i = 1; i <= 5; i++) {
            var event = new AuditEvent.PaymentCreated(
                "cust_audit_5",
                "txn_" + i,
                "100.00",
                "USD",
                "ORDER-" + i,
                Instant.now(),
                "Payment " + i
            );
            testKit.method(AuditLogEntity::logEvent).invoke(event);
        }

        // Get last 3
        var result = testKit.method(AuditLogEntity::getRecentEvents)
            .invoke(3);

        assertThat(result.getReply().events()).hasSize(3);
        // Should be events 3, 4, 5
        assertThat(result.getReply().events().get(0).description()).isEqualTo("Payment 3");
        assertThat(result.getReply().events().get(1).description()).isEqualTo("Payment 4");
        assertThat(result.getReply().events().get(2).description()).isEqualTo("Payment 5");
    }

    @Test
    public void shouldGetEventCount() {
        var testKit = EventSourcedTestKit.of("cust_audit_6", AuditLogEntity::new);

        // Initially 0
        var count1 = testKit.method(AuditLogEntity::getEventCount).invoke();
        assertThat(count1.getReply()).isEqualTo(0);

        // Add 3 events
        for (int i = 1; i <= 3; i++) {
            var event = new AuditEvent.PaymentCreated(
                "cust_audit_6",
                "txn_" + i,
                "100.00",
                "USD",
                "ORDER-" + i,
                Instant.now(),
                "Payment " + i
            );
            testKit.method(AuditLogEntity::logEvent).invoke(event);
        }

        var count2 = testKit.method(AuditLogEntity::getEventCount).invoke();
        assertThat(count2.getReply()).isEqualTo(3);
    }

    @Test
    public void shouldLogDifferentEventTypes() {
        var testKit = EventSourcedTestKit.of("cust_audit_7", AuditLogEntity::new);

        var paymentEvent = new AuditEvent.PaymentCreated(
            "cust_audit_7",
            "txn_1",
            "100.00",
            "USD",
            "ORDER-1",
            Instant.now(),
            "Payment created"
        );

        var refundEvent = new AuditEvent.RefundInitiated(
            "cust_audit_7",
            "txn_1",
            "ref_1",
            "50.00",
            "USD",
            "Customer request",
            Instant.now(),
            "Refund initiated"
        );

        var fraudEvent = new AuditEvent.FraudAlertTriggered(
            "cust_audit_7",
            "txn_2",
            "VELOCITY_EXCEEDED",
            "200.00",
            "USD",
            "Too many payments",
            Instant.now(),
            "Fraud detected"
        );

        testKit.method(AuditLogEntity::logEvent).invoke(paymentEvent);
        testKit.method(AuditLogEntity::logEvent).invoke(refundEvent);
        testKit.method(AuditLogEntity::logEvent).invoke(fraudEvent);

        var allEvents = testKit.method(AuditLogEntity::getAllEvents).invoke();
        assertThat(allEvents.getReply().events()).hasSize(3);

        assertThat(allEvents.getReply().events().get(0).eventType()).isEqualTo("PAYMENT_CREATED");
        assertThat(allEvents.getReply().events().get(1).eventType()).isEqualTo("REFUND_INITIATED");
        assertThat(allEvents.getReply().events().get(2).eventType()).isEqualTo("FRAUD_ALERT");
    }
}
