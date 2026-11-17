package com.pronoide.ddd.domain.model;

import java.util.Optional;

/**
 * Puerto de salida del agregado {@link Customer}: lo que el núcleo necesita para guardar y recuperar clientes, sin
 * saber dónde se guardan. Lo implementa un adaptador de {@code adapter.out.persistence}. La relación del cliente con
 * su usuario no se guarda aquí: es identidad, no dominio ({@code CustomerOwnershipRepository}).
 *
 * <p>Contrato común a cualquier implementación: {@code CustomerRepositoryContract} (tests).
 */
public interface CustomerRepository {

    /**
     * Guarda al cliente, o lo sustituye si ya existe uno con su id. Comprobar el documento y guardar es una sola
     * operación atómica (R30).
     *
     * @throws DuplicateCustomerException si el documento ya es de otro cliente
     * @throws NullPointerException si el cliente es nulo
     */
    Customer save(Customer customer);

    /** @throws NullPointerException si el documento es nulo */
    Optional<Customer> findByTaxId(TaxId taxId);
}
