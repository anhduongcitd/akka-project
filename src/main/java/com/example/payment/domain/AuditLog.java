package com.example.payment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit log state - chronological list of audit events.
 * Immutable event history for compliance and investigation.
 */
public record AuditLog(
    String customerId,
    List<AuditEvent> events
) {

    /**
     * Add new audit event.
     */
    public AuditLog addEvent(AuditEvent event) {
        List<AuditEvent> updated = new ArrayList<>(events);
        updated.add(event);
        return new AuditLog(customerId, updated);
    }

    /**
     * Get events by type.
     */
    public List<AuditEvent> getEventsByType(String eventType) {
        return events.stream()
            .filter(e -> e.eventType().equals(eventType))
            .toList();
    }

    /**
     * Get events in time range.
     */
    public List<AuditEvent> getEventsInRange(Instant start, Instant end) {
        return events.stream()
            .filter(e -> e.timestamp().isAfter(start) && e.timestamp().isBefore(end))
            .toList();
    }

    /**
     * Get recent events.
     */
    public List<AuditEvent> getRecentEvents(int limit) {
        int size = events.size();
        int fromIndex = Math.max(0, size - limit);
        return events.subList(fromIndex, size);
    }

    /**
     * Get event count.
     */
    public int getEventCount() {
        return events.size();
    }

    /**
     * Create empty audit log.
     */
    public static AuditLog empty(String customerId) {
        return new AuditLog(customerId, new ArrayList<>());
    }
}
