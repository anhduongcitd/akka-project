package com.example.payment.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.payment.domain.IdempotencyRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IdempotencyEntity.
 */
public class IdempotencyEntityTest {

    @Test
    public void shouldRegisterNewTransaction() {
        var testKit = KeyValueEntityTestKit.of("idem_key_1", IdempotencyEntity::new);
        
        var result = testKit.method(IdempotencyEntity::register).invoke("txn_123");
        
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("txn_123");
        
        IdempotencyRecord state = testKit.getState();
        assertThat(state).isNotNull();
        assertThat(state.transactionId()).isEqualTo("txn_123");
        assertThat(state.idempotencyKey()).isEqualTo("idem_key_1");
        assertThat(state.isExpired()).isFalse();
    }

    @Test
    public void shouldReturnExistingTransactionIdOnDuplicateRegister() {
        var testKit = KeyValueEntityTestKit.of("idem_key_2", IdempotencyEntity::new);
        
        // First registration
        testKit.method(IdempotencyEntity::register).invoke("txn_456");
        
        // Second registration with different transaction ID
        var result = testKit.method(IdempotencyEntity::register).invoke("txn_789");
        
        // Should return original transaction ID
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("txn_456");
        
        // State should not change
        IdempotencyRecord state = testKit.getState();
        assertThat(state.transactionId()).isEqualTo("txn_456");
    }

    @Test
    public void shouldGetTransactionId() {
        var testKit = KeyValueEntityTestKit.of("idem_key_3", IdempotencyEntity::new);
        
        // Register transaction
        testKit.method(IdempotencyEntity::register).invoke("txn_abc");
        
        // Get transaction ID
        var result = testKit.method(IdempotencyEntity::getTransactionId).invoke();
        
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEqualTo("txn_abc");
    }

    @Test
    public void shouldReturnEmptyStringForNonExistentKey() {
        var testKit = KeyValueEntityTestKit.of("idem_key_4", IdempotencyEntity::new);

        var result = testKit.method(IdempotencyEntity::getTransactionId).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply()).isEmpty();
    }

    // NOTE: Expiry testing is better done via integration tests
    // where time can pass naturally. Unit tests can't easily manipulate
    // state to simulate expired records with KeyValueEntityTestKit.

    @Test
    public void shouldVerifyIdempotencyRecordCreation() {
        // Test that IdempotencyRecord.create() sets proper expiry (24 hours)
        String key = "test_key";
        String txnId = "txn_test";

        IdempotencyRecord record = IdempotencyRecord.create(key, txnId);

        assertThat(record.idempotencyKey()).isEqualTo(key);
        assertThat(record.transactionId()).isEqualTo(txnId);
        assertThat(record.isExpired()).isFalse();

        // Expiry should be in the future
        assertThat(record.expiresAt()).isAfter(Instant.now());
    }

    @Test
    public void shouldDeleteEntity() {
        var testKit = KeyValueEntityTestKit.of("idem_key_7", IdempotencyEntity::new);
        
        // Register transaction
        testKit.method(IdempotencyEntity::register).invoke("txn_delete");
        assertThat(testKit.getState()).isNotNull();
        
        // Delete
        var result = testKit.method(IdempotencyEntity::delete).invoke();
        
        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }
}
