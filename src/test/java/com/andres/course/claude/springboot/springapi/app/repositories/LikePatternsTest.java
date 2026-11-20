package com.andres.course.claude.springboot.springapi.app.repositories;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void envuelveEnComodinesEnMinusculasYSinEspacios() {
        assertThat(LikePatterns.contains("  San Pedro ")).isEqualTo("%san pedro%");
    }

    @Test
    void escapaLosComodinesDelTextoParaBuscarlosLiteralmente() {
        assertThat(LikePatterns.contains("50%_off")).isEqualTo("%50\\%\\_off%");
    }

    @Test
    void escapaLaBarraInvertida() {
        assertThat(LikePatterns.contains("a\\b")).isEqualTo("%a\\\\b%");
    }
}
