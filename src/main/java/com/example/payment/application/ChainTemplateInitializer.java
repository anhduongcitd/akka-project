package com.example.payment.application;

import akka.javasdk.ServiceSetup;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes agent chain templates on startup.
 */
public class ChainTemplateInitializer implements ServiceSetup {

    private static final Logger log = LoggerFactory.getLogger(ChainTemplateInitializer.class);

    private final ComponentClient componentClient;

    public ChainTemplateInitializer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public void onStartup() {
        log.info("Initializing Agent Chain Templates...");

        var templates = ChainTemplateLibrary.getAllChainTemplates();
        int successCount = 0;
        int failureCount = 0;

        for (var template : templates) {
            try {
                componentClient
                    .forKeyValueEntity(template.chainId())
                    .method(AgentChainEntity::createChain)
                    .invoke(new AgentChainEntity.CreateChain(template.chainId(), template));

                log.info("✓ Created chain template: {} ({})", template.name(), template.chainId());
                successCount++;

            } catch (Exception e) {
                // Template may already exist, which is fine
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    log.debug("Chain template {} already exists, skipping", template.chainId());
                    successCount++;
                } else {
                    log.error("✗ Failed to create chain template {}: {}", template.chainId(), e.getMessage());
                    failureCount++;
                }
            }
        }

        log.info("Chain template initialization complete: {} templates created, {} failures",
            successCount, failureCount);
    }
}
