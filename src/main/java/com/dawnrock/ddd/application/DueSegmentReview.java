package com.dawnrock.ddd.application;

import java.time.Clock;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

/**
 * Revisa el segmento de una cuenta si le toca (#98): vuelve a verificar los ingresos del titular con el servicio de
 * dominio y guarda la cuenta. La usan la revisión diaria y los cargos, que revisan antes de decidir el descubierto
 * (#174).
 */
final class DueSegmentReview {

    private final SegmentReviewService segmentReview;
    private final Clock clock;

    DueSegmentReview(SegmentReviewService segmentReview, Clock clock) {
        this.segmentReview = segmentReview;
        this.clock = clock;
    }

    /** Revisa el segmento si le toca y guarda la cuenta. Devuelve si el titular ha vuelto a minorista. */
    boolean reviewIfDue(CheckingAccount account, AccountChanges changes) {
        if (!account.segmentReviewDue(clock.instant())) {
            return false;
        }
        boolean wasHighNetWorth = account.primaryHolder().isHighNetWorth();
        segmentReview.review(account, clock.instant());
        changes.save(account);
        return wasHighNetWorth && !account.primaryHolder().isHighNetWorth();
    }
}
