package com.pronoide.ddd.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;

/**
 * Repositorio de cuentas corrientes en memoria (simulado), uno por agregado: los movimientos se
 * guardan dentro de la cuenta.
 *
 * <p>Guarda referencias, no copias: un cambio en una cuenta ya guardada se ve aunque no se vuelva
 * a llamar a {@link #save(CheckingAccount)}, cosa que una base de datos real no haría. Los casos de
 * uso deben llamar siempre a {@code save} después de modificar una cuenta.
 */
@Repository
public class InMemoryCheckingAccountRepository {

    private final Map<UUID, CheckingAccount> accounts = new ConcurrentHashMap<>();

    /**
     * Guarda la cuenta, o la sustituye si ya existe una con su id.
     *
     * @throws IllegalStateException si otra cuenta ya usa su número de cuenta (equivale a un índice
     *     único en una base de datos)
     */
    public synchronized CheckingAccount save(CheckingAccount account) {
        Objects.requireNonNull(account, "La cuenta a guardar es obligatoria");
        findByAccountNumber(account.accountNumber())
                .filter(existing -> !existing.id().equals(account.id()))
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Ya existe otra cuenta con el número %s".formatted(account.accountNumber()));
                });
        accounts.put(account.id(), account);
        return account;
    }

    public Optional<CheckingAccount> findById(UUID id) {
        Objects.requireNonNull(id, "El id de la cuenta es obligatorio");
        return Optional.ofNullable(accounts.get(id));
    }

    public Optional<CheckingAccount> findByAccountNumber(AccountNumber accountNumber) {
        Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio");
        return accounts.values().stream()
                .filter(account -> account.accountNumber().equals(accountNumber))
                .findFirst();
    }

    /** Todas las cuentas, ordenadas por número de cuenta, en una lista que no se puede modificar. */
    public List<CheckingAccount> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(account -> account.accountNumber().value()))
                .toList();
    }
}
