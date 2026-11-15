package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.Customer;
import com.pronoide.ddd.domain.model.DuplicateCustomerException;
import com.pronoide.ddd.domain.model.EmailAddress;
import com.pronoide.ddd.domain.model.HighNetWorthIncome;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.domain.service.MoneyTransferService;
import com.pronoide.ddd.integration.TaxAgencyClient;
import com.pronoide.ddd.persistence.InMemoryAccountNumberSequence;
import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;
import com.pronoide.ddd.persistence.InMemoryCustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso de las cuentas corrientes: abrir, consultar, listar, ingresar, retirar y transferir. Carga y guarda el
 * agregado; las reglas de negocio siguen en {@code CheckingAccount}. Contrato:
 * {@code docs/contracts/open-query-list-accounts.md}.
 *
 * <p>Cada caso de uso recibe quién lo pide ({@link Caller}, contrato {@code docs/contracts/login-roles.md}): un
 * administrador accede a todas las cuentas y es el único que abre cuentas; un usuario solo a las suyas. Una cuenta
 * ajena, o sin propietario registrado, se trata para un usuario como inexistente ({@link AccountNotFoundException},
 * 404) para no revelar que existe.
 */
@Service
@RequiredArgsConstructor
public class CheckingAccountService {

    private final InMemoryCheckingAccountRepository repository;
    private final InMemoryAccountOwnershipRepository ownership;
    private final InMemoryCustomerRepository customers;
    private final InMemoryAccountNumberSequence accountNumbers;
    private final TaxAgencyClient taxAgency;
    private final Clock clock;
    private final MoneyTransferService transferService = new MoneyTransferService();

    /**
     * Abre una cuenta con un IBAN que asigna el banco (R30). Solo un administrador. Si el documento ya es de un
     * cliente, la cuenta se abre a su nombre y para su usuario; si no, se registra como cliente con los datos del
     * command. Como el alta pública, es todo o nada: la cuenta se construye antes de guardar nada. Contrato:
     * {@code docs/contracts/customer-sign-up.md}.
     *
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws IllegalArgumentException si falta un dato de un cliente nuevo, o si los datos indicados no coinciden con
     *         los del cliente existente
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    public synchronized AccountSummary openAccount(Caller caller, OpenAccountCommand command) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("abrir cuentas");
        }
        TaxId taxId = new TaxId(command.holderTaxId());
        Optional<Customer> existing = customers.findByTaxId(taxId);
        Customer customer = existing.orElseGet(() -> newCustomer(taxId, command));
        String owner = existing.isPresent() ? ownerOfExisting(customer, command) : command.ownerUsername();
        if (existing.isEmpty()) {
            requireFreeUsername(owner);
        }

        CheckingAccount account = openFor(customer, toAmount(command.initialDeposit(), command.currency()));

        if (existing.isEmpty()) {
            customers.save(customer, owner);
        }
        repository.save(account);
        ownership.assign(account.id(), owner);
        return toSummary(account);
    }

    /**
     * Si el usuario ya es de un cliente. Un usuario es de un único cliente: el alta pública lo consulta (junto con los
     * usuarios que ya pueden entrar) para no dar a un visitante el usuario que el administrador asignó a otro cliente.
     */
    public boolean isUsernameTaken(String username) {
        return customers.findByUsername(username).isPresent();
    }

    private void requireFreeUsername(String username) {
        if (isUsernameTaken(username)) {
            throw new IllegalArgumentException("El usuario %s ya es de otro cliente".formatted(username));
        }
    }

    /** Un cliente nuevo desde el alta del administrador: nombre, correo y usuario son obligatorios. */
    private static Customer newCustomer(TaxId taxId, OpenAccountCommand command) {
        if (command.holderName().isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio para un cliente nuevo");
        }
        if (command.holderEmail().isBlank()) {
            throw new IllegalArgumentException("El correo del titular es obligatorio para un cliente nuevo");
        }
        if (command.ownerUsername().isEmpty()) {
            throw new IllegalArgumentException("El usuario del titular es obligatorio para un cliente nuevo");
        }
        return Customer.register(command.holderName(), taxId, new EmailAddress(command.holderEmail()));
    }

    /**
     * El usuario de un cliente existente. Los datos del command van en blanco o coinciden con los del cliente: unos
     * distintos se rechazan en lugar de ignorarlos (decisión del usuario, 2026-11-13).
     */
    private String ownerOfExisting(Customer customer, OpenAccountCommand command) {
        String username = customers.usernameOf(customer.taxId()).orElseThrow();
        boolean nameMatches = command.holderName().isBlank()
                || command.holderName().strip().equals(customer.fullName());
        boolean emailMatches = command.holderEmail().isBlank()
                || new EmailAddress(command.holderEmail()).equals(customer.email());
        boolean usernameMatches = command.ownerUsername().isEmpty() || command.ownerUsername().equals(username);
        if (!nameMatches || !emailMatches || !usernameMatches) {
            throw new IllegalArgumentException(("El documento %s ya es de un cliente: deja en blanco nombre, correo y "
                    + "usuario, o indica los suyos").formatted(customer.taxId()));
        }
        return username;
    }

    /** Todas las cuentas para un administrador; las suyas para un usuario. Ordenadas por número de cuenta. */
    public List<AccountSummary> listAccounts(Caller caller) {
        return repository.findAll().stream()
                .filter(account -> canAccess(caller, account.id()))
                .map(CheckingAccountService::toSummary)
                .toList();
    }

    /**
     * Detalle de una cuenta, con sus movimientos.
     *
     * @throws AccountNotFoundException si no existe o no es de quien lo pide (y no es administrador)
     */
    public AccountDetails findAccount(Caller caller, UUID id) {
        return toDetails(loadFor(caller, id));
    }

    /**
     * Ingresa un importe en la cuenta y la guarda. Contrato: {@code docs/contracts/deposits-withdrawals.md}.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    public TransactionSummary deposit(Caller caller, DepositCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        Transaction transaction = account.deposit(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /**
     * Intenta retirar un importe de la cuenta y la guarda. Una retirada que dejaría la cuenta por debajo de lo
     * permitido no es un error: se devuelve el movimiento con estado {@code REJECTED} y el saldo no cambia. Antes
     * revisa el segmento del titular, para que el descubierto se decida con el segmento que le corresponde hoy.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    public TransactionSummary withdraw(Caller caller, WithdrawCommand command) {
        CheckingAccount account = loadFor(caller, command.accountId());
        reviewSegment(account);
        Transaction transaction = account.tryWithdraw(toAmount(command.amount(), command.currency()));
        repository.save(account);
        return toTransaction(transaction);
    }

    /**
     * Transfiere desde una cuenta de quien lo pide a cualquier cuenta existente, indicada por su número
     * ({@code command.targetAccountNumber()}, que se normaliza como {@code AccountNumber}), y guarda las dos. Una
     * transferencia que la cuenta de origen no permite no es un error: se devuelve con estado {@code REJECTED} y ningún
     * saldo cambia. Contratos: {@code docs/contracts/money-transfers.md} y {@code login-roles.md}.
     *
     * @throws AccountNotFoundException si el origen no existe o no es de quien lo pide (y no es administrador)
     * @throws TargetAccountNotFoundException si no existe ninguna cuenta con el número de destino
     * @throws com.pronoide.ddd.domain.model.SameAccountTransferException si origen y destino son la misma cuenta
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de las cuentas
     */
    public TransferSummary transfer(Caller caller, TransferCommand command) {
        CheckingAccount source = loadFor(caller, command.sourceAccountId());
        AccountNumber targetNumber = new AccountNumber(command.targetAccountNumber());
        CheckingAccount target = repository.findByAccountNumber(targetNumber)
                .orElseThrow(() -> new TargetAccountNotFoundException(targetNumber.value()));
        reviewSegment(source);
        Transaction withdrawal =
                transferService.transfer(source, target, toAmount(command.amount(), command.currency()));
        repository.save(source);
        repository.save(target);
        return TransferSummary.builder()
                .sourceAccountId(source.id())
                .sourceAccountNumber(source.accountNumber().value())
                .targetAccountId(target.id())
                .targetAccountNumber(target.accountNumber().value())
                .amount(withdrawal.amount().amount())
                .currency(withdrawal.amount().currency().getCurrencyCode())
                .occurredOn(withdrawal.occurredOn())
                .status(withdrawal.status().name())
                .build();
    }

    /**
     * Da de alta a un cliente nuevo de Mon-A Bank y le abre su primera cuenta, a nombre de {@code command.username()},
     * con un IBAN que asigna el banco. El segmento lo deciden sus ingresos verificados y el saldo (#99). Lo pide un
     * visitante sin sesión a través de {@code CustomerRegistration}, que crea después su usuario. Contrato:
     * {@code docs/contracts/customer-sign-up.md}.
     *
     * @throws com.pronoide.ddd.domain.model.DuplicateCustomerException si ya hay un cliente con ese documento (R29)
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    public synchronized AccountSummary signUp(SignUpCommand command) {
        TaxId taxId = new TaxId(command.taxId());
        EmailAddress email = new EmailAddress(command.email());
        customers.findByTaxId(taxId).ifPresent(existing -> {
            throw new DuplicateCustomerException(taxId);
        });
        requireFreeUsername(command.username());

        // Todo o nada: la cuenta se construye antes de guardar nada, y el dominio comprueba R1 y la divisa.
        Customer customer = Customer.register(command.fullName(), taxId, email);
        MonetaryAmount initialDeposit = toAmount(command.initialDeposit(), command.currency());
        CheckingAccount account = openFor(customer, initialDeposit);

        customers.save(customer, command.username());
        repository.save(account);
        ownership.assign(account.id(), command.username());
        return toSummary(account);
    }

    /**
     * Abre otra cuenta al cliente que lo pide, indicando solo el depósito: el titular sale de sus datos de cliente.
     *
     * @throws NotACustomerException si quien lo pide no es un cliente registrado (también un administrador)
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     */
    public AccountSummary openAnotherAccount(Caller caller, OpenAnotherAccountCommand command) {
        if (caller.admin()) {
            throw new NotACustomerException(caller.username());
        }
        Customer customer = customers.findByUsername(caller.username())
                .orElseThrow(() -> new NotACustomerException(caller.username()));
        CheckingAccount account = openFor(customer, toAmount(command.initialDeposit(), command.currency()));
        repository.save(account);
        ownership.assign(account.id(), caller.username());
        return toSummary(account);
    }

    /**
     * Sustituye el IBAN de la cuenta por otro que genera el banco (R31). Solo un administrador.
     *
     * @return el resumen de la cuenta con el IBAN nuevo
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws AccountNotFoundException si la cuenta no existe
     */
    public AccountSummary regenerateAccountNumber(Caller caller, UUID id) {
        if (!caller.admin()) {
            throw new ForbiddenOperationException("generar un nuevo número de cuenta");
        }
        CheckingAccount account = load(id);
        account.reassignAccountNumber(accountNumbers.next());
        repository.save(account);
        return toSummary(account);
    }

    /**
     * Revisa el segmento de todas las cuentas a las que les toca: vuelven a minorista los titulares cuyos ingresos
     * verificados ya no son altos; los demás conservan el segmento hasta la siguiente revisión, seis meses después. Es
     * un proceso interno, sin {@link Caller}.
     *
     * @return cuántos titulares han vuelto a minorista
     */
    public int reviewSegments() {
        int demoted = 0;
        for (CheckingAccount account : repository.findAll()) {
            boolean wasHighNetWorth = account.primaryHolder().isHighNetWorth();
            reviewSegment(account);
            if (wasHighNetWorth && !account.primaryHolder().isHighNetWorth()) {
                demoted++;
            }
        }
        return demoted;
    }

    /**
     * Resuelve la revisión del segmento de la cuenta si a esta hora ya le toca: solo entonces verifica de nuevo los
     * ingresos del titular en la agencia tributaria, y el agregado decide (R22, R25). La cuenta se guarda en el acto,
     * para que la revisión no se pierda si el cargo que viene después falla. Es la orquestación que en la fase 2 hace
     * el servicio de dominio {@code SegmentReviewService} con el puerto {@code IncomeVerifier}: aquí el dominio no
     * conoce a la agencia (ADR 0004, ADR 0011).
     */
    private void reviewSegment(CheckingAccount account) {
        Instant now = clock.instant();
        if (account.segmentReviewDue(now)) {
            account.resolveSegmentReview(now,
                    HighNetWorthIncome.isMetBy(taxAgency.verifiedAnnualIncome(account.primaryHolder().taxId())));
            repository.save(account);
        }
    }

    /**
     * Construye (sin guardar) una cuenta con un IBAN nuevo del banco para el cliente: el titular copia su nombre y su
     * documento, y su segmento lo deciden los ingresos verificados en la agencia tributaria y el saldo (#99).
     */
    private CheckingAccount openFor(Customer customer, MonetaryAmount initialDeposit) {
        AccountHolder holder = AccountHolder.register(customer.fullName(), customer.taxId(),
                HighNetWorthIncome.segmentFor(taxAgency.verifiedAnnualIncome(customer.taxId())));
        return CheckingAccount.open(accountNumbers.next(), holder, initialDeposit);
    }

    /** Carga la cuenta; una ajena (o sin propietario, para un usuario) se trata como inexistente. */
    private CheckingAccount loadFor(Caller caller, UUID id) {
        CheckingAccount account = load(id);
        if (!canAccess(caller, id)) {
            throw new AccountNotFoundException(id);
        }
        return account;
    }

    private boolean canAccess(Caller caller, UUID accountId) {
        return caller.admin() || ownership.ownerOf(accountId).filter(caller.username()::equals).isPresent();
    }

    private CheckingAccount load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static MonetaryAmount toAmount(BigDecimal amount, String currency) {
        return new MonetaryAmount(amount, Currency.getInstance(currency));
    }

    private AccountDetails toDetails(CheckingAccount account) {
        TaxId taxId = account.primaryHolder().taxId();
        return AccountDetails.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .holderTaxId(taxId.value())
                .holderEmail(customers.findByTaxId(taxId).map(customer -> customer.email().value()).orElse(""))
                .segment(account.primaryHolder().segment().name())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .transactions(account.transactions().stream().map(CheckingAccountService::toTransaction).toList())
                .build();
    }

    private static TransactionSummary toTransaction(Transaction transaction) {
        return TransactionSummary.builder()
                .id(transaction.id())
                .type(transaction.type().name())
                .amount(transaction.amount().amount())
                .currency(transaction.amount().currency().getCurrencyCode())
                .occurredOn(transaction.occurredOn())
                .status(transaction.status().name())
                .build();
    }

    private static AccountSummary toSummary(CheckingAccount account) {
        return AccountSummary.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .build();
    }
}
