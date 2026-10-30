package com.pronoide.ddd.adapter.in.web;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Formatos de la web, usables desde las plantillas como {@code ${@fmt.money(...)}}: importes en formato español
 * ({@code 1.000,00 €}), importes con signo, IBAN abreviado e iniciales del titular. Solo recibe valores de las vistas
 * de {@code application}, nunca tipos del dominio.
 */
@Component("fmt")
public class WebFormat {

    private static final Locale SPANISH = Locale.of("es", "ES");
    private static final String MINUS = "−";

    /** {@code 1000.00 EUR} → {@code 1.000,00 €}; siempre con separador de miles, a diferencia de {@code es-ES}. */
    public String money(BigDecimal amount, String currency) {
        String formatted = number(amount.abs(), currency) + " " + symbol(currency);
        return amount.signum() < 0 ? MINUS + formatted : formatted;
    }

    /**
     * Importe de un movimiento: {@code +25,00 €} si suma al saldo, {@code −25,00 €} si resta. Quien llama decide si
     * suma ({@code credit}); la plantilla lo deduce del tipo (solo hay ingresos y retiradas) y no lo usa con rechazados.
     */
    public String signedMoney(BigDecimal amount, String currency, boolean credit) {
        return (credit ? "+" : MINUS) + money(amount.abs(), currency);
    }

    /** {@code ES7921000813610123456789} → {@code ES79 •••• 6789}. */
    public String maskedIban(String accountNumber) {
        if (accountNumber.length() < 8) {
            return accountNumber;
        }
        return accountNumber.substring(0, 4) + " •••• " + accountNumber.substring(accountNumber.length() - 4);
    }

    /** Iniciales de las dos primeras palabras del titular: {@code Grace Hopper} → {@code GH}. */
    public String initials(String holderName) {
        return Arrays.stream(holderName.strip().split("\\s+"))
                .limit(2)
                .map(word -> word.substring(0, 1).toUpperCase(SPANISH))
                .collect(Collectors.joining());
    }

    private static String number(BigDecimal amount, String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        DecimalFormat format = new DecimalFormat("#,##0" + (digits > 0 ? "." + "0".repeat(digits) : ""),
                DecimalFormatSymbols.getInstance(SPANISH));
        return format.format(amount);
    }

    private static String symbol(String currency) {
        return Currency.getInstance(currency).getSymbol(SPANISH);
    }
}
