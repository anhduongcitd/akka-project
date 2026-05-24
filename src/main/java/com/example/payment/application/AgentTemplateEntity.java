package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentConfig;
import com.example.payment.agents.domain.AgentTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Agent Template Entity - Manages agent templates in marketplace.
 *
 * Entity ID: templateId
 *
 * Features:
 * - Create and update templates
 * - Track downloads
 * - Collect ratings
 * - Version management
 */
@Component(id = "agent-template")
public class AgentTemplateEntity extends KeyValueEntity<AgentTemplate> {

    // Command records
    public record CreateTemplate(
        String name,
        String description,
        String category,
        AgentConfig config,
        List<String> tags,
        String author,
        String version
    ) {}

    public record UpdateTemplate(AgentConfig config) {}

    public record RateTemplate(double rating) {}

    public record IncrementDownloads() {}

    /**
     * Initialize empty state.
     */
    @Override
    public AgentTemplate emptyState() {
        return null;
    }

    /**
     * Create a new template.
     */
    public Effect<Done> createTemplate(CreateTemplate command) {
        if (currentState() != null) {
            return effects().error("Template already exists");
        }

        String templateId = commandContext().entityId();

        var template = new AgentTemplate(
            templateId,
            command.name(),
            command.description(),
            command.category(),
            command.config(),
            command.tags(),
            command.author(),
            command.version(),
            0, // Initial downloads
            0.0, // Initial rating
            0, // Initial rating count
            Instant.now(),
            Instant.now()
        );

        return effects()
            .updateState(template)
            .thenReply(Done.getInstance());
    }

    /**
     * Update template configuration.
     */
    public Effect<Done> updateTemplate(UpdateTemplate command) {
        if (currentState() == null) {
            return effects().error("Template not found");
        }

        var updated = currentState().withConfig(command.config());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Rate template.
     */
    public Effect<Done> rateTemplate(RateTemplate command) {
        if (currentState() == null) {
            return effects().error("Template not found");
        }

        var updated = currentState().withNewRating(command.rating());

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Increment download count.
     */
    public Effect<Done> incrementDownloads(IncrementDownloads command) {
        if (currentState() == null) {
            return effects().error("Template not found");
        }

        var updated = currentState().withIncrementedDownloads();

        return effects()
            .updateState(updated)
            .thenReply(Done.getInstance());
    }

    /**
     * Get current template.
     */
    public Effect<AgentTemplate> getTemplate() {
        if (currentState() == null) {
            return effects().error("Template not found");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete template.
     */
    public Effect<Done> deleteTemplate() {
        if (currentState() == null) {
            return effects().error("Template not found");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }
}
