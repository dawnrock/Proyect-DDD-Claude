package com.pronoide.ddd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pronoide.ddd.application.AccountOwnershipRepository;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.listener.AuditLogListener;
import com.pronoide.ddd.application.listener.CustomerNotificationListener;
import com.pronoide.ddd.domain.model.AccountNumberSequence;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.IncomeVerifier;
import com.pronoide.ddd.domain.service.MoneyTransferService;

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

    @Bean
    public CheckingAccountFactory checkingAccountFactory(AccountNumberSequence numbering,
                                                         IncomeVerifier incomeVerifier) {
        return new CheckingAccountFactory(numbering, incomeVerifier);
    }

    /** Implementa los puertos de entrada; los adaptadores de entrada lo reciben por ellos, nunca por su clase. */
    @Bean
    public CheckingAccountService checkingAccountService(CheckingAccountRepository repository,
                                                         AccountOwnershipRepository ownership,
                                                         DomainEventPublisher publisher, CheckingAccountFactory factory,
                                                         MoneyTransferService transferService) {
        return new CheckingAccountService(repository, ownership, publisher, factory, transferService);
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
