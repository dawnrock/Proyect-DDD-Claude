package com.pronoide.ddd.domain.model;

import java.util.Objects;

/**
 * Factoría de {@link CheckingAccount}: abrir una cuenta necesita dos colaboradores externos, que son puertos de salida.
 * {@link AccountNumberSequence} da el IBAN nuevo e {@link IncomeVerifier} los ingresos anuales verificados del
 * titular. La factoría valida lo que depende del mundo exterior (R20); el resto de invariantes los protege el
 * agregado.
 *
 * <p>R20: el titular se registra como {@link CustomerSegment#HIGH_NET_WORTH} solo si sus ingresos verificados superan
 * {@code 100.000,00 €}; con ingresos menores, iguales al umbral o sin verificar, es {@link CustomerSegment#RETAIL}.
 */
public class CheckingAccountFactory {

    private static final MonetaryAmount HIGH_NET_WORTH_THRESHOLD = MonetaryAmount.euros("100000.00");

    private final AccountNumberSequence numbering;
    private final IncomeVerifier incomeVerifier;

    public CheckingAccountFactory(AccountNumberSequence numbering, IncomeVerifier incomeVerifier) {
        this.numbering = Objects.requireNonNull(numbering, "La numeración de cuentas es obligatoria");
        this.incomeVerifier = Objects.requireNonNull(incomeVerifier, "El verificador de ingresos es obligatorio");
    }

    public CheckingAccount open(String holderName, TaxId taxId, MonetaryAmount initialDeposit) {
        Objects.requireNonNull(taxId, "El NIF del titular es obligatorio");
        AccountHolder holder = AccountHolder.register(holderName, segmentFor(taxId));
        return CheckingAccount.open(numbering.next(), holder, initialDeposit);
    }

    private CustomerSegment segmentFor(TaxId taxId) {
        boolean aboveThreshold = incomeVerifier.verifiedAnnualIncome(taxId)
                .map(income -> HIGH_NET_WORTH_THRESHOLD.isLessThan(income))
                .orElse(false);
        return aboveThreshold ? CustomerSegment.HIGH_NET_WORTH : CustomerSegment.RETAIL;
    }
}
