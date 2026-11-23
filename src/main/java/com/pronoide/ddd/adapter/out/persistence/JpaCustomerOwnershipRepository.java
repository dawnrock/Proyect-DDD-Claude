package com.pronoide.ddd.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import com.pronoide.ddd.application.CustomerOwnershipRepository;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Adaptador de salida JPA del puerto {@link CustomerOwnershipRepository} (#150, ADR 0019): con qué usuario entra cada
 * cliente. Que un usuario sea de un único cliente (R30) lo comprueba antes de asignar y lo respalda el índice único.
 */
@RequiredArgsConstructor
class JpaCustomerOwnershipRepository implements CustomerOwnershipRepository {

    private final CustomerUsernameJpaRepository rows;

    @Override
    public void assign(TaxId customer, String username) {
        Objects.requireNonNull(customer, "El documento del cliente es obligatorio");
        Objects.requireNonNull(username, "El usuario del cliente es obligatorio");
        rows.findByUsername(username)
                .filter(row -> !row.taxId.equals(customer.value()))
                .ifPresent(row -> {
                    throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(username));
                });
        rows.save(new CustomerUsernameEntity(customer.value(), username));
    }

    @Override
    public Optional<String> usernameOf(TaxId customer) {
        Objects.requireNonNull(customer, "El documento del cliente es obligatorio");
        return rows.findById(customer.value()).map(row -> row.username);
    }

    @Override
    public Optional<TaxId> customerOf(String username) {
        Objects.requireNonNull(username, "El usuario es obligatorio");
        return rows.findByUsername(username).map(row -> new TaxId(row.taxId));
    }
}
