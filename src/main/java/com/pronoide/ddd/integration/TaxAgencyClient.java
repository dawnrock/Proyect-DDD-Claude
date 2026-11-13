package com.pronoide.ddd.integration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Agencia tributaria simulada: verifica los ingresos anuales del titular con una tabla fija para los titulares de
 * demostración. No consulta ningún servicio real. Vive en la capa {@code integration} (ADR 0011): solo la usa
 * {@code application} y solo conoce el dominio. Es una clase concreta, sin interfaz (ADR 0004); en la fase 2 su papel
 * lo hace el adaptador {@code FakeTaxAgencyIncomeVerifier} del puerto {@code IncomeVerifier}, con la misma tabla.
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
public class TaxAgencyClient {

    private static final Map<String, MonetaryAmount> INCOMES = Map.of(
            "87654321X", MonetaryAmount.euros("250000.00"),
            "12345678Z", MonetaryAmount.euros("40000.00"),
            "11111111H", MonetaryAmount.euros("25000.00"));

    /**
     * Ingresos anuales verificados del titular, o vacío si la agencia no puede verificarlos.
     *
     * @throws NullPointerException si el NIF es nulo
     */
    public Optional<MonetaryAmount> verifiedAnnualIncome(TaxId taxId) {
        Objects.requireNonNull(taxId, "El NIF es obligatorio");
        return Optional.ofNullable(INCOMES.get(taxId.value()));
    }
}
