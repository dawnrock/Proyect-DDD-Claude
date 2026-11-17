package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.pronoide.ddd.application.OpenAccountCommandDef;
import com.pronoide.ddd.application.SignUpCommandDef;
import com.pronoide.ddd.adapter.in.CustomerRegistration;

/**
 * Cuerpo de {@code POST /api/customers}. Es un {@code record} (no un DTO de Immutables) porque Hibernate Validator solo
 * ve las restricciones de campos y getters JavaBean. Aplica la validación compartida con el formulario web del alta
 * ("Shared input-adapter validation" del contrato). La letra de control del documento no se comprueba aquí: la comprueba
 * el dominio y responde 400. {@code currency} es opcional ({@code EUR}).
 */
public record SignUpRequest(
        @NotBlank(message = "El nombre y apellidos son obligatorios")
        String fullName,

        @NotBlank(message = "El documento es obligatorio")
        @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN, message = OpenAccountCommandDef.TAX_ID_MESSAGE)
        String taxId,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Pattern(regexp = SignUpCommandDef.EMAIL_PATTERN, message = "El correo electrónico no es válido")
        String email,

        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = OpenAccountCommandDef.USERNAME_PATTERN,
                message = "El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = CustomerRegistration.MINIMUM_PASSWORD_LENGTH,
                message = "La contraseña debe tener al menos " + CustomerRegistration.MINIMUM_PASSWORD_LENGTH
                        + " caracteres")
        String password,

        @NotNull(message = "El depósito inicial es obligatorio")
        @DecimalMin(value = "0.005", message = "El depósito inicial debe ser positivo")
        @DecimalMax(value = "999999999.99", message = "El depósito inicial no puede superar 999999999.99")
        BigDecimal initialDeposit,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency) {
}
