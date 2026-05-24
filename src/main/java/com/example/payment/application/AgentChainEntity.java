package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.payment.agents.domain.AgentChainConfig;

/**
 * Key-Value Entity for managing agent chain configurations.
 */
@Component(id = "agent-chain")
public class AgentChainEntity extends KeyValueEntity<AgentChainConfig> {

    /**
     * Create a new agent chain.
     */
    public Effect<Done> createChain(CreateChain command) {
        if (currentState() != null) {
            return effects().error("Chain " + command.chainId() + " already exists");
        }

        if (!command.config().isValid()) {
            return effects().error("Invalid chain configuration");
        }

        return effects()
            .updateState(command.config())
            .thenReply(Done.getInstance());
    }

    /**
     * Update chain configuration.
     */
    public Effect<Done> updateChain(UpdateChain command) {
        if (currentState() == null) {
            return effects().error("Chain does not exist");
        }

        if (!command.config().isValid()) {
            return effects().error("Invalid chain configuration");
        }

        return effects()
            .updateState(command.config())
            .thenReply(Done.getInstance());
    }

    /**
     * Get chain configuration.
     */
    public Effect<AgentChainConfig> getChain() {
        if (currentState() == null) {
            return effects().error("Chain does not exist");
        }

        return effects().reply(currentState());
    }

    /**
     * Delete chain.
     */
    public Effect<Done> deleteChain() {
        if (currentState() == null) {
            return effects().error("Chain does not exist");
        }

        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }

    // Command records

    public record CreateChain(String chainId, AgentChainConfig config) {
        public CreateChain {
            if (!chainId.equals(config.chainId())) {
                throw new IllegalArgumentException("Chain ID mismatch");
            }
        }
    }

    public record UpdateChain(AgentChainConfig config) {}
}
