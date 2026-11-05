package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountHolderTests {

    @Test
    @DisplayName("Dos titulares con los mismos datos son entidades distintas")
    void dos_titulares_con_los_mismos_datos_son_entidades_distintas() {
        AccountHolder uno = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        AccountHolder otro = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        assertThat(uno).isNotEqualTo(otro);
        assertThat(uno.id()).isNotEqualTo(otro.id());
    }

    @Test
    @DisplayName("Un titular mantiene su identidad aunque cambien sus atributos")
    void un_titular_mantiene_su_identidad_aunque_cambien_sus_atributos() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        UUID identidad = titular.id();
        titular.rename("Ada Byron");
        titular.promoteToHighNetWorth();
        assertThat(titular.id()).isEqualTo(identidad);
        assertThat(titular.fullName()).isEqualTo("Ada Byron");
        assertThat(titular.isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("El nombre del titular es obligatorio al darlo de alta")
    void el_nombre_del_titular_es_obligatorio_al_darlo_de_alta() {
        assertThatThrownBy(() -> AccountHolder.register(null, CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El nombre del titular es obligatorio");
        assertThatThrownBy(() -> AccountHolder.register("   ", CustomerSegment.RETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del titular no puede estar vacío");
    }

    @Test
    @DisplayName("Un cambio de nombre vacío se rechaza y el titular conserva el nombre anterior")
    void un_cambio_de_nombre_vacio_se_rechaza_y_conserva_el_anterior() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);

        assertThatThrownBy(() -> titular.rename(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> titular.rename("")).isInstanceOf(IllegalArgumentException.class);

        assertThat(titular.fullName()).isEqualTo("Ada Lovelace");
    }
}
