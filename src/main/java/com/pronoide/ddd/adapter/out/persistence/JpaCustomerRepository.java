package com.pronoide.ddd.adapter.out.persistence;

import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import com.pronoide.ddd.domain.model.Customer;
import com.pronoide.ddd.domain.model.CustomerRepository;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.EmailAddress;
import com.pronoide.ddd.domain.model.TaxId;

/**
 * Adaptador de salida JPA del puerto {@link CustomerRepository} (#150, ADR 0019). Que dos clientes no compartan
 * documento (R30) lo comprueba antes de guardar y lo respalda el índice único de la tabla.
 */
@RequiredArgsConstructor
class JpaCustomerRepository implements CustomerRepository {

    private final CustomerJpaRepository rows;

    @Override
    public Customer save(Customer customer) {
        Objects.requireNonNull(customer, "El cliente a guardar es obligatorio");
        rows.findByTaxId(customer.taxId().value())
                .filter(existing -> !existing.id.equals(customer.id()))
                .ifPresent(existing -> {
                    throw new DuplicateCustomerException(customer.taxId());
                });
        rows.save(new CustomerEntity(customer.id(), customer.taxId().value(), customer.fullName(),
                customer.email().value()));
        return customer;
    }

    @Override
    public Optional<Customer> findByTaxId(TaxId taxId) {
        Objects.requireNonNull(taxId, "El documento es obligatorio");
        return rows.findByTaxId(taxId.value()).map(row -> Customer.reconstitute(row.id, row.fullName,
                new TaxId(row.taxId), new EmailAddress(row.email)));
    }
}
