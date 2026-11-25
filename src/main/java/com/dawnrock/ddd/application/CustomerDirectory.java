package com.dawnrock.ddd.application;

import java.util.Optional;

import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.TaxId;

/**
 * Los clientes de monA Bank y su usuario (#103): buscarlos, darlos de alta con un usuario libre (R30) y saber de qué
 * cliente es quien pide un caso de uso. Lo usan los casos de uso que abren cuentas (#174).
 */
final class CustomerDirectory {

    private final CustomerRepository customers;
    private final CustomerOwnershipRepository customerOwnership;
    private final UsernameAvailability usernames;

    CustomerDirectory(CustomerRepository customers, CustomerOwnershipRepository customerOwnership,
                      AccountOwnershipRepository ownership) {
        this.customers = customers;
        this.customerOwnership = customerOwnership;
        this.usernames = new UsernameAvailability(customerOwnership, ownership);
    }

    Optional<Customer> findByTaxId(TaxId taxId) {
        return customers.findByTaxId(taxId);
    }

    /** @throws DuplicateCustomerException si ya hay un cliente con ese documento (R30) */
    void requireNotRegistered(TaxId taxId) {
        customers.findByTaxId(taxId).ifPresent(existing -> {
            throw new DuplicateCustomerException(taxId);
        });
    }

    /** Guarda el cliente nuevo con su usuario, que tiene que estar libre (R30). */
    void register(Customer customer, String username) {
        if (usernames.isTaken(username)) {
            throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(username));
        }
        customers.save(customer);
        customerOwnership.assign(customer.taxId(), username);
    }

    /** El usuario de un cliente existente, que lo tiene que tener para abrirle otra cuenta. */
    String usernameOf(Customer customer) {
        return customerOwnership.usernameOf(customer.taxId()).orElseThrow(() ->
                new IllegalArgumentException("El cliente %s no tiene usuario asignado: no se le puede abrir otra cuenta"
                        .formatted(customer.taxId())));
    }

    /** El cliente de quien lo pide; un administrador, aunque su usuario coincida con el de un cliente, no lo es. */
    Customer customerOf(Caller caller) {
        if (caller.admin()) {
            throw new NotACustomerException(caller.username());
        }
        return customerOwnership.customerOf(caller.username())
                .flatMap(customers::findByTaxId)
                .orElseThrow(() -> new NotACustomerException(caller.username()));
    }
}
