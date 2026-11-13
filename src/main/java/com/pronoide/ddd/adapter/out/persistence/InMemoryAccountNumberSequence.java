package com.pronoide.ddd.adapter.out.persistence;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.AccountNumberSequence;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link AccountNumberSequence}: genera IBAN españoles válidos y
 * consecutivos. Es determinista y se reinicia con cada arranque.
 *
 * <p>Cada IBAN lleva la entidad {@code 9999} de Mon-A Bank (inventada), la oficina {@code 0001}, los dígitos de control nacionales calculados con
 * el algoritmo del CCC español, un número de cuenta secuencial de 10 dígitos (desde {@code 0000000001}) y los dígitos
 * de control del IBAN (ISO 13616, módulo 97). Es seguro entre hilos.
 */
@Component
class InMemoryAccountNumberSequence implements AccountNumberSequence {

    private static final String BANK = "9999";
    private static final String BRANCH = "0001";
    private static final int[] CCC_WEIGHTS = {1, 2, 4, 8, 5, 10, 9, 7, 3, 6};

    private final AtomicLong counter = new AtomicLong();

    @Override
    public AccountNumber next() {
        String account = "%010d".formatted(counter.incrementAndGet());
        String bban = BANK + BRANCH + controlDigit("00" + BANK + BRANCH) + controlDigit(account) + account;
        return new AccountNumber("ES" + ibanCheckDigits(bban) + bban);
    }

    /** Dígito de control del CCC: 11 menos el resto de la suma ponderada entre 11; 10 es 1 y 11 es 0. */
    private static String controlDigit(String tenDigits) {
        int sum = 0;
        for (int i = 0; i < CCC_WEIGHTS.length; i++) {
            sum += (tenDigits.charAt(i) - '0') * CCC_WEIGHTS[i];
        }
        int digit = 11 - sum % 11;
        return String.valueOf(digit == 11 ? 0 : digit == 10 ? 1 : digit);
    }

    /** Dígitos de control del IBAN: 98 menos el resto módulo 97 del BBAN seguido de {@code ES00} en cifras. */
    private static String ibanCheckDigits(String bban) {
        BigInteger number = new BigInteger(bban + "142800");
        return "%02d".formatted(98 - number.mod(BigInteger.valueOf(97)).intValue());
    }
}
