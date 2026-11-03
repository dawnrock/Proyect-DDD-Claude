package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.AccountNumberSequence;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.IncomeVerifier;
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
 * {@link AccountOwnershipRepository}, {@link DomainEventPublisher}, {@link AccountNumberSequence} e
 * {@link IncomeVerifier}); Spring le inyecta sus adaptadores. Publica los
 * eventos de dominio de cada cuenta después de guardarla.
 */
@Service
public class CheckingAccountService implements CheckingAccountCommands, CheckingAccountQueries {

    private final CheckingAccountRepository repository;
    private final AccountOwnershipRepository ownership;
    private final DomainEventPublisher publisher;
    private final MoneyTransferService transferService = new MoneyTransferService();
    private final CheckingAccountFactory factory;

    /**
     * Construye la factoría de cuentas a partir de sus dos puertos de salida (numeración y verificación de ingresos).
     * Cuando exista el paquete {@code config} (#20), la factoría se construirá allí.
     */
    public CheckingAccountService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                  DomainEventPublisher publisher, AccountNumberSequence numbering,
                                  IncomeVerifier incomeVerifier) {
        this.repository = repository;
        this.ownership = ownership;
        this.publisher = publisher;
        this.factory = new CheckingAccountFactory(numbering, incomeVerifier);
    }

    @Override
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
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
        CheckingAccount account = saveAndPublish(opened);
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
        return repository.findAll().stream()
                .filter(account -> canAccess(caller, account.id()))
                .map(CheckingAccountService::toSummary)
                .toList();
    }

    @Override
    public AccountDetails findAccount(Caller caller, UUID id) {
        return toDetails(loadFor(caller, id));
    }

    @Override
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        Transaction transaction = account.deposit(toAmount(command.amount(), command.currency()));
        saveAndPublish(account);
        return toTransaction(transaction);
    }

    @Override
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
        saveAndPublish(account);
        return toTransaction(transaction);
    }

    @Override
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        CheckingAccount source = loadFor(caller, command.sourceAccountId());
        AccountNumber targetNumber = new AccountNumber(command.targetAccountNumber());
        CheckingAccount target = repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
        Transaction withdrawal =
                transferService.transfer(source, target, toAmount(command.amount(), command.currency()));
        repository.save(source);
        repository.save(target);
        publisher.publishAll(source.pullDomainEvents());
        publisher.publishAll(target.pullDomainEvents());
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

    /** Guarda la cuenta y, solo después, publica los eventos que ha registrado. */
    private CheckingAccount saveAndPublish(CheckingAccount account) {
        CheckingAccount saved = repository.save(account);
        publisher.publishAll(account.pullDomainEvents());
        return saved;
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
