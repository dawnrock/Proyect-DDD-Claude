package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountHolderTests {

    @Test
    @DisplayName("Dos titulares con los mismos datos son entidades distintas")
    void twoHoldersWithTheSameDataAreDifferentEntities() {
        AccountHolder uno = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
        AccountHolder otro = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
        assertThat(uno).isNotEqualTo(otro);
        assertThat(uno.id()).isNotEqualTo(otro.id());
    }

    @Test
    @DisplayName("Un titular mantiene su identidad aunque cambien sus atributos")
    void aHolderKeepsItsIdentityWhenItsAttributesChange() {
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
    void theHolderNameIsRequiredOnRegistration() {
        assertThatThrownBy(() -> AccountHolder.register(null, new TaxId("12345678Z"), CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El nombre del titular es obligatorio");
        assertThatThrownBy(() -> AccountHolder.register("   ", new TaxId("12345678Z"), CustomerSegment.RETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del titular no puede estar vacío");
    }

    @Test
    @DisplayName("Un cambio de nombre vacío se rechaza y el titular conserva el nombre anterior")
    void aBlankRenameIsRejectedAndThePreviousNameIsKept() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);

        assertThatThrownBy(() -> titular.rename(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> titular.rename("")).isInstanceOf(IllegalArgumentException.class);

        assertThat(titular.fullName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("El titular conserva el NIF con el que se dio de alta")
    void theHolderKeepsTheTaxIdItWasRegisteredWith() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678z"), CustomerSegment.RETAIL);

        assertThat(titular.taxId()).isEqualTo(new TaxId("12345678Z"));
    }

    @Test
    @DisplayName("El NIF del titular es obligatorio")
    void theHolderTaxIdIsRequired() {
        assertThatThrownBy(() -> AccountHolder.register("Ada Lovelace", null, CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El NIF del titular es obligatorio");
    }

    @Test
    @DisplayName("Un titular de alto patrimonio puede volver a ser minorista")
    void aHighNetWorthHolderCanBeDemotedToRetail() {
        AccountHolder titular =
                AccountHolder.register("Grace Hopper", new TaxId("87654321X"), CustomerSegment.HIGH_NET_WORTH);

        titular.demoteToRetail();

        assertThat(titular.segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Un titular reconstituido conserva su identidad, su nombre, su NIF y su segmento")
    void aReconstitutedHolderKeepsItsIdentityNameTaxIdAndSegment() {
        UUID id = UUID.randomUUID();
        AccountHolder titular = AccountHolder.reconstitute(id, "Grace Hopper", new TaxId("87654321X"),
                CustomerSegment.HIGH_NET_WORTH);

        assertThat(titular.id()).isEqualTo(id);
        assertThat(titular.fullName()).isEqualTo("Grace Hopper");
        assertThat(titular.taxId()).isEqualTo(new TaxId("87654321X"));
        assertThat(titular.isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("Un titular no se reconstituye sin identidad, sin NIF, sin segmento ni con un nombre vacío")
    void aHolderIsNotReconstitutedWithoutItsRequiredData() {
        TaxId taxId = new TaxId("87654321X");
        assertThatThrownBy(() -> AccountHolder.reconstitute(null, "Grace Hopper", taxId, CustomerSegment.RETAIL))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AccountHolder.reconstitute(UUID.randomUUID(), "Grace Hopper", null,
                CustomerSegment.RETAIL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AccountHolder.reconstitute(UUID.randomUUID(), "Grace Hopper", taxId, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AccountHolder.reconstitute(UUID.randomUUID(), " ", taxId, CustomerSegment.RETAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del titular no puede estar vacío");
    }
}
