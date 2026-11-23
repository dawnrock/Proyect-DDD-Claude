package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaxIdTests {

    @Test
    @DisplayName("Un documento se normaliza: sin espacios y en mayúsculas")
    void un_nif_se_normaliza() {
        TaxId taxId = new TaxId(" 12345678 z ");
        assertThat(taxId.value()).isEqualTo("12345678Z");
        assertThat(taxId).isEqualTo(new TaxId("12345678Z")).hasToString("12345678Z");
    }

    @Test
    @DisplayName("La normalización no depende del idioma del sistema")
    void la_normalizacion_no_depende_del_idioma_del_sistema() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
            assertThat(new TaxId("x1234567l").value()).isEqualTo("X1234567L");
            assertThat(new TaxId("12345678z").value()).isEqualTo("12345678Z");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567Z", "123456789Z", "12345678", "ABCDEFGHZ", "12345678ZZ", "12345678-Z",
            "W1234567L", "X123456L", "X12345678L"})
    @DisplayName("Un documento que no es un DNI ni un NIE no llega a construirse")
    void un_nif_con_formato_incorrecto_no_llega_a_construirse(String value) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaxId(value))
                .withMessage("El documento debe ser un DNI o un NIE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678Z", "87654321X", "11111111H", "00000000T", "X1234567L", "Y1234567X", "Z1234567R"})
    @DisplayName("Un DNI o un NIE con la letra de control correcta es válido")
    void un_documento_con_la_letra_correcta_es_valido(String value) {
        assertThat(new TaxId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678A", "00000000A", "X1234567A", "Y1234567L", "Z1234567T"})
    @DisplayName("Un documento con la letra de control incorrecta no llega a construirse (R27)")
    void un_documento_con_la_letra_incorrecta_no_llega_a_construirse(String value) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaxId(value))
                .withMessage("La letra de control del documento %s no es correcta".formatted(value));
    }

    @Test
    @DisplayName("El NIF es obligatorio")
    void el_nif_es_obligatorio() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new TaxId(null))
                .withMessage("El NIF es obligatorio");
    }
}
