package com.pronoide.ddd.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a las filas de los clientes (#150). Solo lo usa {@link JpaCustomerRepository}. */
interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

    Optional<CustomerEntity> findByTaxId(String taxId);
}
