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
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar, listar, ingresar y retirar. Carga y guarda el agregado; las
 * reglas de negocio siguen en {@code CheckingAccount}. Contrato: {@code docs/contracts/open-query-list-accounts.md}.
 */
@Service
@RequiredArgsConstructor
public class CheckingAccountService {

    private final InMemoryCheckingAccountRepository repository;

    /**
     * Abre una cuenta con su depósito inicial y la guarda.
     *
     * @throws com.pronoide.ddd.domain.model.DuplicateAccountNumberException si el número de cuenta ya está en uso
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    public AccountSummary openAccount(OpenAccountCommand command) {
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
        throw new UnsupportedOperationException("pendiente: backend-dev");
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
