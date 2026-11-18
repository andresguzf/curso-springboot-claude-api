package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.converters.ExportMediaTypes;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.services.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    // GET /api/customers -> 200 con la lista de clientes.
    // Content negotiation (Accept o ?format=): JSON por defecto, XML, CSV, Excel o PDF.
    @GetMapping(produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            ExportMediaTypes.TEXT_CSV_VALUE,
            ExportMediaTypes.XLSX_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    })
    public List<CustomerDto> findAll() {
        return customerService.findAll();
    }

    // GET /api/customers/{id} -> 200, o 404 si no existe.
    // Content negotiation: JSON por defecto, XML o la ficha del cliente en PDF.
    @GetMapping(value = "/{id}", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    })
    public CustomerDto findById(@PathVariable Long id) {
        return customerService.findById(id);
    }

    // POST /api/customers -> 201 + cabecera Location; 400 si falla la validación, 409 si el email ya existe.
    @PostMapping
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CustomerDto dto) {
        CustomerDto created = customerService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/customers/{id} -> 200; 404 si no existe, 400 validación, 409 email en uso.
    @PutMapping("/{id}")
    public CustomerDto update(@PathVariable Long id, @Valid @RequestBody CustomerDto dto) {
        return customerService.update(id, dto);
    }

    // DELETE /api/customers/{id} -> 204 sin cuerpo, o 404 si no existe.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
