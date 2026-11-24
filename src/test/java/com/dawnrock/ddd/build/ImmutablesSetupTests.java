package com.dawnrock.ddd.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;

import org.immutables.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.application.ApplicationValue;

class ImmutablesSetupTests {

    @ApplicationValue
    @Value.Immutable
    interface SampleCommandDef {
        String holderName();

        BigDecimal amount();
    }

    @Nested
    @DisplayName("El estilo @ApplicationValue")
    class ApplicationValueStyle {

        @Test
        @DisplayName("Genera un builder con el nombre del tipo sin el sufijo Def")
        void generatesABuilderNamedAfterTheTypeWithoutTheDefSuffix() {
            SampleCommand command = SampleCommand.builder()
                    .holderName("Ada Lovelace")
                    .amount(new BigDecimal("50.00"))
                    .build();

            assertThat(command.holderName()).isEqualTo("Ada Lovelace");
            assertThat(command.amount()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Dos instancias con los mismos valores son iguales")
        void twoInstancesWithTheSameValuesAreEqual() {
            SampleCommand one = SampleCommand.builder().holderName("Ada").amount(BigDecimal.TEN).build();
            SampleCommand other = SampleCommand.builder().holderName("Ada").amount(BigDecimal.TEN).build();

            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other).isNotSameAs(other);
        }

        @Test
        @DisplayName("No se puede construir sin un atributo obligatorio")
        void cannotBeBuiltWithoutARequiredAttribute() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> SampleCommand.builder().holderName("Ada").build())
                    .withMessageContaining("amount");
        }
    }
}
