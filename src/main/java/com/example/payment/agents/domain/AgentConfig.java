package com.example.payment.agents.domain;

import java.util.List;
import java.util.Map;

/**
 * Agent Configuration - Template configuration for agent deployment.
 *
 * Defines:
 * - System prompt
 * - Tool configurations
 * - Settings and parameters
 * - Guardrail configuration
 * - Model configuration
 */
public record AgentConfig(
    String systemPrompt,
    List<ToolConfig> tools,
    Map<String, String> settings,
    GuardrailConfig guardrails,
    ModelConfig model
) {
    public AgentConfig {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("System prompt cannot be null or blank");
        }
        if (tools == null) {
            tools = List.of();
        }
        if (settings == null) {
            settings = Map.of();
        }
        if (guardrails == null) {
            guardrails = new GuardrailConfig(List.of());
        }
        if (model == null) {
            model = ModelConfig.DEFAULT;
        }
    }

    /**
     * Tool configuration.
     */
    public record ToolConfig(
        String toolName,
        Map<String, String> params
    ) {
        public ToolConfig {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("Tool name cannot be null or blank");
            }
            if (params == null) {
                params = Map.of();
            }
        }
    }

    /**
     * Guardrail configuration.
     */
    public record GuardrailConfig(
        List<String> enabled
    ) {
        public GuardrailConfig {
            if (enabled == null) {
                enabled = List.of();
            }
        }
    }

    /**
     * Model configuration.
     */
    public record ModelConfig(
        String provider,
        String modelId,
        double temperature
    ) {
        public ModelConfig {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("Provider cannot be null or blank");
            }
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("Model ID cannot be null or blank");
            }
            if (temperature < 0.0 || temperature > 2.0) {
                throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0");
            }
        }

        public static final ModelConfig DEFAULT = new ModelConfig("openai", "gpt-4o", 0.7);
    }
}
