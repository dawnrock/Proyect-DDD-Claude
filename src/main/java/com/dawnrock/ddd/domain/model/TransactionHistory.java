package com.dawnrock.ddd.domain.model;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Historial de movimientos de una cuenta (#182): sus movimientos en el orden en que se registraron. Es parte del
 * agregado {@link CheckingAccount}, que es el único que lo usa. Al reconstituir una cuenta guardada comprueba que lo
 * guardado es coherente: los movimientos no se repiten, son de la cuenta, ninguno está pendiente y el saldo es la suma de
 * los ingresos aprobados menos las retiradas aprobadas.
 */
final class TransactionHistory {

    private final List<Transaction> transactions = new ArrayList<>();

    /** Historial vacío, el de una cuenta que se abre. */
    TransactionHistory() {
    }

    /**
     * Recupera el historial de una cuenta guardada.
     *
     * @param accountId la cuenta a la que deben pertenecer los movimientos
     * @param stored los movimientos en el orden en que se registraron
     * @param balance el saldo guardado de la cuenta
     * @throws IllegalStateException si lo guardado no es coherente
     */
    static TransactionHistory restore(UUID accountId, List<Transaction> stored, MonetaryAmount balance) {
        TransactionHistory history = new TransactionHistory();
        Set<UUID> ids = new HashSet<>();
        for (Transaction transaction : Objects.requireNonNull(stored, "Los movimientos son obligatorios")) {
            requireStorable(accountId, transaction, ids);
            history.transactions.add(transaction);
        }
        MonetaryAmount expected = history.approvedBalance(balance.currency());
        if (!expected.equals(balance)) {
            throw new IllegalStateException("El saldo guardado %s no cuadra con los movimientos aprobados (%s)"
                    .formatted(balance, expected));
        }
        return history;
    }

    /** Un movimiento guardado no se repite, es de la cuenta y no está pendiente. */
    private static void requireStorable(UUID accountId, Transaction transaction, Set<UUID> ids) {
        if (!ids.add(transaction.id())) {
            throw new IllegalStateException("Una cuenta guardada no repite movimientos");
        }
        if (!accountId.equals(transaction.accountId())) {
            throw new IllegalStateException("Un movimiento de la cuenta reconstituida pertenece a otra cuenta");
        }
        if (transaction.status() == TransactionStatus.PENDING) {
            throw new IllegalStateException("Una cuenta guardada no tiene movimientos pendientes");
        }
    }

    /** Ingresos aprobados menos retiradas aprobadas; las retiradas rechazadas no mueven el saldo. */
    private MonetaryAmount approvedBalance(Currency currency) {
        MonetaryAmount total = MonetaryAmount.zero(currency);
        for (Transaction transaction : transactions) {
            if (transaction.isApproved()) {
                total = transaction.type() == TransactionType.DEPOSIT
                        ? total.add(transaction.amount())
                        : total.subtract(transaction.amount());
            }
        }
        return total;
    }

    /** Añade un movimiento al final del historial y lo devuelve. */
    Transaction add(Transaction transaction) {
        transactions.add(transaction);
        return transaction;
    }

    /** Los movimientos en orden, en una copia que no se puede modificar. */
    List<Transaction> asList() {
        return List.copyOf(transactions);
    }
}
