package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Alert history state tracking triggers and resolutions.
 */
public record AlertHistory(
    String alertId,
    List<AlertTrigger> triggers,
    boolean currentlyActive,
    Instant lastTriggeredAt,
    Instant lastResolvedAt
) {

    /**
     * Individual alert trigger record.
     */
    public record AlertTrigger(
        String agentId,
        String message,
        double actualValue,
        double threshold,
        Instant triggeredAt,
        Instant resolvedAt
    ) {
        public boolean isResolved() {
            return resolvedAt != null;
        }

        public AlertTrigger resolve(Instant resolvedAt) {
            return new AlertTrigger(agentId, message, actualValue, threshold, triggeredAt, resolvedAt);
        }
    }

    /**
     * Create empty alert history.
     */
    public static AlertHistory empty(String alertId) {
        return new AlertHistory(alertId, new ArrayList<>(), false, null, null);
    }

    /**
     * Add new trigger.
     */
    public AlertHistory addTrigger(String agentId, String message,
                                     double actualValue, double threshold, Instant triggeredAt) {
        var newTriggers = new ArrayList<>(triggers);
        newTriggers.add(new AlertTrigger(agentId, message, actualValue, threshold, triggeredAt, null));

        return new AlertHistory(
            alertId,
            newTriggers,
            true,
            triggeredAt,
            lastResolvedAt
        );
    }

    /**
     * Resolve current trigger.
     */
    public AlertHistory resolveCurrent(Instant resolvedAt) {
        if (!currentlyActive) {
            return this;
        }

        var newTriggers = new ArrayList<>(triggers);
        if (!newTriggers.isEmpty()) {
            int lastIndex = newTriggers.size() - 1;
            var lastTrigger = newTriggers.get(lastIndex);
            newTriggers.set(lastIndex, lastTrigger.resolve(resolvedAt));
        }

        return new AlertHistory(
            alertId,
            newTriggers,
            false,
            lastTriggeredAt,
            resolvedAt
        );
    }

    /**
     * Get total trigger count.
     */
    public int getTriggerCount() {
        return triggers.size();
    }

    /**
     * Get unresolved trigger count.
     */
    public int getUnresolvedCount() {
        return (int) triggers.stream().filter(t -> !t.isResolved()).count();
    }

    /**
     * Get latest trigger.
     */
    public AlertTrigger getLatestTrigger() {
        if (triggers.isEmpty()) {
            return null;
        }
        return triggers.get(triggers.size() - 1);
    }
}
