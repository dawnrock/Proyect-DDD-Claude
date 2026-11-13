package com.pronoide.ddd.presentation.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.pronoide.ddd.application.OpenAccountCommandDef;

/**
 * Cuerpo de {@code POST /api/accounts}. Es un {@code record} y no un DTO de Immutables porque Hibernate
 * Validator solo ve las restricciones de campos y getters JavaBean. Aplica la misma validación que el
 * formulario web ("Shared presentation validation" del contrato), de modo que lo que acepta nunca hace
 * que el dominio lance {@code IllegalArgumentException}.
 *
 * <p>{@code currency} es opcional ({@code EUR}). El segmento no se pide: lo deciden los ingresos
 * verificados del titular, que se identifica por su NIF ({@code holderTaxId}), y el saldo.
 */
public record OpenAccountRequest(
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$",
                message = "El número de cuenta debe ser un IBAN español: ES y 22 dígitos")
        String accountNumber,

        @NotBlank(message = "El nombre del titular es obligatorio")
        String holderName,

        @NotBlank(message = "El NIF del titular es obligatorio")
        @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN,
                message = "El NIF del titular debe tener 8 dígitos y una letra")
        String holderTaxId,

        @NotNull(message = "El depósito inicial es obligatorio")
        @Positive(message = "El depósito inicial debe ser positivo")
        BigDecimal initialDeposit,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency,

        @NotBlank(message = "El usuario propietario es obligatorio")
        @Pattern(regexp = "[a-z0-9._-]{3,30}",
                message = "El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
        String ownerUsername) {
}
