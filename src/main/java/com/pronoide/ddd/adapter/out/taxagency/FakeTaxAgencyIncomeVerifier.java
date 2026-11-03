package com.pronoide.ddd.adapter.out.taxagency;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.IncomeVerifier;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Adaptador de salida simulado del puerto {@link IncomeVerifier}: una agencia tributaria falsa con una tabla fija para
 * los titulares de demostración. No consulta ningún servicio real.
 *
 * <ul>
 *   <li>{@code 87654321X} (Grace Hopper): 250.000,00 EUR</li>
 *   <li>{@code 12345678Z} (Ada Lovelace): 40.000,00 EUR</li>
 *   <li>{@code 11111111H} (Alan Turing): 25.000,00 EUR</li>
 * </ul>
 *
 * Cualquier otro NIF no se puede verificar (vacío).
 */
@Component
class FakeTaxAgencyIncomeVerifier implements IncomeVerifier {

    private static final Map<String, MonetaryAmount> INCOMES = Map.of(
            "87654321X", MonetaryAmount.euros("250000.00"),
            "12345678Z", MonetaryAmount.euros("40000.00"),
            "11111111H", MonetaryAmount.euros("25000.00"));

    @Override
    public Optional<MonetaryAmount> verifiedAnnualIncome(TaxId taxId) {
        Objects.requireNonNull(taxId, "El NIF es obligatorio");
        return Optional.ofNullable(INCOMES.get(taxId.value()));
    }
}
