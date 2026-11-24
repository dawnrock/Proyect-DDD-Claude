package com.dawnrock.ddd.adapter.out.event;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import org.springframework.stereotype.Component;

import com.dawnrock.ddd.domain.model.CheckingAccountOpened;
import com.dawnrock.ddd.domain.model.CustomerSegmentChanged;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;

import tools.jackson.databind.json.JsonMapper;

/**
 * Registro de los eventos de dominio que guarda el outbox (#165, ADR 0021) y su JSON. Cada evento se guarda con un
 * nombre lógico, su nombre simple ({@code MoneyDeposited}), y no con el de su clase, para que la fila siga leyéndose si
 * la clase cambia de paquete. Los eventos son {@code record}: Jackson los escribe y los lee por su constructor canónico,
 * sin anotaciones en el dominio. Un evento nuevo se añade aquí; un test comprueba que no falte ninguno.
 */
@Component
class DomainEventTypes {

    private static final Map<String, Class<? extends DomainEvent>> TYPES = Stream.of(
                    CheckingAccountOpened.class,
                    MoneyDeposited.class,
                    MoneyWithdrawn.class,
                    WithdrawalRejected.class,
                    CustomerSegmentChanged.class)
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    /**
     * Sin los {@code is…()} derivados ({@code isPositive}, {@code isMonABank}): el JSON lleva solo los componentes de
     * cada record, que es lo que necesita su constructor para volver a crearlo.
     */
    private final JsonMapper json = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withIsGetterVisibility(Visibility.NONE))
            .build();

    String typeOf(DomainEvent event) {
        String name = event.getClass().getSimpleName();
        if (!TYPES.containsKey(name)) {
            throw new IllegalArgumentException("El evento %s no está registrado en el outbox".formatted(name));
        }
        return name;
    }

    String toJson(DomainEvent event) {
        return json.writeValueAsString(event);
    }

    DomainEvent fromJson(String type, String payload) {
        Class<? extends DomainEvent> eventClass = TYPES.get(type);
        if (eventClass == null) {
            throw new IllegalArgumentException("Tipo de evento desconocido en el outbox: " + type);
        }
        return json.readValue(payload, eventClass);
    }

    Set<String> registeredNames() {
        return TYPES.keySet();
    }
}
