package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTests {

    @Test
    @DisplayName("Un correo se normaliza: sin espacios alrededor y en minúsculas (R29)")
    void un_correo_se_normaliza() {
        EmailAddress email = new EmailAddress("  Ada.Lovelace@Example.COM ");
        assertThat(email.value()).isEqualTo("ada.lovelace@example.com");
        assertThat(email).isEqualTo(new EmailAddress("ada.lovelace@example.com")).hasToString("ada.lovelace@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ada", "ada@", "@example.com", "ada@example", "ada@@example.com",
            "ada lovelace@example.com", "ada@exam ple.com"})
    @DisplayName("Un correo sin arroba, sin dominio o con espacios no llega a construirse (R29)")
    void un_correo_mal_formado_no_llega_a_construirse(String value) {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EmailAddress(value))
                .withMessage("El correo electrónico no es válido");
    }

    @Test
    @DisplayName("El correo es obligatorio")
    void el_correo_es_obligatorio() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new EmailAddress(null))
                .withMessage("El correo electrónico es obligatorio");
    }
}
