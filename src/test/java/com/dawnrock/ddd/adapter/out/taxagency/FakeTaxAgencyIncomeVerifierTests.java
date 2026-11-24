package com.dawnrock.ddd.adapter.out.taxagency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.dawnrock.ddd.domain.model.IncomeVerifier;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.TaxId;

class FakeTaxAgencyIncomeVerifierTests {

    private final IncomeVerifier verifier = new FakeTaxAgencyIncomeVerifier();

    @ParameterizedTest
    @CsvSource({"87654321X, 250000.00", "12345678Z, 40000.00", "11111111H, 25000.00"})
    @DisplayName("Los NIF de demostración devuelven sus ingresos anuales")
    void theDemoTaxIdsReturnTheirIncome(String taxId, String income) {
        assertThat(verifier.verifiedAnnualIncome(new TaxId(taxId))).contains(MonetaryAmount.euros(income));
    }

    @Test
    @DisplayName("Un NIF desconocido no se puede verificar")
    void anUnknownTaxIdCannotBeVerified() {
        assertThat(verifier.verifiedAnnualIncome(new TaxId("99999999R"))).isEmpty();
    }
}
