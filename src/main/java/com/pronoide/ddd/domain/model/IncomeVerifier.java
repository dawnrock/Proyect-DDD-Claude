package com.pronoide.ddd.domain.model;

import java.util.Optional;

/**
 * Puerto de salida: la verificación de ingresos del titular ante la agencia tributaria. Lo implementa un adaptador de
 * {@code adapter.out.taxagency}.
 */
public interface IncomeVerifier {

    /**
     * Ingresos anuales verificados del titular, o vacío si la agencia no puede verificarlos.
     *
     * @throws NullPointerException si el NIF es nulo
     */
    Optional<MonetaryAmount> verifiedAnnualIncome(TaxId taxId);
}
