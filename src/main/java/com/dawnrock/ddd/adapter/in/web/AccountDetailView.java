package com.dawnrock.ddd.adapter.in.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.TransactionSummary;

import lombok.RequiredArgsConstructor;

/**
 * El detalle de una cuenta, que comparten su pantalla y las operaciones que se hacen desde ella (#177): pinta la página
 * o, con htmx, solo su panel (#125), y da el aviso tras una operación correcta. También sabe si quien pide es cliente
 * (contrato customer-sign-up), lo que el listado y la otra cuenta del cliente usan igual.
 */
@Component
@RequiredArgsConstructor
class AccountDetailView {

    /** Atributo del aviso de una operación aprobada. */
    static final String MESSAGE = "message";

    /** Atributo del aviso de una operación rechazada por el saldo. */
    static final String REJECTION = "rejection";

    static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");

    private static final String DETAIL_VIEW = "accounts/detail";

    /** Fragmento que se devuelve a htmx: solo el panel de la cuenta, que sustituye al que tiene la página (#125). */
    private static final String DETAIL_PANEL = DETAIL_VIEW + " :: panel";

    private static final List<String> FORMS = List.of("depositForm", "withdrawalForm", "transferForm");

    private final CheckingAccountQueries queries;

    /**
     * Resultado de una operación correcta (#125). A htmx se le responde el panel con el aviso en el modelo y los
     * formularios vacíos; sin htmx, el aviso va como atributo flash y se redirige al detalle (Post/Redirect/Get).
     */
    String notice(UUID id, boolean htmx, String kind, String text, Caller caller, Model model,
                  RedirectAttributes redirect) {
        if (!htmx) {
            redirect.addFlashAttribute(kind, text);
            return redirectToDetail(id);
        }
        // El formulario enviado y su BindingResult repintarían los valores escritos: se quitan para empezar de cero
        for (String form : FORMS) {
            model.asMap().remove(form);
            model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + form);
        }
        model.addAttribute(kind, text);
        return render(id, caller, model, true);
    }

    /**
     * Pinta el detalle; los formularios que no vienen del envío actual se crean vacíos. A htmx solo se le devuelve el
     * panel de la cuenta, que sustituye al que tiene la página (#125).
     */
    String render(UUID id, Caller caller, Model model, boolean htmx) {
        if (!model.containsAttribute("depositForm")) {
            model.addAttribute("depositForm", new MovementForm());
        }
        if (!model.containsAttribute("withdrawalForm")) {
            model.addAttribute("withdrawalForm", new MovementForm());
        }
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", new TransferForm());
        }
        // Primero la cuenta: una ajena es un 404 sin consultar nada más
        AccountDetails account = queries.findAccount(caller, id);
        // Una consulta más al listado para saber si es cliente (contrato customer-sign-up): el administrador no la hace
        List<AccountSummary> own = caller.admin() ? List.of() : queries.listAccounts(caller);
        model.addAttribute("account", account);
        // application devuelve los movimientos del más antiguo al más reciente; la web los pinta al revés. Se invierte
        // una copia, sin ordenar por fecha: dos movimientos pueden compartir instante.
        List<TransactionSummary> newestFirst = new ArrayList<>(List.copyOf(account.transactions()));
        Collections.reverse(newestFirst);
        model.addAttribute("transactions", newestFirst);
        model.addAttribute("admin", caller.admin());
        model.addAttribute("backLabel", backLabel(caller, own));
        model.addAttribute("customer", isCustomer(caller, own));
        model.addAttribute("currencies", CURRENCIES);
        return htmx ? DETAIL_PANEL : DETAIL_VIEW;
    }

    /** Si quien pide es cliente; al administrador no se le consulta el listado. */
    boolean isCustomer(Caller caller) {
        return isCustomer(caller, caller.admin() ? List.of() : queries.listAccounts(caller));
    }

    /** Un cliente es quien no es administrador y tiene alguna cuenta (contrato customer-sign-up). */
    static boolean isCustomer(Caller caller, List<AccountSummary> accounts) {
        return !caller.admin() && !accounts.isEmpty();
    }

    static String redirectToDetail(UUID id) {
        return "redirect:/accounts/" + id;
    }

    /**
     * Texto del enlace de vuelta al inicio: «Cuentas» para el administrador, «Mis cuentas» para un usuario con varias
     * cuentas y ninguno con una sola (su inicio es este mismo detalle).
     */
    private static String backLabel(Caller caller, List<AccountSummary> own) {
        if (caller.admin()) {
            return "Cuentas";
        }
        return own.size() > 1 ? "Mis cuentas" : null;
    }
}
