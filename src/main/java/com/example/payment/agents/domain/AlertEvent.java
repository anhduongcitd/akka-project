package com.example.payment.agents.domain;

import akka.javasdk.annotations.TypeName;

import java.time.Instant;

/**
 * Events for alert history.
 */
public sealed interface AlertEvent {

    /**
     * Alert was triggered.
     */
    @TypeName("alert-triggered")
    record AlertTriggered(
        String alertId,
        String agentId,
        String alertName,
        Alert.AlertSeverity severity,
        String condition,
        double actualValue,
        double threshold,
        String message,
        Instant triggeredAt
    ) implements AlertEvent {}

    /**
     * Alert was resolved.
     */
    @TypeName("alert-resolved")
    record AlertResolved(
        String alertId,
        String agentId,
        String message,
        Instant resolvedAt
    ) implements AlertEvent {}

    /**
     * Alert notification sent.
     */
    @TypeName("alert-notification-sent")
    record AlertNotificationSent(
        String alertId,
        Alert.NotificationChannel channel,
        String recipient,
        boolean success,
        String errorMessage,
        Instant sentAt
    ) implements AlertEvent {}
}
