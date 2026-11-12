package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("Un titular de alto patrimonio puede volver a ser minorista")
    void un_titular_de_alto_patrimonio_puede_volver_a_ser_minorista() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        titular.promoteToHighNetWorth();

        titular.demoteToRetail();

        assertThat(titular.segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(titular.isHighNetWorth()).isFalse();
    }
}
