package com.dawnrock.ddd.adapter.out.persistence;

import java.math.BigInteger;

import com.dawnrock.ddd.domain.model.AccountNumber;

/**
 * IBAN de Mon-A Bank a partir del número secuencial de la cuenta (#150, compartido por las numeraciones). Cada IBAN
 * lleva la entidad {@code 9999} de Mon-A Bank (inventada), la oficina {@code 0001}, los dígitos de control nacionales
 * calculados con el algoritmo del CCC español, el número de cuenta de 10 dígitos y los dígitos de control del IBAN
 * (ISO 13616, módulo 97).
 */
final class MonABankIbans {

    private static final String BANK = "9999";
    private static final String BRANCH = "0001";
    private static final int[] CCC_WEIGHTS = {1, 2, 4, 8, 5, 10, 9, 7, 3, 6};
    private static final long MAX_ACCOUNT = 9_999_999_999L;
    /** Módulo del dígito de control del CCC. */
    private static final int CCC_MODULUS = 11;
    /** Módulo de los dígitos de control del IBAN (ISO 13616). */
    private static final BigInteger IBAN_MODULUS = BigInteger.valueOf(97);
    /** Los dígitos de control del IBAN son esta base menos el resto módulo 97. */
    private static final int IBAN_CHECK_BASE = 98;

    private MonABankIbans() {
    }

    /** @throws IllegalArgumentException si el número no es positivo o no cabe en diez dígitos */
    static AccountNumber forAccount(long number) {
        if (number < 1 || number > MAX_ACCOUNT) {
            throw new IllegalArgumentException("El número de cuenta debe estar entre 1 y %d: %d"
                    .formatted(MAX_ACCOUNT, number));
        }
        String account = "%010d".formatted(number);
        String bban = BANK + BRANCH + controlDigit("00" + BANK + BRANCH) + controlDigit(account) + account;
        return new AccountNumber("ES" + ibanCheckDigits(bban) + bban);
    }

    /** Dígito de control del CCC: 11 menos el resto de la suma ponderada entre 11; 10 es 1 y 11 es 0. */
    private static String controlDigit(String tenDigits) {
        int sum = 0;
        for (int i = 0; i < CCC_WEIGHTS.length; i++) {
            sum += (tenDigits.charAt(i) - '0') * CCC_WEIGHTS[i];
        }
        return String.valueOf(asSingleDigit(CCC_MODULUS - sum % CCC_MODULUS));
    }

    /** El dígito de control del CCC es de una cifra: el 11 se escribe 0 y el 10 se escribe 1. */
    private static int asSingleDigit(int digit) {
        if (digit == CCC_MODULUS) {
            return 0;
        }
        return digit == CCC_MODULUS - 1 ? 1 : digit;
    }

    /** Dígitos de control del IBAN: 98 menos el resto módulo 97 del BBAN seguido de {@code ES00} en cifras. */
    private static String ibanCheckDigits(String bban) {
        BigInteger number = new BigInteger(bban + "142800");
        return "%02d".formatted(IBAN_CHECK_BASE - number.mod(IBAN_MODULUS).intValue());
    }
}
