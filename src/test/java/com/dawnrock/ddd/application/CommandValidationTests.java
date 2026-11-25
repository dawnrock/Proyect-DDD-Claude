package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Comprobaciones de formato con nombre que comparten los commands (#181). */
class CommandValidationTests {

    private static final String DIGITS = "\\d{3}";

    @Test
    @DisplayName("Un texto que cumple el patrón pasa; si no lo cumple, se rechaza con el mensaje dado")
    void aTextMustMatchThePatternOrIsRejectedWithTheGivenMessage() {
        assertThatNoException().isThrownBy(() -> CommandValidation.requireMatching("123", DIGITS, "Mal"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandValidation.requireMatching("12a", DIGITS, "Mal"))
                .withMessage("Mal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123"})
    @DisplayName("Un texto opcional vacío o en blanco pasa sin mirar el patrón; uno que lo cumple, también")
    void anOptionalTextThatIsBlankOrMatchesPasses(String value) {
        assertThatNoException().isThrownBy(() -> CommandValidation.requireMatchingIfPresent(value, DIGITS, "Mal"));
    }

    @Test
    @DisplayName("Un texto opcional indicado que no cumple el patrón se rechaza con el mensaje dado")
    void anOptionalTextGivenThatDoesNotMatchIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandValidation.requireMatchingIfPresent("12a", DIGITS, "Mal"))
                .withMessage("Mal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Un texto obligatorio vacío o en blanco se rechaza con el mensaje dado")
    void aRequiredTextThatIsBlankIsRejected(String value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandValidation.requireNotBlank(value, "Obligatorio"))
                .withMessage("Obligatorio");
        assertThatNoException().isThrownBy(() -> CommandValidation.requireNotBlank("Ada", "Obligatorio"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ", "EURO"})
    @DisplayName("Una divisa que no es un código ISO 4217 se rechaza con el mensaje de divisa")
    void aCurrencyThatIsNotIso4217IsRejected(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CommandValidation.requireIsoCurrency(currency))
                .withMessage("La divisa %s no es un código ISO 4217 válido".formatted(currency));
        assertThatNoException().isThrownBy(() -> CommandValidation.requireIsoCurrency("USD"));
    }
}
