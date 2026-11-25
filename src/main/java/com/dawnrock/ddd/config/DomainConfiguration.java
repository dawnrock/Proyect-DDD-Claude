package com.dawnrock.ddd.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dawnrock.ddd.application.AccountOpeningService;
import com.dawnrock.ddd.application.AccountOwnershipRepository;
import com.dawnrock.ddd.application.AccountSegmentReviewService;
import com.dawnrock.ddd.application.CheckingAccountQueryService;
import com.dawnrock.ddd.application.CustomerOwnershipRepository;
import com.dawnrock.ddd.application.MoneyMovementService;
import com.dawnrock.ddd.application.UnitOfWork;
import com.dawnrock.ddd.application.listener.AuditLogListener;
import com.dawnrock.ddd.application.listener.CustomerNotificationListener;
import com.dawnrock.ddd.domain.model.AccountNumberSequence;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.IncomeVerifier;
import com.dawnrock.ddd.domain.service.MoneyTransferService;
import com.dawnrock.ddd.domain.service.SegmentReviewService;

/**
 * Construye el núcleo del hexágono (#20), que no lleva anotaciones de Spring, y le entrega sus adaptadores. Los
 * adaptadores de salida siguen registrándose solos ({@code @Component}/{@code @Repository}, son package-private y viven
 * fuera del núcleo); aquí se reciben por el tipo de su puerto.
 */
@Configuration
public class DomainConfiguration {

    @Bean
    public MoneyTransferService moneyTransferService() {
        return new MoneyTransferService();
    }

    /** Revisión del segmento: vuelve a consultar los ingresos por el mismo puerto que la factoría. */
    @Bean
    public SegmentReviewService segmentReviewService(IncomeVerifier incomeVerifier) {
        return new SegmentReviewService(incomeVerifier);
    }

    /** Reloj con el que la aplicación pregunta la hora; los tests lo sustituyen por uno que se puede adelantar. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CheckingAccountFactory checkingAccountFactory(AccountNumberSequence numbering,
                                                         IncomeVerifier incomeVerifier) {
        return new CheckingAccountFactory(numbering, incomeVerifier);
    }

    /** Implementa {@code CheckingAccountQueries}; los adaptadores de entrada reciben cada servicio por su puerto. */
    @Bean
    public CheckingAccountQueryService checkingAccountQueryService(CheckingAccountRepository repository,
                                                         AccountOwnershipRepository ownership,
                                                         UnitOfWork unitOfWork, CustomerRepository customers,
                                                         CustomerOwnershipRepository customerOwnership) {
        return new CheckingAccountQueryService(repository, ownership, unitOfWork, customers, customerOwnership);
    }

    /** Implementa {@code AccountOpeningCommands}: apertura, alta de clientes, otra cuenta y regeneración del IBAN. */
    @Bean
    public AccountOpeningService accountOpeningService(CheckingAccountRepository repository,
                                                       AccountOwnershipRepository ownership,
                                                       DomainEventPublisher publisher, CheckingAccountFactory factory,
                                                       UnitOfWork unitOfWork, CustomerRepository customers,
                                                       CustomerOwnershipRepository customerOwnership) {
        return new AccountOpeningService(repository, ownership, publisher, factory, unitOfWork, customers,
                customerOwnership);
    }

    /** Implementa {@code MoneyMovementCommands}: ingresos, retiradas y transferencias. */
    @Bean
    public MoneyMovementService moneyMovementService(CheckingAccountRepository repository,
                                                     AccountOwnershipRepository ownership,
                                                     DomainEventPublisher publisher,
                                                     MoneyTransferService transferService,
                                                     SegmentReviewService segmentReview, Clock clock,
                                                     UnitOfWork unitOfWork) {
        return new MoneyMovementService(repository, ownership, publisher, transferService, segmentReview, clock,
                unitOfWork);
    }

    /** Implementa {@code SegmentReviewCommands}: la revisión periódica de segmentos que dispara la tarea programada. */
    @Bean
    public AccountSegmentReviewService accountSegmentReviewService(CheckingAccountRepository repository,
                                                                   DomainEventPublisher publisher,
                                                                   SegmentReviewService segmentReview, Clock clock,
                                                                   UnitOfWork unitOfWork) {
        return new AccountSegmentReviewService(repository, publisher, segmentReview, clock, unitOfWork);
    }

    /** Consumidores de los eventos de dominio: Spring les entrega cada evento por su {@code @EventListener}. */
    @Bean
    public AuditLogListener auditLogListener() {
        return new AuditLogListener();
    }

    @Bean
    public CustomerNotificationListener customerNotificationListener() {
        return new CustomerNotificationListener();
    }
}
