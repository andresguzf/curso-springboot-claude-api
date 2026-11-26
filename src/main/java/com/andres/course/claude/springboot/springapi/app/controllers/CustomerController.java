package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.converters.ExportMediaTypes;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerDto;
import com.andres.course.claude.springboot.springapi.app.dto.CustomerFilter;
import com.andres.course.claude.springboot.springapi.app.services.CustomerService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.SortDefault;
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

    // GET /api/customers (roles USER y ADMIN). Dos handlers en la misma ruta, separados por produces:
    // la content negotiation (Accept o ?format=) elige uno; sin preferencia responde JSON.
    // Swagger UI los muestra fusionados en una sola operación (misma ruta y método) con todos los formatos.
    // @ParameterObject hace que cada filtro y page/size/sort aparezcan en Swagger como query params sueltos.
    // Filtros opcionales (CustomerFilter): search, active, city, birthFrom, birthTo, createdFrom, createdTo.

    // JSON -> 200 con una página: {content, page:{size, number, totalElements, totalPages}}.
    // ?page= (desde 0), ?size= (10 por defecto, máximo 100) y ?sort=campo,asc|desc (repetible).
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedModel<CustomerDto> findPage(@ParameterObject CustomerFilter filter,
                                            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return new PagedModel<>(customerService.findPage(filter, pageable));
    }

    // XML, CSV, Excel o PDF -> exporta TODO el resultado filtrado y ordenado, sin paginar.
    // Los conversores de converters/ escriben la List<CustomerDto>.
    @GetMapping(produces = {
            MediaType.APPLICATION_XML_VALUE,
            ExportMediaTypes.TEXT_CSV_VALUE,
            ExportMediaTypes.XLSX_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    })
    public List<CustomerDto> export(@ParameterObject CustomerFilter filter, @SortDefault(sort = "id") Sort sort) {
        return customerService.findAll(filter, sort);
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

    // Escritura: solo rol ADMIN (ver SecurityConfig).
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

    // DELETE /api/customers/{id} -> 204 sin cuerpo, o 404 si no existe. Borrado lógico: el cliente y sus
    // facturas quedan con deleted_at y desaparecen de la API (spec 11).
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
