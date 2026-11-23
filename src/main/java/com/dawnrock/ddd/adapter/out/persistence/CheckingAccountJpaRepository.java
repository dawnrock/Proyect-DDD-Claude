package com.dawnrock.ddd.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a las filas de las cuentas (#150). Solo lo usa {@link JpaCheckingAccountRepository}. */
interface CheckingAccountJpaRepository extends JpaRepository<CheckingAccountEntity, UUID> {

    Optional<CheckingAccountEntity> findByAccountNumber(String accountNumber);

    List<CheckingAccountEntity> findAllByOrderByAccountNumber();
}
