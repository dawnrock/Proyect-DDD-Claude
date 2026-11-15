package com.pronoide.ddd.adapter.out.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.pronoide.ddd.domain.model.Customer;
import com.pronoide.ddd.domain.model.CustomerRepository;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Adaptador de salida en memoria (simulado) del puerto {@link CustomerRepository}. El documento es único (R30):
 * comprobarlo y guardar es una sola operación atómica. No guarda la relación cliente-usuario, que es identidad y vive
 * en {@code InMemoryCustomerOwnershipRepository}.
 */
@Repository
class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<TaxId, Customer> byTaxId = new ConcurrentHashMap<>();

    @Override
    public synchronized Customer save(Customer customer) {
        Objects.requireNonNull(customer, "El cliente a guardar es obligatorio");
        Customer existing = byTaxId.get(customer.taxId());
        if (existing != null && !existing.equals(customer)) {
            throw new DuplicateCustomerException(customer.taxId());
        }
        byTaxId.put(customer.taxId(), customer);
        return customer;
    }

    @Override
    public Optional<Customer> findByTaxId(TaxId taxId) {
        Objects.requireNonNull(taxId, "El documento es obligatorio");
        return Optional.ofNullable(byTaxId.get(taxId));
    }
}
