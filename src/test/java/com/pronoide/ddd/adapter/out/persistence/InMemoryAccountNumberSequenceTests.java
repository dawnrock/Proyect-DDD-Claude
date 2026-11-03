package com.pronoide.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.AccountNumberSequence;

class InMemoryAccountNumberSequenceTests {

    private static final int[] CCC_WEIGHTS = {1, 2, 4, 8, 5, 10, 9, 7, 3, 6};

    private final AccountNumberSequence sequence = new InMemoryAccountNumberSequence();

    @Test
    @DisplayName("Los números consecutivos son distintos")
    void los_numeros_consecutivos_son_distintos() {
        assertThat(sequence.next()).isNotEqualTo(sequence.next());
    }

    @Test
    @DisplayName("El IBAN generado es español y lleva el banco 2100 y la oficina 0418")
    void el_iban_generado_lleva_banco_y_oficina() {
        String iban = sequence.next().value();

        assertThat(iban).startsWith("ES").hasSize(24);
        assertThat(iban.substring(4, 8)).isEqualTo("2100");
        assertThat(iban.substring(8, 12)).isEqualTo("0418");
    }

    @Test
    @DisplayName("El IBAN generado supera la comprobación módulo 97")
    void el_iban_generado_supera_el_modulo_97() {
        IntStream.range(0, 50).forEach(i -> {
            String iban = sequence.next().value();
            String rearranged = iban.substring(4) + iban.substring(0, 4);
            String digits = rearranged.chars()
                    .mapToObj(c -> Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf((char) c))
                    .reduce("", String::concat);
            assertThat(new BigInteger(digits).mod(BigInteger.valueOf(97))).as(iban).isEqualTo(BigInteger.ONE);
        });
    }

    @Test
    @DisplayName("El IBAN generado lleva los dígitos de control nacionales correctos")
    void el_iban_generado_lleva_los_digitos_de_control_nacionales_correctos() {
        IntStream.range(0, 50).forEach(i -> {
            String iban = sequence.next().value();
            String entityAndBranch = iban.substring(4, 12);
            String controlDigits = iban.substring(12, 14);
            String account = iban.substring(14);
            assertThat(controlDigits).as(iban)
                    .isEqualTo(controlDigit("00" + entityAndBranch) + controlDigit(account));
        });
    }

    @Test
    @DisplayName("El cálculo de control coincide con un IBAN real conocido (ES91 2100 0418 4502 0005 1332)")
    void el_calculo_de_control_coincide_con_un_iban_conocido() {
        assertThat(controlDigit("0021000418") + controlDigit("0200051332")).isEqualTo("45");
    }

    @Test
    @DisplayName("Con hilos concurrentes no se repite ningún número")
    void con_hilos_concurrentes_no_se_repite_ningun_numero() {
        Set<AccountNumber> generated = ConcurrentHashMap.newKeySet();
        IntStream.range(0, 500).parallel().forEach(i -> generated.add(sequence.next()));

        assertThat(new HashSet<>(generated)).hasSize(500);
    }

    private static String controlDigit(String tenDigits) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (tenDigits.charAt(i) - '0') * CCC_WEIGHTS[i];
        }
        int digit = 11 - sum % 11;
        return String.valueOf(digit == 11 ? 0 : digit == 10 ? 1 : digit);
    }
}
