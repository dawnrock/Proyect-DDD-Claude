package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonetaryAmountTests {

    private static final Currency DOLLAR = Currency.getInstance("USD");

    @Test
    @DisplayName("Dos importes con el mismo valor y la misma divisa son equivalentes")
    void twoAmountsWithTheSameValueAndCurrencyAreEqual() {
        MonetaryAmount cien = MonetaryAmount.euros("100");
        MonetaryAmount cienConDecimales = MonetaryAmount.euros("100.00");
        assertThat(cien).isEqualTo(cienConDecimales);
        assertThat(cien).hasSameHashCodeAs(cienConDecimales);
        assertThat(new HashSet<>(List.of(cien, cienConDecimales))).hasSize(1);
    }

    @Test
    @DisplayName("Un importe es inmutable: las operaciones devuelven nuevas instancias")
    void operationsReturnNewInstances() {
        MonetaryAmount saldo = MonetaryAmount.euros("100.00");
        MonetaryAmount resultado = saldo.add(MonetaryAmount.euros("25.50"));
        assertThat(resultado).isEqualTo(MonetaryAmount.euros("125.50"));
        assertThat(saldo).isEqualTo(MonetaryAmount.euros("100.00"));
        assertThat(resultado).isNotSameAs(saldo);
    }

    @Test
    @DisplayName("El importe se normaliza a los decimales de su divisa")
    void theAmountIsNormalisedToItsCurrencyDecimals() {
        MonetaryAmount importe = MonetaryAmount.euros("10.005");
        assertThat(importe.amount()).isEqualTo(new BigDecimal("10.01"));
        assertThat(importe).hasToString("10.01 EUR");
    }

    @Test
    @DisplayName("No se pueden combinar importes de divisas distintas")
    void amountsInDifferentCurrenciesCannotBeCombined() {
        MonetaryAmount euros = MonetaryAmount.euros("100.00");
        MonetaryAmount dolares = new MonetaryAmount(new BigDecimal("100.00"), DOLLAR);
        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> euros.add(dolares))
                .withMessageContaining("EUR")
                .withMessageContaining("USD");
    }

    @Test
    @DisplayName("Un importe no puede construirse sin divisa")
    void anAmountCannotBeBuiltWithoutACurrency() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new MonetaryAmount(BigDecimal.TEN, null))
                .withMessage("La divisa es obligatoria");
    }
}
