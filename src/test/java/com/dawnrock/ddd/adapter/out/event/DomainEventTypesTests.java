package com.dawnrock.ddd.adapter.out.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccountOpened;
import com.dawnrock.ddd.domain.model.CustomerSegment;
import com.dawnrock.ddd.domain.model.CustomerSegmentChanged;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@DisplayName("Tipos de evento del outbox y su JSON")
class DomainEventTypesTests {

    private static final UUID ACCOUNT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TRANSACTION = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final Instant WHEN = Instant.parse("2026-11-24T10:15:30.123456Z");
    private static final MonetaryAmount AMOUNT = new MonetaryAmount(new BigDecimal("25.00"), Currency.getInstance("EUR"));

    private final DomainEventTypes types = new DomainEventTypes();

    static Stream<DomainEvent> events() {
        return Stream.of(
                new CheckingAccountOpened(ACCOUNT, new AccountNumber("ES8099990001450000000001"), "Ada Lovelace", AMOUNT,
                        WHEN),
                new MoneyDeposited(ACCOUNT, TRANSACTION, AMOUNT, WHEN),
                new MoneyWithdrawn(ACCOUNT, TRANSACTION, AMOUNT, WHEN),
                new WithdrawalRejected(ACCOUNT, TRANSACTION, AMOUNT, WHEN),
                new CustomerSegmentChanged(ACCOUNT, "Ada Lovelace", CustomerSegment.HIGH_NET_WORTH, WHEN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    @DisplayName("Cada evento pasa a JSON y vuelve igual, con su nombre lógico")
    void eachEventSurvivesTheRoundTripWithItsLogicalName(DomainEvent event) {
        String type = types.typeOf(event);
        String json = types.toJson(event);

        assertThat(type).isEqualTo(event.getClass().getSimpleName());
        assertThat(types.fromJson(type, json)).isEqualTo(event);
    }

    @Test
    @DisplayName("El JSON solo lleva los componentes del record, no los métodos derivados como isPositive o isMonABank")
    void theJsonOnlyCarriesTheRecordComponents() {
        String json = types.toJson(events().findFirst().orElseThrow());

        assertThat(json).contains("\"accountNumber\"", "\"initialDeposit\"", "\"amount\"", "\"currency\"")
                .doesNotContain("positive", "negative", "monABank");
    }

    @Test
    @DisplayName("Un nombre de tipo desconocido falla con un mensaje claro")
    void anUnknownTypeNameFailsWithAClearMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> types.fromJson("MoneyTeleported", "{}"))
                .withMessageContaining("MoneyTeleported");
    }

    @Test
    @DisplayName("Todos los eventos de dominio del modelo están registrados")
    void everyDomainEventOfTheModelIsRegistered() {
        List<String> declared = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dawnrock.ddd.domain.model")
                .stream()
                .filter(type -> type.isAssignableTo(DomainEvent.class) && !type.isInterface())
                .map(JavaClass::getSimpleName)
                .toList();

        assertThat(declared).isNotEmpty();
        assertThat(types.registeredNames()).containsExactlyInAnyOrderElementsOf(declared);
    }
}
