package com.dawnrock.ddd.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pide un token a {@code POST /api/auth/token} (#122) para los tests de extremo a extremo y devuelve la cabecera
 * {@code Authorization} lista para usar ({@code Bearer ...}).
 */
public final class ApiTokens {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    private ApiTokens() {
    }

    public static String bearer(HttpClient http, String baseUrl, String username, String password) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/token"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .build(), HttpResponse.BodyHandlers.ofString());
        Matcher token = ACCESS_TOKEN.matcher(response.body());
        if (response.statusCode() != 200 || !token.find()) {
            throw new IllegalStateException("No se pudo obtener el token de %s: %d %s"
                    .formatted(username, response.statusCode(), response.body()));
        }
        return "Bearer " + token.group(1);
    }
}
