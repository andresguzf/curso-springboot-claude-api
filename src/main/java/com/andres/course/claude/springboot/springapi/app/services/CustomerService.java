package com.andres.course.claude.springboot.springapi.app.services;

import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;

import java.util.List;

public interface CustomerService {

    List<CustomerDto> findAll();

    CustomerDto findById(Long id);

    CustomerDto create(CustomerDto dto);

    CustomerDto update(Long id, CustomerDto dto);

    void delete(Long id);
}
