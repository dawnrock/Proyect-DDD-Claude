package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAccountCommandTests {

    private static OpenAccountCommand.Builder validCommand() {
        return OpenAccountCommand.builder()
                .holderTaxId("12345678Z")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Basta el documento y el depósito: nombre, correo y usuario quedan en blanco y la divisa es EUR")
    void basta_el_documento_y_el_deposito() {
        OpenAccountCommand command = validCommand().build();

        assertThat(command.holderTaxId()).isEqualTo("12345678Z");
        assertThat(command.initialDeposit()).isEqualByComparingTo("100.00");
        assertThat(command.currency()).isEqualTo("EUR");
        assertThat(command.holderName()).isEmpty();
        assertThat(command.holderEmail()).isEmpty();
        assertThat(command.ownerUsername()).isEmpty();
    }

    @Test
    @DisplayName("Una divisa ISO 4217 distinta de EUR se acepta: es el dominio quien la rechaza")
    void una_divisa_iso_4217_distinta_de_eur_se_acepta() {
        assertThat(validCommand().currency("USD").build().currency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ada", "ada@", "ada@example", "ada @example.com"})
    @DisplayName("Un correo indicado debe tener un formato válido")
    void un_correo_indicado_debe_tener_un_formato_valido(String email) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderEmail(email).build())
                .withMessage("El correo electrónico no es válido");
    }

    @Test
    @DisplayName("Los datos de un cliente nuevo se aceptan junto al documento")
    void los_datos_de_un_cliente_nuevo_se_aceptan() {
        OpenAccountCommand command = validCommand()
                .holderName("Ada Lovelace").holderEmail("ada@example.com").ownerUsername("ada").build();

        assertThat(command.holderEmail()).isEqualTo("ada@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567Z", "123456789Z", "12345678", "12345678-Z", "W1234567L", "X123456L"})
    @DisplayName("El documento del titular debe ser un DNI o un NIE")
    void el_documento_del_titular_debe_ser_un_dni_o_un_nie(String holderTaxId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderTaxId(holderTaxId).build())
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @Test
    @DisplayName("Un NIE con el formato correcto se acepta")
    void un_nie_con_el_formato_correcto_se_acepta() {
        assertThat(validCommand().holderTaxId("x1234567l").build().holderTaxId()).isEqualTo("x1234567l");
    }

    @Test
    @DisplayName("Un NIF con espacios o en minúsculas se acepta: lo normaliza el dominio")
    void un_nif_con_espacios_o_en_minusculas_se_acepta() {
        assertThat(validCommand().holderTaxId(" 12345678 z ").build().holderTaxId()).isEqualTo(" 12345678 z ");
    }

    @ParameterizedTest
    @CsvSource({"0", "0.00", "-1", "-50.00"})
    @DisplayName("El depósito inicial debe ser positivo")
    void el_deposito_inicial_debe_ser_positivo(String deposit) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(new BigDecimal(deposit)).build())
                .withMessage("El depósito inicial debe ser positivo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ", "EU", "EURO"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido")
    void la_divisa_debe_ser_un_codigo_iso_4217_valido(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().currency(currency).build())
                .withMessage("La divisa %s no es un código ISO 4217 válido".formatted(currency));
    }
}
