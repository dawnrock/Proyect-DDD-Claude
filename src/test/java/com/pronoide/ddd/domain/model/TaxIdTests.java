package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaxIdTests {

    @Test
    @DisplayName("Un NIF se normaliza: sin espacios y en mayúsculas")
    void un_nif_se_normaliza() {
        TaxId taxId = new TaxId(" 12345678 z ");
        assertThat(taxId.value()).isEqualTo("12345678Z");
        assertThat(taxId).isEqualTo(new TaxId("12345678Z")).hasToString("12345678Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567Z", "123456789Z", "12345678", "ABCDEFGHZ", "12345678ZZ", "12345678-Z"})
    @DisplayName("Un NIF que no son 8 dígitos y una letra no llega a construirse")
    void un_nif_con_formato_incorrecto_no_llega_a_construirse(String value) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TaxId(value))
                .withMessage("El NIF debe tener 8 dígitos y una letra");
    }

    @Test
    @DisplayName("El NIF es obligatorio")
    void el_nif_es_obligatorio() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new TaxId(null))
                .withMessage("El NIF es obligatorio");
    }
}
