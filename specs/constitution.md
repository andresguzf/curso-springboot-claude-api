# Constitución del backend (`3-spring-api`)

Reglas que no se negocian en ninguna especificación ni tarea del backend. Las specs
y tareas las **citan**; no las repiten. Si una tarea necesita romper una regla, se
cambia primero esta constitución (con su motivo) y se dice explícitamente.

Fuentes: [`CLAUDE.md`](../CLAUDE.md), [`README.md`](../README.md) y la skill
`spring-boot-best-practices` (`.claude/skills/spring-boot-best-practices/`, symlink
al proyecto `2-spring-di`: **no se edita desde aquí**).

## 1. Stack

- Java 25, Spring Boot 4.1.2+, Maven con wrapper (`./mvnw`).
- Spring Web MVC, Validation, Spring Data JPA + Hibernate 7, PostgreSQL.
- Spring Security 7 con JWT (jjwt) en cookies `HttpOnly`, refresh token con rotación y CSRF.
- Jackson 3 (`tools.jackson.*`), springdoc-openapi, OpenPDF 3, Apache POI.
- Package base: `com.andres.course.claude.springboot.springapi.app` (no se cambia).

## 2. Arquitectura

- Capas: `models` → `repositories` → `services` (interfaz `XService` + `XServiceImpl`) → `controllers`, más `dto`, `mappers`, `exceptions`, `converters`, `security`, `config`.
- Las entities **nunca salen del service**: el service recibe y devuelve DTOs y usa un `Mapper` `@Component` escrito a mano (`toDto`, `toEntity`, `updateEntity`).
- Un único `record` DTO por recurso para request y response, con Bean Validation y `message` en español.
  - Excepción (spec 01): las fechas de auditoría `createdAt` / `updatedAt` van en el DTO como **solo lectura** (`@JsonProperty(access = READ_ONLY)`): salen en la respuesta y se ignoran en el request.
  - Excepción (spec 01): los parámetros de búsqueda de un listado se agrupan en un `record XFilter` (no es un DTO de recurso).
- `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras.
- Inyección por constructor con campos `final`.
- Consultas dinámicas con `JpaSpecificationExecutor` + clases `XSpecifications`; nunca `@Query` con `(:param is null or ...)` (falla en PostgreSQL con nulos).
- Errores siempre como `ProblemDetail` desde `GlobalExceptionHandler`, que **extiende `ResponseEntityExceptionHandler`**. Validación: 400 con propiedad `errors` (campo → mensaje). Reglas de negocio: excepciones de dominio (`ResourceNotFoundException` 404, `DuplicateResourceException` 409, `OperationNotAllowedException` 409).
- Content negotiation de exportación (`?format=` o `Accept`) con `HttpMessageConverter` en `converters/`; JSON sigue siendo el formato por defecto.

## 3. Seguridad

- Sesión stateless: `access_token` (15 min) y `refresh_token` (7 días, `Path=/api/auth`) en cookies `HttpOnly`, `SameSite=Strict`. Los tokens nunca van en el cuerpo de una respuesta.
- CSRF activo con cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN` (solo el login está exento).
- Reglas de acceso en `SecurityConfig` (no `@PreAuthorize`). `GET /api/customers/**` → USER/ADMIN; resto de `/api/customers/**` y `/api/users/**` → ADMIN.
- Reglas de administradores en `UserServiceImpl`: nadie se borra, se deshabilita ni se quita ADMIN a sí mismo, y (spec 01) siempre queda al menos un ADMIN habilitado.
- No se agrega CORS: el frontend usa el mismo origen (proxy de Vite).

## 4. Privacidad

El código, los commits y la documentación son públicos (curso y GitHub). Nunca se
incluyen correos reales, contraseñas, tokens, secretos ni datos personales. Se usan
variables de entorno (`.env`, ignorado por git) y placeholders genéricos.

## 5. Calidad (definición de "hecho")

Una tarea solo se marca como hecha cuando:

1. `./mvnw test` pasa completo.
2. Hay tests nuevos para el comportamiento nuevo:
   - controllers con `@WebMvcTest` + `@Import(SecurityTestConfig.class)` + `@ActiveProfiles("test")`, autenticando con `TestRequests.accessCookie` y `TestRequests.withCsrf` en escrituras;
   - services con Mockito puro (sin base de datos).
3. Si cambia el contrato de la API, `README.md` y `CLAUDE.md` quedan actualizados en la última tarea de la fase.
4. Se cumplen los criterios de "Hecho cuando" y "Verificación" de la tarea.

## 6. Git

- Rama de trabajo de la spec 01: `feature/paginacion-filtros` (la crea la primera tarea).
- Un commit por tarea, mensaje en español e imperativo ("Agrega…", "Expone…"), con el trailer `Co-Authored-By` que indique la sesión.
- Sin push ni merge a `main` sin el OK explícito del usuario al cerrar la fase.
