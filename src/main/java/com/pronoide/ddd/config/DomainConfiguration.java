package com.pronoide.ddd.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pronoide.ddd.domain.model.AccountNumberSequence;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
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
    public CheckingAccountFactory checkingAccountFactory(AccountNumberSequence numbering, IncomeVerifier incomeVerifier) {
        return new CheckingAccountFactory(numbering, incomeVerifier);
    }
}
