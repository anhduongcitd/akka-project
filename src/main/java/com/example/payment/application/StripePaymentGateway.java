package com.example.payment.application;

import com.example.payment.domain.Money;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.model.Token;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.RefundCreateParams;
import com.typesafe.config.Config;

import java.util.concurrent.CompletableFuture;

/**
 * Stripe payment gateway client wrapper.
 * Handles payment authorization, capture, and refunds via Stripe API.
 * Aligned with FR-004: Encrypt all payment data (handled by Stripe).
 * Aligned with FR-005: Never store raw card numbers (tokenization via Stripe).
 */
public class StripePaymentGateway {

    public record PaymentResult(
        String chargeId,
        boolean success,
        String failureReason
    ) {}

    public record RefundResult(
        String refundId,
        boolean success,
        String failureReason
    ) {}

    public StripePaymentGateway(Config config) {
        String apiKey = config.getString("payment.stripe.api-key");
        Stripe.apiKey = apiKey;
    }

    /**
     * Create and authorize a charge using a payment token.
     * @param token Stripe token (tok_*) from client-side tokenization
     * @param amount Payment amount
     * @param description Transaction description
     * @param idempotencyKey Unique key for duplicate prevention
     * @return CompletableFuture with payment result
     */
    public CompletableFuture<PaymentResult> authorizePayment(
        String token,
        Money amount,
        String description,
        String idempotencyKey
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChargeCreateParams params = ChargeCreateParams.builder()
                    .setAmount(convertToMinorUnits(amount))
                    .setCurrency(amount.currency().name().toLowerCase())
                    .setSource(token)
                    .setDescription(description)
                    .setCapture(true)  // Auto-capture for simplicity
                    .build();

                // Use idempotency key to prevent duplicate charges
                Charge charge = Charge.create(params,
                    com.stripe.net.RequestOptions.builder()
                        .setIdempotencyKey(idempotencyKey)
                        .build()
                );

                if ("succeeded".equals(charge.getStatus())) {
                    return new PaymentResult(charge.getId(), true, null);
                } else {
                    return new PaymentResult(
                        charge.getId(),
                        false,
                        charge.getFailureMessage()
                    );
                }
            } catch (StripeException e) {
                return new PaymentResult(null, false, e.getMessage());
            }
        });
    }

    /**
     * Create a refund for a previous charge.
     * @param chargeId Original Stripe charge ID
     * @param amount Refund amount (can be partial)
     * @param idempotencyKey Unique key for duplicate prevention
     * @return CompletableFuture with refund result
     */
    public CompletableFuture<RefundResult> refundPayment(
        String chargeId,
        Money amount,
        String idempotencyKey
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RefundCreateParams params = RefundCreateParams.builder()
                    .setCharge(chargeId)
                    .setAmount(convertToMinorUnits(amount))
                    .build();

                Refund refund = Refund.create(params,
                    com.stripe.net.RequestOptions.builder()
                        .setIdempotencyKey(idempotencyKey)
                        .build()
                );

                if ("succeeded".equals(refund.getStatus())) {
                    return new RefundResult(refund.getId(), true, null);
                } else {
                    return new RefundResult(
                        refund.getId(),
                        false,
                        refund.getFailureReason()
                    );
                }
            } catch (StripeException e) {
                return new RefundResult(null, false, e.getMessage());
            }
        });
    }

    /**
     * Synchronous refund method for workflow integration.
     * @param chargeId Original Stripe charge ID (stripePaymentIntentId)
     * @param amount Refund amount
     * @return Gateway refund ID
     * @throws RuntimeException if refund fails
     */
    public String refund(String chargeId, Money amount) {
        try {
            RefundResult result = refundPayment(chargeId, amount, java.util.UUID.randomUUID().toString())
                .get(30, java.util.concurrent.TimeUnit.SECONDS);

            if (result.success) {
                return result.refundId;
            } else {
                throw new RuntimeException("Refund failed: " + result.failureReason);
            }
        } catch (Exception e) {
            throw new RuntimeException("Refund error: " + e.getMessage(), e);
        }
    }

    /**
     * Convert Money amount to minor units (cents for USD, EUR, etc.)
     * Stripe expects amounts in smallest currency unit.
     */
    private Long convertToMinorUnits(Money amount) {
        int decimalPlaces = amount.currency().decimalPlaces();
        long multiplier = (long) Math.pow(10, decimalPlaces);
        return amount.value().multiply(new java.math.BigDecimal(multiplier)).longValue();
    }
}
