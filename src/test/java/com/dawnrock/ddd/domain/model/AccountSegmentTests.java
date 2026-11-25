package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Segmento de la cuenta (#182): lo que el segmento del titular supone para la cuenta (R6-R8, R22-R27). */
class AccountSegmentTests {

    private static final Instant DAY = Instant.parse("2026-11-03T10:15:30Z");
    private static final MonetaryAmount BELOW_MINIMUM = MonetaryAmount.euros("99999.99");
    private static final MonetaryAmount MINIMUM = MonetaryAmount.euros("100000.00");

    private static AccountHolder holder(CustomerSegment segment) {
        return AccountHolder.reconstitute(UUID.randomUUID(), "Ada Lovelace", new TaxId("12345678Z"), segment);
    }

    @Nested
    @DisplayName("Descubierto")
    class Overdraft {

        @ParameterizedTest
        @CsvSource({"RETAIL, 0.00, true", "RETAIL, -0.01, false", "HIGH_NET_WORTH, -1000.00, true",
            "HIGH_NET_WORTH, -1000.01, false"})
        @DisplayName("solo un titular de alto patrimonio puede quedar en negativo, y hasta -1.000 EUR")
        void onlyHighNetWorthMayGoNegativeDownToTheLimit(CustomerSegment segment, String balance, boolean allowed) {
            assertThat(AccountSegment.of(holder(segment)).allows(MonetaryAmount.euros(balance))).isEqualTo(allowed);
        }
    }

    @Nested
    @DisplayName("Tras un ingreso")
    class AfterDeposit {

        @Test
        @DisplayName("un saldo que llega al patrimonio mínimo convierte al minorista en alto patrimonio, una sola vez")
        void reachingTheMinimumNetWorthPromotesARetailHolderOnce() {
            AccountHolder minorista = holder(CustomerSegment.RETAIL);
            AccountSegment segment = AccountSegment.of(minorista);

            assertThat(segment.afterDeposit(MINIMUM)).isTrue();
            assertThat(minorista.isHighNetWorth()).isTrue();
            assertThat(segment.afterDeposit(MINIMUM)).isFalse();
        }

        @Test
        @DisplayName("un saldo por debajo del patrimonio mínimo no cambia el segmento")
        void aBalanceBelowTheMinimumDoesNotChangeTheSegment() {
            AccountHolder minorista = holder(CustomerSegment.RETAIL);

            assertThat(AccountSegment.of(minorista).afterDeposit(BELOW_MINIMUM)).isFalse();
            assertThat(minorista.isHighNetWorth()).isFalse();
        }

        @Test
        @DisplayName("recuperar el patrimonio mínimo cancela la revisión programada")
        void recoveringTheMinimumCancelsTheScheduledReview() {
            AccountSegment segment = AccountSegment.restore(holder(CustomerSegment.HIGH_NET_WORTH), DAY);

            assertThat(segment.afterDeposit(MINIMUM)).isFalse();
            assertThat(segment.nextReview()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Programación de la revisión")
    class Schedule {

        @Test
        @DisplayName("una retirada que deja al alto patrimonio bajo el mínimo programa la revisión un mes después, y otra no la mueve")
        void aWithdrawalBelowTheMinimumSchedulesTheReviewOneMonthLaterOnce() {
            AccountSegment segment = AccountSegment.of(holder(CustomerSegment.HIGH_NET_WORTH));

            segment.afterWithdrawal(BELOW_MINIMUM, DAY);
            segment.afterWithdrawal(BELOW_MINIMUM, DAY.plusSeconds(86_400));

            assertThat(segment.nextReview()).contains(Instant.parse("2026-12-03T10:15:30Z"));
        }

        @Test
        @DisplayName("una retirada de un minorista no programa ninguna revisión")
        void aRetailWithdrawalSchedulesNoReview() {
            AccountSegment segment = AccountSegment.of(holder(CustomerSegment.RETAIL));

            segment.afterWithdrawal(MonetaryAmount.euros("10.00"), DAY);

            assertThat(segment.nextReview()).isEmpty();
        }

        @Test
        @DisplayName("un alto patrimonio que abre bajo el mínimo tiene su primera revisión a los seis meses")
        void aHighNetWorthOpeningBelowTheMinimumHasItsFirstReviewSixMonthsLater() {
            AccountSegment segment = AccountSegment.of(holder(CustomerSegment.HIGH_NET_WORTH));

            segment.afterOpening(BELOW_MINIMUM, DAY);

            assertThat(segment.nextReview()).contains(Instant.parse("2027-05-03T10:15:30Z"));
        }

        @Test
        @DisplayName("una apertura con el patrimonio mínimo no programa revisión")
        void anOpeningWithTheMinimumSchedulesNoReview() {
            AccountSegment segment = AccountSegment.of(holder(CustomerSegment.HIGH_NET_WORTH));

            segment.afterOpening(MINIMUM, DAY);

            assertThat(segment.nextReview()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Revisión del segmento")
    class Review {

        @Test
        @DisplayName("vence desde su fecha en adelante, no antes")
        void isDueFromItsDateOn() {
            AccountSegment segment = AccountSegment.restore(holder(CustomerSegment.HIGH_NET_WORTH), DAY);

            assertThat(segment.reviewDue(DAY.minusSeconds(1))).isFalse();
            assertThat(segment.reviewDue(DAY)).isTrue();
            assertThat(AccountSegment.of(holder(CustomerSegment.HIGH_NET_WORTH)).reviewDue(DAY)).isFalse();
        }

        @Test
        @DisplayName("con los ingresos todavía altos se conserva el segmento y la siguiente es seis meses después")
        void withIncomeStillHighTheSegmentStaysAndTheNextReviewIsSixMonthsLater() {
            AccountHolder titular = holder(CustomerSegment.HIGH_NET_WORTH);
            AccountSegment segment = AccountSegment.restore(titular, DAY);

            assertThat(segment.resolveReview(DAY, true)).isFalse();
            assertThat(titular.isHighNetWorth()).isTrue();
            assertThat(segment.nextReview()).contains(Instant.parse("2027-05-03T10:15:30Z"));
        }

        @Test
        @DisplayName("sin los ingresos altos el titular vuelve a minorista y no queda revisión")
        void withoutHighIncomeTheHolderGoesBackToRetail() {
            AccountHolder titular = holder(CustomerSegment.HIGH_NET_WORTH);
            AccountSegment segment = AccountSegment.restore(titular, DAY);

            assertThat(segment.resolveReview(DAY, false)).isTrue();
            assertThat(titular.isHighNetWorth()).isFalse();
            assertThat(segment.nextReview()).isEmpty();
        }

        @Test
        @DisplayName("no se resuelve antes de vencer ni sin el momento de la revisión")
        void cannotBeResolvedBeforeItIsDueOrWithoutAMoment() {
            AccountSegment segment = AccountSegment.restore(holder(CustomerSegment.HIGH_NET_WORTH), DAY);

            assertThatIllegalStateException().isThrownBy(() -> segment.resolveReview(DAY.minusSeconds(1), true))
                    .withMessage("La revisión del segmento aún no está vencida");
            assertThatNullPointerException().isThrownBy(() -> segment.resolveReview(null, true))
                    .withMessage("El momento de la revisión es obligatorio");
        }

        @Test
        @DisplayName("una cuenta guardada de un minorista no puede tener revisión pendiente")
        void aStoredRetailAccountCannotHaveAPendingReview() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> AccountSegment.restore(holder(CustomerSegment.RETAIL), DAY))
                    .withMessage("Solo una cuenta de alto patrimonio tiene revisión de segmento pendiente");
        }
    }
}
