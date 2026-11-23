package com.pronoide.ddd.adapter.out.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;

/**
 * Adaptador de salida JPA del puerto {@link CheckingAccountRepository} (#150, ADR 0019): guarda cada cuenta en su fila
 * y sus movimientos en la suya. Devuelve copias: un cambio en una cuenta solo llega a la base al guardarla. Corre dentro
 * de la transacción de la unidad de trabajo.
 */
@Repository
@RequiredArgsConstructor
class JpaCheckingAccountRepository implements CheckingAccountRepository {

    private final CheckingAccountJpaRepository rows;

    @Override
    public CheckingAccount save(CheckingAccount account) {
        Objects.requireNonNull(account, "La cuenta a guardar es obligatoria");
        rows.findByAccountNumber(account.accountNumber().value())
                .filter(existing -> !existing.id.equals(account.id()))
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Ya existe otra cuenta con el número %s".formatted(account.accountNumber()));
                });
        CheckingAccountEntity entity = rows.findById(account.id())
                .orElseGet(() -> new CheckingAccountEntity(account.id()));
        rows.save(CheckingAccountMapper.copyInto(account, entity));
        return account;
    }

    @Override
    public Optional<CheckingAccount> findById(UUID id) {
        Objects.requireNonNull(id, "El id de la cuenta es obligatorio");
        return rows.findById(id).map(CheckingAccountMapper::toDomain);
    }

    @Override
    public Optional<CheckingAccount> findByAccountNumber(AccountNumber accountNumber) {
        Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio");
        return rows.findByAccountNumber(accountNumber.value()).map(CheckingAccountMapper::toDomain);
    }

    @Override
    public List<CheckingAccount> findAll() {
        return rows.findAllByOrderByAccountNumber().stream().map(CheckingAccountMapper::toDomain).toList();
    }
}
