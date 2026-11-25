package com.dawnrock.ddd.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class CheckingAccount {

    private static final MonetaryAmount MINIMUM_INITIAL_DEPOSIT = MonetaryAmount.euros("50.00");
    private final UUID id;
    private AccountNumber accountNumber;
    private final AccountHolder primaryHolder;
    private final TransactionHistory transactions;
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    private final AccountSegment segment;
    private MonetaryAmount currentBalance;

    private CheckingAccount(AccountNumber accountNumber, 
                            AccountHolder primaryHolder, 
                            MonetaryAmount initialDeposit) {
        this.id = UUID.randomUUID();
        this.accountNumber = accountNumber;
        this.primaryHolder = primaryHolder;
        this.currentBalance = MonetaryAmount.zero(initialDeposit.currency());
        this.transactions = new TransactionHistory();
        this.segment = AccountSegment.of(primaryHolder);
    }

    private CheckingAccount(UUID id, AccountNumber accountNumber, AccountHolder primaryHolder,
                            MonetaryAmount currentBalance, AccountSegment segment, TransactionHistory transactions) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.primaryHolder = primaryHolder;
        this.currentBalance = currentBalance;
        this.segment = segment;
        this.transactions = transactions;
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
        Objects.requireNonNull(id, "El id de la cuenta es obligatorio");
        Objects.requireNonNull(accountNumber, "El número de cuenta es obligatorio");
        Objects.requireNonNull(primaryHolder, "El titular de la cuenta es obligatorio");
        Objects.requireNonNull(currentBalance, "El saldo de la cuenta es obligatorio");
        AccountSegment segment = AccountSegment.restore(primaryHolder, nextSegmentReview);
        TransactionHistory history = TransactionHistory.restore(id, transactions, currentBalance);
        return new CheckingAccount(id, accountNumber, primaryHolder, currentBalance, segment, history);
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
        if (segment.afterDeposit(currentBalance)) {
            recordSegmentChange(transaction.occurredOn());
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
        boolean allowed = segment.allows(newBalance);

        Transaction transaction = register(movement);

        if (allowed) {
            transaction.approve();
            currentBalance = newBalance;
            record(new MoneyWithdrawn(id, transaction.id(), amount, transaction.occurredOn()));
            segment.afterWithdrawal(currentBalance, transaction.occurredOn());
        } else {
            transaction.reject();
            record(new WithdrawalRejected(id, transaction.id(), amount, transaction.occurredOn()));
        }
        return transaction;
    }

    /** Si a {@code now} ya le toca revisión al segmento del titular. */
    public boolean segmentReviewDue(Instant now) {
        return segment.reviewDue(now);
    }

    /**
     * Resuelve una revisión vencida con el resultado de verificar de nuevo los ingresos del titular. Si siguen siendo
     * altos conserva el alto patrimonio y la siguiente revisión es seis meses después; si no, vuelve a minorista.
     *
     * @throws IllegalStateException si la revisión aún no está vencida
     */
    public void resolveSegmentReview(Instant now, boolean incomeStillHigh) {
        if (segment.resolveReview(now, incomeStillHigh)) {
            recordSegmentChange(now);
        }
    }

    public List<Transaction> transactions() {
        return transactions.asList();
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
     * Sustituye el número de cuenta por otro que haya generado monA Bank y que sea distinto del actual (R32). Que no
     * lo use otra cuenta no lo sabe el agregado: lo comprueba el repositorio al guardar.
     */
    public void reassignAccountNumber(AccountNumber newAccountNumber) {
        Objects.requireNonNull(newAccountNumber, "El número de cuenta es obligatorio");
        if (!newAccountNumber.isMonABank()) {
            throw new IllegalArgumentException(
                    "El número de cuenta %s no es de monA Bank".formatted(newAccountNumber));
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
        return segment.nextReview();
    }

    private Transaction register(Transaction transaction) {
        return transactions.add(transaction);
    }

    /** El evento de apertura va antes que el del depósito inicial, pero comparte su instante. */
    private void recordOpening(MonetaryAmount initialDeposit) {
        int firstEvent = domainEvents.size();
        Transaction deposit = deposit(initialDeposit);
        segment.afterOpening(currentBalance, deposit.occurredOn());
        domainEvents.add(firstEvent, new CheckingAccountOpened(
                id, accountNumber, primaryHolder.fullName(), initialDeposit, deposit.occurredOn()));
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
