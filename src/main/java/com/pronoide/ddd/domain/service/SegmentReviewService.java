package com.pronoide.ddd.domain.service;

import java.time.Instant;
import java.util.Objects;

import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.HighNetWorthIncome;
import com.pronoide.ddd.domain.model.IncomeVerifier;

/**
 * Servicio de dominio de la revisión del segmento. Cuando a una cuenta le toca revisión (lleva el plazo por debajo del
 * patrimonio mínimo), vuelve a verificar los ingresos del titular en la agencia tributaria y le comunica el resultado
 * al agregado, que decide. Solo consulta a la agencia si la revisión está vencida.
 */
public class SegmentReviewService {

    private final IncomeVerifier incomeVerifier;

    public SegmentReviewService(IncomeVerifier incomeVerifier) {
        this.incomeVerifier = Objects.requireNonNull(incomeVerifier, "El verificador de ingresos es obligatorio");
    }

    /** Revisa el segmento del titular de la cuenta si a {@code now} ya le toca; si no, no hace nada. */
    public void review(CheckingAccount account, Instant now) {
        Objects.requireNonNull(account, "La cuenta es obligatoria");
        if (!account.segmentReviewDue(now)) {
            return;
        }
        boolean incomeStillHigh =
                HighNetWorthIncome.isMetBy(incomeVerifier.verifiedAnnualIncome(account.primaryHolder().taxId()));
        account.resolveSegmentReview(now, incomeStillHigh);
    }
}
