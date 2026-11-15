package com.pronoide.ddd.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.pronoide.ddd.domain.model.Customer;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Clientes de Mon-A Bank en memoria. Guarda fuera del agregado la relación cliente-usuario, como
 * {@code InMemoryAccountOwnershipRepository} hace con las cuentas: el dominio no conoce a los usuarios. El documento
 * es único (R29).
 */
@Repository
public class InMemoryCustomerRepository {

    private final Map<TaxId, Customer> byTaxId = new ConcurrentHashMap<>();
    private final Map<String, Customer> byUsername = new ConcurrentHashMap<>();
    private final Map<TaxId, String> usernames = new ConcurrentHashMap<>();

    /**
     * Guarda al cliente y lo asocia al usuario con el que entra. Comprobar el documento y guardar es una sola
     * operación atómica (R29).
     *
     * @throws DuplicateCustomerException si otro cliente ya tiene ese documento
     */
    public synchronized void save(Customer customer, String username) {
        Objects.requireNonNull(customer, "El cliente a guardar es obligatorio");
        Objects.requireNonNull(username, "El usuario del cliente es obligatorio");
        Customer existing = byTaxId.get(customer.taxId());
        if (existing != null && !existing.equals(customer)) {
            throw new DuplicateCustomerException(customer.taxId());
        }
        byTaxId.put(customer.taxId(), customer);
        byUsername.put(username, customer);
        usernames.put(customer.taxId(), username);
    }

    public Optional<Customer> findByTaxId(TaxId taxId) {
        Objects.requireNonNull(taxId, "El documento es obligatorio");
        return Optional.ofNullable(byTaxId.get(taxId));
    }

    /** Usuario con el que entra el cliente del documento, si existe. */
    public Optional<String> usernameOf(TaxId taxId) {
        Objects.requireNonNull(taxId, "El documento es obligatorio");
        return Optional.ofNullable(usernames.get(taxId));
    }

    public Optional<Customer> findByUsername(String username) {
        Objects.requireNonNull(username, "El usuario es obligatorio");
        return Optional.ofNullable(byUsername.get(username));
    }
}
