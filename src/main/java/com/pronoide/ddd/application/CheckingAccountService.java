package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.domain.service.MoneyTransferService;
import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar, listar, ingresar, retirar y transferir. Carga y guarda el agregado; las
 * reglas de negocio siguen en {@code CheckingAccount}. Contrato: {@code docs/contracts/open-query-list-accounts.md}.
 */
@Service
@RequiredArgsConstructor
public class CheckingAccountService {

    private final InMemoryCheckingAccountRepository repository;
    private final InMemoryAccountOwnershipRepository ownership;
    private final MoneyTransferService transferService = new MoneyTransferService();

    /**
     * Abre una cuenta con su depósito inicial y la guarda.
     *
     * @throws com.pronoide.ddd.domain.model.DuplicateAccountNumberException si el número de cuenta ya está en uso
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    public AccountSummary openAccount(OpenAccountCommand command) {
        AccountSummary summary = open(command);
        if (!command.ownerUsername().isEmpty()) {
            ownership.assign(summary.id(), command.ownerUsername());
        }
        return summary;
    }

    private AccountSummary open(OpenAccountCommand command) {
        AccountNumber accountNumber = new AccountNumber(command.accountNumber());
        repository.findByAccountNumber(accountNumber).ifPresent(existing -> {
            throw new DuplicateAccountNumberException(accountNumber);
        });

        AccountHolder holder = AccountHolder.register(command.holderName(), CustomerSegment.valueOf(command.segment()));
        MonetaryAmount initialDeposit =
                new MonetaryAmount(command.initialDeposit(), Currency.getInstance(command.currency()));
        CheckingAccount account = CheckingAccount.open(accountNumber, holder, initialDeposit);
        return toSummary(repository.save(account));
    }

    /**
     * Detalle de una cuenta, con sus movimientos.
     *
     * @throws AccountNotFoundException si no existe ninguna cuenta con ese id
     */
    public AccountDetails findAccount(UUID id) {
        return repository.findById(id)
                .map(CheckingAccountService::toDetails)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /** Todas las cuentas, ordenadas por número de cuenta. */
    public List<AccountSummary> listAccounts() {
        return repository.findAll().stream().map(CheckingAccountService::toSummary).toList();
    }

    /**
     * Ingresa un importe en la cuenta y la guarda. Contrato: {@code docs/contracts/deposits-withdrawals.md}.
     *
     * @throws AccountNotFoundException si no existe ninguna cuenta con ese id
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    public TransactionSummary deposit(DepositCommand command) {
        CheckingAccount account = load(command.accountId());
        Transaction transaction = account.deposit(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /**
     * Intenta retirar un importe de la cuenta y la guarda. Una retirada que dejaría la cuenta por debajo de lo
     * permitido no es un error: se devuelve el movimiento con estado {@code REJECTED} y el saldo no cambia.
     *
     * @throws AccountNotFoundException si no existe ninguna cuenta con ese id
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    public TransactionSummary withdraw(WithdrawCommand command) {
        CheckingAccount account = load(command.accountId());
        Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /**
     * Transfiere un importe de la cuenta de origen a la de destino y guarda las dos. Una transferencia que la cuenta de
     * origen no permite no es un error: se devuelve con estado {@code REJECTED} y ningún saldo cambia. Contrato:
     * {@code docs/contracts/money-transfers.md}.
     *
     * @throws AccountNotFoundException si no existe la cuenta de origen o la de destino
     * @throws com.pronoide.ddd.domain.model.SameAccountTransferException si origen y destino son la misma cuenta
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de las cuentas
     */
    public TransferSummary transfer(TransferCommand command) {
        if (command.targetAccountId() == null) {
            throw new IllegalArgumentException("Indica la cuenta de destino por su id");
        }
        CheckingAccount source = load(command.sourceAccountId());
        CheckingAccount target = load(command.targetAccountId());
        return move(source, target, command);
    }

    private TransferSummary move(CheckingAccount source, CheckingAccount target, TransferCommand command) {
        Transaction withdrawal =
                transferService.transfer(source, target, toAmount(command.amount(), command.currency()));
        repository.save(source);
        repository.save(target);
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

    // Casos de uso con quien los pide (contrato docs/contracts/login-roles.md, #50). Un administrador accede a todas
    // las cuentas; un usuario solo a las suyas, y una ajena se trata como inexistente (AccountNotFoundException, 404).
    // Una cuenta sin propietario registrado solo la ve un administrador. Sustituyen a los de arriba, que se eliminan
    // al integrar la #50; mientras tanto, openAccount(command) registra el propietario si viene, y transfer(command)
    // exige targetAccountId (IllegalArgumentException si falta).

    /**
     * Abre una cuenta para {@code command.ownerUsername()}. Solo un administrador.
     *
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws IllegalArgumentException si el command no indica el usuario propietario (mensaje en español)
     */
    public AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        if (command.ownerUsername().isEmpty()) {
            throw new IllegalArgumentException("El usuario propietario de la cuenta es obligatorio");
        }
        AccountSummary summary = open(command);
        ownership.assign(summary.id(), command.ownerUsername());
        return summary;
    }

    /** Todas las cuentas para un administrador; las suyas para un usuario. Ordenadas por número de cuenta. */
    public List<AccountSummary> listAccounts(Caller caller) {
        return repository.findAll().stream()
                .filter(account -> canAccess(caller, account.id()))
                .map(CheckingAccountService::toSummary)
                .toList();
    }

    /** @throws AccountNotFoundException si no existe o no es de quien lo pide (y no es administrador) */
    public AccountDetails findAccount(Caller caller, UUID id) {
        return toDetails(loadFor(caller, id));
    }

    /** @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador) */
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        Transaction transaction = account.deposit(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /** @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador) */
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /**
     * Transfiere desde una cuenta de quien lo pide a cualquier cuenta existente, indicada por su número
     * ({@code command.targetAccountNumber()}, que se normaliza como {@code AccountNumber} antes de buscarla).
     *
     * @throws AccountNotFoundException si el origen no existe o no es de quien lo pide
     * @throws TargetAccountNotFoundException si no existe ninguna cuenta con el número de destino
     * @throws IllegalArgumentException si el command solo trae {@code targetAccountId} (vía antigua)
     */
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        if (command.targetAccountNumber() == null) {
            throw new IllegalArgumentException("Indica la cuenta de destino por su número");
        }
        CheckingAccount source = loadFor(caller, command.sourceAccountId());
        AccountNumber targetNumber = new AccountNumber(command.targetAccountNumber());
        CheckingAccount target = repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
        return move(source, target, command);
    }

    /** Carga la cuenta; una ajena (o sin propietario, para un usuario) se trata como inexistente. */
    private CheckingAccount loadFor(Caller caller, UUID id) {
        CheckingAccount account = load(id);
        if (!canAccess(caller, id)) {
            throw new AccountNotFoundException(id);
        }
        return account;
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
