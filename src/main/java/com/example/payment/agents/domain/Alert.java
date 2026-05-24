package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.List;

/**
 * Alert definition with severity and conditions.
 */
public record Alert(
    String alertId,
    String name,
    String description,
    AlertSeverity severity,
    AlertType type,
    AlertCondition condition,
    List<NotificationChannel> channels,
    boolean enabled,
    Instant createdAt,
    Instant lastTriggeredAt,
    int triggerCount
) {

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        INFO,        // Informational
        WARNING,     // Needs attention
        ERROR,       // Requires action
        CRITICAL     // Urgent action required
    }

    /**
     * Alert types.
     */
    public enum AlertType {
        HIGH_ERROR_RATE,        // Error rate exceeded threshold
        HIGH_LATENCY,           // Latency exceeded threshold
        COST_THRESHOLD,         // Cost budget exceeded
        AGENT_UNAVAILABLE,      // Agent health check failed
        RATE_LIMIT_EXCEEDED,    // Token rate limit hit
        CIRCUIT_OPEN            // Circuit breaker opened
    }

    /**
     * Notification channels.
     */
    public enum NotificationChannel {
        EMAIL,
        WEBHOOK,
        LOG
    }

    /**
     * Create new alert.
     */
    public static Alert create(String alertId, String name, String description,
                                AlertSeverity severity, AlertType type,
                                AlertCondition condition, List<NotificationChannel> channels) {
        return new Alert(
            alertId,
            name,
            description,
            severity,
            type,
            condition,
            channels,
            true,  // Enabled by default
            Instant.now(),
            null,
            0
        );
    }

    /**
     * Record alert trigger.
     */
    public Alert recordTrigger() {
        return new Alert(
            alertId,
            name,
            description,
            severity,
            type,
            condition,
            channels,
            enabled,
            createdAt,
            Instant.now(),
            triggerCount + 1
        );
    }

    /**
     * Enable alert.
     */
    public Alert enable() {
        return new Alert(
            alertId, name, description, severity, type, condition, channels,
            true, createdAt, lastTriggeredAt, triggerCount
        );
    }

    /**
     * Disable alert.
     */
    public Alert disable() {
        return new Alert(
            alertId, name, description, severity, type, condition, channels,
            false, createdAt, lastTriggeredAt, triggerCount
        );
    }

    /**
     * Check if alert is active.
     */
    public boolean isActive() {
        return enabled;
    }

    /**
     * Evaluate condition against metrics.
     */
    public boolean shouldTrigger(AgentHealthStatus health) {
        if (!enabled) {
            return false;
        }

        return condition.evaluate(health);
    }
}
