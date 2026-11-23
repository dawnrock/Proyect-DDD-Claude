package com.dawnrock.ddd.adapter.out.persistence;

import java.util.Currency;
import java.util.List;

import com.dawnrock.ddd.domain.model.AccountHolder;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CustomerSegment;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.domain.model.Transaction;
import com.dawnrock.ddd.domain.model.TransactionStatus;
import com.dawnrock.ddd.domain.model.TransactionType;

/**
 * Traduce entre el agregado {@link CheckingAccount} y sus filas (#150). Para leer reconstituye el agregado con
 * {@code reconstitute(...)}, que comprueba que lo guardado es coherente y no registra eventos.
 */
final class CheckingAccountMapper {

    private CheckingAccountMapper() {
    }

    static CheckingAccount toDomain(CheckingAccountEntity entity) {
        AccountHolder holder = AccountHolder.reconstitute(entity.holderId, entity.holderFullName,
                new TaxId(entity.holderTaxId), CustomerSegment.valueOf(entity.holderSegment));
        Currency currency = Currency.getInstance(entity.currency);
        List<Transaction> transactions = entity.transactions.stream()
                .map(CheckingAccountMapper::toDomain)
                .toList();
        return CheckingAccount.reconstitute(entity.id, new AccountNumber(entity.accountNumber), holder,
                new MonetaryAmount(entity.balance, currency), entity.nextSegmentReview, transactions);
    }

    /**
     * Copia el estado de la cuenta en su fila. Los movimientos no cambian una vez registrados, así que solo se añaden
     * los que la fila aún no tiene.
     */
    static CheckingAccountEntity copyInto(CheckingAccount account, CheckingAccountEntity entity) {
        AccountHolder holder = account.primaryHolder();
        entity.accountNumber = account.accountNumber().value();
        entity.holderId = holder.id();
        entity.holderTaxId = holder.taxId().value();
        entity.holderFullName = holder.fullName();
        entity.holderSegment = holder.segment().name();
        entity.balance = account.currentBalance().amount();
        entity.currency = account.currentBalance().currency().getCurrencyCode();
        entity.nextSegmentReview = account.nextSegmentReview().orElse(null);
        List<Transaction> transactions = account.transactions();
        for (int position = entity.transactions.size(); position < transactions.size(); position++) {
            entity.transactions.add(toEntity(transactions.get(position), position, entity));
        }
        return entity;
    }

    private static Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute(entity.id, entity.account.id, TransactionType.valueOf(entity.type),
                new MonetaryAmount(entity.amount, Currency.getInstance(entity.currency)), entity.occurredOn,
                entity.counterpart == null ? null : new AccountNumber(entity.counterpart),
                TransactionStatus.valueOf(entity.status));
    }

    private static TransactionEntity toEntity(Transaction transaction, int position, CheckingAccountEntity account) {
        TransactionEntity entity = new TransactionEntity(transaction.id());
        entity.account = account;
        entity.position = position;
        entity.type = transaction.type().name();
        entity.amount = transaction.amount().amount();
        entity.currency = transaction.amount().currency().getCurrencyCode();
        entity.occurredOn = transaction.occurredOn();
        entity.counterpart = transaction.counterpart().map(AccountNumber::value).orElse(null);
        entity.status = transaction.status().name();
        return entity;
    }
}
