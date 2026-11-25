package com.dawnrock.ddd.application;

import static com.dawnrock.ddd.application.ServiceTestSupport.ADMIN;
import static com.dawnrock.ddd.application.ServiceTestSupport.publishedBatches;
import static com.dawnrock.ddd.application.ServiceTestSupport.retailOpening;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.CustomerSegment;
import com.dawnrock.ddd.domain.model.CustomerSegmentChanged;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.support.ApplicationServicesFixture;
import com.dawnrock.ddd.support.MutableClock;

/** Revisión periódica de segmentos (#98) con los adaptadores en memoria detrás de los puertos (#174). */
@DisplayName("Revisión periódica de segmentos")
class AccountSegmentReviewServiceTests {

    private final MutableClock clock = new MutableClock();
    private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
    private CheckingAccountQueryService service;
    private AccountOpeningCommands opening;
    private SegmentReviewCommands segmentReview;
    private MoneyMovementCommands movements;
    private UUID belowMinimum;
    private UUID aboveMinimum;
    private UUID retail;

    @BeforeEach
    void setUp() {
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                .publisher(publisher)
                .clock(clock)
                .build();
        service = fixture.queryService();
        opening = fixture.accountOpeningService();
        segmentReview = fixture.segmentReviewService();
        movements = fixture.moneyMovementService();
        openThreeAccounts();
    }

    /** Tres cuentas: una de alto patrimonio que baja del mínimo, otra que sigue por encima y una minorista. */
    private void openThreeAccounts() {
        belowMinimum = opening.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("100000")).build()).id();
        movements.withdraw(ADMIN, WithdrawCommand.builder().accountId(belowMinimum).amount(new BigDecimal("500")).build());
        aboveMinimum = opening.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("100000")).build()).id();
        retail = opening.openAccount(ADMIN, retailOpening().build()).id();
        clearInvocations(publisher);
    }

    @Test
    @DisplayName("Pasado un mes, solo vuelve a minorista el titular que sigue bajo el mínimo, y se publica su cambio de segmento")
    void afterAMonthOnlyTheHolderStillBelowTheMinimumIsDemotedToRetail() {
        clock.advance(Duration.ofDays(32));

        int demoted = segmentReview.reviewSegments();

        assertThat(demoted).isEqualTo(1);
        assertThat(service.findAccount(ADMIN, belowMinimum).segment()).isEqualTo("RETAIL");
        assertThat(service.findAccount(ADMIN, aboveMinimum).segment()).isEqualTo("HIGH_NET_WORTH");
        assertThat(service.findAccount(ADMIN, retail).segment()).isEqualTo("RETAIL");
        assertThat(publishedBatches(publisher).stream().flatMap(List::stream))
                .singleElement()
                .isInstanceOfSatisfying(CustomerSegmentChanged.class, event -> {
                    assertThat(event.accountId()).isEqualTo(belowMinimum);
                    assertThat(event.segment()).isEqualTo(CustomerSegment.RETAIL);
                });
    }

    @Test
    @DisplayName("Antes de un mes la revisión no cambia ningún segmento ni publica eventos")
    void beforeAMonthTheReviewChangesNoSegment() {
        clock.advance(Duration.ofDays(20));

        assertThat(segmentReview.reviewSegments()).isZero();
        assertThat(service.findAccount(ADMIN, belowMinimum).segment()).isEqualTo("HIGH_NET_WORTH");
        assertThat(publishedBatches(publisher).stream().flatMap(List::stream)).isEmpty();
    }
}
