package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CheckingAccount {

    private static final MonetaryAmount MINIMUM_INITIAL_DEPOSIT = MonetaryAmount.euros("50.00");
    private static final MonetaryAmount OVERDRAFT_LIMIT = MonetaryAmount.euros("-1000.00");
    private static final MonetaryAmount MINIMUM_NET_WORTH = MonetaryAmount.euros("100000.00");
    /** Plazo desde que una cuenta de alto patrimonio cae bajo el patrimonio mínimo hasta que se revisa su segmento. */
    private static final Period SEGMENT_GRACE_PERIOD = Period.ofMonths(1);
    /** Cada cuánto se vuelven a verificar los ingresos de un titular que conserva el segmento sin el saldo mínimo. */
    private static final Period INCOME_REVIEW_PERIOD = Period.ofMonths(6);
    private final UUID id;
    private final AccountNumber accountNumber;
    private final AccountHolder primaryHolder;
    private final List<Transaction> transactions = new ArrayList<>();
    private MonetaryAmount currentBalance;
    private Instant nextSegmentReview;

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
        Transaction deposit = account.deposit(initialDeposit);
        if (primaryHolder.isHighNetWorth() && !account.reachesMinimumNetWorth()) {
            account.nextSegmentReview = after(deposit.occurredOn(), INCOME_REVIEW_PERIOD);
        }
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
            nextSegmentReview = null;
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
            if (primaryHolder.isHighNetWorth() && !reachesMinimumNetWorth() && nextSegmentReview == null) {
                nextSegmentReview = after(transaction.occurredOn(), SEGMENT_GRACE_PERIOD);
            }
        } else {
            transaction.reject();
        }
        return transaction;
    }

    /** Si a {@code now} ya le toca revisión al segmento del titular. */
    public boolean segmentReviewDue(Instant now) {
        Objects.requireNonNull(now, "El momento de la revisión es obligatorio");
        return nextSegmentReview != null && !now.isBefore(nextSegmentReview);
    }

    /**
     * Resuelve una revisión vencida con el resultado de verificar de nuevo los ingresos del titular, que consulta quien
     * llama (el dominio no conoce a la agencia tributaria). Si siguen siendo altos conserva el alto patrimonio y la
     * siguiente revisión es seis meses después; si no, vuelve a minorista.
     *
     * @throws IllegalStateException si la revisión aún no está vencida
     */
    public void resolveSegmentReview(Instant now, boolean incomeStillHigh) {
        if (!segmentReviewDue(now)) {
            throw new IllegalStateException("La revisión del segmento aún no está vencida");
        }
        if (!primaryHolder.isHighNetWorth()) {
            throw new IllegalStateException("Solo se revisa el segmento de un titular de alto patrimonio");
        }
        if (incomeStillHigh) {
            nextSegmentReview = after(now, INCOME_REVIEW_PERIOD);
        } else {
            primaryHolder.demoteToRetail();
            nextSegmentReview = null;
        }
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

    /**
     * Cuándo toca revisar el segmento del titular; vacío si no hay revisión pendiente. Solo la tiene una cuenta de alto
     * patrimonio cuyo saldo está por debajo del patrimonio mínimo.
     */
    public Optional<Instant> nextSegmentReview() {
        return Optional.ofNullable(nextSegmentReview);
    }

    private boolean isAllowed(MonetaryAmount newBalance) {
        if (!newBalance.isNegative()) {
            return true;
        }
        return primaryHolder.isHighNetWorth() && !newBalance.isLessThan(OVERDRAFT_LIMIT);
    }

    /** Plazos en meses naturales, en UTC. */
    private static Instant after(Instant instant, Period period) {
        return instant.atZone(ZoneOffset.UTC).plus(period).toInstant();
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
