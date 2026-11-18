package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;

import java.util.List;

public interface CustomerService {

    /**
     * Lista los clientes ordenados por id, con filtros opcionales.
     *
     * @param search texto a buscar en nombre, apellido, email o ciudad (null o vacío: sin filtro)
     * @param active estado del cliente (null: todos)
     */
    List<CustomerDto> findAll(String search, Boolean active);

    CustomerDto findById(Long id);

    CustomerDto create(CustomerDto dto);

    CustomerDto update(Long id, CustomerDto dto);

    void delete(Long id);
}
