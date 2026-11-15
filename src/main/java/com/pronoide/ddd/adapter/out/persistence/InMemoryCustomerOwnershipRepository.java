package com.pronoide.ddd.adapter.out.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.pronoide.ddd.application.CustomerOwnershipRepository;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link CustomerOwnershipRepository}: con qué usuario entra cada
 * cliente. Un cliente tiene un único usuario y un usuario es de un único cliente (R30); comprobarlo y asignar es una
 * sola operación atómica.
 */
@Repository
class InMemoryCustomerOwnershipRepository implements CustomerOwnershipRepository {

    private final Map<TaxId, String> usernames = new HashMap<>();
    private final Map<String, TaxId> customers = new HashMap<>();

    @Override
    public synchronized void assign(TaxId customer, String username) {
        Objects.requireNonNull(customer, "El documento del cliente es obligatorio");
        Objects.requireNonNull(username, "El usuario del cliente es obligatorio");
        TaxId owner = customers.get(username);
        if (owner != null && !owner.equals(customer)) {
            throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(username));
        }
        String previous = usernames.put(customer, username);
        if (previous != null) {
            customers.remove(previous);
        }
        customers.put(username, customer);
    }

    @Override
    public synchronized Optional<String> usernameOf(TaxId customer) {
        Objects.requireNonNull(customer, "El documento del cliente es obligatorio");
        return Optional.ofNullable(usernames.get(customer));
    }

    @Override
    public synchronized Optional<TaxId> customerOf(String username) {
        Objects.requireNonNull(username, "El usuario es obligatorio");
        return Optional.ofNullable(customers.get(username));
    }
}
