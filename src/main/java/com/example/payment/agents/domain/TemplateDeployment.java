package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Template Deployment - Instance of deployed agent from template.
 *
 * Tracks:
 * - Deployed agent instance
 * - Template used
 * - Customizations applied
 * - Deployment status
 */
public record TemplateDeployment(
    String deploymentId,
    String templateId,
    String agentId,              // ID of deployed agent instance
    Map<String, String> customizations,
    String status,               // "active", "inactive", "failed"
    Instant deployedAt,
    Instant lastUpdatedAt
) {
    public TemplateDeployment {
        if (deploymentId == null || deploymentId.isBlank()) {
            throw new IllegalArgumentException("Deployment ID cannot be null or blank");
        }
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Template ID cannot be null or blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("Agent ID cannot be null or blank");
        }
        if (customizations == null) {
            customizations = Map.of();
        }
        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    private static boolean isValidStatus(String status) {
        return status.equals("active") ||
               status.equals("inactive") ||
               status.equals("failed");
    }

    /**
     * Update deployment status.
     */
    public TemplateDeployment withStatus(String newStatus) {
        if (!isValidStatus(newStatus)) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }

        return new TemplateDeployment(
            deploymentId,
            templateId,
            agentId,
            customizations,
            newStatus,
            deployedAt,
            Instant.now()
        );
    }

    /**
     * Update customizations.
     */
    public TemplateDeployment withCustomizations(Map<String, String> newCustomizations) {
        return new TemplateDeployment(
            deploymentId,
            templateId,
            agentId,
            newCustomizations,
            status,
            deployedAt,
            Instant.now()
        );
    }

    /**
     * Check if deployment is active.
     */
    public boolean isActive() {
        return status.equals("active");
    }

    /**
     * Check if deployment failed.
     */
    public boolean isFailed() {
        return status.equals("failed");
    }
}
