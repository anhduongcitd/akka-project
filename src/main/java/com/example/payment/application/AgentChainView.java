package com.example.payment.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.payment.agents.domain.AgentChainConfig;

import java.util.List;

/**
 * View for querying agent chain configurations.
 */
@Component(id = "agent-chain-view")
public class AgentChainView extends View {

    /**
     * Chain entry in the view.
     */
    public record ChainEntry(
        String chainId,
        String name,
        String description,
        String executionMode,
        int stepCount,
        List<String> agentIds,
        boolean continueOnError
    ) {}

    /**
     * Wrapper for list results.
     */
    public record ChainList(List<ChainEntry> chains) {}

    /**
     * Get all chains.
     */
    @Query("SELECT * AS chains FROM agent_chains")
    public QueryEffect<ChainList> getAllChains() {
        return queryResult();
    }

    /**
     * Get chain by ID.
     */
    @Query("SELECT * FROM agent_chains WHERE chainId = :chainId")
    public QueryEffect<ChainEntry> getById(String chainId) {
        return queryResult();
    }

    /**
     * Get chains by execution mode.
     */
    @Query("SELECT * AS chains FROM agent_chains WHERE executionMode = :mode")
    public QueryEffect<ChainList> getByExecutionMode(String mode) {
        return queryResult();
    }

    /**
     * Search chains by name.
     */
    @Query("SELECT * AS chains FROM agent_chains WHERE name LIKE :query")
    public QueryEffect<ChainList> searchChains(String query) {
        return queryResult();
    }

    /**
     * Table updater consuming AgentChainEntity state.
     */
    @Consume.FromKeyValueEntity(AgentChainEntity.class)
    public static class AgentChainTableUpdater extends TableUpdater<ChainEntry> {

        public Effect<ChainEntry> onUpdate(AgentChainConfig config) {
            if (config == null) {
                return effects().deleteRow();
            }

            var agentIds = config.steps().stream()
                .map(AgentChainConfig.ChainStep::agentId)
                .distinct()
                .toList();

            var entry = new ChainEntry(
                config.chainId(),
                config.name(),
                config.description(),
                config.executionMode().name(),
                config.getStepCount(),
                agentIds,
                config.continueOnError()
            );

            return effects().updateRow(entry);
        }
    }
}
