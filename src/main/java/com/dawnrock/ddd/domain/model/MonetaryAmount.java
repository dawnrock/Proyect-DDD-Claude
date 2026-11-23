package com.dawnrock.ddd.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record MonetaryAmount(BigDecimal amount, Currency currency) {

    private static final Currency EURO = Currency.getInstance("EUR");

    public MonetaryAmount {
        Objects.requireNonNull(amount, "El importe es obligatorio");
        Objects.requireNonNull(currency, "La divisa es obligatoria");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    public static MonetaryAmount euros(String amount) {
        return new MonetaryAmount(new BigDecimal(amount), EURO);
    }

    public static MonetaryAmount zero(Currency currency) {
        return new MonetaryAmount(BigDecimal.ZERO, currency);
    }

    public MonetaryAmount add(MonetaryAmount other) {
        requireSameCurrency(other);
        return new MonetaryAmount(amount.add(other.amount), currency);
    }

    public MonetaryAmount subtract(MonetaryAmount other) {
        requireSameCurrency(other);
        return new MonetaryAmount(amount.subtract(other.amount), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isLessThan(MonetaryAmount other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    private void requireSameCurrency(MonetaryAmount other) {
        Objects.requireNonNull(other, "El importe de la operación es obligatorio");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount + " " + currency.getCurrencyCode();
    }
}
