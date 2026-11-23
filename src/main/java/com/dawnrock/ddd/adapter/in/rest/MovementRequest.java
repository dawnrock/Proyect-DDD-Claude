package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code POST /api/accounts/{id}/deposits} y {@code /withdrawals}. Es un {@code record} por la
 * misma razón que {@link OpenAccountRequest}. Un importe no positivo es un error de validación (400), no
 * una regla de dominio. Admite de 0,005 (que se redondea a 0,01) a 999.999.999,99 (#108). {@code currency} es opcional
 * ({@code EUR} por defecto).
 */
public record MovementRequest(
        @NotNull(message = "El importe es obligatorio")
        @DecimalMin(value = "0.005", message = "El importe debe ser positivo")
        @DecimalMax(value = "999999999.99", message = "El importe no puede superar 999999999.99")
        BigDecimal amount,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency) {
}
