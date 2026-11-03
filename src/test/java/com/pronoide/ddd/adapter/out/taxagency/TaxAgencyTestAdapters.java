package com.pronoide.ddd.adapter.out.taxagency;

import com.pronoide.ddd.domain.model.IncomeVerifier;

/**
 * Adaptadores de la agencia tributaria para los tests de otros paquetes. El adaptador es package-private: fuera de
 * {@code adapter.out.taxagency} solo se conoce por su puerto, y así lo devuelve esta clase.
 */
public final class TaxAgencyTestAdapters {

    private TaxAgencyTestAdapters() {
    }

    /** La agencia simulada con su tabla fija de NIF de demostración. */
    public static IncomeVerifier incomeVerifier() {
        return new FakeTaxAgencyIncomeVerifier();
    }
}
