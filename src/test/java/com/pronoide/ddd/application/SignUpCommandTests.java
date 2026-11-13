package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignUpCommandTests {

    private static SignUpCommand.Builder validCommand() {
        return SignUpCommand.builder()
                .fullName("Margaret Hamilton")
                .taxId("00000000T")
                .email("margaret@example.com")
                .username("margaret")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Una orden de alta con todos los campos válidos se construye y usa EUR por defecto")
    void una_orden_de_alta_valida_se_construye_y_usa_eur_por_defecto() {
        SignUpCommand command = validCommand().build();

        assertThat(command.fullName()).isEqualTo("Margaret Hamilton");
        assertThat(command.username()).isEqualTo("margaret");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El nombre del cliente no puede estar en blanco")
    void el_nombre_no_puede_estar_en_blanco(String fullName) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().fullName(fullName).build())
                .withMessage("El nombre y apellidos son obligatorios");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567Z", "12345678", "W1234567L"})
    @DisplayName("El documento debe ser un DNI o un NIE")
    void el_documento_debe_ser_un_dni_o_un_nie(String taxId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().taxId(taxId).build())
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "margaret", "margaret@", "@example.com", "a b@example.com", "a@example"})
    @DisplayName("El correo debe tener arroba y dominio")
    void el_correo_debe_tener_arroba_y_dominio(String email) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().email(email).build())
                .withMessage("El correo electrónico no es válido");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "Margaret", "con espacio", "abcdefghijklmnopqrstuvwxyzabcde"})
    @DisplayName("El usuario debe cumplir el formato de usuario")
    void el_usuario_debe_cumplir_el_formato(String username) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().username(username).build())
                .withMessage("El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("El depósito inicial debe ser positivo")
    void el_deposito_inicial_debe_ser_positivo(String deposit) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(new BigDecimal(deposit)).build())
                .withMessage("El depósito inicial debe ser positivo");
    }

    @Test
    @DisplayName("La divisa debe ser un código ISO 4217")
    void la_divisa_debe_ser_un_codigo_iso_4217() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().currency("XYZ1").build())
                .withMessage("La divisa XYZ1 no es un código ISO 4217 válido");
    }

    @Test
    @DisplayName("Otra cuenta: solo el depósito, positivo y en una divisa ISO")
    void otra_cuenta_solo_el_deposito() {
        assertThat(OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal("50")).build().currency())
                .isEqualTo("EUR");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenAnotherAccountCommand.builder().initialDeposit(BigDecimal.ZERO).build())
                .withMessage("El depósito inicial debe ser positivo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenAnotherAccountCommand.builder().initialDeposit(BigDecimal.TEN)
                        .currency("XYZ1").build())
                .withMessage("La divisa XYZ1 no es un código ISO 4217 válido");
    }
}
