package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.Alert;
import com.example.payment.agents.domain.AlertCondition;

import java.util.List;

/**
 * Key-Value Entity for alert configuration.
 */
@Component(id = "alert")
public class AlertEntity extends KeyValueEntity<Alert> {

    /**
     * Create new alert.
     */
    public Effect<Done> createAlert(CreateAlert command) {
        if (currentState() != null) {
            return effects().error("Alert " + command.alertId() + " already exists");
        }

        var alert = Alert.create(
            command.alertId(),
            command.name(),
            command.description(),
            command.severity(),
            command.type(),
            command.condition(),
            command.channels()
        );

        return effects()
            .updateState(alert)
            .thenReply(Done.getInstance());
    }

    /**
     * Update alert configuration.
     */
    public Effect<Done> updateAlert(UpdateAlert command) {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        var updated = Alert.create(
            currentState().alertId(),
            command.name(),
            command.description(),
            command.severity(),
            command.type(),
            command.condition(),
            command.channels()
        );

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Enable alert.
     */
    public Effect<Done> enableAlert() {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        var enabled = currentState().enable();

        return effects()
            .updateState(enabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Disable alert.
     */
    public Effect<Done> disableAlert() {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        var disabled = currentState().disable();

        return effects()
            .updateState(disabled)
            .thenReply(Done.getInstance());
    }

    /**
     * Record alert trigger.
     */
    public Effect<Done> recordTrigger() {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        var triggered = currentState().recordTrigger();

        return effects()
            .updateState(triggered)
            .thenReply(Done.getInstance());
    }

    /**
     * Get alert configuration.
     */
    public Effect<Alert> getAlert() {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete alert.
     */
    public Effect<Done> deleteAlert() {
        if (currentState() == null) {
            return effects().error("Alert does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateAlert(
        String alertId,
        String name,
        String description,
        Alert.AlertSeverity severity,
        Alert.AlertType type,
        AlertCondition condition,
        List<Alert.NotificationChannel> channels
    ) {}

    public record UpdateAlert(
        String name,
        String description,
        Alert.AlertSeverity severity,
        Alert.AlertType type,
        AlertCondition condition,
        List<Alert.NotificationChannel> channels
    ) {}
}
