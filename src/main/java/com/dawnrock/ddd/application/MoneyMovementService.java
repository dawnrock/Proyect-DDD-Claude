package com.dawnrock.ddd.application;

import java.time.Clock;
import java.util.UUID;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.Transaction;
import com.dawnrock.ddd.domain.service.MoneyTransferService;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

/**
 * Casos de uso de los movimientos de dinero: ingresar, retirar y transferir. Carga y guarda las cuentas; las reglas de
 * negocio siguen en {@code CheckingAccount} y en {@link MoneyTransferService}. Contratos:
 * {@code docs/contracts/deposits-withdrawals.md} y {@code money-transfers.md}.
 *
 * <p>Cada caso de uso recibe quién lo pide ({@link Caller}): una cuenta ajena, o sin propietario registrado, se trata
 * para un usuario como inexistente (ADR 0008). Se ejecuta dentro de una unidad de trabajo que publica los eventos de
 * las cuentas guardadas al terminar (#78, #165). Antes de un cargo se revisa el segmento de la cuenta de origen en su
 * propia unidad (#98).
 *
 * <p>Implementa {@link MoneyMovementCommands}, lo único que conocen los adaptadores de entrada. Sin anotaciones de
 * Spring: lo construye {@code config} (#174).
 */
public class MoneyMovementService implements MoneyMovementCommands {

    private final MoneyTransferService transferService;
    private final AccountUnitOfWork accountUnit;
    private final AccountAccess access;
    private final DueSegmentReview dueSegmentReview;

    /** Recibe sus colaboradores ya construidos: los entrega {@code config.DomainConfiguration}. */
    public MoneyMovementService(CheckingAccountRepository repository, AccountOwnershipRepository ownership,
                                DomainEventPublisher publisher, MoneyTransferService transferService,
                                SegmentReviewService segmentReview, Clock clock, UnitOfWork unitOfWork) {
        this.transferService = transferService;
        this.accountUnit = new AccountUnitOfWork(unitOfWork, repository, publisher);
        this.access = new AccountAccess(repository, ownership);
        this.dueSegmentReview = new DueSegmentReview(segmentReview, clock);
    }

    @Override
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        return accountUnit.execute(changes -> {
            CheckingAccount account = access.loadFor(caller, command.accountId());
            Transaction transaction = account.deposit(AccountViews.toAmount(command.amount(), command.currency()));
            changes.save(account);
            return AccountViews.toTransaction(transaction);
        });
    }

    @Override
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        reviewSegmentBeforeDebit(caller, command.accountId());
        return accountUnit.execute(changes -> {
            CheckingAccount account = access.loadFor(caller, command.accountId());
            Transaction transaction = account.tryWithdraw(AccountViews.toAmount(command.amount(), command.currency()));
            changes.save(account);
            return AccountViews.toTransaction(transaction);
        });
    }

    @Override
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        reviewSegmentBeforeDebit(caller, command.sourceAccountId());
        return accountUnit.execute(changes -> transfer(caller, command, changes));
    }

    private TransferSummary transfer(Caller caller, TransferCommand command, AccountChanges changes) {
        CheckingAccount source = access.loadFor(caller, command.sourceAccountId());
        CheckingAccount target = access.loadTarget(command.targetAccountNumber());
        Transaction withdrawal =
                transferService.transfer(source, target, AccountViews.toAmount(command.amount(), command.currency()));
        changes.save(source);
        changes.save(target);
        return AccountViews.toTransfer(source, target, withdrawal);
    }

    /**
     * Revisa el segmento de la cuenta antes de un cargo, en su propia unidad de trabajo: así el descubierto se decide
     * con el segmento que corresponde hoy y, si el cargo falla después, la revisión ya está guardada y su evento
     * publicado.
     */
    private void reviewSegmentBeforeDebit(Caller caller, UUID accountId) {
        accountUnit.execute(changes -> dueSegmentReview.reviewIfDue(access.loadFor(caller, accountId), changes));
    }
}
