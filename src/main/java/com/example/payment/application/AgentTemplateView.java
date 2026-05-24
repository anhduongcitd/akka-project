package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentTemplate;

/**
 * Agent Template View - Query templates in marketplace.
 *
 * Queries:
 * - Browse all templates
 * - Filter by category
 * - Top rated templates
 * - Most downloaded templates
 * - Search templates
 */
@Component(id = "agent-template-view")
public class AgentTemplateView extends View {

    /**
     * Template view row.
     */
    public record TemplateEntry(
        String templateId,
        String name,
        String description,
        String category,
        String author,
        String version,
        int downloads,
        double rating,
        int ratingCount,
        String createdAt
    ) {}

    public record TemplateEntries(java.util.List<TemplateEntry> templates) {}

    /**
     * Query all templates.
     */
    @Query("SELECT * AS templates FROM agent_template ORDER BY createdAt DESC")
    public QueryEffect<TemplateEntries> getAllTemplates() {
        return queryResult();
    }

    /**
     * Query templates by category.
     */
    @Query("SELECT * AS templates FROM agent_template WHERE category = :category ORDER BY rating DESC, downloads DESC")
    public QueryEffect<TemplateEntries> getByCategory(String category) {
        return queryResult();
    }

    /**
     * Query top rated templates.
     */
    @Query("SELECT * AS templates FROM agent_template WHERE ratingCount >= 3 ORDER BY rating DESC, downloads DESC LIMIT 10")
    public QueryEffect<TemplateEntries> getTopRated() {
        return queryResult();
    }

    /**
     * Query most downloaded templates.
     */
    @Query("SELECT * AS templates FROM agent_template ORDER BY downloads DESC LIMIT 10")
    public QueryEffect<TemplateEntries> getMostDownloaded() {
        return queryResult();
    }

    /**
     * Search templates by name or description.
     */
    @Query("SELECT * AS templates FROM agent_template WHERE name LIKE :searchTerm OR description LIKE :searchTerm ORDER BY rating DESC")
    public QueryEffect<TemplateEntries> searchTemplates(String searchTerm) {
        return queryResult();
    }

    /**
     * Get single template.
     */
    @Query("SELECT * FROM agent_template WHERE templateId = :templateId")
    public QueryEffect<TemplateEntry> getById(String templateId) {
        return queryResult();
    }

    /**
     * Table updater - consumes from AgentTemplateEntity.
     */
    @Consume.FromKeyValueEntity(AgentTemplateEntity.class)
    public static class AgentTemplateTableUpdater extends TableUpdater<TemplateEntry> {

        public Effect<TemplateEntry> onUpdate(AgentTemplate template) {
            if (template == null) {
                return effects().ignore();
            }

            var entry = new TemplateEntry(
                template.templateId(),
                template.name(),
                template.description(),
                template.category(),
                template.author(),
                template.version(),
                template.downloads(),
                template.rating(),
                template.ratingCount(),
                template.createdAt().toString()
            );

            return effects().updateRow(entry);
        }

        @akka.javasdk.annotations.DeleteHandler
        public Effect<TemplateEntry> onDelete() {
            return effects().deleteRow();
        }
    }
}
