package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de {@code POST /api/accounts}. Es un {@code record} y no un DTO de Immutables porque Hibernate
 * Validator solo ve las restricciones de campos y getters JavaBean. Aplica la misma validación que el
 * formulario web ("Shared presentation validation" del contrato), de modo que lo que acepta nunca hace
 * que el dominio lance {@code IllegalArgumentException}.
 *
 * <p>{@code segment} es un {@code String} y no un enum para que un valor inválido llegue al mapa de
 * errores por campo en lugar de fallar al leer el JSON. {@code currency} es opcional ({@code EUR}).
 */
public record OpenAccountRequest(
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$",
                message = "El número de cuenta debe ser un IBAN español: ES y 22 dígitos")
        String accountNumber,

        @NotBlank(message = "El nombre del titular es obligatorio")
        String holderName,

        @NotBlank(message = "El segmento es obligatorio")
        @Pattern(regexp = "RETAIL|HIGH_NET_WORTH", message = "El segmento debe ser RETAIL o HIGH_NET_WORTH")
        String segment,

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
