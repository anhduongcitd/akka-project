package com.example.payment.agents.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheEntryEntityTest {

    @Test
    public void shouldStoreCacheEntry() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        var result = testKit
            .method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                "cache-key-1",
                "agent-1",
                "request-hash-123",
                "cached response",
                3600
            ));

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNotNull();
        assertThat(testKit.getState().response()).isEqualTo("cached response");
    }

    @Test
    public void shouldGetCachedResponse() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        testKit.method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                "cache-key-1", "agent-1", "hash-123", "response", 3600
            ));

        var result = testKit.method(CacheEntryEntity::get).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().hit()).isTrue();
        assertThat(result.getReply().response()).isEqualTo("response");
    }

    @Test
    public void shouldReturnCacheMissForNonExistent() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        var result = testKit.method(CacheEntryEntity::get).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().hit()).isFalse();
        assertThat(result.getReply().response()).isNull();
    }

    @Test
    public void shouldIncrementAccessCount() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        testKit.method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                "cache-key-1", "agent-1", "hash-123", "response", 3600
            ));

        testKit.method(CacheEntryEntity::get).invoke();
        var result = testKit.method(CacheEntryEntity::get).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().accessCount()).isEqualTo(2);
    }

    @Test
    public void shouldExtendExpiration() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        testKit.method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                "cache-key-1", "agent-1", "hash-123", "response", 3600
            ));

        var result = testKit.method(CacheEntryEntity::extendExpiration).invoke(7200L);

        assertThat(result.isReply()).isTrue();
    }

    @Test
    public void shouldInvalidateCache() {
        var testKit = KeyValueEntityTestKit.of("cache-key-1", CacheEntryEntity::new);

        testKit.method(CacheEntryEntity::store)
            .invoke(new CacheEntryEntity.StoreEntry(
                "cache-key-1", "agent-1", "hash-123", "response", 3600
            ));

        var result = testKit.method(CacheEntryEntity::invalidate).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }
}
