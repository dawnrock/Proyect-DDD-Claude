package com.pronoide.ddd.adapter.in.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.pronoide.ddd.adapter.in.UsernameTakenException;
import com.pronoide.ddd.application.NotFoundException;
import com.pronoide.ddd.application.OperationNotAllowedException;
import com.pronoide.ddd.domain.model.DomainException;

import lombok.extern.slf4j.Slf4j;

/**
 * Único punto de traducción de errores a HTTP para la API REST, con el formato {@link ProblemDetail}
 * (RFC 9457). Ver "Traducción de errores" en {@code docs/arquitectura/fase-1-capas.md}:
 * reglas de negocio → 422, recurso inexistente → 404, argumentos inválidos y validación → 400.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    static final String BUSINESS_RULE_TITLE = "Regla de negocio incumplida";
    static final String NOT_FOUND_TITLE = "Recurso inexistente";
    static final String BAD_REQUEST_TITLE = "Petición no válida";
    static final String UNSUPPORTED_MEDIA_TYPE_TITLE = "Tipo de contenido no admitido";
    static final String BAD_CREDENTIALS_DETAIL = "Usuario o contraseña incorrectos";

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleBusinessRule(DomainException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, BUSINESS_RULE_TITLE, exception.getMessage());
    }

    /**
     * El usuario elegido en el alta ya existe. No es un {@code DomainException} (la identidad no llega al dominio),
     * pero para quien llama es una regla de negocio incumplida: 422.
     */
    @ExceptionHandler(UsernameTakenException.class)
    ProblemDetail handleUsernameTaken(UsernameTakenException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, BUSINESS_RULE_TITLE, exception.getMessage());
    }

    /** Operación reservada a administradores (red de seguridad tras la regla de URL de {@link ApiSecurityConfig}). */
    @ExceptionHandler(OperationNotAllowedException.class)
    ProblemDetail handleNotAllowed(OperationNotAllowedException exception) {
        return problem(HttpStatus.FORBIDDEN, ApiSecurityConfig.FORBIDDEN_TITLE, exception.getMessage());
    }

    /** Credenciales erróneas al pedir un token ({@link TokenController}): 401 sin decir si falla el usuario o la contraseña. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleBadCredentials(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, ApiSecurityConfig.UNAUTHENTICATED_TITLE, BAD_CREDENTIALS_DETAIL);
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TITLE, exception.getMessage());
    }

    /**
     * Argumentos que el dominio rechaza (importe no positivo, IBAN mal formado): no son reglas de negocio.
     * Es una red de seguridad: la presentación debería validarlos antes. Se registra en el log porque una
     * {@code IllegalArgumentException} también puede venir de un error de programación.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Argumento no válido rechazado con 400", exception);
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE, exception.getMessage());
    }

    /** Los 400 que resuelve Spring (JSON mal formado, parámetros ausentes o de tipo incorrecto) usan el título del contrato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && status.value() == HttpStatus.BAD_REQUEST.value()) {
            problem.setTitle(BAD_REQUEST_TITLE);
        }
        return response;
    }

    /**
     * Un cuerpo que no es JSON (#110): 415 con el título y el detalle en español, como el resto de errores. Spring
     * añade la cabecera {@code Accept} con los tipos admitidos; se conserva.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        MediaType received = exception.getContentType();
        String detail = received == null
                ? "Falta el tipo de contenido: envía application/json"
                : "El tipo de contenido %s/%s no está admitido: envía application/json"
                        .formatted(received.getType(), received.getSubtype());
        ProblemDetail problem = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_TITLE, detail);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "La petición tiene %d campo(s) no válido(s)".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
