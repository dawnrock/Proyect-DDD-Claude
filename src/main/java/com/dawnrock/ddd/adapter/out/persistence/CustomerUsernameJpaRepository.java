package com.dawnrock.ddd.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la relación cliente-usuario (#150). Solo lo usa {@link JpaCustomerOwnershipRepository}. */
interface CustomerUsernameJpaRepository extends JpaRepository<CustomerUsernameEntity, String> {

    Optional<CustomerUsernameEntity> findByUsername(String username);
}
