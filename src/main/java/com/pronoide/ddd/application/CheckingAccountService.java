package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.domain.service.MoneyTransferService;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar, listar, ingresar, retirar y transferir. Carga y guarda el
 * agregado; las reglas de negocio siguen en {@code CheckingAccount}. Contrato:
 * {@code docs/contracts/open-query-list-accounts.md}.
 *
 * <p>Cada caso de uso recibe quién lo pide ({@link Caller}, contrato {@code docs/contracts/login-roles.md}): un
 * administrador accede a todas las cuentas y es el único que abre cuentas; un usuario solo a las suyas. Una cuenta
 * ajena, o sin propietario registrado, se trata para un usuario como inexistente ({@link AccountNotFoundException},
 * 404) para no revelar que existe.
 *
 * <p>Implementa los puertos de entrada {@link CheckingAccountCommands} y {@link CheckingAccountQueries}, que son lo
 * único que conocen los adaptadores de entrada. Solo conoce los puertos de salida ({@link CheckingAccountRepository},
 * {@link AccountOwnershipRepository} y {@link DomainEventPublisher}); Spring le inyecta sus adaptadores. Publica los
 * eventos de dominio de cada cuenta después de guardarla.
 */
@Service
@RequiredArgsConstructor
public class CheckingAccountService implements CheckingAccountCommands, CheckingAccountQueries {

    private final CheckingAccountRepository repository;
    private final AccountOwnershipRepository ownership;
    private final DomainEventPublisher publisher;
    private final MoneyTransferService transferService = new MoneyTransferService();

    @Override
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        if (command.ownerUsername().isEmpty()) {
            throw new IllegalArgumentException("El usuario propietario de la cuenta es obligatorio");
        }
        AccountNumber accountNumber = new AccountNumber(command.accountNumber());
        repository.findByAccountNumber(accountNumber).ifPresent(existing -> {
            throw new DuplicateAccountNumberException(accountNumber);
        });

        AccountHolder holder = AccountHolder.register(command.holderName(), CustomerSegment.valueOf(command.segment()));
        MonetaryAmount initialDeposit =
                new MonetaryAmount(command.initialDeposit(), Currency.getInstance(command.currency()));
        CheckingAccount account = saveAndPublish(CheckingAccount.open(accountNumber, holder, initialDeposit));
        ownership.assign(account.id(), command.ownerUsername());
        return toSummary(account);
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
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .build();
    }
}
