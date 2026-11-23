package com.dawnrock.ddd.adapter.out.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.dawnrock.ddd.application.AccountOwnershipRepository;

/**
 * Solo para tests (#150, ADR 0019): la aplicación usa el adaptador JPA. Cumple el mismo contrato del puerto.
 *
 * <p>Doble de test en memoria del puerto {@link AccountOwnershipRepository}: id de cuenta a nombre de
 * usuario. Guarda la relación fuera del agregado: el dominio no conoce a los usuarios. Contrato:
 * {@code docs/contracts/login-roles.md}.
 */
class InMemoryAccountOwnershipRepository implements AccountOwnershipRepository {

    private final Map<UUID, String> owners = new ConcurrentHashMap<>();

    @Override
    public void assign(UUID accountId, String username) {
        Objects.requireNonNull(accountId, "El id de la cuenta es obligatorio");
        Objects.requireNonNull(username, "El usuario propietario es obligatorio");
        owners.put(accountId, username);
    }

    @Override
    public Optional<String> ownerOf(UUID accountId) {
        Objects.requireNonNull(accountId, "El id de la cuenta es obligatorio");
        return Optional.ofNullable(owners.get(accountId));
    }

    @Override
    public Set<UUID> accountsOf(String username) {
        Objects.requireNonNull(username, "El usuario es obligatorio");
        return owners.entrySet().stream()
                .filter(entry -> entry.getValue().equals(username))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
