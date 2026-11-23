package com.dawnrock.ddd.adapter.out.persistence;

import java.util.concurrent.atomic.AtomicLong;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.AccountNumberSequence;

/**
 * Solo para tests (#150, ADR 0019): la aplicación usa el adaptador JPA. Cumple el mismo contrato del puerto.
 *
 * <p>Doble de test en memoria del puerto {@link AccountNumberSequence}: genera IBAN españoles válidos y
 * consecutivos. Es determinista y se reinicia con cada arranque.
 *
 * <p>El IBAN lo calcula {@link MonABankIbans} a partir de un número secuencial (desde {@code 0000000001}). Es seguro
 * entre hilos.
 */
class InMemoryAccountNumberSequence implements AccountNumberSequence {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public AccountNumber next() {
        return MonABankIbans.forAccount(counter.incrementAndGet());
    }
}
