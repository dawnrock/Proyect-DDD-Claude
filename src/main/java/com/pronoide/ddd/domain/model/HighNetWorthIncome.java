package com.pronoide.ddd.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Umbral de ingresos anuales verificados con el que un titular es de alto patrimonio (R24): por encima de
 * {@code 100.000,00 €}. Con ingresos menores, iguales al umbral o sin verificar, no lo es. Lo comparten la apertura de
 * la cuenta y la revisión del segmento, que deciden en {@code CheckingAccountService} con lo que verifica la agencia
 * tributaria.
 */
public final class HighNetWorthIncome {

    private static final MonetaryAmount THRESHOLD = MonetaryAmount.euros("100000.00");

    private HighNetWorthIncome() {
    }

    public static boolean isMetBy(Optional<MonetaryAmount> verifiedAnnualIncome) {
        return Objects.requireNonNull(verifiedAnnualIncome, "Los ingresos verificados son obligatorios")
                .map(income -> THRESHOLD.isLessThan(income))
                .orElse(false);
    }

    /** R24: segmento con el que se registra un titular según sus ingresos verificados. */
    public static CustomerSegment segmentFor(Optional<MonetaryAmount> verifiedAnnualIncome) {
        return isMetBy(verifiedAnnualIncome) ? CustomerSegment.HIGH_NET_WORTH : CustomerSegment.RETAIL;
    }
}
