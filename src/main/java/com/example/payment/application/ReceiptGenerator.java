package com.example.payment.application;

import com.example.payment.domain.PaymentTransaction;

import java.time.format.DateTimeFormatter;

/**
 * Receipt Generator Service.
 * Generates payment receipts in various formats.
 * Aligned with FR-012: Download receipts for completed transactions.
 */
public class ReceiptGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_INSTANT;

    /**
     * Generate a simple text receipt for a payment transaction.
     * In a real implementation, this would generate a PDF using a library like iText or Apache PDFBox.
     */
    public String generateTextReceipt(PaymentTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        var sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("                PAYMENT RECEIPT\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        sb.append("Transaction ID:     ").append(transaction.transactionId()).append("\n");
        sb.append("Date:               ").append(formatDate(transaction.createdAt())).append("\n");
        sb.append("Status:             ").append(transaction.status()).append("\n\n");

        sb.append("───────────────────────────────────────────────────\n");
        sb.append("CUSTOMER INFORMATION\n");
        sb.append("───────────────────────────────────────────────────\n");
        sb.append("Customer ID:        ").append(transaction.customer().customerId()).append("\n");
        sb.append("Name:               ").append(transaction.customer().name()).append("\n");
        sb.append("Email:              ").append(transaction.customer().email()).append("\n\n");

        sb.append("───────────────────────────────────────────────────\n");
        sb.append("PAYMENT DETAILS\n");
        sb.append("───────────────────────────────────────────────────\n");
        sb.append("Amount:             ").append(transaction.amount().format()).append("\n");
        sb.append("Currency:           ").append(transaction.amount().currency()).append("\n");
        sb.append("Merchant Ref:       ").append(transaction.merchantReference()).append("\n");

        if (transaction.gatewayTransactionId() != null) {
            sb.append("Gateway ID:         ").append(transaction.gatewayTransactionId()).append("\n");
        }

        if (transaction.completedAt() != null) {
            sb.append("Completed:          ").append(formatDate(transaction.completedAt())).append("\n");
        }

        if (transaction.failureReason() != null) {
            sb.append("\nFailure Reason:     ").append(transaction.failureReason()).append("\n");
        }

        // Show refund information if any
        if (!transaction.refunds().isEmpty()) {
            sb.append("\n───────────────────────────────────────────────────\n");
            sb.append("REFUNDS\n");
            sb.append("───────────────────────────────────────────────────\n");

            for (var refund : transaction.refunds()) {
                sb.append("\nRefund ID:          ").append(refund.refundId()).append("\n");
                sb.append("Amount:             ").append(refund.amount().format()).append("\n");
                sb.append("Status:             ").append(refund.status()).append("\n");
                if (refund.reason() != null) {
                    sb.append("Reason:             ").append(refund.reason()).append("\n");
                }
                sb.append("Created:            ").append(formatDate(refund.createdAt())).append("\n");
                if (refund.completedAt() != null) {
                    sb.append("Completed:          ").append(formatDate(refund.completedAt())).append("\n");
                }
            }
        }

        sb.append("\n═══════════════════════════════════════════════════\n");
        sb.append("         Thank you for your business!\n");
        sb.append("═══════════════════════════════════════════════════\n");

        return sb.toString();
    }

    /**
     * Generate an HTML receipt for a payment transaction.
     * Suitable for display in a browser or conversion to PDF.
     */
    public String generateHtmlReceipt(PaymentTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n<head>\n");
        sb.append("<meta charset='UTF-8'>\n");
        sb.append("<title>Payment Receipt - ").append(transaction.transactionId()).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: Arial, sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; }\n");
        sb.append("h1 { color: #333; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; }\n");
        sb.append("h2 { color: #666; margin-top: 30px; }\n");
        sb.append(".info { margin: 10px 0; }\n");
        sb.append(".label { font-weight: bold; display: inline-block; width: 200px; }\n");
        sb.append(".value { color: #555; }\n");
        sb.append(".status { padding: 5px 10px; border-radius: 3px; font-weight: bold; }\n");
        sb.append(".status-succeeded { background-color: #4CAF50; color: white; }\n");
        sb.append(".status-failed { background-color: #f44336; color: white; }\n");
        sb.append(".status-pending { background-color: #FF9800; color: white; }\n");
        sb.append(".refund { background-color: #f5f5f5; padding: 15px; margin: 10px 0; border-radius: 5px; }\n");
        sb.append(".footer { margin-top: 40px; text-align: center; color: #999; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>Payment Receipt</h1>\n");

        sb.append("<div class='info'><span class='label'>Transaction ID:</span><span class='value'>")
            .append(transaction.transactionId()).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Date:</span><span class='value'>")
            .append(formatDate(transaction.createdAt())).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Status:</span><span class='status status-")
            .append(transaction.status().name().toLowerCase()).append("'>")
            .append(transaction.status()).append("</span></div>\n");

        sb.append("<h2>Customer Information</h2>\n");
        sb.append("<div class='info'><span class='label'>Customer ID:</span><span class='value'>")
            .append(transaction.customer().customerId()).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Name:</span><span class='value'>")
            .append(transaction.customer().name()).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Email:</span><span class='value'>")
            .append(transaction.customer().email()).append("</span></div>\n");

        sb.append("<h2>Payment Details</h2>\n");
        sb.append("<div class='info'><span class='label'>Amount:</span><span class='value'>")
            .append(transaction.amount().format()).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Currency:</span><span class='value'>")
            .append(transaction.amount().currency()).append("</span></div>\n");
        sb.append("<div class='info'><span class='label'>Merchant Reference:</span><span class='value'>")
            .append(transaction.merchantReference()).append("</span></div>\n");

        if (transaction.gatewayTransactionId() != null) {
            sb.append("<div class='info'><span class='label'>Gateway ID:</span><span class='value'>")
                .append(transaction.gatewayTransactionId()).append("</span></div>\n");
        }

        if (transaction.completedAt() != null) {
            sb.append("<div class='info'><span class='label'>Completed:</span><span class='value'>")
                .append(formatDate(transaction.completedAt())).append("</span></div>\n");
        }

        if (transaction.failureReason() != null) {
            sb.append("<div class='info'><span class='label'>Failure Reason:</span><span class='value'>")
                .append(transaction.failureReason()).append("</span></div>\n");
        }

        if (!transaction.refunds().isEmpty()) {
            sb.append("<h2>Refunds</h2>\n");
            for (var refund : transaction.refunds()) {
                sb.append("<div class='refund'>\n");
                sb.append("<div class='info'><span class='label'>Refund ID:</span><span class='value'>")
                    .append(refund.refundId()).append("</span></div>\n");
                sb.append("<div class='info'><span class='label'>Amount:</span><span class='value'>")
                    .append(refund.amount().format()).append("</span></div>\n");
                sb.append("<div class='info'><span class='label'>Status:</span><span class='value'>")
                    .append(refund.status()).append("</span></div>\n");
                if (refund.reason() != null) {
                    sb.append("<div class='info'><span class='label'>Reason:</span><span class='value'>")
                        .append(refund.reason()).append("</span></div>\n");
                }
                sb.append("<div class='info'><span class='label'>Created:</span><span class='value'>")
                    .append(formatDate(refund.createdAt())).append("</span></div>\n");
                if (refund.completedAt() != null) {
                    sb.append("<div class='info'><span class='label'>Completed:</span><span class='value'>")
                        .append(formatDate(refund.completedAt())).append("</span></div>\n");
                }
                sb.append("</div>\n");
            }
        }

        sb.append("<div class='footer'>Thank you for your business!</div>\n");
        sb.append("</body>\n</html>");

        return sb.toString();
    }

    private String formatDate(java.time.Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return DATE_FORMAT.format(instant);
    }
}
