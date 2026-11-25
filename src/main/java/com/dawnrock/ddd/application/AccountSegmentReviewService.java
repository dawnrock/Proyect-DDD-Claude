package com.dawnrock.ddd.application;

import java.time.Clock;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

/**
 * Caso de uso de la revisión periódica de segmentos (#98): revisa todas las cuentas a las que les toca en una sola
 * unidad de trabajo y publica sus cambios de segmento al terminarla. Implementa {@link SegmentReviewCommands}; se llama
 * así porque el dominio ya tiene {@link SegmentReviewService}, que revisa una cuenta. Sin anotaciones de Spring: lo
 * construye {@code config} (#174).
 */
public class AccountSegmentReviewService implements SegmentReviewCommands {

    private final CheckingAccountRepository repository;
    private final AccountUnitOfWork accountUnit;
    private final DueSegmentReview dueSegmentReview;

    public AccountSegmentReviewService(CheckingAccountRepository repository, DomainEventPublisher publisher,
                                       SegmentReviewService segmentReview, Clock clock, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.accountUnit = new AccountUnitOfWork(unitOfWork, repository, publisher);
        this.dueSegmentReview = new DueSegmentReview(segmentReview, clock);
    }

    @Override
    public int reviewSegments() {
        return accountUnit.execute(changes -> {
            int demoted = 0;
            for (CheckingAccount account : repository.findAll()) {
                if (dueSegmentReview.reviewIfDue(account, changes)) {
                    demoted++;
                }
            }
            return demoted;
        });
    }
}
