package com.dawnrock.ddd.domain.model;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * Segmento de la cuenta (#182): lo que el segmento del titular supone para su cuenta. Es parte del agregado
 * {@link CheckingAccount}, que es el único que lo usa y quien registra los eventos.
 *
 * <ul>
 *   <li>El descubierto que permite: solo al alto patrimonio y hasta {@link #OVERDRAFT_LIMIT} (R6-R8).</li>
 * *   <li>El paso a alto patrimonio cuando el saldo llega al patrimonio mínimo ({@link #MINIMUM_NET_WORTH}).</li>
 *   <li>La revisión del segmento: se programa un {@link #SEGMENT_GRACE_PERIOD} después de la retirada que deja una
 *       cuenta de alto patrimonio bajo el mínimo, se cancela si lo recupera y, al resolverla, el titular conserva el
 *       segmento con una nueva revisión a los {@link #INCOME_REVIEW_PERIOD} o vuelve a minorista (R22-R27).</li>
 * </ul>
 */
final class AccountSegment {

    /** Saldo negativo máximo de una cuenta de alto patrimonio (R8). */
    private static final MonetaryAmount OVERDRAFT_LIMIT = MonetaryAmount.euros("-1000.00");
    /** Saldo con el que un titular pasa a alto patrimonio y por debajo del cual se programa la revisión. */
    private static final MonetaryAmount MINIMUM_NET_WORTH = MonetaryAmount.euros("100000.00");
    /** Plazo desde que una cuenta de alto patrimonio cae bajo el patrimonio mínimo hasta que se revisa su segmento. */
    private static final Period SEGMENT_GRACE_PERIOD = Period.ofMonths(1);
    /** Cada cuánto se vuelven a verificar los ingresos de un titular que conserva el segmento sin el saldo mínimo. */
    private static final Period INCOME_REVIEW_PERIOD = Period.ofMonths(6);

    private final AccountHolder holder;
    private Instant nextReview;

    private AccountSegment(AccountHolder holder, Instant nextReview) {
        this.holder = holder;
        this.nextReview = nextReview;
    }

    /** El de una cuenta que se abre: sin revisión pendiente. */
    static AccountSegment of(AccountHolder holder) {
        return new AccountSegment(holder, null);
    }

    /**
     * El de una cuenta guardada.
     *
     * @param nextReview cuándo toca revisar el segmento, o {@code null} si no hay revisión pendiente
     * @throws IllegalStateException si un titular minorista tiene revisión pendiente
     */
    static AccountSegment restore(AccountHolder holder, Instant nextReview) {
        if (nextReview != null && !holder.isHighNetWorth()) {
            throw new IllegalStateException("Solo una cuenta de alto patrimonio tiene revisión de segmento pendiente");
        }
        return new AccountSegment(holder, nextReview);
    }

    /** Si la cuenta puede quedarse con {@code newBalance}: en negativo solo el alto patrimonio, hasta el límite. */
    boolean allows(MonetaryAmount newBalance) {
        if (!newBalance.isNegative()) {
            return true;
        }
        return holder.isHighNetWorth() && !newBalance.isLessThan(OVERDRAFT_LIMIT);
    }

    /**
     * Tras un ingreso aprobado: con el patrimonio mínimo se cancela la revisión pendiente y un minorista pasa a alto
     * patrimonio.
     *
     * @return {@code true} si el titular ha cambiado de segmento
     */
    boolean afterDeposit(MonetaryAmount balance) {
        if (balance.isLessThan(MINIMUM_NET_WORTH)) {
            return false;
        }
        nextReview = null;
        if (holder.isHighNetWorth()) {
            return false;
        }
        holder.promoteToHighNetWorth();
        return true;
    }

    /** Tras una retirada aprobada: si deja al alto patrimonio bajo el mínimo y no hay revisión, se programa. */
    void afterWithdrawal(MonetaryAmount balance, Instant occurredOn) {
        if (holder.isHighNetWorth() && balance.isLessThan(MINIMUM_NET_WORTH) && nextReview == null) {
            nextReview = after(occurredOn, SEGMENT_GRACE_PERIOD);
        }
    }

    /** Tras la apertura: un alto patrimonio que abre bajo el mínimo tiene su primera revisión semestral. */
    void afterOpening(MonetaryAmount balance, Instant occurredOn) {
        if (holder.isHighNetWorth() && balance.isLessThan(MINIMUM_NET_WORTH)) {
            nextReview = after(occurredOn, INCOME_REVIEW_PERIOD);
        }
    }

    /** Si a {@code now} ya le toca revisión al segmento del titular. */
    boolean reviewDue(Instant now) {
        Objects.requireNonNull(now, "El momento de la revisión es obligatorio");
        return nextReview != null && !now.isBefore(nextReview);
    }

    /**
     * Resuelve una revisión vencida con el resultado de verificar de nuevo los ingresos del titular.
     *
     * @return {@code true} si el titular ha vuelto a minorista
     * @throws IllegalStateException si la revisión aún no está vencida
     */
    boolean resolveReview(Instant now, boolean incomeStillHigh) {
        if (!reviewDue(now)) {
            throw new IllegalStateException("La revisión del segmento aún no está vencida");
        }
        if (!holder.isHighNetWorth()) {
            // Defensa: una revisión pendiente siempre es de un alto patrimonio (restore y afterWithdrawal lo exigen)
            throw new IllegalStateException("Solo se revisa el segmento de un titular de alto patrimonio");
        }
        if (incomeStillHigh) {
            nextReview = after(now, INCOME_REVIEW_PERIOD);
            return false;
        }
        holder.demoteToRetail();
        nextReview = null;
        return true;
    }

    /** Cuándo toca revisar el segmento; vacío si no hay revisión pendiente. */
    Optional<Instant> nextReview() {
        return Optional.ofNullable(nextReview);
    }

    /** Plazos en meses naturales, en UTC. */
    private static Instant after(Instant instant, Period period) {
        return instant.atZone(ZoneOffset.UTC).plus(period).toInstant();
    }
}
