package com.andres.course.claude.springboot.springapi.app.services;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageablesTest {

    private static final Set<String> ALLOWED = Set.of("id", "name", "lastName", "createdAt");

    @Test
    void sinOrdenUsaElIdComoOrdenPorDefecto() {
        Sort sort = Pageables.checked(Sort.unsorted(), ALLOWED);

        assertThat(sort).isEqualTo(Sort.by("id"));
    }

    @Test
    void agregaElIdComoDesempateAlFinal() {
        Sort sort = Pageables.checked(Sort.by(Sort.Direction.DESC, "lastName"), ALLOWED);

        assertThat(sort).containsExactly(Sort.Order.desc("lastName"), Sort.Order.asc("id"));
    }

    @Test
    void noDuplicaElIdSiYaVieneEnElOrden() {
        Sort sort = Pageables.checked(Sort.by(Sort.Order.desc("id")), ALLOWED);

        assertThat(sort).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void rechazaCamposFueraDeLaListaBlanca() {
        assertThatThrownBy(() -> Pageables.checked(Sort.by("password"), ALLOWED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se puede ordenar por 'password'. Campos permitidos: createdAt, id, lastName, name");
    }

    @Test
    void conservaPaginaYTamanoDelPageable() {
        Pageable pageable = Pageables.checked(PageRequest.of(2, 25, Sort.by("name")), ALLOWED);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void validaTambienElOrdenDeUnPageable() {
        assertThatThrownBy(() -> Pageables.checked(PageRequest.of(0, 10, Sort.by("password")), ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unPageableSinPaginarConservaElOrdenValidado() {
        Pageable pageable = Pageables.checked(Pageable.unpaged(Sort.by("name")), ALLOWED);

        assertThat(pageable.isUnpaged()).isTrue();
        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }
}
