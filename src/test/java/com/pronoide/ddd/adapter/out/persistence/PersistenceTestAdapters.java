package com.pronoide.ddd.adapter.out.persistence;

import com.pronoide.ddd.application.AccountOwnershipRepository;
import com.pronoide.ddd.application.UnitOfWork;
import com.pronoide.ddd.domain.model.AccountNumberSequence;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;

/**
 * Adaptadores de persistencia en memoria para los tests de otros paquetes. Los adaptadores son package-private: fuera
 * de {@code adapter.out.persistence} solo se conocen por su puerto, y así los devuelve esta clase.
 */
public final class PersistenceTestAdapters {

    private PersistenceTestAdapters() {
    }

    /** Un repositorio de cuentas en memoria, vacío. */
    public static CheckingAccountRepository accounts() {
        return new InMemoryCheckingAccountRepository();
    }

    /** Una numeración de cuentas en memoria, que empieza desde el primer número. */
    public static AccountNumberSequence accountNumbers() {
        return new InMemoryAccountNumberSequence();
    }

    /** La unidad de trabajo en memoria (un cerrojo para todos los casos de uso, #78). */
    public static UnitOfWork unitOfWork() {
        return new InMemoryUnitOfWork();
    }

    /** Un repositorio de propietarios de cuentas en memoria, vacío. */
    public static AccountOwnershipRepository ownership() {
        return new InMemoryAccountOwnershipRepository();
    }
}
