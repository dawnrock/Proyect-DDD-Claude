package com.pronoide.ddd.adapter.out.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.pronoide.ddd.application.AccountOwnershipRepository;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link AccountOwnershipRepository}: id de cuenta a nombre de
 * usuario. Guarda la relación fuera del agregado: el dominio no conoce a los usuarios. Contrato:
 * {@code docs/contracts/login-roles.md}.
 */
@Repository
public class InMemoryAccountOwnershipRepository implements AccountOwnershipRepository {

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
