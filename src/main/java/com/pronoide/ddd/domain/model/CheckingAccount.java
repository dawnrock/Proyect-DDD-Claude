package com.pronoide.ddd.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CheckingAccount {

    private static final MonetaryAmount MINIMUM_INITIAL_DEPOSIT = MonetaryAmount.euros("50.00");
    private static final MonetaryAmount OVERDRAFT_LIMIT = MonetaryAmount.euros("-1000.00");
    private static final MonetaryAmount MINIMUM_NET_WORTH = MonetaryAmount.euros("100000.00");
    private final UUID id;
    private final AccountNumber accountNumber;
    private final AccountHolder primaryHolder;
    private final List<Transaction> transactions = new ArrayList<>();
    private MonetaryAmount currentBalance;

    private CheckingAccount(AccountNumber accountNumber, 
                            AccountHolder primaryHolder, 
                            MonetaryAmount initialDeposit) {
        this.id = UUID.randomUUID();
        this.accountNumber = accountNumber;
        this.primaryHolder = primaryHolder;
        this.currentBalance = MonetaryAmount.zero(initialDeposit.currency());
    }

    public static CheckingAccount open(AccountNumber accountNumber, 
                                       AccountHolder primaryHolder,
                                       MonetaryAmount initialDeposit) {
        Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio");
        Objects.requireNonNull(primaryHolder, "El titular de la cuenta es obligatorio");
        requirePositive(initialDeposit);

        if (initialDeposit.isLessThan(MINIMUM_INITIAL_DEPOSIT)) {
            throw new InsufficientInitialDepositException(initialDeposit, MINIMUM_INITIAL_DEPOSIT);
        }

        CheckingAccount account = new CheckingAccount(accountNumber, primaryHolder, initialDeposit);
        account.deposit(initialDeposit);
        return account;
    }

    public Transaction deposit(MonetaryAmount amount) {
        requirePositive(amount);

        MonetaryAmount newBalance = currentBalance.add(amount);
        Transaction transaction = register(Transaction.deposit(id, amount));
        transaction.approve();
        currentBalance = newBalance;
        if (reachesMinimumNetWorth()) {
            primaryHolder.promoteToHighNetWorth();
        }
        return transaction;
    }

    public Transaction tryWithdraw(MonetaryAmount amount) {
        requirePositive(amount);

        MonetaryAmount newBalance = currentBalance.subtract(amount);

        Transaction transaction = register(Transaction.withdrawal(id, amount));

        if (isAllowed(newBalance)) {
            transaction.approve();
            currentBalance = newBalance;
        } else {
            transaction.reject();
        }
        return transaction;
    }

    public List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    public UUID id() {
        return id;
    }

    public AccountNumber accountNumber() {
        return accountNumber;
    }

    public AccountHolder primaryHolder() {
        return primaryHolder;
    }

    public MonetaryAmount currentBalance() {
        return currentBalance;
    }

    private boolean isAllowed(MonetaryAmount newBalance) {
        if (!newBalance.isNegative()) {
            return true;
        }
        return primaryHolder.isHighNetWorth() && !newBalance.isLessThan(OVERDRAFT_LIMIT);
    }

    private boolean reachesMinimumNetWorth() {
        return !currentBalance.isLessThan(MINIMUM_NET_WORTH);
    }

    private Transaction register(Transaction transaction) {
        transactions.add(transaction);
        return transaction;
    }

    private static void requirePositive(MonetaryAmount amount) {

        Objects.requireNonNull(amount, "El importe de la operación es obligatorio");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("El importe de la operación debe ser positivo: " + amount);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CheckingAccount that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s de %s con saldo %s".formatted(accountNumber, primaryHolder.fullName(), currentBalance);
    }
}
