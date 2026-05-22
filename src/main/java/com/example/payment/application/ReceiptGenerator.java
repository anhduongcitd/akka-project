package com.example.payment.application;

import com.example.payment.domain.PaymentTransaction;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Receipt Generator Service.
 * Generates PDF receipts for payment transactions.
 * Note: This is a simplified implementation. In production, use a proper PDF library like iText or Apache PDFBox.
 */
public class ReceiptGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    /**
     * Generate receipt as HTML (can be converted to PDF).
     * In production, use a PDF library to generate actual PDF bytes.
     */
    public String generateReceiptHtml(PaymentTransaction transaction) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Receipt - %s</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        max-width: 800px;
                        margin: 40px auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .receipt {
                        background-color: white;
                        padding: 40px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px solid #333;
                        padding-bottom: 20px;
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        margin: 0;
                        color: #333;
                    }
                    .section {
                        margin-bottom: 30px;
                    }
                    .section-title {
                        font-weight: bold;
                        color: #666;
                        margin-bottom: 10px;
                        font-size: 14px;
                        text-transform: uppercase;
                    }
                    .info-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        border-bottom: 1px solid #eee;
                    }
                    .info-label {
                        color: #666;
                    }
                    .info-value {
                        font-weight: bold;
                        color: #333;
                    }
                    .total {
                        background-color: #f9f9f9;
                        padding: 15px;
                        margin-top: 20px;
                        border-radius: 4px;
                    }
                    .total .info-row {
                        border: none;
                        font-size: 18px;
                    }
                    .status {
                        display: inline-block;
                        padding: 4px 12px;
                        border-radius: 4px;
                        font-size: 12px;
                        font-weight: bold;
                    }
                    .status-succeeded {
                        background-color: #d4edda;
                        color: #155724;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 40px;
                        padding-top: 20px;
                        border-top: 1px solid #eee;
                        color: #666;
                        font-size: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="receipt">
                    <div class="header">
                        <h1>Payment Receipt</h1>
                        <p style="color: #666; margin-top: 10px;">Transaction ID: %s</p>
                    </div>

                    <div class="section">
                        <div class="section-title">Customer Information</div>
                        <div class="info-row">
                            <span class="info-label">Name:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Email:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Customer ID:</span>
                            <span class="info-value">%s</span>
                        </div>
                    </div>

                    <div class="section">
                        <div class="section-title">Transaction Details</div>
                        <div class="info-row">
                            <span class="info-label">Date:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Status:</span>
                            <span class="info-value">
                                <span class="status status-succeeded">%s</span>
                            </span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Order Reference:</span>
                            <span class="info-value">%s</span>
                        </div>
                        <div class="info-row">
                            <span class="info-label">Payment Method:</span>
                            <span class="info-value">Card</span>
                        </div>
                    </div>

                    <div class="total">
                        <div class="info-row">
                            <span class="info-label">Total Amount:</span>
                            <span class="info-value">%s</span>
                        </div>
                    </div>

                    <div class="footer">
                        <p>This is an automated receipt. No signature required.</p>
                        <p>For questions about this transaction, please contact support.</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            transaction.transactionId(),
            transaction.transactionId(),
            transaction.customer().name(),
            transaction.customer().email(),
            transaction.customer().customerId(),
            DATE_FORMATTER.format(transaction.createdAt()),
            transaction.status().name(),
            transaction.merchantReference(),
            transaction.amount().format()
        );
    }

    /**
     * Generate receipt as plain text.
     * Useful for email attachments or simple display.
     */
    public String generateReceiptText(PaymentTransaction transaction) {
        return String.format("""
            ═══════════════════════════════════════════════════════
                            PAYMENT RECEIPT
            ═══════════════════════════════════════════════════════

            Transaction ID: %s
            Date: %s

            ───────────────────────────────────────────────────────
            CUSTOMER INFORMATION
            ───────────────────────────────────────────────────────
            Name:         %s
            Email:        %s
            Customer ID:  %s

            ───────────────────────────────────────────────────────
            TRANSACTION DETAILS
            ───────────────────────────────────────────────────────
            Status:              %s
            Order Reference:     %s
            Payment Method:      Card

            ───────────────────────────────────────────────────────
            AMOUNT
            ───────────────────────────────────────────────────────
            Total Amount:        %s

            ═══════════════════════════════════════════════════════
            This is an automated receipt. No signature required.
            For questions, please contact support.
            ═══════════════════════════════════════════════════════
            """,
            transaction.transactionId(),
            DATE_FORMATTER.format(transaction.createdAt()),
            transaction.customer().name(),
            transaction.customer().email(),
            transaction.customer().customerId(),
            transaction.status().name(),
            transaction.merchantReference(),
            transaction.amount().format()
        );
    }
}
