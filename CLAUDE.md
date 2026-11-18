# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

API REST de clientes (`Customer`) con Spring Boot 4.1.2, Java 25, Maven, Spring Data JPA + Hibernate 7 y PostgreSQL. Es parte de un curso: el código se muestra en pantalla y se publica en GitHub.

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
- PostgreSQL local con Docker (contenedor `3-spring-api-db`, se reinicia solo salvo `docker stop`): `docker run -d --name 3-spring-api-db --restart unless-stopped -e POSTGRES_DB=spring_api -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18`
- Consultar la base: `docker exec -it 3-spring-api-db psql -U postgres -d spring_api`
- pgAdmin: contenedor `3-spring-api-pgadmin` en http://localhost:5050 (comando completo en el README). Comparte la red `spring-api-net` con PostgreSQL, por eso el host del servidor es `3-spring-api-db` y no `localhost`. El servidor se registra desde `docker/pgadmin/servers.json` **solo la primera vez** que se crea el volumen `3-spring-api-pgadmin-data`; para recargarlo, borra el contenedor y el volumen.
- Con la app arriba: Swagger UI en `/swagger-ui.html`, health en `/actuator/health`.

## Configuración

- `application.properties` importa `.env` de la raíz con `spring.config.import=optional:file:.env[.properties]`; la ruta es relativa al directorio de trabajo, así que la app debe ejecutarse desde la raíz del proyecto.
- Variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (con valores por defecto genéricos de desarrollo). `.env` está en `.gitignore`; la plantilla versionada es `.env.example`.
- `spring.jpa.hibernate.ddl-auto=update`: el esquema lo crea Hibernate a partir de las entities (no hay Flyway).
- Seed: `src/main/resources/data.sql` corre en cada arranque (`spring.sql.init.mode=always` + `spring.jpa.defer-datasource-initialization=true`, para que corra después de que Hibernate cree las tablas), pero solo inserta los 20 clientes si la tabla está vacía (`INSERT ... SELECT ... FROM (VALUES ...) WHERE NOT EXISTS`). No uses `ON CONFLICT DO NOTHING`: cada fila descartada igual avanza la secuencia del `id`, y con DevTools reiniciando la app los IDs saltan de a 20. Usa nombres de **columna** (`last_name`, `created_at`, `update_at`) y asigna `created_at` a mano, porque `@PrePersist` no corre en SQL directo: si cambias columnas de la entity, actualiza el seed o la app no arranca.

## Arquitectura

Capas en packages bajo el package base: `models` → `repositories` → `services` → `controllers`, más `dto`, `mappers`, `exceptions`, `converters` y `config`.

- Las entities nunca salen del service: el service recibe y devuelve solo DTOs y usa un `Mapper` (`@Component`, mapeo a mano con `toDto`, `toEntity`, `updateEntity`).
- Un único `record` DTO por recurso, para request y response, con Bean Validation y `message` en español. No incluye las fechas de auditoría.
- Services: interfaz `XService` + `XServiceImpl`; `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras. Los controllers inyectan la interfaz.
- Auditoría en entities con callbacks JPA `@PrePersist` / `@PreUpdate` (no `@CreationTimestamp` / `@UpdateTimestamp`).
- Unicidad (p. ej. email): el service la comprueba y lanza `DuplicateResourceException`; la restricción `UNIQUE` de la columna cubre las carreras y llega como `DataIntegrityViolationException`. Ambas se traducen a 409.
- Content negotiation del listado y el detalle (`Accept` o `?format=`, configurado en `application.properties`): cada formato de exportación es un `HttpMessageConverter` en `converters/` que extiende `AbstractCustomerHttpMessageConverter` (solo escribe `List<CustomerDto>` y, si el formato lo permite, `CustomerDto`; cualquier otro cuerpo, como un `ProblemDetail`, cae a los conversores por defecto). Se registran en `config/WebConfig` con `configureMessageConvertersList`: CSV, Excel y PDF **al final** para que JSON siga siendo el formato por defecto, y el XML del listado **al inicio** para ganarle al XML genérico de Jackson. Los formatos de cada endpoint se declaran en `@GetMapping(produces = ...)`; un formato fuera de esa lista responde 406. Librerías: OpenPDF 3 (package `org.openpdf.text`, no `com.lowagie`), Apache POI y Jackson XML 3 (`tools.jackson.dataformat.xml`). En PDF horizontal usa un `Rectangle` con ancho y alto intercambiados, no `PageSize.A4.rotate()` (agrega `/Rotate 90` y los visores muestran la página de lado).
- `GlobalExceptionHandler` **extiende `ResponseEntityExceptionHandler`** y sobrescribe `handleMethodArgumentNotValid`. No lo cambies a `@ExceptionHandler(MethodArgumentNotValidException.class)`: con `spring.mvc.problemdetails.enabled=true` el advice por defecto de Boot atendería la validación primero y se perderían los errores por campo. Todas las respuestas de error son `ProblemDetail`; la validación agrega la propiedad `errors` (campo → mensaje).

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
