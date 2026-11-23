package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountNumberTests {

    @Test
    @DisplayName("El número de cuenta se normaliza y equivale al mismo IBAN sin espacios")
    void el_numero_de_cuenta_se_normaliza() {
        AccountNumber conEspacios = new AccountNumber("ES91 2100 0418 4502 0005 1332");
        AccountNumber sinEspacios = new AccountNumber("ES9121000418450200051332");
        assertThat(conEspacios).isEqualTo(sinEspacios);
        assertThat(conEspacios).hasToString("ES9121000418450200051332");
    }

    @Test
    @DisplayName("Un IBAN con formato incorrecto no llega a construirse")
    void un_iban_con_formato_incorrecto_no_llega_a_construirse() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AccountNumber("1234567890"))
                .withMessageContaining("no es un IBAN español válido");
    }
}
