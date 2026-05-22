package com.example.payment.api;

import akka.javasdk.testkit.TestKitSupport;
import com.example.payment.application.ReceiptGenerator;
import com.example.payment.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Receipt Download Integration Test.
 * Tests receipt generation functionality.
 */
public class ReceiptDownloadIntegrationTest extends TestKitSupport {

    @Test
    public void shouldGenerateHtmlReceipt() {
        var receiptGenerator = new ReceiptGenerator();

        var transaction = createTestTransaction();
        String html = receiptGenerator.generateReceiptHtml(transaction);

        assertThat(html).isNotNull();
        assertThat(html).contains("Payment Receipt");
        assertThat(html).contains(transaction.transactionId());
        assertThat(html).contains(transaction.customer().name());
        assertThat(html).contains(transaction.customer().email());
        assertThat(html).contains(transaction.amount().format());
    }

    @Test
    public void shouldGenerateTextReceipt() {
        var receiptGenerator = new ReceiptGenerator();

        var transaction = createTestTransaction();
        String text = receiptGenerator.generateReceiptText(transaction);

        assertThat(text).isNotNull();
        assertThat(text).contains("PAYMENT RECEIPT");
        assertThat(text).contains(transaction.transactionId());
        assertThat(text).contains(transaction.customer().name());
        assertThat(text).contains(transaction.amount().format());
    }

    private PaymentTransaction createTestTransaction() {
        var customer = new Customer("cust_123", "test@example.com", "Test Customer");
        var amount = new Money(new BigDecimal("100.00"), Currency.USD);

        return new PaymentTransaction(
            "txn_receipt_test",
            customer,
            amount,
            PaymentStatus.SUCCEEDED,
            "ORDER-RECEIPT-001",
            "ch_stripe_123",
            new ArrayList<>(),
            Instant.now(),
            Instant.now(),
            null
        );
    }
}
