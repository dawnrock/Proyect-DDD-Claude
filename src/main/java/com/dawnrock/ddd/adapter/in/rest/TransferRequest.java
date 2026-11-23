package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code POST /api/transfers} (esquema {@code TransferRequest} del OpenAPI). Es un {@code record} por la
 * misma razón que {@link MovementRequest}. El destino se indica por su número de cuenta (IBAN), no por su id (#50). Que origen y destino coincidan no se valida aquí: es una regla de dominio
 * (422). {@code currency} es opcional ({@code EUR} por defecto).
 */
public record TransferRequest(
        @NotNull(message = "La cuenta de origen es obligatoria")
        UUID sourceAccountId,

        @NotBlank(message = "El número de la cuenta de destino es obligatorio")
        @Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$",
                message = "El número de cuenta de destino debe ser un IBAN español: ES y 22 dígitos")
        String targetAccountNumber,

        @NotNull(message = "El importe es obligatorio")
        @DecimalMin(value = "0.005", message = "El importe debe ser positivo")
        @DecimalMax(value = "999999999.99", message = "El importe no puede superar 999999999.99")
        BigDecimal amount,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency) {
}
