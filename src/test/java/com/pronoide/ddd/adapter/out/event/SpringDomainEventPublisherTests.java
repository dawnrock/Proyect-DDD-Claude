package com.pronoide.ddd.adapter.out.event;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import com.pronoide.ddd.domain.model.MoneyDeposited;
import com.pronoide.ddd.domain.model.MoneyWithdrawn;
import com.pronoide.ddd.domain.model.MonetaryAmount;

class SpringDomainEventPublisherTests {

    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(spring);

    @Test
    @DisplayName("Entrega a Spring cada evento de dominio, en el orden en que ocurrieron")
    void entrega_a_spring_cada_evento_de_dominio_en_orden() {
        UUID accountId = UUID.randomUUID();
        MoneyDeposited ingreso =
                new MoneyDeposited(accountId, UUID.randomUUID(), MonetaryAmount.euros("50.00"), Instant.now());
        MoneyWithdrawn retirada =
                new MoneyWithdrawn(accountId, UUID.randomUUID(), MonetaryAmount.euros("20.00"), Instant.now());

        publisher.publishAll(List.of(ingreso, retirada));

        InOrder order = inOrder(spring);
        order.verify(spring).publishEvent(ingreso);
        order.verify(spring).publishEvent(retirada);
        order.verifyNoMoreInteractions();
    }
}
