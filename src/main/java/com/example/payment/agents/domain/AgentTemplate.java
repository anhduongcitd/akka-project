package com.example.payment.agents.domain;

import java.time.Instant;
import java.util.List;

/**
 * Agent Template - Pre-built agent configuration for marketplace.
 *
 * Contains:
 * - Template metadata (name, description, category)
 * - Agent configuration (prompt, tools, settings)
 * - Usage statistics (downloads, ratings)
 * - Version information
 */
public record AgentTemplate(
    String templateId,
    String name,
    String description,
    String category,              // "customer-support", "fraud", "analytics", "general"
    AgentConfig config,
    List<String> tags,
    String author,
    String version,
    int downloads,
    double rating,
    int ratingCount,
    Instant createdAt,
    Instant updatedAt
) {
    public AgentTemplate {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("Template ID cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category cannot be null or blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (tags == null) {
            tags = List.of();
        }
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        if (downloads < 0) {
            throw new IllegalArgumentException("Downloads cannot be negative");
        }
        if (rating < 0.0 || rating > 5.0) {
            throw new IllegalArgumentException("Rating must be between 0.0 and 5.0");
        }
        if (ratingCount < 0) {
            throw new IllegalArgumentException("Rating count cannot be negative");
        }
    }

    /**
     * Increment download count.
     */
    public AgentTemplate withIncrementedDownloads() {
        return new AgentTemplate(
            templateId,
            name,
            description,
            category,
            config,
            tags,
            author,
            version,
            downloads + 1,
            rating,
            ratingCount,
            createdAt,
            Instant.now()
        );
    }

    /**
     * Update rating with new rating value.
     */
    public AgentTemplate withNewRating(double newRating) {
        if (newRating < 0.0 || newRating > 5.0) {
            throw new IllegalArgumentException("Rating must be between 0.0 and 5.0");
        }

        // Calculate new average rating
        double totalRating = (rating * ratingCount) + newRating;
        int newRatingCount = ratingCount + 1;
        double newAverage = totalRating / newRatingCount;

        return new AgentTemplate(
            templateId,
            name,
            description,
            category,
            config,
            tags,
            author,
            version,
            downloads,
            Math.round(newAverage * 10.0) / 10.0, // Round to 1 decimal
            newRatingCount,
            createdAt,
            Instant.now()
        );
    }

    /**
     * Update configuration.
     */
    public AgentTemplate withConfig(AgentConfig newConfig) {
        return new AgentTemplate(
            templateId,
            name,
            description,
            category,
            newConfig,
            tags,
            author,
            version,
            downloads,
            rating,
            ratingCount,
            createdAt,
            Instant.now()
        );
    }

    /**
     * Check if template is popular (high downloads or high rating).
     */
    public boolean isPopular() {
        return downloads >= 10 || (rating >= 4.0 && ratingCount >= 3);
    }

    /**
     * Get popularity score for sorting.
     */
    public double getPopularityScore() {
        // Weighted score: 70% rating, 30% downloads (normalized)
        double ratingScore = rating / 5.0; // 0-1
        double downloadScore = Math.min(downloads / 100.0, 1.0); // 0-1, cap at 100
        return (ratingScore * 0.7) + (downloadScore * 0.3);
    }
}
