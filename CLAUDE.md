# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

API REST de clientes (`Customer`), sus facturas (`Invoice` + `InvoiceItem`) y usuarios (`User`) con Spring Boot 4.1.2, Java 25, Maven, Spring Data JPA + Hibernate 7, PostgreSQL y Spring Security 7 con JWT (jjwt 0.13) en cookies HttpOnly. Es parte de un curso: el código se muestra en pantalla y se publica en GitHub.

Package base: `com.andres.course.claude.springboot.springapi.app` (no lo cambies).

## Comandos

Usa siempre el wrapper `./mvnw`.

```bash
./mvnw -q -DskipTests compile                  # compilar
./mvnw -DskipTests spring-boot:run             # levantar (requiere PostgreSQL)
./mvnw test                                    # todos los tests
./mvnw test -Dtest=NombreDelTest               # un test
./mvnw test -Dtest=NombreDelTest#metodo        # un método
```

- `ApplicationTests` (`@SpringBootTest`) levanta el contexto completo y **falla sin PostgreSQL**. Para probar la capa web sin base de datos usa `@WebMvcTest` (en Boot 4 está en `org.springframework.boot.webmvc.test.autoconfigure`) con `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- Los `@WebMvcTest` llevan `@Import(SecurityTestConfig.class)` (carga `SecurityConfig` y las clases de `security/`, que el slice no escanea) y `@ActiveProfiles("test")` (`application-test.properties` fija `app.jwt.secret` y `app.jwt.cookie-secure` para no depender del `.env` local). Autentica con una cookie `access_token` real (`TestRequests.accessCookie`) y en escrituras agrega CSRF con `TestRequests.withCsrf` (cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN`).
- PostgreSQL local con Docker (contenedor `3-spring-api-db`, se reinicia solo salvo `docker stop`): `docker run -d --name 3-spring-api-db --restart unless-stopped -e POSTGRES_DB=spring_api -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18`
- Consultar la base: `docker exec -it 3-spring-api-db psql -U postgres -d spring_api`
- pgAdmin: contenedor `3-spring-api-pgadmin` en http://localhost:5050 (comando completo en el README). Comparte la red `spring-api-net` con PostgreSQL, por eso el host del servidor es `3-spring-api-db` y no `localhost`. El servidor se registra desde `docker/pgadmin/servers.json` **solo la primera vez** que se crea el volumen `3-spring-api-pgadmin-data`; para recargarlo, borra el contenedor y el volumen.
- Con la app arriba: Swagger UI en `/swagger-ui.html`, health en `/actuator/health`.
- Docker (spec 09): `docker build -t cartera-api .` (compila con `-DskipTests`: corre `./mvnw test` antes); `docker run --rm --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 cartera-api` contra Supabase; `docker compose up --build` levanta backend + PostgreSQL propio (perfil de desarrollo, con seed; sin publicar el 5432; `API_PORT` cambia el puerto) y `docker compose down -v` lo borra todo.

## Configuración

- `application.properties` importa `.env` de la raíz con `spring.config.import=optional:file:.env[.properties]`; la ruta es relativa al directorio de trabajo, así que la app debe ejecutarse desde la raíz del proyecto.
- Base de datos: el `.env` puede apuntar al PostgreSQL local o a **Supabase** por el **Session pooler** (`jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres`, usuario `postgres.<ref>`); no uses el Transaction pooler (6543, falla con sentencias preparadas). El seed (`data.sql` + `AdminUserInitializer`) se carga solo en la base vacía. `ApplicationTests` usa la base del `.env`.
- Variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (con valores por defecto genéricos de desarrollo); `JWT_SECRET` (Base64, ≥ 256 bits, **sin valor por defecto**: la app no arranca sin ella; `openssl rand -base64 32`), `JWT_COOKIE_SECURE` (por defecto `true`; `false` en local para Postman/curl sobre http), `ADMIN_EMAIL` y `ADMIN_PASSWORD` (admin inicial). `.env` está en `.gitignore`; la plantilla versionada es `.env.example`.
- El admin inicial lo crea `config/AdminUserInitializer` al arrancar si no existe un usuario con `ADMIN_EMAIL` (sin `ADMIN_PASSWORD` solo avisa). No pongas usuarios en `data.sql`: serían contraseñas/hashes versionados.
- `spring.jpa.hibernate.ddl-auto=update`: el esquema lo crea Hibernate a partir de las entities (no hay Flyway).
- Puerto: `server.port=${PORT:8080}` (Render asigna `PORT`).
- **Perfil `prod`** (`application-prod.properties`, `SPRING_PROFILES_ACTIVE=prod`): `ddl-auto=validate`, sin seed (`spring.sql.init.mode=never`), sin SQL en el log, Swagger desactivado, datasource sin valores por defecto (sin `DB_URL` falla con `'url' must start with "jdbc"`), Hikari con `DB_POOL_SIZE` (5) y `server.forward-headers-strategy=framework`. `ProdProfileTest` comprueba esos valores: si cambias el perfil, actualízalo. Con `validate`, un cambio en una entity no arranca en `prod` hasta que exista la migración.
- Docker y nube: `Dockerfile` multi-stage (JRE 25, usuario `spring`, `TZ=America/Santiago`, `-XX:MaxRAMPercentage=75`), `.dockerignore` (sin `.env`), `compose.yaml` y `render.yaml` (Render, plan gratis, región `oregon` como el pooler de Supabase; secretos con `sync: false` y `JWT_SECRET` con `generateValue`). La base de producción es Supabase. El frontend va en Vercel con un rewrite de `/api/*` a Render: mismo origen, por eso **no hay CORS** ni cookies `SameSite=None`.
- Seed: `src/main/resources/data.sql` corre en cada arranque (`spring.sql.init.mode=always` + `spring.jpa.defer-datasource-initialization=true`, para que corra después de que Hibernate cree las tablas), pero solo inserta los 20 clientes si la tabla está vacía (`INSERT ... SELECT ... FROM (VALUES ...) WHERE NOT EXISTS`); igual las 6 facturas y sus líneas (buscan el cliente por email y la factura por número; nunca insertes `line_total`, es generada). No uses `ON CONFLICT DO NOTHING`: cada fila descartada igual avanza la secuencia del `id`, y con DevTools reiniciando la app los IDs saltan de a 20. Usa nombres de **columna** (`last_name`, `created_at`, `update_at`) y asigna `created_at` a mano, porque `@PrePersist` no corre en SQL directo: si cambias columnas de la entity, actualiza el seed o la app no arranca.

## Arquitectura

Capas en packages bajo el package base: `models` → `repositories` → `services` → `controllers`, más `dto`, `mappers`, `exceptions`, `converters`, `security` y `config`.

- Las entities nunca salen del service: el service recibe y devuelve solo DTOs y usa un `Mapper` (`@Component`, mapeo a mano con `toDto`, `toEntity`, `updateEntity`).
- Un único `record` DTO por recurso, para request y response, con Bean Validation y `message` en español. Las fechas de auditoría `createdAt` / `updatedAt` van como **solo lectura** (`@JsonProperty(access = READ_ONLY)`): salen en la respuesta y se ignoran en el request; el mapper solo las copia en `toDto`. Es una excepción a la skill compartida `spring-boot-best-practices` (que dice que el DTO no las lleva): no edites la skill, es un symlink a otro repo; la excepción está en `specs/constitution.md`.
- Los query params de búsqueda de un listado se agrupan en un `record XFilter` (`dto/CustomerFilter`, `dto/UserFilter`): no es un DTO de recurso, Spring MVC lo enlaza por constructor y cada campo `null` significa "sin filtro". Las fechas llevan `@DateTimeFormat(iso = DATE)`.
- Services: interfaz `XService` + `XServiceImpl`; `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras. Los controllers inyectan la interfaz.
- Auditoría en entities con callbacks JPA `@PrePersist` / `@PreUpdate` (no `@CreationTimestamp` / `@UpdateTimestamp`).
- Unicidad (p. ej. email): el service la comprueba y lanza `DuplicateResourceException`; la restricción `UNIQUE` de la columna cubre las carreras y llega como `DataIntegrityViolationException`. Ambas se traducen a 409.
- Content negotiation del listado y el detalle (`Accept` o `?format=`, configurado en `application.properties`): cada formato de exportación es un `HttpMessageConverter` en `converters/` que extiende `AbstractCustomerHttpMessageConverter` (solo escribe `List<CustomerDto>` y, si el formato lo permite, `CustomerDto`; cualquier otro cuerpo, como un `ProblemDetail`, cae a los conversores por defecto). Se registran en `config/WebConfig` con `configureMessageConvertersList`: CSV, Excel y PDF **al final** para que JSON siga siendo el formato por defecto, y el XML del listado **al inicio** para ganarle al XML genérico de Jackson. Los formatos de cada endpoint se declaran en `@GetMapping(produces = ...)`; un formato fuera de esa lista responde 406. Librerías: OpenPDF 3 (package `org.openpdf.text`, no `com.lowagie`), Apache POI y Jackson XML 3 (`tools.jackson.dataformat.xml`). En PDF horizontal usa un `Rectangle` con ancho y alto intercambiados, no `PageSize.A4.rotate()` (agrega `/Rotate 90` y los visores muestran la página de lado).
- `GlobalExceptionHandler` **extiende `ResponseEntityExceptionHandler`** y sobrescribe `handleMethodArgumentNotValid`. No lo cambies a `@ExceptionHandler(MethodArgumentNotValidException.class)`: con `spring.mvc.problemdetails.enabled=true` el advice por defecto de Boot atendería la validación primero y se perderían los errores por campo. Todas las respuestas de error son `ProblemDetail`; la validación agrega la propiedad `errors` (campo → mensaje). Los errores de binding de un record de filtros (`?birthFrom=ayer`, `?role=FOO`) llegan por el mismo método con `isBindingFailure()`: se reemplaza el mensaje técnico en inglés por "El valor 'x' no tiene un formato válido".

## Available skills

| Skill | Ubicación | Propósito |
|---|---|---|
| `spring-boot-best-practices` | `.claude/skills/spring-boot-best-practices/` | Crea proyectos Spring Boot con Spring Initializr (Java 25, Spring Boot 4.1.2+, Maven, jar, `application.properties`) y mantiene la arquitectura controllers / services (interface + impl) / repositories / models, con DTOs `record`, Mapper explícito, excepciones con `ProblemDetail`, JPA + PostgreSQL bajo demanda y vistas Thymeleaf + Tailwind. |

La skill es un **symlink** al original en `../2-spring-di/.claude/skills/spring-boot-best-practices/`: editar sus archivos desde aquí modifica el original del proyecto `2-spring-di` (y queda sin commit en ese repositorio).

## Skill trigger rules

Usa `spring-boot-best-practices` cuando el usuario:

- Pida crear, agregar o modificar un entity, model, DTO, mapper, repository, service o controller de Spring (p. ej. un recurso nuevo junto a `Customer`).
- Pida agregar un CRUD o endpoints REST a la API.
- Pida cambiar el manejo de errores, las excepciones de dominio o la validación de campos.
- Pida agregar o cambiar persistencia: Spring Data JPA, Hibernate, PostgreSQL, un repositorio con `CrudRepository` / `JpaRepository` o una entity `@Entity`.
- Pida crear un proyecto Spring Boot nuevo, una API REST básica o un monolito con Spring Web / Spring MVC (con vistas Thymeleaf).

Reglas clave de la skill en este proyecto:

- Este proyecto **ya tiene** JPA y PostgreSQL: los repositorios extienden `JpaRepository` (no se usan repositorios en memoria) y los services llevan `@Transactional`.
- Al agregar un recurso, crea el conjunto completo: entity, record DTO, mapper, repository, service + impl y controller REST.
- Al agregar un campo a una entity, actualiza en el mismo cambio el DTO (si no es sensible) y el mapper (`toDto`, `toEntity`, `updateEntity`).
- Para ejecutar la aplicación localmente usa siempre: `./mvnw -DskipTests spring-boot:run`.

## Seguridad

- Stateless, sin sesión HTTP. `security/JwtAuthenticationFilter` lee la cookie `access_token` (JWT HS256 con `sub` = email y claim `role`, 15 min) y autentica sin ir a la BD. Si el token es inválido o expiró **no corta la petición**: sigue anónima y el `ProblemDetailAuthenticationEntryPoint` responde 401 solo en rutas protegidas (así login/refresh funcionan con una cookie vencida).
- El filtro **no es `@Component`**: se crea con `new` en `config/SecurityConfig`; como bean, Boot lo registraría también como filtro de servlet y correría dos veces.
- Los 401/403 de los filtros no pasan por `GlobalExceptionHandler`: los escriben `ProblemDetailAuthenticationEntryPoint` y `ProblemDetailAccessDeniedHandler` vía `ProblemDetailResponseWriter` (Jackson 3 + `ProblemDetailJacksonMixin`). Los errores de login (`AuthenticationException`) sí llegan al advice: 401 con mensaje genérico para no revelar si el email existe.
- Reglas de acceso en `SecurityConfig` (no `@PreAuthorize`): `GET /api/customers/**` y `GET /api/invoices/**` → USER/ADMIN; resto de `/api/customers/**`, `/api/invoices/**` y `/api/users/**` → ADMIN; públicos login, refresh, logout, `GET /api/auth/csrf`, Swagger, `/actuator/health`, `/error`.
- CSRF activo con `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler` con `setCsrfRequestAttributeName(null)` (token sin XOR y cargado en cada petición: la cookie `XSRF-TOKEN` sale desde la primera respuesta, incluido el login). Solo el login está exento. Swagger lo envía solo (`springdoc.swagger-ui.csrf.enabled=true`).
- Cookies en `security/AuthCookies`: `HttpOnly`, `SameSite=Strict`, `Secure` configurable; `refresh_token` con `Path=/api/auth`. En `AuthController` se agregan con `response.addHeader(SET_COOKIE, ...)`, **no** con `ResponseEntity.header(...)`: esas cabeceras reemplazan los `Set-Cookie` que ya escribió Spring Security y se pierde la cookie `XSRF-TOKEN`.
- Refresh token (`RefreshTokenServiceImpl`): opaco de 256 bits, en `refresh_tokens` solo su hash SHA-256; rotación en cada `/refresh` y detección de reuso (un token revocado que vuelve → se revocan todos los del usuario). `rotate` y `AuthServiceImpl.refresh` llevan `noRollbackFor = InvalidTokenException.class` para que esa revocación no se deshaga al lanzar la excepción. La limpieza diaria (`@Scheduled`) borra solo los **vencidos**: los revocados se conservan para poder detectar reusos. FK a `users` con `ON DELETE CASCADE` (`@OnDelete`).
- `UserServiceImpl` revoca los refresh tokens al cambiar rol, contraseña o deshabilitar, y un admin no puede borrarse, deshabilitarse ni quitarse el rol ADMIN (`OperationNotAllowedException` → 409). Recibe el email del usuario actual como parámetro desde el controller (`Authentication#getName`).
- Último admin: `ensureNotLastActiveAdmin` (en `update` y `delete`, **después** de las reglas de "uno mismo" para conservar sus mensajes) impide borrar, degradar o deshabilitar al último ADMIN habilitado, con `UserRepository.countByRoleAndEnabledTrue(ADMIN)` → 409 "Debe quedar al menos un administrador habilitado". Solo cuenta cuando el usuario es hoy un ADMIN habilitado y deja de serlo. Existe porque el rol va en el JWT: un admin recién degradado conserva ADMIN en su token hasta 15 min y podría degradar o borrar al otro. Carrera entre dos admins simultáneos aceptada (sin lock).
- `jjwt-jackson` trae Jackson 2 (`com.fasterxml.jackson.databind`) solo para uso interno de jjwt; Spring MVC sigue con Jackson 3.

## Listados paginados y filtros

- **Clientes**: `GET /api/customers` tiene **dos handlers en la misma ruta separados por `produces`**. `findPage` (JSON) devuelve `PagedModel<CustomerDto>` (`{content, page:{size, number, totalElements, totalPages}}`) y `export` (XML, CSV, XLSX, PDF) devuelve `List<CustomerDto>` con **todo** el resultado filtrado y ordenado. Así `WebConfig` y los conversores no cambian: `AbstractCustomerHttpMessageConverter.canWrite` solo acepta `List<CustomerDto>` / `CustomerDto`, y la content negotiation ya configurada elige el handler (`?format=` o `Accept`; sin preferencia, `default-content-types=application/json` → `findPage`). No unas los handlers ni devuelvas `Page` en la exportación.
- Filtros de clientes en `CustomerFilter` (`search`, `active`, `city`, `birthFrom`, `birthTo`, `createdFrom`, `createdTo`) → `CustomerSpecifications.of(filter)` (`Specification.allOf`). `createdTo` se compara con `< (to + 1 día) 00:00` porque `createdAt` es `LocalDateTime`. El service rechaza rangos con "desde" posterior a "hasta" (`IllegalArgumentException` → 400).
- **Usuarios**: `GET /api/users` devuelve `PagedModel<UserDto>` con `UserFilter` (`search` en email, `role`, `enabled`) → `UserSpecifications.of(filter)`; `UserRepository` extiende `JpaSpecificationExecutor`. Orden por defecto `email`.
- Siempre Specifications (`JpaSpecificationExecutor`), no `@Query` con `(:param is null or ...)`, que en PostgreSQL falla con parámetros nulos. Los LIKE "contiene" usan `repositories/LikePatterns.contains(text)` + `ESCAPE` (minúsculas, sin espacios a los lados, `%` y `_` escapados).
- Orden: `services/Pageables.checked(pageable|sort, SORT_FIELDS)` valida contra una lista blanca por recurso (`CustomerServiceImpl.SORT_FIELDS`, `UserServiceImpl.SORT_FIELDS`; nunca `password`) y agrega `id` como desempate. Un campo fuera de la lista → `IllegalArgumentException` → 400 (sin esto sería un 500 por `PropertyReferenceException`).
- Tamaño de página: `spring.data.web.pageable.default-page-size=10` y `max-page-size=100` en `application.properties` (aplica también en `@WebMvcTest`; un `size` mayor se recorta sin error).
- `@ParameterObject` (springdoc) en los records de filtros y en `Pageable` para que Swagger muestre cada parámetro suelto. Swagger fusiona los dos handlers de clientes en una sola operación.
- Seed: `data.sql` reparte `created_at` en los últimos 120 días (`days_ago`) para poder probar `createdFrom` / `createdTo`.

## Facturas (spec 10)

- Tablas `invoices` e `invoice_items` creadas por **migración en Supabase** (no por Hibernate). `Invoice` / `InvoiceItem` copian ese esquema, incluidos los nombres de restricciones e índices (`invoices_invoice_number_key`, `uq_invoice_items_line`, `idx_…`, checks): así `prod` (`validate`) arranca y `update` (desarrollo contra Supabase) no crea duplicados. Si cambias una de esas entities, antes de arrancar contra Supabase (también `ApplicationTests`) revisa el DDL en seco: `ddl-auto=none`, `spring.sql.init.mode=never`, `jakarta.persistence.schema-generation.scripts.action=update` y `…scripts.create-target=<archivo>` (ver `specs/10-facturacion/plan.md` §7).
- `line_total` es una columna **generada** (`round(quantity * unit_price, 2)`): se mapea con `@Generated` + `@Column(insertable = false, updatable = false, precision = 12, scale = 2, columnDefinition = "… generated always as (…) stored")`. No uses `@GeneratedColumn` (sin `STORED`: PostgreSQL 18 la crea virtual y 17 rechaza la sintaxis) ni quites `precision`/`scale` (`update` intentaría un `ALTER COLUMN` en cada arranque). Los importes se calculan en Java con `InvoiceItem.calculateLineTotal()`.
- `InvoiceServiceImpl`: subtotal, IVA 19 % (`TAX_RATE`) y total con `BigDecimal` + `HALF_UP`; número `FAC-<año de emisión>-<NNNN>` (último con el prefijo + 1; carrera → 409 por el `UNIQUE`); líneas actualizadas **en el lugar** por posición (Hibernate hace los `INSERT` antes que los `DELETE` y borrar/reinsertar 1..n choca con `uq_invoice_items_line`); `update` usa `saveAndFlush` para que `@PreUpdate` llene `updatedAt` en la respuesta. Lista blanca de orden en `SORT_FIELDS` (por defecto `issueDate,desc`).
- Un `InvoiceDto` con `List<InvoiceItemDto>` (`@Valid`): número, nombre del cliente, `lineNumber`, importes y auditoría son de solo lectura. Errores por línea como `items[0].quantity`. Filtros en `InvoiceFilter` → `InvoiceSpecifications`; el listado trae el cliente con `@EntityGraph` y las líneas con `@BatchSize` (3 consultas por página).
- `CustomerServiceImpl.delete` → 409 si el cliente tiene facturas (la FK no tiene `ON DELETE`).

## Especificaciones (SDD)

Los cambios que necesita el dashboard React (`../4-react`) se especifican con SDD en `specs/`:

- `specs/constitution.md`: reglas del backend que toda spec y tarea debe respetar.
- `specs/01-paginacion-filtros-admin/`: `spec.md` (qué y por qué), `plan.md` (contrato y decisiones) y `tasks.md` (tareas `A-01` … `A-11`): fechas de auditoría de solo lectura, listados paginados con filtros (clientes y usuarios) sin romper la exportación, y protección del último ADMIN.
- `specs/09-docker-despliegue/` (tareas `I-01` … `I-06`, también toca `4-react`): perfil `prod`, `PORT`, Docker, Compose, blueprint de Render y rewrite de Vercel.
- `specs/10-facturacion/` (tareas `J-01` … `J-09`, backend y `4-react`): facturas por cliente sobre las tablas de Supabase; USER consulta, ADMIN administra.

Las tareas se ejecutan una a una desde la sesión de `4-react` con su skill `sdd-next-task` (índice global y estado en `../4-react/specs/README.md`). Las specs 01 (rama `feature/paginacion-filtros`), 09 (rama `feature/docker`) y 10 (rama `feature/facturacion`, en ambos repos) están **hechas**. Si cambias el contrato de la API, actualiza en el mismo cambio la spec, este archivo y el README.
