package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface CustomerService {

    /**
     * Página de clientes que cumplen el filtro.
     *
     * @throws IllegalArgumentException si el orden no está permitido o un rango de fechas está invertido (400)
     */
    Page<CustomerDto> findPage(CustomerFilter filter, Pageable pageable);

    /**
     * Todos los clientes que cumplen el filtro, sin paginar (para la exportación).
     *
     * @throws IllegalArgumentException si el orden no está permitido o un rango de fechas está invertido (400)
     */
    List<CustomerDto> findAll(CustomerFilter filter, Sort sort);

    CustomerDto findById(Long id);

    CustomerDto create(CustomerDto dto);

    CustomerDto update(Long id, CustomerDto dto);

    /** Borrado lógico del cliente y de sus facturas (deleted_at). @throws com.andres.course.claude.springboot.springapi.app.exceptions.ResourceNotFoundException (404) */
    void delete(Long id);
}
