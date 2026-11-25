package com.dawnrock.ddd.application;

import java.time.Clock;
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
import com.dawnrock.ddd.domain.model.Transaction;
import com.dawnrock.ddd.domain.service.MoneyTransferService;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

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
    private final MoneyTransferService transferService;
    private final SegmentReviewService segmentReview;
    private final Clock clock;
    private final UnitOfWork unitOfWork;
    private final AccountUnitOfWork accountUnit;
    private final CustomerRepository customers;
    private final CustomerOwnershipRepository customerOwnership;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration} (#20). */
    public CheckingAccountService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                  DomainEventPublisher publisher, CheckingAccountFactory factory,
                                  MoneyTransferService transferService, SegmentReviewService segmentReview,
                                  Clock clock, UnitOfWork unitOfWork, CustomerRepository customers,
                                  CustomerOwnershipRepository customerOwnership) {
        this.repository = repository;
        this.ownership = ownership;
        this.factory = factory;
        this.transferService = transferService;
        this.segmentReview = segmentReview;
        this.clock = clock;
        this.unitOfWork = unitOfWork;
        this.accountUnit = new AccountUnitOfWork(unitOfWork, repository, publisher);
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
            if (usernameTaken(owner)) {
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
            if (usernameTaken(command.username())) {
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
            CheckingAccount account = load(id);
            AccountNumber generated = factory.nextAccountNumber();
            requireUnused(generated);
            account.reassignAccountNumber(generated);
            return AccountViews.toSummary(changes.save(account));
        });
    }

    @Override
    public boolean isUsernameTaken(String username) {
        return unitOfWork.execute(() -> usernameTaken(username));
    }

    /** Un usuario está tomado si es de un cliente o ya es propietario de alguna cuenta (R30). */
    private boolean usernameTaken(String username) {
        return customerOwnership.customerOf(username).isPresent() || !ownership.accountsOf(username).isEmpty();
    }

    @Override
    public List<AccountSummary> listAccounts(Caller caller) {
        return unitOfWork.execute(() -> repository.findAll().stream()
                .filter(account -> canAccess(caller, account.id()))
                .map(AccountViews::toSummary)
                .toList());
    }

    @Override
    public AccountDetails findAccount(Caller caller, UUID id) {
        return unitOfWork.execute(() -> {
            CheckingAccount account = loadFor(caller, id);
            return AccountViews.toDetails(account, holderEmail(account));
        });
    }

    @Override
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        return accountUnit.execute(changes -> {
            CheckingAccount account = loadFor(caller, command.accountId());
            Transaction transaction = account.deposit(AccountViews.toAmount(command.amount(), command.currency()));
            changes.save(account);
            return AccountViews.toTransaction(transaction);
        });
    }

    @Override
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        reviewSegmentBeforeDebit(caller, command.accountId());
        return accountUnit.execute(changes -> {
            CheckingAccount account = loadFor(caller, command.accountId());
            Transaction transaction = account.tryWithdraw(AccountViews.toAmount(command.amount(), command.currency()));
            changes.save(account);
            return AccountViews.toTransaction(transaction);
        });
    }

    @Override
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        reviewSegmentBeforeDebit(caller, command.sourceAccountId());
        return accountUnit.execute(changes -> transfer(caller, command, changes));
    }

    private TransferSummary transfer(Caller caller, TransferCommand command, AccountChanges changes) {
        CheckingAccount source = loadFor(caller, command.sourceAccountId());
        AccountNumber targetNumber = new AccountNumber(command.targetAccountNumber());
        CheckingAccount target = repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
        Transaction withdrawal =
                transferService.transfer(source, target, AccountViews.toAmount(command.amount(), command.currency()));
        changes.save(source);
        changes.save(target);
        return AccountViews.toTransfer(source, target, withdrawal);
    }

    @Override
    public int reviewSegments() {
        return accountUnit.execute(changes -> {
            int demoted = 0;
            for (CheckingAccount account : repository.findAll()) {
                if (reviewSegment(account, changes)) {
                    demoted++;
                }
            }
            return demoted;
        });
    }

    /**
     * Revisa el segmento de la cuenta antes de un cargo, en su propia unidad de trabajo: así el descubierto se decide
     * con el segmento que corresponde hoy y, si el cargo falla después, la revisión ya está guardada y su evento
     * publicado.
     */
    private void reviewSegmentBeforeDebit(Caller caller, UUID accountId) {
        accountUnit.execute(changes -> reviewSegment(loadFor(caller, accountId), changes));
    }

    /** Revisa el segmento si le toca y guarda la cuenta. Devuelve si el titular ha vuelto a minorista. */
    private boolean reviewSegment(CheckingAccount account, AccountChanges changes) {
        if (!account.segmentReviewDue(clock.instant())) {
            return false;
        }
        boolean wasHighNetWorth = account.primaryHolder().isHighNetWorth();
        segmentReview.review(account, clock.instant());
        changes.save(account);
        return wasHighNetWorth && !account.primaryHolder().isHighNetWorth();
    }

    /** Carga la cuenta; una ajena (o sin propietario, para un usuario) se trata como inexistente. */
    private CheckingAccount loadFor(Caller caller, UUID id) {
        CheckingAccount account = load(id);
        if (!canAccess(caller, id)) {
            throw new AccountNotFoundException(id);
        }
        return account;
    }

    private String holderEmail(CheckingAccount account) {
        return customers.findByTaxId(account.primaryHolder().taxId()).map(customer -> customer.email().value()).orElse("");
    }

    private boolean canAccess(Caller caller, UUID accountId) {
        return caller.admin() || ownership.ownerOf(accountId).filter(caller.username()::equals).isPresent();
    }

    private CheckingAccount load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
