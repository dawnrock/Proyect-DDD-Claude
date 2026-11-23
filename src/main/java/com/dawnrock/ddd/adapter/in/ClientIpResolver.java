package com.dawnrock.ddd.adapter.in;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * IP del cliente para el límite de intentos de inicio de sesión (#133, ADR 0017) y el de altas de clientes (#131, ADR
 * 0018). En Render la petición llega a través
 * de Cloudflare y del proxy interno de Render, que añaden cada uno la dirección de quien les conecta a
 * {@code X-Forwarded-For}.
 *
 * <p>Si la conexión no viene de un proxy de confianza, su dirección es la del cliente y la cabecera se ignora: un
 * cliente directo no puede elegir su IP. Si viene de uno, la cabecera se recorre de derecha a izquierda saltando los
 * proxies de confianza, y la primera dirección que no lo es, la que añadió el último proxy de confianza, es el cliente.
 * Lo que el cliente añada por su cuenta queda a su izquierda y no cuenta. Una entrada que no es una IP literal, o una
 * cabecera solo de proxies, deja la dirección de la conexión.
 *
 * <p>Las entradas se validan como IP literal antes de compararlas: {@link IpAddressMatcher} resolvería un nombre de
 * host consultando el DNS, y la cabecera la escribe quien hace la petición.
 */
public class ClientIpResolver {

    private static final Pattern IPV4 =
            Pattern.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

    /** Una IPv6 literal: dígitos hexadecimales y dos puntos (con una IPv4 al final, si acaso). Nunca un nombre. */
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies.stream().map(IpAddressMatcher::new).toList();
    }

    /** IP del cliente de una petición: su dirección de conexión y sus cabeceras {@code X-Forwarded-For}. */
    public String resolve(HttpServletRequest request) {
        // Varias cabeceras X-Forwarded-For se unen en orden: la primera puede escribirla el cliente, y la que añade un
        // proxy de confianza va después
        String forwardedFor = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
        return resolve(request.getRemoteAddr(), forwardedFor.isEmpty() ? null : forwardedFor);
    }

    /**
     * @param connection   dirección de quien abre la conexión con la aplicación ({@code getRemoteAddr()})
     * @param forwardedFor cabecera {@code X-Forwarded-For}, o {@code null}
     */
    public String resolve(String connection, String forwardedFor) {
        if (!isTrusted(connection) || forwardedFor == null || forwardedFor.isBlank()) {
            return connection;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!isIpLiteral(hop)) {
                return connection;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return connection;
    }

    private boolean isTrusted(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher proxy : trustedProxies) {
            try {
                if (proxy.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // No debería ocurrir con una IP literal ya validada; por si acaso, no es un proxy de confianza
                return false;
            }
        }
        return false;
    }

    static boolean isIpLiteral(String address) {
        if (address == null) {
            return false;
        }
        if (IPV4.matcher(address).matches()) {
            return true;
        }
        if (!IPV6.matcher(address).matches()) {
            return false;
        }
        // El patrón deja pasar cadenas como ":::" o "1:2". Con dos puntos, Java la trata como IPv6 literal: la
        // valida sin consultar el DNS y lanza la excepción si no lo es
        try {
            InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
