package com.dawnrock.ddd.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.AccountNumberSequence;

/**
 * Adaptador de salida del puerto {@link AccountNumberSequence} sobre la secuencia {@code account_number_seq} de la base
 * (#150, ADR 0019): los números no se repiten tras un reinicio ni entre transacciones, porque una secuencia no vuelve
 * atrás aunque la transacción que la usó se deshaga (puede dejar huecos).
 */
@Component
@RequiredArgsConstructor
class DatabaseAccountNumberSequence implements AccountNumberSequence {

    private final JdbcTemplate jdbc;

    @Override
    public AccountNumber next() {
        Long number = jdbc.queryForObject("select nextval('account_number_seq')", Long.class);
        return MonABankIbans.forAccount(number);
    }
}
