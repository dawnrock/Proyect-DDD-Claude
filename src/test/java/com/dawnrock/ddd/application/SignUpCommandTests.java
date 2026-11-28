package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.dawnrock.ddd.support.CommandBuilders;

/** Reglas de formato del command del alta de cliente (#103), antes de llegar al dominio. */
class SignUpCommandTests {

    private static CommandBuilders.SignUpBuilder validCommand() {
        return CommandBuilders.signUp()
                .fullName("Ada Lovelace")
                .taxId("12345678Z")
                .email("ada@example.com")
                .username("ada")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Un alta con todos sus datos válidos se acepta y la divisa es EUR")
    void aSignUpWithValidDataIsAcceptedInEuros() {
        SignUpCommand command = validCommand().build();

        assertThat(command.fullName()).isEqualTo("Ada Lovelace");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El nombre y apellidos son obligatorios")
    void theFullNameIsRequired(String fullName) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().fullName(fullName).build())
                .withMessage("El nombre y apellidos son obligatorios");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567Z", "ABCDEFGHZ", "12345678-Z"})
    @DisplayName("El documento debe ser un DNI o un NIE")
    void theTaxIdMustBeADniOrANie(String taxId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().taxId(taxId).build())
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ada", "ada@example", "ada @example.com"})
    @DisplayName("El correo es obligatorio y debe tener un formato válido")
    void theEmailIsRequiredAndMustHaveAValidFormat(String email) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().email(email).build())
                .withMessage("El correo electrónico no es válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ad", "Ada", "ada lovelace", "adá"})
    @DisplayName("El usuario es obligatorio y debe tener el formato de usuario")
    void theUsernameIsRequiredAndMustHaveTheUsernameFormat(String username) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().username(username).build())
                .withMessage(OpenAccountCommand.USERNAME_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido")
    void theCurrencyMustBeAValidIso4217Code(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().currency(currency).build())
                .withMessage("La divisa %s no es un código ISO 4217 válido".formatted(currency));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.004", "-10.00"})
    @DisplayName("El depósito inicial debe ser positivo una vez redondeado")
    void theInitialDepositMustBePositiveOnceRounded(String deposit) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(new BigDecimal(deposit)).build())
                .withMessage("El depósito inicial debe ser positivo");
    }

    @Test
    @DisplayName("El depósito inicial no puede superar el máximo técnico")
    void theInitialDepositCannotExceedTheTechnicalMaximum() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(new BigDecimal("1000000000.00")).build())
                .withMessage("El depósito inicial no puede superar 999999999.99");
    }

    @Test
    @DisplayName("Con varios datos mal, se informa la primera regla en orden: nombre, documento, correo, usuario, divisa y depósito")
    void withSeveralWrongFieldsTheFirstRuleInOrderIsReported() {
        CommandBuilders.SignUpBuilder command = CommandBuilders.signUp()
                .fullName("").taxId("x").email("x").username("X").currency("eur").initialDeposit(BigDecimal.ZERO);
        List<UnaryOperator<CommandBuilders.SignUpBuilder>> fixes = List.of(
                b -> b.fullName("Ada Lovelace"), b -> b.taxId("12345678Z"), b -> b.email("ada@example.com"),
                b -> b.username("ada"), b -> b.currency("EUR"));
        List<String> messages = List.of("El nombre y apellidos son obligatorios", "El documento debe ser un DNI o un NIE",
                "El correo electrónico no es válido", OpenAccountCommand.USERNAME_MESSAGE,
                "La divisa eur no es un código ISO 4217 válido", "El depósito inicial debe ser positivo");

        for (int i = 0; i < messages.size(); i++) {
            assertThatIllegalArgumentException().isThrownBy(command::build).withMessage(messages.get(i));
            if (i < fixes.size()) {
                fixes.get(i).apply(command);
            }
        }
    }
}
