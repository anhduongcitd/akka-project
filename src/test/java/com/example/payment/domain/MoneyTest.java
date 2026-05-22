package com.example.payment.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

public class MoneyTest {

    @Test
    public void shouldCreateMoneyWithValidAmount() {
        Money money = new Money(new BigDecimal("10.50"), Currency.USD);

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(money.currency()).isEqualTo(Currency.USD);
    }

    @Test
    public void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-5.00"), Currency.USD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    public void shouldRejectAmountExceedingMaximum() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1000000.00"), Currency.USD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum");
    }

    @Test
    public void shouldAddTwoAmountsWithSameCurrency() {
        Money money1 = new Money(new BigDecimal("10.50"), Currency.USD);
        Money money2 = new Money(new BigDecimal("5.25"), Currency.USD);

        Money result = money1.add(money2);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.75"));
        assertThat(result.currency()).isEqualTo(Currency.USD);
    }

    @Test
    public void shouldRejectAddingDifferentCurrencies() {
        Money usd = new Money(new BigDecimal("10.00"), Currency.USD);
        Money eur = new Money(new BigDecimal("10.00"), Currency.EUR);

        assertThatThrownBy(() -> usd.add(eur))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different currencies");
    }

    @Test
    public void shouldSubtractTwoAmounts() {
        Money money1 = new Money(new BigDecimal("10.50"), Currency.USD);
        Money money2 = new Money(new BigDecimal("5.25"), Currency.USD);

        Money result = money1.subtract(money2);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("5.25"));
    }

    @Test
    public void shouldRejectSubtractionResultingInNegative() {
        Money money1 = new Money(new BigDecimal("5.00"), Currency.USD);
        Money money2 = new Money(new BigDecimal("10.00"), Currency.USD);

        assertThatThrownBy(() -> money1.subtract(money2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }

    @Test
    public void shouldConvertToAnotherCurrency() {
        Money usd = new Money(new BigDecimal("100.00"), Currency.USD);
        BigDecimal exchangeRate = new BigDecimal("0.85"); // USD to EUR

        Money eur = usd.convert(Currency.EUR, exchangeRate);

        assertThat(eur.amount()).isEqualByComparingTo(new BigDecimal("85.00"));
        assertThat(eur.currency()).isEqualTo(Currency.EUR);
    }

    @Test
    public void shouldRejectInvalidExchangeRate() {
        Money usd = new Money(new BigDecimal("100.00"), Currency.USD);

        assertThatThrownBy(() -> usd.convert(Currency.EUR, BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be positive");
    }

    @Test
    public void shouldCompareAmounts() {
        Money money1 = new Money(new BigDecimal("10.00"), Currency.USD);
        Money money2 = new Money(new BigDecimal("20.00"), Currency.USD);

        assertThat(money1.isLessThanOrEqual(money2)).isTrue();
        assertThat(money2.isGreaterThan(money1)).isTrue();
    }

    @Test
    public void shouldFormatWithCurrencySymbol() {
        Money usd = new Money(new BigDecimal("10.50"), Currency.USD);
        Money jpy = new Money(new BigDecimal("1000"), Currency.JPY);

        assertThat(usd.format()).isEqualTo("$10.50");
        assertThat(jpy.format()).isEqualTo("¥1000");
    }

    @Test
    public void shouldScaleToCorrectDecimalPlaces() {
        Money usd = new Money(new BigDecimal("10.5"), Currency.USD);
        Money jpy = new Money(new BigDecimal("1000.99"), Currency.JPY);

        assertThat(usd.amount()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(jpy.amount()).isEqualByComparingTo(new BigDecimal("1001")); // Rounded
    }
}
