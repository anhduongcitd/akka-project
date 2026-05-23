package com.example.payment.application;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import com.example.payment.domain.Money;
import com.typesafe.config.Config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Email service wrapper for AWS SES.
 * Sends transaction confirmation emails.
 * Aligned with FR-009: Send confirmation emails immediately after successful payments.
 */
public class EmailService {

    private final AmazonSimpleEmailService sesClient;
    private final String fromEmail;
    private final boolean testMode;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault());

    public EmailService(Config config) {
        this.fromEmail = config.getString("payment.email.from");

        // Check if in test mode (no real AWS credentials)
        boolean hasAwsConfig = config.hasPath("payment.aws.access-key") &&
                               config.hasPath("payment.aws.secret-key") &&
                               !config.getString("payment.aws.access-key").isEmpty();
        this.testMode = !hasAwsConfig;

        if (!testMode) {
            String accessKey = config.getString("payment.aws.access-key");
            String secretKey = config.getString("payment.aws.secret-key");
            String region = config.getString("payment.aws.region");

            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);

            this.sesClient = AmazonSimpleEmailServiceClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRegion(region)
                .build();
        } else {
            this.sesClient = null; // No SES client in test mode
        }
    }

    /**
     * Send payment confirmation email.
     */
    public CompletableFuture<Void> sendPaymentConfirmation(
        String toEmail,
        String customerName,
        String transactionId,
        Money amount,
        String merchantReference,
        Instant timestamp
    ) {
        // In test mode, don't send real emails
        if (testMode) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String subject = "Payment Confirmation - " + transactionId;
            String body = buildPaymentConfirmationBody(
                customerName,
                transactionId,
                amount,
                merchantReference,
                timestamp
            );

            sendEmail(toEmail, subject, body);
        });
    }

    /**
     * Send refund notification email.
     */
    public CompletableFuture<Void> sendRefundNotification(
        String toEmail,
        String customerName,
        String transactionId,
        Money refundAmount,
        Instant timestamp
    ) {
        // In test mode, don't send real emails
        if (testMode) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String subject = "Refund Processed - " + transactionId;
            String body = buildRefundNotificationBody(
                customerName,
                transactionId,
                refundAmount,
                timestamp
            );

            sendEmail(toEmail, subject, body);
        });
    }

    /**
     * Send email via AWS SES.
     */
    private void sendEmail(String toEmail, String subject, String body) {
        try {
            SendEmailRequest request = new SendEmailRequest()
                .withDestination(new Destination().withToAddresses(toEmail))
                .withMessage(new Message()
                    .withBody(new Body().withHtml(
                        new Content().withCharset("UTF-8").withData(body)
                    ))
                    .withSubject(new Content().withCharset("UTF-8").withData(subject))
                )
                .withSource(fromEmail);

            sesClient.sendEmail(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private String buildPaymentConfirmationBody(
        String customerName,
        String transactionId,
        Money amount,
        String merchantReference,
        Instant timestamp
    ) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Payment Confirmation</h2>
                <p>Dear %s,</p>
                <p>Your payment has been successfully processed.</p>

                <table style="border-collapse: collapse; margin: 20px 0;">
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Transaction ID:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Amount:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Order Reference:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Date:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                </table>

                <p>Thank you for your payment!</p>
                <p style="color: #666; font-size: 12px;">
                    This is an automated email. Please do not reply.
                </p>
            </body>
            </html>
            """,
            customerName,
            transactionId,
            amount.format(),
            merchantReference,
            DATE_FORMATTER.format(timestamp)
        );
    }

    private String buildRefundNotificationBody(
        String customerName,
        String transactionId,
        Money refundAmount,
        Instant timestamp
    ) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Refund Notification</h2>
                <p>Dear %s,</p>
                <p>A refund has been processed for your transaction.</p>

                <table style="border-collapse: collapse; margin: 20px 0;">
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Transaction ID:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Refund Amount:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Date:</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                </table>

                <p>The refund will be credited to your original payment method within 5-10 business days.</p>
                <p style="color: #666; font-size: 12px;">
                    This is an automated email. Please do not reply.
                </p>
            </body>
            </html>
            """,
            customerName,
            transactionId,
            refundAmount.format(),
            DATE_FORMATTER.format(timestamp)
        );
    }
}
