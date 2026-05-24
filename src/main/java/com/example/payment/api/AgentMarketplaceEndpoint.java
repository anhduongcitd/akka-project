package com.example.payment.api;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import com.example.payment.agents.domain.AgentConfig;
import com.example.payment.agents.domain.AgentTemplate;
import com.example.payment.agents.domain.TemplateDeployment;
import com.example.payment.application.AgentTemplateEntity;
import com.example.payment.application.AgentTemplateView;
import com.example.payment.application.TemplateDeploymentEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Marketplace Endpoint - API for agent template marketplace.
 *
 * Endpoints:
 * - POST /marketplace/templates - Publish template
 * - GET /marketplace/templates - List all templates
 * - GET /marketplace/templates/{id} - Get template details
 * - PUT /marketplace/templates/{id} - Update template
 * - DELETE /marketplace/templates/{id} - Delete template
 * - GET /marketplace/templates/category/{category} - Filter by category
 * - GET /marketplace/templates/top-rated - Top rated templates
 * - GET /marketplace/templates/popular - Most downloaded
 * - GET /marketplace/templates/search - Search templates
 * - POST /marketplace/deploy/{templateId} - Deploy template
 * - GET /marketplace/deployments - List deployments
 * - DELETE /marketplace/deployments/{id} - Delete deployment
 */
@HttpEndpoint("/marketplace")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AgentMarketplaceEndpoint {

    private final ComponentClient componentClient;

    public AgentMarketplaceEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record PublishTemplateRequest(
        String templateId,
        String name,
        String description,
        String category,
        AgentConfig config,
        List<String> tags,
        String author,
        String version
    ) {}

    public record UpdateTemplateRequest(AgentConfig config) {}

    public record RateTemplateRequest(double rating) {}

    public record DeployTemplateRequest(
        String agentId,
        Map<String, String> customizations
    ) {}

    public record TemplateResponse(
        String templateId,
        String name,
        String description,
        String category,
        AgentConfig config,
        List<String> tags,
        String author,
        String version,
        int downloads,
        double rating,
        int ratingCount,
        String createdAt
    ) {}

    public record TemplateListResponse(List<TemplateSummary> templates) {}

    public record TemplateSummary(
        String templateId,
        String name,
        String description,
        String category,
        String author,
        String version,
        int downloads,
        double rating,
        int ratingCount
    ) {}

    public record DeploymentResponse(
        String deploymentId,
        String templateId,
        String agentId,
        Map<String, String> customizations,
        String status,
        String deployedAt
    ) {}

    public record DeploymentListResponse(List<DeploymentSummary> deployments) {}

    public record DeploymentSummary(
        String deploymentId,
        String templateId,
        String agentId,
        String status,
        String deployedAt
    ) {}

    /**
     * Publish a new template.
     */
    @Post("/templates")
    public Done publishTemplate(PublishTemplateRequest request) {
        componentClient
            .forKeyValueEntity(request.templateId())
            .method(AgentTemplateEntity::createTemplate)
            .invoke(new AgentTemplateEntity.CreateTemplate(
                request.name(),
                request.description(),
                request.category(),
                request.config(),
                request.tags(),
                request.author(),
                request.version()
            ));

        return Done.getInstance();
    }

    /**
     * Get template details.
     */
    @Get("/templates/{templateId}")
    public TemplateResponse getTemplate(String templateId) {
        var template = componentClient
            .forKeyValueEntity(templateId)
            .method(AgentTemplateEntity::getTemplate)
            .invoke();

        return toTemplateResponse(template);
    }

    /**
     * Update template.
     */
    @Put("/templates/{templateId}")
    public Done updateTemplate(String templateId, UpdateTemplateRequest request) {
        componentClient
            .forKeyValueEntity(templateId)
            .method(AgentTemplateEntity::updateTemplate)
            .invoke(new AgentTemplateEntity.UpdateTemplate(request.config()));

        return Done.getInstance();
    }

    /**
     * Delete template.
     */
    @Delete("/templates/{templateId}")
    public Done deleteTemplate(String templateId) {
        componentClient
            .forKeyValueEntity(templateId)
            .method(AgentTemplateEntity::deleteTemplate)
            .invoke();

        return Done.getInstance();
    }

    /**
     * List all templates.
     */
    @Get("/templates")
    public TemplateListResponse listAllTemplates() {
        var entries = componentClient
            .forView()
            .method(AgentTemplateView::getAllTemplates)
            .invoke();

        var summaries = entries.templates().stream()
            .map(this::toTemplateSummary)
            .toList();

        return new TemplateListResponse(summaries);
    }

    /**
     * Filter templates by category.
     */
    @Get("/templates/category/{category}")
    public TemplateListResponse getTemplatesByCategory(String category) {
        var entries = componentClient
            .forView()
            .method(AgentTemplateView::getByCategory)
            .invoke(category);

        var summaries = entries.templates().stream()
            .map(this::toTemplateSummary)
            .toList();

        return new TemplateListResponse(summaries);
    }

    /**
     * Get top rated templates.
     */
    @Get("/templates/top-rated")
    public TemplateListResponse getTopRated() {
        var entries = componentClient
            .forView()
            .method(AgentTemplateView::getTopRated)
            .invoke();

        var summaries = entries.templates().stream()
            .map(this::toTemplateSummary)
            .toList();

        return new TemplateListResponse(summaries);
    }

    /**
     * Get most downloaded templates.
     */
    @Get("/templates/popular")
    public TemplateListResponse getPopular() {
        var entries = componentClient
            .forView()
            .method(AgentTemplateView::getMostDownloaded)
            .invoke();

        var summaries = entries.templates().stream()
            .map(this::toTemplateSummary)
            .toList();

        return new TemplateListResponse(summaries);
    }

    /**
     * Search templates.
     */
    @Get("/templates/search")
    public TemplateListResponse searchTemplates(String query) {
        // Add wildcards for LIKE search
        String searchTerm = "%" + query + "%";

        var entries = componentClient
            .forView()
            .method(AgentTemplateView::searchTemplates)
            .invoke(searchTerm);

        var summaries = entries.templates().stream()
            .map(this::toTemplateSummary)
            .toList();

        return new TemplateListResponse(summaries);
    }

    /**
     * Rate a template.
     */
    @Post("/templates/{templateId}/rate")
    public Done rateTemplate(String templateId, RateTemplateRequest request) {
        componentClient
            .forKeyValueEntity(templateId)
            .method(AgentTemplateEntity::rateTemplate)
            .invoke(new AgentTemplateEntity.RateTemplate(request.rating()));

        return Done.getInstance();
    }

    /**
     * Deploy template as agent.
     */
    @Post("/deploy/{templateId}")
    public DeploymentResponse deployTemplate(String templateId, DeployTemplateRequest request) {
        // Increment download count
        componentClient
            .forKeyValueEntity(templateId)
            .method(AgentTemplateEntity::incrementDownloads)
            .invoke(new AgentTemplateEntity.IncrementDownloads());

        // Create deployment
        String deploymentId = "deploy_" + UUID.randomUUID().toString().substring(0, 8);

        componentClient
            .forKeyValueEntity(deploymentId)
            .method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                templateId,
                request.agentId(),
                request.customizations()
            ));

        // Get deployment details
        var deployment = componentClient
            .forKeyValueEntity(deploymentId)
            .method(TemplateDeploymentEntity::getDeployment)
            .invoke();

        return toDeploymentResponse(deployment);
    }

    /**
     * List all deployments.
     */
    @Get("/deployments")
    public DeploymentListResponse listDeployments() {
        // Note: In production, you'd want a view for deployments
        // For now, returning empty list as placeholder
        return new DeploymentListResponse(List.of());
    }

    /**
     * Delete deployment.
     */
    @Delete("/deployments/{deploymentId}")
    public Done deleteDeployment(String deploymentId) {
        componentClient
            .forKeyValueEntity(deploymentId)
            .method(TemplateDeploymentEntity::deleteDeployment)
            .invoke();

        return Done.getInstance();
    }

    /**
     * Convert AgentTemplate to TemplateResponse.
     */
    private TemplateResponse toTemplateResponse(AgentTemplate template) {
        return new TemplateResponse(
            template.templateId(),
            template.name(),
            template.description(),
            template.category(),
            template.config(),
            template.tags(),
            template.author(),
            template.version(),
            template.downloads(),
            template.rating(),
            template.ratingCount(),
            template.createdAt().toString()
        );
    }

    /**
     * Convert TemplateEntry to TemplateSummary.
     */
    private TemplateSummary toTemplateSummary(AgentTemplateView.TemplateEntry entry) {
        return new TemplateSummary(
            entry.templateId(),
            entry.name(),
            entry.description(),
            entry.category(),
            entry.author(),
            entry.version(),
            entry.downloads(),
            entry.rating(),
            entry.ratingCount()
        );
    }

    /**
     * Convert TemplateDeployment to DeploymentResponse.
     */
    private DeploymentResponse toDeploymentResponse(TemplateDeployment deployment) {
        return new DeploymentResponse(
            deployment.deploymentId(),
            deployment.templateId(),
            deployment.agentId(),
            deployment.customizations(),
            deployment.status(),
            deployment.deployedAt().toString()
        );
    }
}
