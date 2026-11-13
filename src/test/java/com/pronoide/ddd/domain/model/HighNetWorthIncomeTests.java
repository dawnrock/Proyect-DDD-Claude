package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HighNetWorthIncomeTests {

    @ParameterizedTest(name = "{0} EUR → {1}")
    @CsvSource({"100000.01, true", "250000.00, true", "100000.00, false", "40000.00, false"})
    @DisplayName("Solo unos ingresos verificados por encima de 100.000,00 € dan el alto patrimonio")
    void solo_unos_ingresos_por_encima_del_umbral_dan_el_alto_patrimonio(String income, boolean highNetWorth) {
        assertThat(HighNetWorthIncome.isMetBy(Optional.of(MonetaryAmount.euros(income)))).isEqualTo(highNetWorth);
    }

    @ParameterizedTest(name = "{0} EUR → {1}")
    @CsvSource({"250000.00, HIGH_NET_WORTH", "100000.00, RETAIL"})
    @DisplayName("Los ingresos verificados deciden el segmento con el que se registra el titular")
    void los_ingresos_verificados_deciden_el_segmento_del_titular(String income, CustomerSegment segment) {
        assertThat(HighNetWorthIncome.segmentFor(Optional.of(MonetaryAmount.euros(income)))).isEqualTo(segment);
        assertThat(HighNetWorthIncome.segmentFor(Optional.empty())).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Sin ingresos verificados no hay alto patrimonio")
    void sin_ingresos_verificados_no_hay_alto_patrimonio() {
        assertThat(HighNetWorthIncome.isMetBy(Optional.empty())).isFalse();
    }

    @Test
    @DisplayName("Los ingresos verificados son obligatorios")
    void los_ingresos_verificados_son_obligatorios() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> HighNetWorthIncome.isMetBy(null))
                .withMessage("Los ingresos verificados son obligatorios");
    }
}
