package com.dawnrock.ddd.application;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.mockito.ArgumentCaptor;

import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;

/** Datos y comprobaciones comunes de los tests de los servicios de aplicación (#174). */
final class ServiceTestSupport {

    /** Los casos de uso se piden como administrador; la propiedad se prueba en sus tests propios. */
    static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private ServiceTestSupport() {
    }

    /** Eventos publicados en cada llamada a {@code publishAll} del publicador simulado, en orden. */
    @SuppressWarnings("unchecked")
    static List<List<DomainEvent>> publishedBatches(DomainEventPublisher publisher) {
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher, atLeast(0)).publishAll(captor.capture());
        return captor.getAllValues();
    }

    /** Titular minorista por NIF (40.000 € verificados); el IBAN lo genera la factoría. */
    static OpenAccountCommand.Builder retailOpening() {
        return OpenAccountCommand.builder()
                .holderName("Ada Lovelace")
                .holderTaxId("12345678Z")
                .holderEmail("ada@example.com")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("ada");
    }

    /** Titular de alto patrimonio por NIF (250.000 € verificados). */
    static OpenAccountCommand.Builder highNetWorthOpening() {
        return retailOpening().holderName("Grace Hopper").holderTaxId("87654321X").holderEmail("grace@example.com")
                .ownerUsername("grace");
    }
}
