package com.pronoide.ddd.adapter.out.persistence;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.AccountNumberSequence;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link AccountNumberSequence}: genera IBAN españoles válidos y
 * consecutivos. Es determinista y se reinicia con cada arranque.
 *
 * <p>El IBAN lo calcula {@link MonABankIbans} a partir de un número secuencial (desde {@code 0000000001}). Es seguro
 * entre hilos.
 */
@Component
class InMemoryAccountNumberSequence implements AccountNumberSequence {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public AccountNumber next() {
        return MonABankIbans.forAccount(counter.incrementAndGet());
    }
}
