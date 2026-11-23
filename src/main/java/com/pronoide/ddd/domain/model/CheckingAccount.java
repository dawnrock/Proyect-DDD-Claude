package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private AccountNumber accountNumber;
    private final AccountHolder primaryHolder;
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<DomainEvent> domainEvents = new ArrayList<>();
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

    private CheckingAccount(UUID id, AccountNumber accountNumber, AccountHolder primaryHolder,
                            MonetaryAmount currentBalance, Instant nextSegmentReview) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.primaryHolder = primaryHolder;
        this.currentBalance = currentBalance;
        this.nextSegmentReview = nextSegmentReview;
    }

    /**
     * Reconstruye una cuenta ya guardada (#150). Solo la usan los adaptadores de persistencia al leerla (regla de
     * ArchUnit): no comprueba las reglas de apertura, que ya se cumplieron al abrirla, ni registra eventos. Sí comprueba
     * que lo guardado es coherente: los movimientos son de esta cuenta, no se repiten, ninguno está pendiente y el saldo
     * es la suma de los ingresos aprobados menos las retiradas aprobadas; solo un titular de alto patrimonio tiene
     * revisión de segmento pendiente.
     *
     * @param nextSegmentReview cuándo toca revisar el segmento, o {@code null} si no hay revisión pendiente
     * @param transactions los movimientos en el orden en que se registraron
     * @throws IllegalStateException si lo guardado no es coherente
     */
    public static CheckingAccount reconstitute(UUID id, AccountNumber accountNumber, AccountHolder primaryHolder,
                                               MonetaryAmount currentBalance, Instant nextSegmentReview,
                                               List<Transaction> transactions) {
        CheckingAccount account = new CheckingAccount(
                Objects.requireNonNull(id, "El id de la cuenta es obligatorio"),
                Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio"),
                Objects.requireNonNull(primaryHolder, "El titular de la cuenta es obligatorio"),
                Objects.requireNonNull(currentBalance, "El saldo de la cuenta es obligatorio"),
                nextSegmentReview);
        if (nextSegmentReview != null && !primaryHolder.isHighNetWorth()) {
            throw new IllegalStateException("Solo una cuenta de alto patrimonio tiene revisión de segmento pendiente");
        }
        MonetaryAmount expected = MonetaryAmount.zero(currentBalance.currency());
        Set<UUID> ids = new HashSet<>();
        for (Transaction transaction : Objects.requireNonNull(transactions, "Los movimientos son obligatorios")) {
            if (!ids.add(transaction.id())) {
                throw new IllegalStateException("Una cuenta guardada no repite movimientos");
            }
            if (!id.equals(transaction.accountId())) {
                throw new IllegalStateException("Un movimiento de la cuenta reconstituida pertenece a otra cuenta");
            }
            if (transaction.status() == TransactionStatus.PENDING) {
                throw new IllegalStateException("Una cuenta guardada no tiene movimientos pendientes");
            }
            if (transaction.isApproved()) {
                expected = transaction.type() == TransactionType.DEPOSIT
                        ? expected.add(transaction.amount())
                        : expected.subtract(transaction.amount());
            }
            account.transactions.add(transaction);
        }
        if (!expected.equals(currentBalance)) {
            throw new IllegalStateException("El saldo guardado %s no cuadra con los movimientos aprobados (%s)"
                    .formatted(currentBalance, expected));
        }
        return account;
    }

    /**
     * Abre una cuenta. Solo lo usan {@link CheckingAccountFactory} y los tests del mismo paquete: fuera del dominio las
     * cuentas se abren con la factoría, que da el número y decide el segmento.
     */
    static CheckingAccount open(AccountNumber accountNumber, 
                                       AccountHolder primaryHolder,
                                       MonetaryAmount initialDeposit) {
        Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio");
        Objects.requireNonNull(primaryHolder, "El titular de la cuenta es obligatorio");
        requirePositive(initialDeposit);

        if (initialDeposit.isLessThan(MINIMUM_INITIAL_DEPOSIT)) {
            throw new InsufficientInitialDepositException(initialDeposit, MINIMUM_INITIAL_DEPOSIT);
        }

        CheckingAccount account = new CheckingAccount(accountNumber, primaryHolder, initialDeposit);
        account.recordOpening(initialDeposit);
        return account;
    }

    public Transaction deposit(MonetaryAmount amount) {
        requirePositive(amount);
        return deposit(Transaction.deposit(id, amount));
    }

    /** Ingreso que llega por una transferencia desde {@code source}; mismas reglas que {@link #deposit}. */
    public Transaction depositFromTransfer(MonetaryAmount amount, AccountNumber source) {
        requirePositive(amount);
        requireOtherAccount(source);
        return deposit(Transaction.depositFromTransfer(id, amount, source));
    }

    private Transaction deposit(Transaction movement) {
        MonetaryAmount amount = movement.amount();
        MonetaryAmount newBalance = currentBalance.add(amount);
        Transaction transaction = register(movement);
        transaction.approve();
        currentBalance = newBalance;
        record(new MoneyDeposited(id, transaction.id(), amount, transaction.occurredOn()));
        if (reachesMinimumNetWorth()) {
            if (!primaryHolder.isHighNetWorth()) {
                primaryHolder.promoteToHighNetWorth();
                recordSegmentChange(transaction.occurredOn());
            }
            nextSegmentReview = null;
        }
        return transaction;
    }

    public Transaction tryWithdraw(MonetaryAmount amount) {
        requirePositive(amount);
        return tryWithdraw(Transaction.withdrawal(id, amount));
    }

    /** Retirada que sale por una transferencia hacia {@code target}; mismas reglas que {@link #tryWithdraw}, también si se rechaza. */
    public Transaction tryWithdrawForTransfer(MonetaryAmount amount, AccountNumber target) {
        requirePositive(amount);
        requireOtherAccount(target);
        return tryWithdraw(Transaction.withdrawalForTransfer(id, amount, target));
    }

    /** R17: la contrapartida de una transferencia es siempre otra cuenta. */
    private void requireOtherAccount(AccountNumber counterpart) {
        if (accountNumber.equals(Objects.requireNonNull(counterpart, "La cuenta de contrapartida es obligatoria"))) {
            throw new SameAccountTransferException(accountNumber);
        }
    }

    /**
     * Evalúa la retirada antes de registrar el movimiento: si la evaluación fallara (por ejemplo, un límite en otra
     * divisa), no queda ningún movimiento {@code PENDING} en la cuenta (#82).
     */
    private Transaction tryWithdraw(Transaction movement) {
        MonetaryAmount amount = movement.amount();
        MonetaryAmount newBalance = currentBalance.subtract(amount);
        boolean allowed = isAllowed(newBalance);

        Transaction transaction = register(movement);

        if (allowed) {
            transaction.approve();
            currentBalance = newBalance;
            record(new MoneyWithdrawn(id, transaction.id(), amount, transaction.occurredOn()));
            if (primaryHolder.isHighNetWorth() && !reachesMinimumNetWorth() && nextSegmentReview == null) {
                nextSegmentReview = after(transaction.occurredOn(), SEGMENT_GRACE_PERIOD);
            }
        } else {
            transaction.reject();
            record(new WithdrawalRejected(id, transaction.id(), amount, transaction.occurredOn()));
        }
        return transaction;
    }

    /** Si a {@code now} ya le toca revisión al segmento del titular. */
    public boolean segmentReviewDue(Instant now) {
        Objects.requireNonNull(now, "El momento de la revisión es obligatorio");
        return nextSegmentReview != null && !now.isBefore(nextSegmentReview);
    }

    /**
     * Resuelve una revisión vencida con el resultado de verificar de nuevo los ingresos del titular. Si siguen siendo
     * altos conserva el alto patrimonio y la siguiente revisión es seis meses después; si no, vuelve a minorista.
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
            recordSegmentChange(now);
        }
    }

    public List<Transaction> transactions() {
        return List.copyOf(transactions);
    }

    /** Entrega los eventos registrados desde la última recogida y los olvida, para no publicarlos dos veces. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pulled = List.copyOf(domainEvents);
        domainEvents.clear();
        return pulled;
    }

    public UUID id() {
        return id;
    }

    /**
     * Sustituye el número de cuenta por otro que haya generado Mon-A Bank y que sea distinto del actual (R32). Que no
     * lo use otra cuenta no lo sabe el agregado: lo comprueba el repositorio al guardar.
     */
    public void reassignAccountNumber(AccountNumber newAccountNumber) {
        Objects.requireNonNull(newAccountNumber, "El número de cuenta es obligatorio");
        if (!newAccountNumber.isMonABank()) {
            throw new IllegalArgumentException(
                    "El número de cuenta %s no es de Mon-A Bank".formatted(newAccountNumber));
        }
        if (newAccountNumber.equals(accountNumber)) {
            throw new IllegalArgumentException("La cuenta ya tiene el número %s".formatted(accountNumber));
        }
        this.accountNumber = newAccountNumber;
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

    private Transaction register(Transaction transaction) {
        transactions.add(transaction);
        return transaction;
    }

    /** El evento de apertura va antes que el del depósito inicial, pero comparte su instante. */
    private void recordOpening(MonetaryAmount initialDeposit) {
        int firstEvent = domainEvents.size();
        Transaction deposit = deposit(initialDeposit);
        if (primaryHolder.isHighNetWorth() && !reachesMinimumNetWorth()) {
            nextSegmentReview = after(deposit.occurredOn(), INCOME_REVIEW_PERIOD);
        }
        domainEvents.add(firstEvent, new CheckingAccountOpened(
                id, accountNumber, primaryHolder.fullName(), initialDeposit, deposit.occurredOn()));
    }

    /** Plazos en meses naturales, en UTC. */
    private static Instant after(Instant instant, Period period) {
        return instant.atZone(ZoneOffset.UTC).plus(period).toInstant();
    }

    private boolean reachesMinimumNetWorth() {
        return !currentBalance.isLessThan(MINIMUM_NET_WORTH);
    }

    private void recordSegmentChange(Instant occurredOn) {
        record(new CustomerSegmentChanged(id, primaryHolder.fullName(), primaryHolder.segment(), occurredOn));
    }

    private void record(DomainEvent event) {
        domainEvents.add(event);
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
