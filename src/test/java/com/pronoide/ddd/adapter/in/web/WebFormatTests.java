package com.pronoide.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WebFormatTests {

    private final WebFormat fmt = new WebFormat();

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource(delimiter = '|', value = {
            "1000.00  | EUR | 1.000,00 €",
            "250      | EUR | 250,00 €",
            "1234567.5| EUR | 1.234.567,50 €",
            "0.00     | EUR | 0,00 €",
            "-970.00  | EUR | −970,00 €"})
    @DisplayName("Un importe se muestra en formato español, con separador de miles y el símbolo de la divisa")
    void un_importe_se_muestra_en_formato_espanol(String amount, String currency, String expected) {
        assertThat(fmt.money(new BigDecimal(amount.strip()), currency.strip())).isEqualTo(expected);
    }

    @Test
    @DisplayName("Un importe en otra divisa usa su símbolo")
    void un_importe_en_otra_divisa_usa_su_simbolo() {
        assertThat(fmt.money(new BigDecimal("5.5"), "USD")).startsWith("5,50 ").doesNotContain("€");
    }

    @Test
    @DisplayName("Un movimiento muestra el importe con signo: + si suma y − si resta")
    void un_movimiento_muestra_el_importe_con_signo() {
        assertThat(fmt.signedMoney(new BigDecimal("25.00"), "EUR", true)).isEqualTo("+25,00 €");
        assertThat(fmt.signedMoney(new BigDecimal("1200.00"), "EUR", false)).isEqualTo("−1.200,00 €");
    }

    @Test
    @DisplayName("El IBAN abreviado muestra los cuatro primeros y los cuatro últimos caracteres")
    void el_iban_abreviado_muestra_los_cuatro_primeros_y_los_cuatro_ultimos() {
        assertThat(fmt.maskedIban("ES7921000813610123456789")).isEqualTo("ES79 •••• 6789");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"Grace Hopper, GH", "ada lovelace byron, AL", "'  Alan   Turing ', AT", "Ada, A"})
    @DisplayName("Las iniciales son las de las dos primeras palabras del titular, en mayúsculas")
    void las_iniciales_son_las_de_las_dos_primeras_palabras(String holder, String expected) {
        assertThat(fmt.initials(holder)).isEqualTo(expected);
    }
}
