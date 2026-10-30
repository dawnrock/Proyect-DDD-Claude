package com.pronoide.ddd.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida del agregado {@link CheckingAccount}: lo que el núcleo necesita para guardar y recuperar cuentas,
 * sin saber dónde se guardan. Lo implementa un adaptador de {@code adapter.out.persistence}. Un repositorio por
 * agregado: los movimientos se guardan dentro de la cuenta.
 *
 * <p>Contrato común a cualquier implementación: {@code CheckingAccountRepositoryContract} (tests).
 */
public interface CheckingAccountRepository {

    /**
     * Guarda la cuenta, o la sustituye si ya existe una con su id.
     *
     * @throws NullPointerException si la cuenta es nula
     * @throws IllegalStateException si otra cuenta ya usa su número de cuenta (equivale a un índice único en una base
     *     de datos)
     */
    CheckingAccount save(CheckingAccount account);

    /** @throws NullPointerException si el id es nulo */
    Optional<CheckingAccount> findById(UUID id);

    /** @throws NullPointerException si el número de cuenta es nulo */
    Optional<CheckingAccount> findByAccountNumber(AccountNumber accountNumber);

    /** Todas las cuentas, ordenadas por número de cuenta, en una lista que no se puede modificar. */
    List<CheckingAccount> findAll();
}
