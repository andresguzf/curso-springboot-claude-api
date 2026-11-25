package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.InvoiceDto;
import com.andres.course.claude.springboot.springapi.app.dto.InvoiceFilter;
import com.andres.course.claude.springboot.springapi.app.services.InvoiceService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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

/** Facturas: USER y ADMIN leen; solo ADMIN crea, edita y elimina (ver SecurityConfig). */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // GET /api/invoices -> 200 con una página: {content, page:{size, number, totalElements, totalPages}}.
    // Cada factura trae el nombre del cliente y sus líneas.
    // Filtros opcionales (InvoiceFilter): search, customerId, status, issuedFrom, issuedTo.
    // ?page= (desde 0), ?size= (10 por defecto, máximo 100) y ?sort=campo,asc|desc (por defecto issueDate,desc).
    @GetMapping
    public PagedModel<InvoiceDto> findPage(@ParameterObject InvoiceFilter filter,
                                           @ParameterObject @PageableDefault(sort = "issueDate", direction = Sort.Direction.DESC)
                                           Pageable pageable) {
        return new PagedModel<>(invoiceService.findPage(filter, pageable));
    }

    // GET /api/invoices/{id} -> 200, o 404 si no existe.
    @GetMapping("/{id}")
    public InvoiceDto findById(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    // POST /api/invoices -> 201 + Location; 400 validación, 404 cliente inexistente, 409 número duplicado.
    @PostMapping
    public ResponseEntity<InvoiceDto> create(@Valid @RequestBody InvoiceDto dto) {
        InvoiceDto created = invoiceService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/invoices/{id} -> 200; 404 (factura o cliente), 400 validación.
    @PutMapping("/{id}")
    public InvoiceDto update(@PathVariable Long id, @Valid @RequestBody InvoiceDto dto) {
        return invoiceService.update(id, dto);
    }

    // DELETE /api/invoices/{id} -> 204 sin cuerpo (también borra sus líneas), o 404 si no existe.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
