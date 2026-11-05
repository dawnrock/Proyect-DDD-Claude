package com.pronoide.ddd.adapter.in;

/**
 * Nombre en español de un segmento de cliente para los adaptadores de entrada (web y línea de comandos). Las vistas de
 * {@code application} traen el código ({@code RETAIL}, {@code HIGH_NET_WORTH}); un código desconocido se muestra tal
 * cual. Vive en la raíz de {@code adapter.in} para que los adaptadores lo compartan sin conocerse (#77).
 */
public final class SegmentLabels {

    private SegmentLabels() {
    }

    public static String of(String segment) {
        return switch (segment) {
            case "RETAIL" -> "Minorista";
            case "HIGH_NET_WORTH" -> "Alto patrimonio";
            default -> segment;
        };
    }
}
