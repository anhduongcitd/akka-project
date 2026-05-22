package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Payment History Endpoint Integration Test.
 * Tests payment history API endpoints.
 */
public class PaymentHistoryIntegrationTest extends TestKitSupport {

    @Test
    public void shouldCreateHistoryResponse() {
        var historyEntry = new PaymentEndpoint.HistoryEntryResponse(
            "txn_123",
            "SUCCEEDED",
            new PaymentEndpoint.MoneyResponse("100.00", "USD", "$100.00"),
            "ORDER-001",
            java.time.Instant.now().toString(),
            java.time.Instant.now().toString(),
            false
        );

        assertThat(historyEntry.transactionId()).isEqualTo("txn_123");
        assertThat(historyEntry.status()).isEqualTo("SUCCEEDED");
        assertThat(historyEntry.hasRefunds()).isFalse();
    }

    @Test
    public void shouldCreateHistoryListResponse() {
        var entries = java.util.List.of(
            new PaymentEndpoint.HistoryEntryResponse(
                "txn_1",
                "SUCCEEDED",
                new PaymentEndpoint.MoneyResponse("100.00", "USD", "$100.00"),
                "ORDER-001",
                java.time.Instant.now().toString(),
                java.time.Instant.now().toString(),
                false
            )
        );

        var response = new PaymentEndpoint.PaymentHistoryResponse(entries, entries.size());

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.transactions()).hasSize(1);
    }
}
