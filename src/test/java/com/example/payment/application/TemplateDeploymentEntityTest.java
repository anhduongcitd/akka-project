package com.example.payment.application;

import akka.Done;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateDeploymentEntity.
 */
public class TemplateDeploymentEntityTest {

    @Test
    public void shouldDeployTemplate() {
        // Given: Empty entity
        var testKit = KeyValueEntityTestKit.of("deployment-001", TemplateDeploymentEntity::new);

        // When: Deploying template
        var response = testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001",
                "agent-instance-001",
                Map.of("customSetting", "value")
            ));

        // Then: Should succeed
        assertThat(response.isReply()).isTrue();
        assertThat(response.getReply()).isEqualTo(Done.getInstance());

        var state = testKit.getState();
        assertThat(state.deploymentId()).isEqualTo("deployment-001");
        assertThat(state.templateId()).isEqualTo("template-001");
        assertThat(state.agentId()).isEqualTo("agent-instance-001");
        assertThat(state.status()).isEqualTo("active");
        assertThat(state.isActive()).isTrue();
    }

    @Test
    public void shouldRejectDuplicateDeployment() {
        // Given: Existing deployment
        var testKit = KeyValueEntityTestKit.of("deployment-002", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of()
            ));

        // When: Deploying again
        var response = testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-002", "agent-002", Map.of()
            ));

        // Then: Should fail
        assertThat(response.isError()).isTrue();
        assertThat(response.getError()).contains("already exists");
    }

    @Test
    public void shouldUpdateDeployment() {
        // Given: Deployment
        var testKit = KeyValueEntityTestKit.of("deployment-003", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of("key1", "value1")
            ));

        // When: Updating customizations
        var response = testKit.method(TemplateDeploymentEntity::updateDeployment)
            .invoke(new TemplateDeploymentEntity.UpdateDeployment(
                Map.of("key1", "newValue", "key2", "value2")
            ));

        // Then: Should update
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.customizations()).containsEntry("key1", "newValue");
        assertThat(state.customizations()).containsEntry("key2", "value2");
    }

    @Test
    public void shouldActivateDeployment() {
        // Given: Inactive deployment
        var testKit = KeyValueEntityTestKit.of("deployment-004", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of()
            ));

        testKit.method(TemplateDeploymentEntity::deactivateDeployment)
            .invoke(new TemplateDeploymentEntity.DeactivateDeployment());

        // When: Activating
        var response = testKit.method(TemplateDeploymentEntity::activateDeployment)
            .invoke(new TemplateDeploymentEntity.ActivateDeployment());

        // Then: Should activate
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo("active");
        assertThat(state.isActive()).isTrue();
    }

    @Test
    public void shouldDeactivateDeployment() {
        // Given: Active deployment
        var testKit = KeyValueEntityTestKit.of("deployment-005", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of()
            ));

        // When: Deactivating
        var response = testKit.method(TemplateDeploymentEntity::deactivateDeployment)
            .invoke(new TemplateDeploymentEntity.DeactivateDeployment());

        // Then: Should deactivate
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo("inactive");
        assertThat(state.isActive()).isFalse();
    }

    @Test
    public void shouldFailDeployment() {
        // Given: Deployment
        var testKit = KeyValueEntityTestKit.of("deployment-006", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of()
            ));

        // When: Marking as failed
        var response = testKit.method(TemplateDeploymentEntity::failDeployment)
            .invoke(new TemplateDeploymentEntity.FailDeployment("Deployment error"));

        // Then: Should mark as failed
        assertThat(response.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo("failed");
        assertThat(state.isFailed()).isTrue();
    }

    @Test
    public void shouldGetDeployment() {
        // Given: Deployment
        var testKit = KeyValueEntityTestKit.of("deployment-007", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of("custom", "value")
            ));

        // When: Getting deployment
        var response = testKit.method(TemplateDeploymentEntity::getDeployment)
            .invoke();

        // Then: Should return deployment
        assertThat(response.isReply()).isTrue();
        var deployment = response.getReply();
        assertThat(deployment.templateId()).isEqualTo("template-001");
        assertThat(deployment.agentId()).isEqualTo("agent-001");
    }

    @Test
    public void shouldDeleteDeployment() {
        // Given: Deployment
        var testKit = KeyValueEntityTestKit.of("deployment-008", TemplateDeploymentEntity::new);

        testKit.method(TemplateDeploymentEntity::deployTemplate)
            .invoke(new TemplateDeploymentEntity.DeployTemplate(
                "template-001", "agent-001", Map.of()
            ));

        // When: Deleting
        var response = testKit.method(TemplateDeploymentEntity::deleteDeployment)
            .invoke();

        // Then: Should delete
        assertThat(response.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }
}
