package com.pronoide.ddd.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;

import org.immutables.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.application.ApplicationValue;

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
        void genera_un_builder_con_el_nombre_del_tipo_sin_el_sufijo_def() {
            SampleCommand command = SampleCommand.builder()
                    .holderName("Ada Lovelace")
                    .amount(new BigDecimal("50.00"))
                    .build();

            assertThat(command.holderName()).isEqualTo("Ada Lovelace");
            assertThat(command.amount()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Dos instancias con los mismos valores son iguales")
        void dos_instancias_con_los_mismos_valores_son_iguales() {
            SampleCommand one = SampleCommand.builder().holderName("Ada").amount(BigDecimal.TEN).build();
            SampleCommand other = SampleCommand.builder().holderName("Ada").amount(BigDecimal.TEN).build();

            assertThat(one).isEqualTo(other).hasSameHashCodeAs(other).isNotSameAs(other);
        }

        @Test
        @DisplayName("No se puede construir sin un atributo obligatorio")
        void no_se_puede_construir_sin_un_atributo_obligatorio() {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> SampleCommand.builder().holderName("Ada").build())
                    .withMessageContaining("amount");
        }
    }
}
