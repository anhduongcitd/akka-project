package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.TemplateDeployment;

import java.time.Instant;
import java.util.Map;

/**
 * Template Deployment Entity - Manages deployed agent instances.
 *
 * Entity ID: deploymentId
 *
 * Features:
 * - Deploy template as agent
 * - Update deployment configuration
 * - Activate/deactivate deployment
 * - Track deployment status
 */
@Component(id = "template-deployment")
public class TemplateDeploymentEntity extends KeyValueEntity<TemplateDeployment> {

    // Command records
    public record DeployTemplate(
        String templateId,
        String agentId,
        Map<String, String> customizations
    ) {}

    public record UpdateDeployment(Map<String, String> customizations) {}

    public record ActivateDeployment() {}

    public record DeactivateDeployment() {}

    public record FailDeployment(String reason) {}

    /**
     * Initialize empty state.
     */
    @Override
    public TemplateDeployment emptyState() {
        return null;
    }

    /**
     * Deploy template as agent.
     */
    public Effect<Done> deployTemplate(DeployTemplate command) {
        if (currentState() != null) {
            return effects().error("Deployment already exists");
        }

        String deploymentId = commandContext().entityId();

        var deployment = new TemplateDeployment(
            deploymentId,
            command.templateId(),
            command.agentId(),
            command.customizations(),
            "active",
            Instant.now(),
            Instant.now()
        );

        return effects()
            .updateState(deployment)
            .thenReply(Done.getInstance());
    }

    /**
     * Update deployment customizations.
     */
    public Effect<Done> updateDeployment(UpdateDeployment command) {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        var updated = currentState().withCustomizations(command.customizations());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Activate deployment.
     */
    public Effect<Done> activateDeployment(ActivateDeployment command) {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        var updated = currentState().withStatus("active");

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Deactivate deployment.
     */
    public Effect<Done> deactivateDeployment(DeactivateDeployment command) {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        var updated = currentState().withStatus("inactive");

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Mark deployment as failed.
     */
    public Effect<Done> failDeployment(FailDeployment command) {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        var updated = currentState().withStatus("failed");

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get deployment status.
     */
    public Effect<TemplateDeployment> getDeployment() {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete deployment.
     */
    public Effect<Done> deleteDeployment() {
        if (currentState() == null) {
            return effects().error("Deployment not found");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }
}
