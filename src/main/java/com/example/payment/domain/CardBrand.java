package com.example.payment.domain;

/**
 * Supported payment card brands.
 * Aligned with FR-001 and FR-002: Credit and debit card acceptance.
 */
public enum CardBrand {
    VISA,
    MASTERCARD,
    AMERICAN_EXPRESS,
    DISCOVER,
    UNKNOWN
}
