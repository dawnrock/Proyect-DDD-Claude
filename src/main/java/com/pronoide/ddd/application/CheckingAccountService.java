package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.DomainEvent;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.domain.service.MoneyTransferService;

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
    private final UnitOfWork unitOfWork;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration} (#20). */
    public CheckingAccountService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                  DomainEventPublisher publisher, CheckingAccountFactory factory,
                                  MoneyTransferService transferService, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.ownership = ownership;
        this.publisher = publisher;
        this.factory = factory;
        this.transferService = transferService;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        return inUnit(events -> open(caller, command, events));
    }

    private AccountSummary open(Caller caller, OpenAccountCommand command, Events events) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        if (command.ownerUsername().isEmpty()) {
            throw new IllegalArgumentException("El usuario propietario de la cuenta es obligatorio");
        }
        MonetaryAmount initialDeposit =
                new MonetaryAmount(command.initialDeposit(), Currency.getInstance(command.currency()));
        CheckingAccount opened =
                factory.open(command.holderName(), new TaxId(command.holderTaxId()), initialDeposit);
        requireUnused(opened.accountNumber());
        CheckingAccount account = save(opened, events);
        ownership.assign(account.id(), command.ownerUsername());
        return toSummary(account);
    }

    /** R16: con la numeración generada es solo una red de seguridad ante un generador defectuoso. */
    private void requireUnused(AccountNumber accountNumber) {
        repository.findByAccountNumber(accountNumber).ifPresent(existing -> {
            throw new DuplicateAccountNumberException(accountNumber);
        });
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
        return inUnit(events -> {
            CheckingAccount account = loadFor(caller, command.accountId());
            Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
            save(account, events);
            return toTransaction(transaction);
        });
    }

    @Override
    public TransferSummary transfer(Caller caller, TransferCommand command) {
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

    private static AccountDetails toDetails(CheckingAccount account) {
        return AccountDetails.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
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
