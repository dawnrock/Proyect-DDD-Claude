package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de {@code POST /api/accounts/{id}/deposits} y {@code /withdrawals}. Es un {@code record} por la
 * misma razón que {@link OpenAccountRequest}. Un importe no positivo es un error de validación (400), no
 * una regla de dominio. {@code currency} es opcional ({@code EUR} por defecto).
 */
public record MovementRequest(
        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser positivo")
        BigDecimal amount,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency) {
}
