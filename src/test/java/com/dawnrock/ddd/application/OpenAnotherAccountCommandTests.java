package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.dawnrock.ddd.support.CommandBuilders;

/** Reglas de formato del command de otra cuenta del cliente, antes de llegar al dominio. */
class OpenAnotherAccountCommandTests {

    private static CommandBuilders.OpenAnotherAccountBuilder validCommand() {
        return CommandBuilders.openAnotherAccount().initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Basta el depósito inicial y la divisa es EUR")
    void theInitialDepositIsEnoughAndTheCurrencyIsEuro() {
        assertThat(validCommand().build().currency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido, antes que el depósito")
    void theCurrencyMustBeAValidIso4217CodeBeforeTheDeposit(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().currency(currency).initialDeposit(BigDecimal.ZERO).build())
                .withMessage("La divisa %s no es un código ISO 4217 válido".formatted(currency));
    }

    @Test
    @DisplayName("El depósito inicial debe ser positivo")
    void theInitialDepositMustBePositive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(BigDecimal.ZERO).build())
                .withMessage("El depósito inicial debe ser positivo");
    }
}
