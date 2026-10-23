package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuplicateAccountNumberExceptionTests {

    private static final AccountNumber ACCOUNT_NUMBER = new AccountNumber("es91 2100 0418 4502 0005 1332");

    @Test
    @DisplayName("Un número de cuenta duplicado es una regla de negocio incumplida")
    void un_numero_de_cuenta_duplicado_es_una_regla_de_negocio_incumplida() {
        assertThat(new DuplicateAccountNumberException(ACCOUNT_NUMBER)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("El mensaje en español incluye el número de cuenta normalizado")
    void el_mensaje_en_espanol_incluye_el_numero_de_cuenta_normalizado() {
        assertThat(new DuplicateAccountNumberException(ACCOUNT_NUMBER))
                .hasMessage("Ya existe una cuenta con el número ES9121000418450200051332");
    }
}
