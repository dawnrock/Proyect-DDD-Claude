package com.pronoide.ddd.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;

class TaxAgencyClientTests {

    private final TaxAgencyClient taxAgency = new TaxAgencyClient();

    @ParameterizedTest
    @CsvSource({"87654321X, 250000.00", "12345678Z, 40000.00", "11111111H, 25000.00"})
    @DisplayName("Los NIF de demostración devuelven sus ingresos anuales")
    void los_nif_de_demostracion_devuelven_sus_ingresos(String taxId, String income) {
        assertThat(taxAgency.verifiedAnnualIncome(new TaxId(taxId))).contains(MonetaryAmount.euros(income));
    }

    @Test
    @DisplayName("Un NIF desconocido no se puede verificar")
    void un_nif_desconocido_no_se_puede_verificar() {
        assertThat(taxAgency.verifiedAnnualIncome(new TaxId("99999999R"))).isEmpty();
    }

    @Test
    @DisplayName("El NIF es obligatorio para verificar los ingresos")
    void el_nif_es_obligatorio_para_verificar_los_ingresos() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> taxAgency.verifiedAnnualIncome(null))
                .withMessage("El NIF es obligatorio");
    }
}
