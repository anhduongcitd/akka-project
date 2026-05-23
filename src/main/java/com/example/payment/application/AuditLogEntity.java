package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.payment.domain.AuditEvent;
import com.example.payment.domain.AuditLog;

import java.time.Instant;
import java.util.List;

/**
 * Audit log entity - immutable audit trail.
 * Event sourced for compliance and forensics.
 */
@Component(id = "audit-log")
public class AuditLogEntity extends EventSourcedEntity<AuditLog, AuditEvent> {

    private final String customerId;

    public AuditLogEntity(EventSourcedEntityContext context) {
        this.customerId = context.entityId();
    }

    @Override
    public AuditLog emptyState() {
        return AuditLog.empty(customerId);
    }

    /**
     * Log audit event.
     */
    public Effect<String> logEvent(AuditEvent event) {
        return effects()
            .persist(event)
            .thenReply(state -> "LOGGED");
    }

    public record EventList(List<AuditEvent> events) {}

    /**
     * Get all events.
     */
    public Effect<EventList> getAllEvents() {
        return effects().reply(new EventList(currentState().events()));
    }

    /**
     * Get events by type.
     */
    public Effect<EventList> getEventsByType(String eventType) {
        return effects().reply(new EventList(currentState().getEventsByType(eventType)));
    }

    /**
     * Get events in time range.
     */
    public record TimeRangeQuery(Instant start, Instant end) {}

    public Effect<EventList> getEventsInRange(TimeRangeQuery query) {
        return effects().reply(new EventList(currentState().getEventsInRange(query.start, query.end)));
    }

    /**
     * Get recent events.
     */
    public Effect<EventList> getRecentEvents(int limit) {
        return effects().reply(new EventList(currentState().getRecentEvents(limit)));
    }

    /**
     * Get event count.
     */
    public Effect<Integer> getEventCount() {
        return effects().reply(currentState().getEventCount());
    }

    @Override
    public AuditLog applyEvent(AuditEvent event) {
        return currentState().addEvent(event);
    }
}
