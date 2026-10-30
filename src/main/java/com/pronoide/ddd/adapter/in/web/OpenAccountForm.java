package com.pronoide.ddd.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.pronoide.ddd.application.OpenAccountCommandDef;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de alta de cuenta. Aplica la misma validación que {@code OpenAccountRequest} (REST), según
 * "Shared presentation validation" de {@code docs/contracts/open-query-list-accounts.md}.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class OpenAccountForm {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$",
            message = "El número de cuenta debe ser ES seguido de 22 dígitos")
    private String accountNumber;

    @NotBlank(message = "El titular es obligatorio")
    private String holderName;

    /** Usuario (cliente) que verá y operará la cuenta; mismo formato que el command (contrato login-roles). */
    @NotBlank(message = "El usuario del titular es obligatorio")
    @Pattern(regexp = OpenAccountCommandDef.USERNAME_PATTERN,
            message = "El usuario del titular debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
    private String ownerUsername;

    @NotBlank(message = "El segmento es obligatorio")
    @Pattern(regexp = "RETAIL|HIGH_NET_WORTH", message = "El segmento debe ser Minorista o Alto patrimonio")
    private String segment;

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
