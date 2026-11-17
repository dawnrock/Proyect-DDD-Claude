package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
        assertThat(command.holderName()).isEmpty();
        assertThat(command.holderEmail()).isEmpty();
        assertThat(command.ownerUsername()).isEmpty();
        assertThat(command.initialDeposit()).isEqualByComparingTo("100.00");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una divisa ISO 4217 distinta de EUR se acepta: es el dominio quien la rechaza")
    void una_divisa_iso_4217_distinta_de_eur_se_acepta() {
        assertThat(validCommand().currency("USD").build().currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Los datos de un cliente nuevo se aceptan junto al documento")
    void los_datos_de_un_cliente_nuevo_se_aceptan() {
        OpenAccountCommand command = validCommand()
                .holderName("Ada Lovelace").holderEmail("ada@example.com").ownerUsername("ada").build();

        assertThat(command.holderName()).isEqualTo("Ada Lovelace");
        assertThat(command.holderEmail()).isEqualTo("ada@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ada", "ada@", "ada@example", "ada @example.com"})
    @DisplayName("Un correo indicado debe tener un formato válido")
    void un_correo_indicado_debe_tener_un_formato_valido(String email) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderEmail(email).build())
                .withMessage("El correo electrónico no es válido");
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

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El NIF del titular es obligatorio")
    void el_nif_del_titular_es_obligatorio(String taxId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderTaxId(taxId).build())
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567Z", "123456789", "ABCDEFGHZ", "12345678-Z"})
    @DisplayName("El documento debe ser un DNI o un NIE")
    void el_nif_debe_tener_8_digitos_y_una_letra(String taxId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderTaxId(taxId).build())
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @Test
    @DisplayName("El NIF admite espacios y minúsculas, que normaliza el dominio")
    void el_nif_admite_espacios_y_minusculas() {
        assertThat(validCommand().holderTaxId(" 12345678 z ").build().holderTaxId()).isEqualTo(" 12345678 z ");
    }

    /** El depósito inicial de las tres formas de abrir una cuenta se valida igual que el importe de un movimiento. */
    @Nested
    @DisplayName("Depósito inicial de cualquier apertura")
    class InitialDepositOfEveryOpening {

        private static final Function<String, Object> OPEN = deposit ->
                validCommand().initialDeposit(new BigDecimal(deposit)).build();

        private static final Function<String, Object> SIGN_UP = deposit -> SignUpCommand.builder()
                .fullName("Margaret Hamilton").taxId("X1234567L").email("margaret@example.com")
                .username("margaret").initialDeposit(new BigDecimal(deposit)).build();

        private static final Function<String, Object> ANOTHER = deposit -> OpenAnotherAccountCommand.builder()
                .initialDeposit(new BigDecimal(deposit)).build();

        @ParameterizedTest
        @ValueSource(strings = {"0.001", "0.004"})
        @DisplayName("Un depósito inicial que redondea a cero se rechaza en el command, antes de llegar al dominio")
        void un_deposito_inicial_que_redondea_a_cero_se_rechaza(String deposit) {
            for (Function<String, Object> opening : List.of(OPEN, SIGN_UP, ANOTHER)) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> opening.apply(deposit))
                        .withMessage("El depósito inicial debe ser positivo");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"1000000000.00", "999999999.995"})
        @DisplayName("Un depósito inicial que supera el máximo de 999.999.999,99 se rechaza")
        void un_deposito_inicial_que_supera_el_maximo_se_rechaza(String deposit) {
            for (Function<String, Object> opening : List.of(OPEN, SIGN_UP, ANOTHER)) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> opening.apply(deposit))
                        .withMessage("El depósito inicial no puede superar 999999999.99");
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.005", "999999999.99"})
        @DisplayName("Un depósito inicial entre el mínimo redondeado y el máximo se acepta: el mínimo de 50 EUR lo exige el dominio")
        void un_deposito_inicial_entre_los_limites_se_acepta(String deposit) {
            for (Function<String, Object> opening : List.of(OPEN, SIGN_UP, ANOTHER)) {
                assertThat(opening.apply(deposit)).isNotNull();
            }
        }
    }
}
