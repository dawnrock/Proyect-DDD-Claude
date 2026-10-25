package com.pronoide.ddd.presentation.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Cuerpo de {@code POST /api/transfers} (esquema {@code TransferRequest} del OpenAPI). Es un {@code record} por la
 * misma razón que {@link MovementRequest}. Que origen y destino coincidan no se valida aquí: es una regla de dominio
 * (422). {@code currency} es opcional ({@code EUR} por defecto).
 */
public record TransferRequest(
        @NotNull(message = "La cuenta de origen es obligatoria")
        UUID sourceAccountId,

        @NotNull(message = "La cuenta de destino es obligatoria")
        UUID targetAccountId,

        @NotNull(message = "El importe es obligatorio")
        @Positive(message = "El importe debe ser positivo")
        BigDecimal amount,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency) {
}
