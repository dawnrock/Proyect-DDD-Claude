package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del puerto {@link AccountNumberSequence}: lo que cumple cualquier implementación. Cada adaptador lo hereda
 * con un test que crea una numeración nueva y añade solo lo propio de su tecnología.
 */
public abstract class AccountNumberSequenceContract {

    protected AccountNumberSequence sequence;

    /** Una numeración nueva en cada test. */
    protected abstract AccountNumberSequence createSequence();

    @BeforeEach
    void createNewSequence() {
        sequence = createSequence();
    }

    @Test
    @DisplayName("Los números generados no se repiten")
    void generatedNumbersAreNeverRepeated() {
        List<AccountNumber> generated = IntStream.range(0, 100).mapToObj(i -> sequence.next()).toList();

        assertThat(generated).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Los números generados son IBAN españoles que superan la comprobación módulo 97")
    void generatedNumbersAreValidSpanishIbans() {
        IntStream.range(0, 50).forEach(i -> {
            String iban = sequence.next().value();
            assertThat(iban).startsWith("ES").hasSize(24);
            String rearranged = iban.substring(4) + iban.substring(0, 4);
            String digits = rearranged.chars()
                    .mapToObj(c -> Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf((char) c))
                    .reduce("", String::concat);
            assertThat(new BigInteger(digits).mod(BigInteger.valueOf(97))).as(iban).isEqualTo(BigInteger.ONE);
        });
    }
}
