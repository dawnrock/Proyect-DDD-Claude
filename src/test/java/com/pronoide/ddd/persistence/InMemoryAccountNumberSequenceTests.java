package com.pronoide.ddd.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountNumber;

class InMemoryAccountNumberSequenceTests {

    private final InMemoryAccountNumberSequence sequence = new InMemoryAccountNumberSequence();

    @Test
    @DisplayName("El primer número es ES, dígitos de control, 99990001, dígitos de control nacionales y 0000000001 (R30)")
    void el_primer_numero_lleva_la_entidad_la_oficina_y_el_secuencial() {
        String iban = sequence.next().value();

        assertThat(iban).matches("ES\\d{2}99990001\\d{2}0000000001");
    }

    @Test
    @DisplayName("Los dígitos de control del IBAN son correctos (módulo 97)")
    void los_digitos_de_control_del_iban_son_correctos() {
        for (int i = 0; i < 20; i++) {
            String iban = sequence.next().value();
            String rearranged = iban.substring(4) + "1428" + iban.substring(2, 4);
            assertThat(new BigInteger(rearranged).mod(BigInteger.valueOf(97))).isEqualTo(BigInteger.ONE);
        }
    }

    @Test
    @DisplayName("Los dígitos de control nacionales siguen el algoritmo del CCC español")
    void los_digitos_de_control_nacionales_siguen_el_ccc() {
        // Entidad 9999, oficina 0001, cuenta 0000000001: DC1 sobre 00 9999 0001, DC2 sobre 0000000001.
        assertThat(controlDigit("0099990001") + controlDigit("0000000001"))
                .isEqualTo(sequence.next().value().substring(12, 14));
    }

    @Test
    @DisplayName("Los números son consecutivos y siempre de Mon-A Bank")
    void los_numeros_son_consecutivos_y_de_mon_a_bank() {
        AccountNumber first = sequence.next();
        AccountNumber second = sequence.next();

        assertThat(first).isNotEqualTo(second);
        assertThat(first.value()).endsWith("0000000001");
        assertThat(second.value()).endsWith("0000000002");
        assertThat(first.isMonABank()).isTrue();
        assertThat(second.isMonABank()).isTrue();
    }

    @Test
    @DisplayName("Con llamadas concurrentes nunca se repite un número")
    void con_llamadas_concurrentes_nunca_se_repite_un_numero() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = IntStream.range(0, 500)
                    .<Callable<String>>mapToObj(i -> () -> sequence.next().value())
                    .toList();
            Set<String> numbers = new HashSet<>();
            for (Future<String> future : executor.invokeAll(calls)) {
                numbers.add(future.get());
            }
            assertThat(numbers).hasSize(500);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String controlDigit(String tenDigits) {
        int[] weights = {1, 2, 4, 8, 5, 10, 9, 7, 3, 6};
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (tenDigits.charAt(i) - '0') * weights[i];
        }
        int digit = 11 - sum % 11;
        return String.valueOf(digit == 11 ? 0 : digit == 10 ? 1 : digit);
    }
}
