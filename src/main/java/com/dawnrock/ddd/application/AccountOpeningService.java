package com.dawnrock.ddd.application;

import java.util.UUID;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.EmailAddress;
import com.dawnrock.ddd.domain.model.TaxId;

/**
 * Casos de uso que abren cuentas: la apertura del administrador, el alta de un cliente nuevo, otra cuenta del cliente y
 * la regeneración del IBAN. Las cuentas se construyen con la factoría (contrato {@code docs/contracts/account-factory.md})
 * y quedan a nombre de su propietario. Contratos: {@code open-query-list-accounts.md} y {@code customer-sign-up.md}.
 *
 * <p>Cada caso de uso recibe quién lo pide ({@link Caller}, contrato {@code docs/contracts/login-roles.md}): solo un
 * administrador abre cuentas a otros y regenera IBAN; un cliente se abre otra cuenta a sí mismo. Se ejecuta dentro de
 * una unidad de trabajo que publica los eventos de las cuentas guardadas al terminar (#78, #165).
 *
 * <p>Implementa {@link AccountOpeningCommands}, lo único que conocen los adaptadores de entrada. Sin anotaciones de
 * Spring: lo construye {@code config} (#174).
 */
public class AccountOpeningService implements AccountOpeningCommands {

    private final AccountUnitOfWork accountUnit;
    private final AccountOpener opener;
    private final CustomerDirectory directory;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration}. */
    public AccountOpeningService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                 DomainEventPublisher publisher, CheckingAccountFactory factory,
                                 UnitOfWork unitOfWork, CustomerRepository customers,
                                 CustomerOwnershipRepository customerOwnership) {
        this.accountUnit = new AccountUnitOfWork(unitOfWork, repository, publisher);
        this.opener = new AccountOpener(factory, repository, ownership);
        this.directory = new CustomerDirectory(customers, customerOwnership, ownership);
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
        return directory.findByTaxId(taxId)
                .map(existing -> openForExisting(existing, command, changes))
                .orElseGet(() -> openForNew(newCustomer(taxId, command), command, changes));
    }

    /** Otra cuenta para un cliente que ya existe, a nombre del usuario que ya tiene. */
    private AccountSummary openForExisting(Customer customer, OpenAccountCommand command, AccountChanges changes) {
        String owner = ownerOfExisting(customer, command);
        CheckingAccount opened = opener.prepare(customer, command.initialDeposit(), command.currency());
        return opener.save(opened, owner, changes);
    }

    /** La primera cuenta de un cliente nuevo: se da de alta con su usuario solo si la cuenta se puede abrir. */
    private AccountSummary openForNew(Customer customer, OpenAccountCommand command, AccountChanges changes) {
        CheckingAccount opened = opener.prepare(customer, command.initialDeposit(), command.currency());
        directory.register(customer, command.ownerUsername());
        return opener.save(opened, command.ownerUsername(), changes);
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
        String username = directory.usernameOf(customer);
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
            CheckingAccount opened = opener.prepare(customer, command.initialDeposit(), command.currency());
            directory.requireNotRegistered(taxId);
            directory.register(customer, command.username());
            return opener.save(opened, command.username(), changes);
        });
    }

    @Override
    public AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand command) {
        return accountUnit.execute(changes -> {
            CheckingAccount opened = opener.prepare(directory.customerOf(caller), command.initialDeposit(), command.currency());
            return opener.save(opened, caller.username(), changes);
        });
    }

    @Override
    public AccountSummary regenerateAccountNumber(Caller caller, UUID id) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("generar un nuevo número de cuenta");
        }
        return accountUnit.execute(changes -> opener.reassignNumber(id, changes));
    }
}
