package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.Set;

import org.immutables.value.Value;

/**
 * Orden de abrir una cuenta corriente. Valida sus campos al construirse ({@code IllegalArgumentException}),
 * antes de llegar al dominio. Contrato: {@code docs/contracts/account-factory.md}.
 *
 * <p>El IBAN lo genera la factoría y el segmento lo decide a partir de los ingresos verificados del titular, que se
 * identifica por su NIF ({@code holderTaxId}). {@code currency} es un código ISO 4217 ({@code EUR} por defecto).
 *
 * <p>Transición (#18): {@code accountNumber} y {@code segment} siguen como campos opcionales y obsoletos hasta que la
 * web y el back usen el NIF; después se eliminan y {@code holderTaxId} pasa a ser obligatorio.
 */
@ApplicationValue
@Value.Immutable
public interface OpenAccountCommandDef {

    Set<String> SEGMENTS = Set.of("RETAIL", "HIGH_NET_WORTH");

    /** Formato de un nombre de usuario: 3-30 minúsculas, dígitos, punto, guion o guion bajo. */
    String USERNAME_PATTERN = "[a-z0-9._-]{3,30}";

    /** Formato de un NIF: 8 dígitos y una letra; se admiten espacios y minúsculas. No se comprueba la letra. */
    String TAX_ID_PATTERN = "^ *\\d{8} *[A-Za-z] *$";

    /** Obsoleto (#18): lo genera {@code AccountNumberSequence}. Vacío si no se indica. */
    @Deprecated
    @Value.Default
    default String accountNumber() {
        return "";
    }

    String holderName();

    /** NIF del titular, para verificar sus ingresos. Vacío por defecto solo durante la transición (#18). */
    @Value.Default
    default String holderTaxId() {
        return "";
    }

    /** Obsoleto (#18): lo decide la factoría. Vacío si no se indica. */
    @Deprecated
    @Value.Default
    default String segment() {
        return "";
    }

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    /**
     * Usuario (cliente) propietario de la cuenta: solo él y los administradores podrán verla y operarla. Vacío por
     * defecto en el command; {@code openAccount(Caller, ...)} lo exige.
     */
    @Value.Default
    default String ownerUsername() {
        return "";
    }

    @Value.Check
    default void validate() {
        if (!accountNumber().isEmpty() && accountNumber().isBlank()) {
            throw new IllegalArgumentException("El número de cuenta es obligatorio");
        }
        if (!holderTaxId().isEmpty() && !holderTaxId().matches(TAX_ID_PATTERN)) {
            throw new IllegalArgumentException("El NIF debe tener 8 dígitos y una letra");
        }
        if (holderName().isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio");
        }
        if (!segment().isEmpty() && !SEGMENTS.contains(segment())) {
            throw new IllegalArgumentException("El segmento debe ser RETAIL o HIGH_NET_WORTH");
        }
        if (initialDeposit().signum() <= 0) {
            throw new IllegalArgumentException("El depósito inicial debe ser positivo");
        }
        if (!ownerUsername().isEmpty() && !ownerUsername().matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "El usuario del titular debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones");
        }
        if (!CommandValidation.isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
    }
}
