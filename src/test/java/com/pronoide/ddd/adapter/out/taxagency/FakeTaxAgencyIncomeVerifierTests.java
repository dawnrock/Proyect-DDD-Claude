package com.pronoide.ddd.adapter.out.taxagency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.domain.model.IncomeVerifier;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;

class FakeTaxAgencyIncomeVerifierTests {

    private final IncomeVerifier verifier = new FakeTaxAgencyIncomeVerifier();

    @ParameterizedTest
    @CsvSource({"87654321X, 250000.00", "12345678Z, 40000.00", "11111111H, 25000.00"})
    @DisplayName("Los NIF de demostración devuelven sus ingresos anuales")
    void los_nif_de_demostracion_devuelven_sus_ingresos(String taxId, String income) {
        assertThat(verifier.verifiedAnnualIncome(new TaxId(taxId))).contains(MonetaryAmount.euros(income));
    }

    @Test
    @DisplayName("Un NIF desconocido no se puede verificar")
    void un_nif_desconocido_no_se_puede_verificar() {
        assertThat(verifier.verifiedAnnualIncome(new TaxId("99999999R"))).isEmpty();
    }
}
