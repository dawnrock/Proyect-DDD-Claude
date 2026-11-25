package com.dawnrock.ddd.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.EmailAddress;
import com.dawnrock.ddd.domain.model.TaxId;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar, listar, ingresar, retirar y transferir. Carga y guarda el
 * agregado; las reglas de negocio siguen en {@code CheckingAccount}. Contratos:
 * {@code docs/contracts/open-query-list-accounts.md} y {@code account-factory.md} (apertura con la factoría).
 *
 * <p>Cada caso de uso recibe quién lo pide ({@link Caller}, contrato {@code docs/contracts/login-roles.md}): un
 * administrador accede a todas las cuentas y es el único que abre cuentas; un usuario solo a las suyas. Una cuenta
 * ajena, o sin propietario registrado, se trata para un usuario como inexistente ({@link AccountNotFoundException},
 * 404) para no revelar que existe.
 *
 * <p>Implementa los puertos de entrada {@link CheckingAccountCommands} y {@link CheckingAccountQueries}, que son lo
 * único que conocen los adaptadores de entrada. Solo conoce los puertos de salida ({@link CheckingAccountRepository},
 * {@link AccountOwnershipRepository} y {@link DomainEventPublisher}) y la factoría de cuentas; {@code config} le entrega
 * sus adaptadores.
 *
 * <p>Cada caso de uso, también las consultas, se ejecuta dentro de una {@link UnitOfWork} (#78): nadie ve una cuenta a
 * medio cambiar ni pisa los cambios de otro. Los eventos de dominio de cada cuenta se recogen al guardarla y se
 * publican cuando la unidad ha terminado.
 */
public class CheckingAccountService implements CheckingAccountCommands, CheckingAccountQueries {

    private final CheckingAccountRepository repository;
    private final AccountOwnershipRepository ownership;
    private final CheckingAccountFactory factory;
    private final UnitOfWork unitOfWork;
    private final AccountUnitOfWork accountUnit;
    private final AccountAccess access;
    private final UsernameAvailability usernames;
    private final CustomerRepository customers;
    private final CustomerOwnershipRepository customerOwnership;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration} (#20). */
    public CheckingAccountService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                  DomainEventPublisher publisher, CheckingAccountFactory factory,
                                  UnitOfWork unitOfWork, CustomerRepository customers,
                                  CustomerOwnershipRepository customerOwnership) {
        this.repository = repository;
        this.ownership = ownership;
        this.factory = factory;
        this.unitOfWork = unitOfWork;
        this.accountUnit = new AccountUnitOfWork(unitOfWork, repository, publisher);
        this.access = new AccountAccess(repository, ownership);
        this.usernames = new UsernameAvailability(customerOwnership, ownership);
        this.customers = customers;
        this.customerOwnership = customerOwnership;
    }

    @Override
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        return accountUnit.execute(changes -> open(caller, command, changes));
    }

    private AccountSummary open(Caller caller, OpenAccountCommand command, AccountChanges changes) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        TaxId taxId = new TaxId(command.holderTaxId());
        Optional<Customer> existing = customers.findByTaxId(taxId);
        Customer customer = existing.orElseGet(() -> newCustomer(taxId, command));
        String owner = existing.isPresent() ? ownerOfExisting(customer, command) : command.ownerUsername();

        CheckingAccount opened = factory.open(customer, AccountViews.toAmount(command.initialDeposit(), command.currency()));
        requireUnused(opened.accountNumber());
        if (existing.isEmpty()) {
            if (usernames.isTaken(owner)) {
                throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(owner));
            }
            customers.save(customer);
            customerOwnership.assign(taxId, owner);
        }
        CheckingAccount account = changes.save(opened);
        ownership.assign(account.id(), owner);
        return AccountViews.toSummary(account);
    }

    /** Un cliente nuevo desde el alta del administrador: nombre, correo y usuario son obligatorios. */
    private static Customer newCustomer(TaxId taxId, OpenAccountCommand command) {
        if (command.holderName().isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio para un cliente nuevo");
        }
        if (command.holderEmail().isBlank()) {
            throw new IllegalArgumentException("El correo del titular es obligatorio para un cliente nuevo");
        }
        if (command.ownerUsername().isEmpty()) {
            throw new IllegalArgumentException("El usuario del titular es obligatorio para un cliente nuevo");
        }
        return Customer.register(command.holderName(), taxId, new EmailAddress(command.holderEmail()));
    }

    /**
     * El usuario de un cliente existente. Los datos del command van en blanco o coinciden con los del cliente: unos
     * distintos se rechazan en lugar de ignorarlos (como en {@code Monolithic}, decisión del usuario de 2026-11-13).
     */
    private String ownerOfExisting(Customer customer, OpenAccountCommand command) {
        String username = customerOwnership.usernameOf(customer.taxId()).orElseThrow(() ->
                new IllegalArgumentException("El cliente %s no tiene usuario asignado: no se le puede abrir otra cuenta"
                        .formatted(customer.taxId())));
        boolean nameMatches = command.holderName().isBlank()
                || command.holderName().strip().equals(customer.fullName());
        boolean emailMatches = command.holderEmail().isBlank()
                || new EmailAddress(command.holderEmail()).equals(customer.email());
        boolean usernameMatches = command.ownerUsername().isEmpty() || command.ownerUsername().equals(username);
        if (!nameMatches || !emailMatches || !usernameMatches) {
            throw new IllegalArgumentException(("El documento %s ya es de un cliente: deja en blanco nombre, correo y "
                    + "usuario, o indica los suyos").formatted(customer.taxId()));
        }
        return username;
    }

    /** R16: con la numeración generada es solo una red de seguridad ante un generador defectuoso. */
    private void requireUnused(AccountNumber accountNumber) {
        repository.findByAccountNumber(accountNumber).ifPresent(existing -> {
            throw new DuplicateAccountNumberException(accountNumber);
        });
    }

    /**
     * Todo o nada sin deshacer: la unidad de trabajo no tiene rollback, así que primero se construye la cuenta (el
     * dominio comprueba R1 y la divisa), luego se comprueba que el documento y el usuario estén libres (R30) y solo
     * entonces se guarda. Un fallo antes de guardar no deja ni cliente, ni propiedad, ni cuenta.
     */
    @Override
    public AccountSummary signUp(SignUpCommand command) {
        return accountUnit.execute(changes -> {
            TaxId taxId = new TaxId(command.taxId());
            Customer customer = Customer.register(command.fullName(), taxId, new EmailAddress(command.email()));
            CheckingAccount opened = factory.open(customer, AccountViews.toAmount(command.initialDeposit(), command.currency()));
            requireUnused(opened.accountNumber());
            customers.findByTaxId(taxId).ifPresent(existing -> {
                throw new DuplicateCustomerException(taxId);
            });
            if (usernames.isTaken(command.username())) {
                throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(command.username()));
            }
            customers.save(customer);
            customerOwnership.assign(taxId, command.username());
            CheckingAccount account = changes.save(opened);
            ownership.assign(account.id(), command.username());
            return AccountViews.toSummary(account);
        });
    }

    @Override
    public AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand command) {
        return accountUnit.execute(changes -> {
            Customer customer = customerOf(caller);
            CheckingAccount opened =
                    factory.open(customer, AccountViews.toAmount(command.initialDeposit(), command.currency()));
            requireUnused(opened.accountNumber());
            CheckingAccount account = changes.save(opened);
            ownership.assign(account.id(), caller.username());
            return AccountViews.toSummary(account);
        });
    }

    /** El cliente de quien lo pide; un administrador, aunque su usuario coincida con el de un cliente, no lo es. */
    private Customer customerOf(Caller caller) {
        if (caller.admin()) {
            throw new NotACustomerException(caller.username());
        }
        return customerOwnership.customerOf(caller.username())
                .flatMap(customers::findByTaxId)
                .orElseThrow(() -> new NotACustomerException(caller.username()));
    }

    @Override
    public AccountSummary regenerateAccountNumber(Caller caller, UUID id) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("generar un nuevo número de cuenta");
        }
        return accountUnit.execute(changes -> {
            CheckingAccount account = access.load(id);
            AccountNumber generated = factory.nextAccountNumber();
            requireUnused(generated);
            account.reassignAccountNumber(generated);
            return AccountViews.toSummary(changes.save(account));
        });
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
        return customers.findByTaxId(account.primaryHolder().taxId()).map(customer -> customer.email().value()).orElse("");
    }

}
