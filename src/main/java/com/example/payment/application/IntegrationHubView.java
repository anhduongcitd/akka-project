package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.IntegrationConfig;

/**
 * Integration Hub View - Query integrations by type and status.
 *
 * Queries:
 * - Find all integrations
 * - Find integrations by type
 * - Find enabled integrations
 * - Find disabled integrations
 */
@Component(id = "integration-hub-view")
public class IntegrationHubView extends View {

    /**
     * Integration view row.
     */
    public record IntegrationEntry(
        String integrationId,
        String integrationType,
        String name,
        boolean enabled,
        int requestsPerMinute,
        String createdAt,
        String updatedAt
    ) {}

    public record IntegrationEntries(java.util.List<IntegrationEntry> integrations) {}

    /**
     * Query all integrations.
     */
    @Query("SELECT * AS integrations FROM integration_hub ORDER BY createdAt DESC")
    public QueryEffect<IntegrationEntries> getAll() {
        return queryResult();
    }

    /**
     * Query integrations by type.
     */
    @Query("SELECT * AS integrations FROM integration_hub WHERE integrationType = :type ORDER BY createdAt DESC")
    public QueryEffect<IntegrationEntries> getByType(String type) {
        return queryResult();
    }

    /**
     * Query enabled integrations.
     */
    @Query("SELECT * AS integrations FROM integration_hub WHERE enabled = true ORDER BY createdAt DESC")
    public QueryEffect<IntegrationEntries> getEnabled() {
        return queryResult();
    }

    /**
     * Query disabled integrations.
     */
    @Query("SELECT * AS integrations FROM integration_hub WHERE enabled = false ORDER BY createdAt DESC")
    public QueryEffect<IntegrationEntries> getDisabled() {
        return queryResult();
    }

    /**
     * Query single integration.
     */
    @Query("SELECT * FROM integration_hub WHERE integrationId = :integrationId")
    public QueryEffect<IntegrationEntry> getById(String integrationId) {
        return queryResult();
    }

    /**
     * Table updater - consumes from IntegrationHubEntity.
     */
    @Consume.FromKeyValueEntity(IntegrationHubEntity.class)
    public static class IntegrationHubTableUpdater extends TableUpdater<IntegrationEntry> {

        public Effect<IntegrationEntry> onUpdate(IntegrationConfig config) {
            if (config == null) {
                return effects().ignore();
            }

            var entry = new IntegrationEntry(
                config.integrationId(),
                config.integrationType(),
                config.name(),
                config.enabled(),
                config.rateLimits().requestsPerMinute(),
                config.createdAt().toString(),
                config.updatedAt().toString()
            );

            return effects().updateRow(entry);
        }

        @akka.javasdk.annotations.DeleteHandler
        public Effect<IntegrationEntry> onDelete() {
            return effects().deleteRow();
        }
    }
}
