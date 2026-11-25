package com.dawnrock.ddd.application;

import java.util.UUID;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;

/**
 * Carga cuentas respetando su propiedad (ADR 0008, contrato {@code docs/contracts/login-roles.md}): un administrador
 * accede a todas; un usuario solo a las suyas. Una cuenta ajena, o sin propietario registrado, se trata para un usuario
 * como inexistente ({@link AccountNotFoundException}, 404) para no revelar que existe. Lo comparten los servicios de
 * aplicación (#174).
 */
final class AccountAccess {

    private final CheckingAccountRepository repository;
    private final AccountOwnershipRepository ownership;

    AccountAccess(CheckingAccountRepository repository, AccountOwnershipRepository ownership) {
        this.repository = repository;
        this.ownership = ownership;
    }

    /** La cuenta, si existe; sin mirar quién lo pide (solo para casos de uso de administrador). */
    CheckingAccount load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    /** La cuenta, si existe y quien lo pide puede verla. */
    CheckingAccount loadFor(Caller caller, UUID id) {
        CheckingAccount account = load(id);
        if (!canAccess(caller, id)) {
            throw new AccountNotFoundException(id);
        }
        return account;
    }

    /**
     * La cuenta de destino de una transferencia, por su número (que se normaliza como {@link AccountNumber}). Cualquier
     * cuenta existente puede recibir una transferencia, sea de quien sea.
     *
     * @throws TargetAccountNotFoundException si no existe ninguna cuenta con ese número
     */
    CheckingAccount loadTarget(String accountNumber) {
        AccountNumber targetNumber = new AccountNumber(accountNumber);
        return repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
    }

    boolean canAccess(Caller caller, UUID accountId) {
        return caller.admin() || ownership.ownerOf(accountId).filter(caller.username()::equals).isPresent();
    }
}
