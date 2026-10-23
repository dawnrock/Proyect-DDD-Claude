package com.pronoide.ddd.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

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

    public CheckingAccount save(CheckingAccount account) {
        Objects.requireNonNull(account, "La cuenta a guardar es obligatoria");
        accounts.put(account.id(), account);
        return account;
    }

    public Optional<CheckingAccount> findById(UUID id) {
        Objects.requireNonNull(id, "El id de la cuenta es obligatorio");
        return Optional.ofNullable(accounts.get(id));
    }
}
