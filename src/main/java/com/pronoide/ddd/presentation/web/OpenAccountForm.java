package com.pronoide.ddd.presentation.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

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

    @NotBlank(message = "El segmento es obligatorio")
    @Pattern(regexp = "RETAIL|HIGH_NET_WORTH", message = "El segmento debe ser Minorista o Alto patrimonio")
    private String segment;

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
