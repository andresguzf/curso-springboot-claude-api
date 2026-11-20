package com.andres.course.claude.springboot.springapi.app.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Validación del orden pedido en los listados ({@code ?sort=campo,asc|desc}).
 *
 * Solo se permite ordenar por una lista blanca de campos por recurso: sin ella, un campo
 * inexistente termina en un 500 (PropertyReferenceException) y se podría ordenar por campos
 * sensibles como el password. Además agrega {@code id} como desempate para que la paginación
 * sea estable: si dos filas empatan en el campo pedido, el orden entre páginas no cambia.
 */
public final class Pageables {

    private static final String TIEBREAKER = "id";

    private Pageables() {
    }

    /**
     * Devuelve el mismo pageable (página y tamaño) con el orden validado y el desempate por id.
     *
     * @throws IllegalArgumentException si se pide ordenar por un campo que no está en {@code allowed} (400)
     */
    public static Pageable checked(Pageable pageable, Set<String> allowed) {
        Sort sort = checked(pageable.getSort(), allowed);
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * Valida el orden contra la lista blanca y agrega {@code id} ascendente como desempate si falta.
     *
     * @throws IllegalArgumentException si se pide ordenar por un campo que no está en {@code allowed} (400)
     */
    public static Sort checked(Sort sort, Set<String> allowed) {
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw new IllegalArgumentException("No se puede ordenar por '" + order.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", new TreeSet<>(allowed)));
            }
        }
        return sort.getOrderFor(TIEBREAKER) == null ? sort.and(Sort.by(TIEBREAKER)) : sort;
    }
}
