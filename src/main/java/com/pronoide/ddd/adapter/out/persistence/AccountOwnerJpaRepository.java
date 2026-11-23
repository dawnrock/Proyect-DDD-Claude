package com.pronoide.ddd.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la propiedad de las cuentas (#150). Solo lo usa {@link JpaAccountOwnershipRepository}. */
interface AccountOwnerJpaRepository extends JpaRepository<AccountOwnerEntity, UUID> {

    List<AccountOwnerEntity> findByUsername(String username);
}
