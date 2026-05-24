package com.example.payment.application;

import akka.javasdk.ServiceSetup;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes marketplace with pre-built agent templates on startup.
 */
public class MarketplaceInitializer implements ServiceSetup {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceInitializer.class);

    private final ComponentClient componentClient;

    public MarketplaceInitializer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public void onStartup() {
        log.info("Initializing Agent Marketplace with pre-built templates...");

        var templates = TemplateLibrary.getAllTemplates();
        int successCount = 0;
        int failureCount = 0;

        for (var template : templates) {
            try {
                componentClient
                    .forKeyValueEntity(template.templateId())
                    .method(AgentTemplateEntity::createTemplate)
                    .invoke(new AgentTemplateEntity.CreateTemplate(
                        template.name(),
                        template.description(),
                        template.category(),
                        template.config(),
                        template.tags(),
                        template.author(),
                        template.version()
                    ));

                log.info("✓ Published template: {} ({})", template.name(), template.templateId());
                successCount++;

            } catch (Exception e) {
                // Template may already exist, which is fine
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.debug("Template {} already exists, skipping", template.templateId());
                    successCount++;
                } else {
                    log.error("✗ Failed to publish template {}: {}", template.templateId(), e.getMessage());
                    failureCount++;
                }
            }
        }

        log.info("Marketplace initialization complete: {} templates published, {} failures",
            successCount, failureCount);
    }
}
