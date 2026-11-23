package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;

class MonABankIbansTests {

    @ParameterizedTest(name = "La cuenta {0} es {1}")
    @CsvSource({
            "1, ES8099990001450000000001",
            "2, ES1099990001410000000002",
            "3, ES8899990001440000000003"})
    @DisplayName("El número secuencial de una cuenta da el mismo IBAN de Mon-A Bank que la demo")
    void el_numero_secuencial_da_el_iban_de_mon_a_bank(long number, String iban) {
        assertThat(MonABankIbans.forAccount(number)).isEqualTo(new AccountNumber(iban));
    }

    @Test
    @DisplayName("El número secuencial de una cuenta es positivo y cabe en diez dígitos")
    void el_numero_secuencial_es_positivo_y_cabe_en_diez_digitos() {
        assertThatIllegalArgumentException().isThrownBy(() -> MonABankIbans.forAccount(0));
        assertThatIllegalArgumentException().isThrownBy(() -> MonABankIbans.forAccount(10_000_000_000L));
    }
}
