package com.andres.course.claude.springboot.springapi.app.controllers;

import com.andres.course.claude.springboot.springapi.app.dto.UserDto;
import com.andres.course.claude.springboot.springapi.app.dto.UserFilter;
import com.andres.course.claude.springboot.springapi.app.services.UserService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

/** Administración de usuarios: solo rol ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/users -> 200 con una página de usuarios (sin password):
    // {content, page:{size, number, totalElements, totalPages}}.
    // Filtros opcionales (UserFilter): ?search= (email), ?role=USER|ADMIN, ?enabled=true|false.
    // ?page= (desde 0), ?size= (10 por defecto, máximo 100) y ?sort=campo,asc|desc (por defecto email).
    @GetMapping
    public PagedModel<UserDto> findPage(@ParameterObject UserFilter filter,
                                        @ParameterObject @PageableDefault(sort = "email") Pageable pageable) {
        return new PagedModel<>(userService.findPage(filter, pageable));
    }

    // GET /api/users/{id} -> 200, o 404 si no existe.
    @GetMapping("/{id}")
    public UserDto findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    // POST /api/users -> 201 + Location; 400 validación o sin password, 409 email en uso.
    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto dto) {
        UserDto created = userService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/users/{id} -> 200; 404, 400, 409 (email en uso o quitarse el rol ADMIN a sí mismo).
    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserDto dto, Authentication authentication) {
        return userService.update(id, dto, authentication.getName());
    }

    // DELETE /api/users/{id} -> 204; 404 si no existe, 409 si es el propio usuario.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
