package com.pronoide.ddd.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import com.pronoide.ddd.application.AccountOwnershipRepository;

/**
 * Adaptador de salida JPA del puerto {@link AccountOwnershipRepository} (#150, ADR 0019): id de cuenta a nombre de
 * usuario, fuera del agregado, porque el dominio no conoce a los usuarios (ADR 0008).
 */
@RequiredArgsConstructor
class JpaAccountOwnershipRepository implements AccountOwnershipRepository {

    private final AccountOwnerJpaRepository rows;

    @Override
    public void assign(UUID accountId, String username) {
        Objects.requireNonNull(accountId, "El id de la cuenta es obligatorio");
        Objects.requireNonNull(username, "El usuario propietario es obligatorio");
        rows.save(new AccountOwnerEntity(accountId, username));
    }

    @Override
    public Optional<String> ownerOf(UUID accountId) {
        Objects.requireNonNull(accountId, "El id de la cuenta es obligatorio");
        return rows.findById(accountId).map(row -> row.username);
    }

    @Override
    public Set<UUID> accountsOf(String username) {
        Objects.requireNonNull(username, "El usuario es obligatorio");
        return rows.findByUsername(username).stream()
                .map(row -> row.accountId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
