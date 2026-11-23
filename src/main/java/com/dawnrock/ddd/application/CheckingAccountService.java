package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.EmailAddress;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
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
    private final DomainEventPublisher publisher;
    private final CheckingAccountFactory factory;
    private final MoneyTransferService transferService;
    private final SegmentReviewService segmentReview;
    private final Clock clock;
    private final UnitOfWork unitOfWork;
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
        this.publisher = publisher;
        this.factory = factory;
        this.transferService = transferService;
        this.segmentReview = segmentReview;
        this.clock = clock;
        this.unitOfWork = unitOfWork;
        this.customers = customers;
        this.customerOwnership = customerOwnership;
    }

    @Override
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        return inUnit(events -> open(caller, command, events));
    }

    private AccountSummary open(Caller caller, OpenAccountCommand command, Events events) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        TaxId taxId = new TaxId(command.holderTaxId());
        Optional<Customer> existing = customers.findByTaxId(taxId);
        Customer customer = existing.orElseGet(() -> newCustomer(taxId, command));
        String owner = existing.isPresent() ? ownerOfExisting(customer, command) : command.ownerUsername();

        CheckingAccount opened = factory.open(customer, toAmount(command.initialDeposit(), command.currency()));
        requireUnused(opened.accountNumber());
        if (existing.isEmpty()) {
            if (usernameTaken(owner)) {
                throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(owner));
            }
            customers.save(customer);
            customerOwnership.assign(taxId, owner);
        }
        CheckingAccount account = save(opened, events);
        ownership.assign(account.id(), owner);
        return toSummary(account);
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
        return inUnit(events -> {
            TaxId taxId = new TaxId(command.taxId());
            Customer customer = Customer.register(command.fullName(), taxId, new EmailAddress(command.email()));
            CheckingAccount opened = factory.open(customer, toAmount(command.initialDeposit(), command.currency()));
            requireUnused(opened.accountNumber());
            customers.findByTaxId(taxId).ifPresent(existing -> {
                throw new DuplicateCustomerException(taxId);
            });
            if (usernameTaken(command.username())) {
                throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(command.username()));
            }
            customers.save(customer);
            customerOwnership.assign(taxId, command.username());
            CheckingAccount account = save(opened, events);
            ownership.assign(account.id(), command.username());
            return toSummary(account);
        });
    }

    @Override
    public AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand command) {
        return inUnit(events -> {
            Customer customer = customerOf(caller);
            CheckingAccount opened =
                    factory.open(customer, toAmount(command.initialDeposit(), command.currency()));
            requireUnused(opened.accountNumber());
            CheckingAccount account = save(opened, events);
            ownership.assign(account.id(), caller.username());
            return toSummary(account);
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
        return inUnit(events -> {
            CheckingAccount account = load(id);
            AccountNumber generated = factory.nextAccountNumber();
            requireUnused(generated);
            account.reassignAccountNumber(generated);
            return toSummary(save(account, events));
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
                .map(CheckingAccountService::toSummary)
                .toList());
    }

    @Override
    public AccountDetails findAccount(Caller caller, UUID id) {
        return unitOfWork.execute(() -> toDetails(loadFor(caller, id)));
    }

    @Override
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        return inUnit(events -> {
            CheckingAccount account = loadFor(caller, command.accountId());
            Transaction transaction = account.deposit(toAmount(command.amount(), command.currency()));
            save(account, events);
            return toTransaction(transaction);
        });
    }

    @Override
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        reviewSegmentBeforeDebit(caller, command.accountId());
        return inUnit(events -> {
            CheckingAccount account = loadFor(caller, command.accountId());
            Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
            save(account, events);
            return toTransaction(transaction);
        });
    }

    @Override
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        reviewSegmentBeforeDebit(caller, command.sourceAccountId());
        return inUnit(events -> transfer(caller, command, events));
    }

    private TransferSummary transfer(Caller caller, TransferCommand command, Events events) {
        CheckingAccount source = loadFor(caller, command.sourceAccountId());
        AccountNumber targetNumber = new AccountNumber(command.targetAccountNumber());
        CheckingAccount target = repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
        Transaction withdrawal =
                transferService.transfer(source, target, toAmount(command.amount(), command.currency()));
        save(source, events);
        save(target, events);
        return TransferSummary.builder()
                .sourceAccountId(source.id())
                .sourceAccountNumber(source.accountNumber().value())
                .targetAccountId(target.id())
                .targetAccountNumber(target.accountNumber().value())
                .amount(withdrawal.amount().amount())
                .currency(withdrawal.amount().currency().getCurrencyCode())
                .occurredOn(withdrawal.occurredOn())
                .status(withdrawal.status().name())
                .build();
    }

    @Override
    public int reviewSegments() {
        return inUnit(events -> {
            int demoted = 0;
            for (CheckingAccount account : repository.findAll()) {
                if (reviewSegment(account, events)) {
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
        inUnit(events -> reviewSegment(loadFor(caller, accountId), events));
    }

    /** Revisa el segmento si le toca y guarda la cuenta. Devuelve si el titular ha vuelto a minorista. */
    private boolean reviewSegment(CheckingAccount account, Events events) {
        if (!account.segmentReviewDue(clock.instant())) {
            return false;
        }
        boolean wasHighNetWorth = account.primaryHolder().isHighNetWorth();
        segmentReview.review(account, clock.instant());
        save(account, events);
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

    /**
     * Ejecuta el caso de uso dentro de la unidad de trabajo y, cuando ha terminado, publica los eventos de cada cuenta
     * guardada, una llamada por cuenta y en el orden en que se guardaron. Si el caso de uso falla, no publica nada.
     */
    private <T> T inUnit(Function<Events, T> work) {
        Events events = new Events();
        T result = unitOfWork.execute(() -> work.apply(events));
        events.batches.forEach(publisher::publishAll);
        return result;
    }

    /** Guarda la cuenta y recoge los eventos que ha registrado, para publicarlos al terminar la unidad. */
    private CheckingAccount save(CheckingAccount account, Events events) {
        CheckingAccount saved = repository.save(account);
        events.batches.add(account.pullDomainEvents());
        return saved;
    }

    /** Eventos pendientes de publicar de un caso de uso: un lote por cuenta guardada. */
    private static final class Events {
        private final List<List<DomainEvent>> batches = new ArrayList<>();
    }

    private boolean canAccess(Caller caller, UUID accountId) {
        return caller.admin() || ownership.ownerOf(accountId).filter(caller.username()::equals).isPresent();
    }

    private CheckingAccount load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static MonetaryAmount toAmount(BigDecimal amount, String currency) {
        return new MonetaryAmount(amount, Currency.getInstance(currency));
    }

    private AccountDetails toDetails(CheckingAccount account) {
        TaxId taxId = account.primaryHolder().taxId();
        return AccountDetails.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .holderTaxId(taxId.value())
                .holderEmail(customers.findByTaxId(taxId).map(customer -> customer.email().value()).orElse(""))
                .segment(account.primaryHolder().segment().name())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .transactions(account.transactions().stream().map(CheckingAccountService::toTransaction).toList())
                .build();
    }

    private static TransactionSummary toTransaction(Transaction transaction) {
        return TransactionSummary.builder()
                .id(transaction.id())
                .type(transaction.type().name())
                .amount(transaction.amount().amount())
                .currency(transaction.amount().currency().getCurrencyCode())
                .occurredOn(transaction.occurredOn())
                .status(transaction.status().name())
                .counterpartAccountNumber(transaction.counterpart().map(AccountNumber::value))
                .build();
    }

    private static AccountSummary toSummary(CheckingAccount account) {
        return AccountSummary.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .segment(account.primaryHolder().segment().name())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .build();
    }
}
