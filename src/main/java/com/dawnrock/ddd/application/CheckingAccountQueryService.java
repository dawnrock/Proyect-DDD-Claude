package com.dawnrock.ddd.application;

import java.util.List;
import java.util.UUID;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CustomerRepository;

/**
 * Casos de uso que consultan cuentas: listado, detalle y si un usuario ya está tomado. Contrato:
 * {@code docs/contracts/open-query-list-accounts.md}.
 *
 * <p>Cada consulta recibe quién la pide ({@link Caller}, contrato {@code docs/contracts/login-roles.md}): un
 * administrador ve todas las cuentas; un usuario solo las suyas. Una cuenta ajena, o sin propietario registrado, se
 * trata para un usuario como inexistente ({@link AccountNotFoundException}, 404) para no revelar que existe. También
 * las consultas se ejecutan dentro de una {@link UnitOfWork} (#78): nadie ve una cuenta a medio cambiar.
 *
 * <p>Implementa {@link CheckingAccountQueries}, lo único que conocen los adaptadores de entrada. Sin anotaciones de
 * Spring: lo construye {@code config} (#174).
 */
public class CheckingAccountQueryService implements CheckingAccountQueries {

    private final CheckingAccountRepository repository;
    private final UnitOfWork unitOfWork;
    private final AccountAccess access;
    private final UsernameAvailability usernames;
    private final CustomerRepository customers;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration}. */
    public CheckingAccountQueryService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                       UnitOfWork unitOfWork, CustomerRepository customers,
                                       CustomerOwnershipRepository customerOwnership) {
        this.repository = repository;
        this.unitOfWork = unitOfWork;
        this.access = new AccountAccess(repository, ownership);
        this.usernames = new UsernameAvailability(customerOwnership, ownership);
        this.customers = customers;
    }

    @Override
    public boolean isUsernameTaken(String username) {
        return unitOfWork.execute(() -> usernames.isTaken(username));
    }

    @Override
    public List<AccountSummary> listAccounts(Caller caller) {
        return unitOfWork.execute(() -> repository.findAll().stream()
                .filter(account -> access.canAccess(caller, account.id()))
                .map(AccountViews::toSummary)
                .toList());
    }

    @Override
    public AccountDetails findAccount(Caller caller, UUID id) {
        return unitOfWork.execute(() -> {
            CheckingAccount account = access.loadFor(caller, id);
            return AccountViews.toDetails(account, holderEmail(account));
        });
    }

    private String holderEmail(CheckingAccount account) {
        return customers.findByTaxId(account.primaryHolder().taxId())
                .map(customer -> customer.email().value())
                .orElse("");
    }
}
