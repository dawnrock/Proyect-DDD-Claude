package com.dawnrock.ddd.support;

import static org.mockito.Mockito.mock;

import java.time.Clock;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.dawnrock.ddd.application.AccountOwnershipRepository;
import com.dawnrock.ddd.application.AccountSegmentReviewService;
import com.dawnrock.ddd.application.CheckingAccountService;
import com.dawnrock.ddd.application.CustomerOwnershipRepository;
import com.dawnrock.ddd.application.MoneyMovementService;
import com.dawnrock.ddd.application.UnitOfWork;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.service.MoneyTransferService;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

import lombok.Builder;

/**
 * Construye los servicios de aplicación de los tests sobre los adaptadores en memoria (#174). Cada colaborador tiene un
 * valor por defecto (repositorios vacíos, publicador simulado, agencia tributaria simulada, reloj del sistema) y el test
 * sustituye solo los que necesita ver o controlar:
 *
 * <pre>{@code
 * service = ApplicationServicesFixture.builder().accounts(accounts).clock(clock).build().service();
 * }</pre>
 */
@Builder
public final class ApplicationServicesFixture {

    @Builder.Default
    private final CheckingAccountRepository accounts = PersistenceTestAdapters.accounts();
    @Builder.Default
    private final AccountOwnershipRepository ownership = PersistenceTestAdapters.ownership();
    @Builder.Default
    private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
    @Builder.Default
    private final CheckingAccountFactory factory = new CheckingAccountFactory(PersistenceTestAdapters.accountNumbers(),
            TaxAgencyTestAdapters.incomeVerifier());
    @Builder.Default
    private final SegmentReviewService segmentReview = new SegmentReviewService(TaxAgencyTestAdapters.incomeVerifier());
    @Builder.Default
    private final Clock clock = Clock.systemUTC();
    @Builder.Default
    private final UnitOfWork unitOfWork = PersistenceTestAdapters.unitOfWork();
    @Builder.Default
    private final CustomerRepository customers = PersistenceTestAdapters.customers();
    @Builder.Default
    private final CustomerOwnershipRepository customerOwnership = PersistenceTestAdapters.customerOwnership();

    /** El servicio de aplicación con estos colaboradores. */
    public CheckingAccountService service() {
        return new CheckingAccountService(accounts, ownership, publisher, factory, unitOfWork, customers,
                customerOwnership);
    }

    /** Ingresos, retiradas y transferencias sobre los mismos colaboradores. */
    public MoneyMovementService moneyMovementService() {
        return new MoneyMovementService(accounts, ownership, publisher, new MoneyTransferService(), segmentReview, clock,
                unitOfWork);
    }

    /** La revisión periódica de segmentos sobre los mismos colaboradores. */
    public AccountSegmentReviewService segmentReviewService() {
        return new AccountSegmentReviewService(accounts, publisher, segmentReview, clock, unitOfWork);
    }
}
