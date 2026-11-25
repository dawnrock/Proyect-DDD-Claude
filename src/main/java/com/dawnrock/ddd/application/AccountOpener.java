package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;

/**
 * Los pasos comunes de abrir una cuenta (#174): construirla con la factoría, comprobar que su IBAN está libre (R16) y,
 * cuando el caso de uso ha hecho sus comprobaciones, guardarla a nombre de su propietario. También da a una cuenta un
 * IBAN nuevo (R32), con la misma comprobación. Los dos pasos van separados
 * porque la unidad de trabajo no tiene rollback: se construye, se comprueba todo y solo entonces se guarda.
 */
final class AccountOpener {

    private final CheckingAccountFactory factory;
    private final CheckingAccountRepository repository;
    private final AccountOwnershipRepository ownership;
    private final AccountAccess access;

    AccountOpener(CheckingAccountFactory factory, CheckingAccountRepository repository,
                  AccountOwnershipRepository ownership) {
        this.factory = factory;
        this.repository = repository;
        this.ownership = ownership;
        this.access = new AccountAccess(repository, ownership);
    }

    /** La cuenta nueva del cliente, todavía sin guardar; el dominio comprueba R1 y la divisa. */
    CheckingAccount prepare(Customer customer, BigDecimal initialDeposit, String currency) {
        CheckingAccount opened = factory.open(customer, AccountViews.toAmount(initialDeposit, currency));
        requireUnused(opened.accountNumber());
        return opened;
    }

    /** Guarda la cuenta preparada y la asigna a su propietario. */
    AccountSummary save(CheckingAccount opened, String owner, AccountChanges changes) {
        CheckingAccount account = changes.save(opened);
        ownership.assign(account.id(), owner);
        return AccountViews.toSummary(account);
    }

    /**
     * Sustituye el IBAN de la cuenta por otro que genera el banco, comprobado como libre (R32), y la guarda.
     *
     * @throws AccountNotFoundException si la cuenta no existe
     */
    AccountSummary reassignNumber(UUID id, AccountChanges changes) {
        CheckingAccount account = access.load(id);
        AccountNumber generated = factory.nextAccountNumber();
        requireUnused(generated);
        account.reassignAccountNumber(generated);
        return AccountViews.toSummary(changes.save(account));
    }

    /** R16: con la numeración generada es solo una red de seguridad ante un generador defectuoso. */
    private void requireUnused(AccountNumber accountNumber) {
        repository.findByAccountNumber(accountNumber).ifPresent(existing -> {
            throw new DuplicateAccountNumberException(accountNumber);
        });
    }
}
