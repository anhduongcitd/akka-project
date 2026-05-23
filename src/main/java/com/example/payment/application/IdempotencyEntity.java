package com.example.payment.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.payment.domain.IdempotencyRecord;

/**
 * Key-Value Entity for idempotency key tracking.
 * Prevents duplicate payment processing when clients retry requests.
 * 
 * Entity ID: idempotency key (e.g., "idempotency_abc123")
 */
@Component(id = "idempotency")
public class IdempotencyEntity extends KeyValueEntity<IdempotencyRecord> {

    private final String idempotencyKey;

    public IdempotencyEntity(KeyValueEntityContext context) {
        this.idempotencyKey = context.entityId();
    }

    /**
     * Register a payment with this idempotency key.
     * Returns the transaction ID (new or existing).
     */
    public Effect<String> register(String transactionId) {
        IdempotencyRecord current = currentState();

        // If already registered and not expired, return existing transaction ID
        if (current != null && !current.isExpired()) {
            return effects().reply(current.transactionId());
        }

        // Register new transaction
        IdempotencyRecord record = IdempotencyRecord.create(idempotencyKey, transactionId);
        return effects()
            .updateState(record)
            .thenReply(record.transactionId());
    }

    /**
     * Get the transaction ID associated with this idempotency key.
     * Returns empty string if key not found or expired.
     */
    public Effect<String> getTransactionId() {
        IdempotencyRecord current = currentState();

        if (current == null || current.isExpired()) {
            return effects().reply("");
        }

        return effects().reply(current.transactionId());
    }

    /**
     * Delete this idempotency record (for testing/cleanup).
     */
    public Effect<Done> delete() {
        return effects()
            .deleteEntity()
            .thenReply(Done.getInstance());
    }
}
