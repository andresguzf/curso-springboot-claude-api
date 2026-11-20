package com.andres.course.claude.springboot.springapi.app.repositories;

/**
 * Patrones LIKE "contiene" compartidos por las Specifications de clientes y usuarios.
 * Escapa los comodines para que "%" o "_" escritos por el usuario se busquen literalmente.
 */
final class LikePatterns {

    /** Carácter de escape que se pasa a {@code cb.like(..., ESCAPE)}. */
    static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    /** {@code "%texto%"} en minúsculas, sin espacios a los lados y con los comodines escapados. */
    static String contains(String text) {
        String escaped = text.trim().toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
