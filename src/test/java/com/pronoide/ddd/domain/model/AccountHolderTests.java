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
        AccountHolder uno = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
        AccountHolder otro = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
        assertThat(uno).isNotEqualTo(otro);
        assertThat(uno.id()).isNotEqualTo(otro.id());
    }

    @Test
    @DisplayName("Un titular mantiene su identidad aunque cambien sus atributos")
    void un_titular_mantiene_su_identidad_aunque_cambien_sus_atributos() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
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
        assertThatThrownBy(() -> AccountHolder.register(null, new TaxId("12345678Z"), CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El nombre del titular es obligatorio");
        assertThatThrownBy(() -> AccountHolder.register("   ", new TaxId("12345678Z"), CustomerSegment.RETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del titular no puede estar vacío");
    }

    @Test
    @DisplayName("Un cambio de nombre vacío se rechaza y el titular conserva el nombre anterior")
    void un_cambio_de_nombre_vacio_se_rechaza_y_conserva_el_anterior() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);

        assertThatThrownBy(() -> titular.rename(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> titular.rename("")).isInstanceOf(IllegalArgumentException.class);

        assertThat(titular.fullName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("El titular conserva el NIF con el que se dio de alta")
    void el_titular_conserva_el_nif_con_el_que_se_dio_de_alta() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678z"), CustomerSegment.RETAIL);

        assertThat(titular.taxId()).isEqualTo(new TaxId("12345678Z"));
    }

    @Test
    @DisplayName("El NIF del titular es obligatorio")
    void el_nif_del_titular_es_obligatorio() {
        assertThatThrownBy(() -> AccountHolder.register("Ada Lovelace", null, CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El NIF del titular es obligatorio");
    }

    @Test
    @DisplayName("Un titular de alto patrimonio puede volver a ser minorista")
    void un_titular_de_alto_patrimonio_puede_volver_a_ser_minorista() {
        AccountHolder titular =
                AccountHolder.register("Grace Hopper", new TaxId("87654321X"), CustomerSegment.HIGH_NET_WORTH);

        titular.demoteToRetail();

        assertThat(titular.segment()).isEqualTo(CustomerSegment.RETAIL);
    }
}
