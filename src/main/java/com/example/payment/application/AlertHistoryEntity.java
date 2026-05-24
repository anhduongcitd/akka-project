package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.payment.agents.domain.Alert;
import com.example.payment.agents.domain.AlertEvent;
import com.example.payment.agents.domain.AlertHistory;

import java.time.Instant;

/**
 * Event Sourced Entity for alert history tracking.
 */
@Component(id = "alert-history")
public class AlertHistoryEntity extends EventSourcedEntity<AlertHistory, AlertEvent> {

    @Override
    public AlertHistory emptyState() {
        return null;
    }

    /**
     * Trigger alert.
     */
    public Effect<Done> triggerAlert(TriggerAlert command) {
        var event = new AlertEvent.AlertTriggered(
            command.alertId(),
            command.agentId(),
            command.alertName(),
            command.severity(),
            command.condition(),
            command.actualValue(),
            command.threshold(),
            command.message(),
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(newState -> Done.getInstance());
    }

    /**
     * Resolve alert.
     */
    public Effect<Done> resolveAlert(ResolveAlert command) {
        if (currentState() == null || !currentState().currentlyActive()) {
            return effects().error("No active alert to resolve");
        }

        var event = new AlertEvent.AlertResolved(
            currentState().alertId(),
            command.agentId(),
            command.message(),
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(newState -> Done.getInstance());
    }

    /**
     * Record notification sent.
     */
    public Effect<Done> recordNotification(RecordNotification command) {
        if (currentState() == null) {
            return effects().error("Alert history not found");
        }

        var event = new AlertEvent.AlertNotificationSent(
            currentState().alertId(),
            command.channel(),
            command.recipient(),
            command.success(),
            command.errorMessage(),
            Instant.now()
        );

        return effects()
            .persist(event)
            .thenReply(newState -> Done.getInstance());
    }

    /**
     * Get alert history.
     */
    public Effect<AlertHistory> getHistory() {
        if (currentState() == null) {
            return effects().error("Alert history not found");
        }

        return effects().reply(currentState());
    }

    // Event handlers

    @Override
    public AlertHistory applyEvent(AlertEvent event) {
        return switch (event) {
            case AlertEvent.AlertTriggered triggered -> {
                if (currentState() == null) {
                    yield AlertHistory.empty(triggered.alertId())
                        .addTrigger(
                            triggered.agentId(),
                            triggered.message(),
                            triggered.actualValue(),
                            triggered.threshold(),
                            triggered.triggeredAt()
                        );
                } else {
                    yield currentState().addTrigger(
                        triggered.agentId(),
                        triggered.message(),
                        triggered.actualValue(),
                        triggered.threshold(),
                        triggered.triggeredAt()
                    );
                }
            }
            case AlertEvent.AlertResolved resolved ->
                currentState().resolveCurrent(resolved.resolvedAt());
            case AlertEvent.AlertNotificationSent notification ->
                currentState(); // No state change for notifications
        };
    }

    // Command records

    public record TriggerAlert(
        String alertId,
        String agentId,
        String alertName,
        Alert.AlertSeverity severity,
        String condition,
        double actualValue,
        double threshold,
        String message
    ) {}

    public record ResolveAlert(
        String agentId,
        String message
    ) {}

    public record RecordNotification(
        Alert.NotificationChannel channel,
        String recipient,
        boolean success,
        String errorMessage
    ) {}
}
