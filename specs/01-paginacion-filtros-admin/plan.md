# Plan técnico — Spec 01

Cómo se implementa [`spec.md`](spec.md) respetando la [constitución](../constitution.md).
Rutas relativas a `src/main/java/com/andres/course/claude/springboot/springapi/app/`
(`app/`) y `src/test/java/.../app/` (`test/`).

## 1. Contrato de la API

### Clientes

`GET /api/customers` — dos handlers en la misma ruta, separados por `produces`:

| `Accept` / `format` | Handler | Respuesta |
|---|---|---|
| `application/json` (por defecto, también `*/*`) | `findPage(CustomerFilter, Pageable)` | `PagedModel<CustomerDto>` |
| `xml`, `csv`, `xlsx`, `pdf` | `export(CustomerFilter, Sort)` | `List<CustomerDto>` (lo escriben los converters) |

Parámetros:

| Parámetro | Tipo | Notas |
|---|---|---|
| `search` | texto | LIKE sin mayúsculas en `name`, `lastName`, `email`, `city` (como hoy) |
| `active` | `true` / `false` | |
| `city` | texto | LIKE sin mayúsculas en `city` |
| `birthFrom`, `birthTo` | `yyyy-MM-dd` | inclusivos sobre `birthDate` |
| `createdFrom`, `createdTo` | `yyyy-MM-dd` | `createdAt >= from 00:00` y `createdAt < (to + 1 día) 00:00` |
| `page` | entero ≥ 0 | solo JSON |
| `size` | entero, máx. 100 | solo JSON; por defecto 10 |
| `sort` | `campo,asc|desc`, repetible | lista blanca: `id, name, lastName, email, city, birthDate, active, createdAt, updatedAt` |

Respuesta JSON:

```json
{
  "content": [
    { "id": 1, "name": "…", "lastName": "…", "email": "…", "phone": "…", "address": "…",
      "city": "…", "birthDate": "1990-05-20", "active": true,
      "createdAt": "2026-11-19T10:15:30", "updatedAt": null }
  ],
  "page": { "size": 10, "number": 0, "totalElements": 20, "totalPages": 2 }
}
```

### Usuarios

`GET /api/users?search=&role=&enabled=&page=&size=&sort=` → `PagedModel<UserDto>`.
Lista blanca de orden: `id, email, role, enabled, createdAt`. Orden por defecto: `email`.
`UserDto` en la respuesta: `{id, email, role, enabled, createdAt, updatedAt}` (sin `password`).

### Errores nuevos

| Caso | Status | Origen |
|---|---|---|
| `sort` fuera de la lista blanca | 400 "Solicitud inválida" | `IllegalArgumentException` en `Pageables` |
| Rango con `from > to` | 400 "Solicitud inválida" | `IllegalArgumentException` en el service |
| Fecha o enum con formato inválido | 400 | error de binding (con mensaje en español si se implementa A-09 opcional) |
| Último admin | 409 "Operación no permitida" | `OperationNotAllowedException` |

## 2. Decisiones

| Decisión | Motivo |
|---|---|
| Dos handlers por `produces` en vez de un endpoint `/export` | `AbstractCustomerHttpMessageConverter.canWrite` solo acepta `List<CustomerDto>` o `CustomerDto`; con `produces` separado la negociación ya configurada (`spring.mvc.contentnegotiation.*`, `default-content-types=application/json`) elige el handler sin tocar `WebConfig` ni los converters, y las URLs del README no cambian. |
| `PagedModel` explícito (`new PagedModel<>(page)`) | JSON estable y documentado, sin depender de `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`. |
| `record CustomerFilter` / `UserFilter` enlazados como `@ModelAttribute` implícito | Un parámetro en vez de siete; se pasa tal cual al service y a `XSpecifications.of(filter)`. |
| `Pageables.checked(...)` con lista blanca | Sin ella, un `sort` inválido produce `PropertyReferenceException` (500) y permitiría ordenar por `password`. Agrega `id` como desempate para que la paginación sea estable. |
| `createdTo` exclusivo al día siguiente | `createdAt` es `LocalDateTime`; así el último día queda completo. |
| `spring.data.web.pageable.default-page-size=10` y `max-page-size=100` | El resolver recorta `size` sin error. Si en Boot 4 no aplica en el slice `@WebMvcTest`, se usa un `PageableHandlerMethodArgumentResolverCustomizer` en `WebConfig`. |
| Último admin en `UserServiceImpl`, después de las reglas de "uno mismo" | Mantiene los mensajes actuales; el conteo `countByRoleAndEnabledTrue(ADMIN)` cubre el intervalo en que un JWT aún dice ADMIN. |
| Auditoría `READ_ONLY` en el DTO | Excepción documentada en la constitución y en `CLAUDE.md`; la skill compartida no se edita (es un symlink a otro repo). |

## 3. Archivos

| Archivo | Cambio |
|---|---|
| `app/dto/CustomerDto.java`, `app/dto/UserDto.java` | + `LocalDateTime createdAt, updatedAt` con `@JsonProperty(access = READ_ONLY)` |
| `app/mappers/CustomerMapper.java`, `app/mappers/UserMapper.java` | `toDto` copia fechas; `toEntity` / `updateEntity` las ignoran |
| `app/config/AdminUserInitializer.java` | constructor de `UserDto` con los campos nuevos (`null`) |
| `app/dto/CustomerFilter.java`, `app/dto/UserFilter.java` | nuevos records de parámetros |
| `app/repositories/CustomerSpecifications.java` | + `cityContains`, `birthDateBetween`, `createdBetween`, `of(CustomerFilter)` |
| `app/repositories/UserSpecifications.java` | nuevo: `emailContains`, `hasRole`, `hasEnabled`, `of(UserFilter)` |
| `app/repositories/UserRepository.java` | + `JpaSpecificationExecutor<User>`, + `countByRoleAndEnabledTrue(Role)` |
| `app/services/Pageables.java` | nuevo: lista blanca y desempate |
| `app/services/CustomerService(Impl).java` | `findPage(filter, pageable)`, `findAll(filter, sort)`, validación de rangos |
| `app/services/UserService(Impl).java` | `findPage(filter, pageable)`, `ensureNotLastActiveAdmin` en `update` y `delete` |
| `app/controllers/CustomerController.java` | handlers `findPage` (JSON) y `export` |
| `app/controllers/UserController.java` | listado paginado |
| `app/exceptions/GlobalExceptionHandler.java` | (opcional) mensaje en español para errores de binding |
| `src/main/resources/application.properties` | propiedades de `Pageable` |
| `src/main/resources/data.sql` | (opcional) `created_at` repartido |
| `test/controllers/CustomerControllerSecurityTest.java`, `test/controllers/UserControllerTest.java`, `test/controllers/AuthControllerTest.java` | ajustes de constructores y tests nuevos |
| `test/services/PageablesTest.java`, `test/services/CustomerServiceImplTest.java`, `test/services/UserServiceImplTest.java` | nuevos |
| `README.md`, `CLAUDE.md` | contrato, cambio incompatible, regla del último admin, excepciones a la convención |

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| springdoc fusiona los dos handlers en una sola operación | Confirmado en A-06: Swagger muestra una sola operación `GET /api/customers` con todos los formatos de respuesta. Con `@ParameterObject` en `CustomerFilter` y `Pageable`, cada filtro y `page` / `size` / `sort` aparecen como query params. Se documenta en el controller y en el README (A-11). |
| `max-page-size` no aplica en el slice de test | Descartado en A-06: la propiedad sí aplica en `@WebMvcTest`; no hizo falta el customizer en `WebConfig`. |
| Carrera entre dos admins que se degradan a la vez | Aceptado; mejora opcional con `@Lock(PESSIMISTIC_WRITE)`. |
| `updatedAt` nulo en registros nunca editados | Documentado; el frontend lo trata como opcional. |

## 5. Trazabilidad

| Requisito | Tareas |
|---|---|
| RF-01-01 | A-01, A-02 |
| RF-01-02 | A-05, A-06 |
| RF-01-03 | A-06 |
| RF-01-04 | A-04, A-06, A-09 |
| RF-01-05 | A-03, A-05, A-06, A-07 |
| RF-01-06 | A-06 |
| RF-01-07 | A-08, A-09 |
| RF-01-08 | A-10 |
| RF-01-09 | A-11 |
| RNF-01-01 | A-03, A-05, A-08 |
| RNF-01-02 | A-04, A-05, A-09, A-10 |
| RNF-01-03 | todas |
