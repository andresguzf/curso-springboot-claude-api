# Tareas — Spec 01 (Fase A, backend)

Se ejecutan **una a una** con la skill `sdd-next-task` (desde la sesión de `4-react`).
Cada tarea: implementar → verificar → marcar `[x]` → commit en `3-spring-api` → pausa
para revisión. Definición de "hecho" en la [constitución](../constitution.md#5-calidad-definición-de-hecho).

Rutas: `app/` = `src/main/java/com/andres/course/claude/springboot/springapi/app/`,
`test/` = `src/test/java/com/andres/course/claude/springboot/springapi/app/`.

- [x] **A-01** Crear la rama y exponer la auditoría de solo lectura en clientes
  - Depende de: —
  - Cubre: RF-01-01, RNF-01-03
  - Archivos: `app/dto/CustomerDto.java`, `app/mappers/CustomerMapper.java`, `test/controllers/CustomerControllerSecurityTest.java`
  - Hecho cuando: existe la rama `feature/paginacion-filtros`; `GET /api/customers/{id}` incluye `createdAt` y `updatedAt`; un POST con `createdAt` llega al service con `createdAt == null`
  - Verificación: `./mvnw test` (test nuevo `lasFechasDeAuditoriaSonSoloLectura`)
  - Commit: "Expone las fechas de auditoría como solo lectura en el DTO de clientes"

- [x] **A-02** Exponer la auditoría de solo lectura en usuarios
  - Depende de: A-01
  - Cubre: RF-01-01, RNF-01-03
  - Archivos: `app/dto/UserDto.java`, `app/mappers/UserMapper.java`, `app/config/AdminUserInitializer.java`, `test/controllers/UserControllerTest.java`, `test/controllers/AuthControllerTest.java`
  - Hecho cuando: `UserDto` devuelve `createdAt` / `updatedAt` y los ignora en el request; el admin inicial se sigue creando
  - Verificación: `./mvnw test`
  - Commit: "Expone las fechas de auditoría como solo lectura en el DTO de usuarios"

- [x] **A-03** Record de filtros y Specifications de clientes
  - Depende de: A-01
  - Cubre: RF-01-05, RNF-01-01
  - Archivos: `app/dto/CustomerFilter.java`, `app/repositories/CustomerSpecifications.java`
  - Hecho cuando: existen `cityContains`, `birthDateBetween`, `createdBetween` (fin inclusivo con `to + 1 día`) y `of(CustomerFilter)` con `Specification.allOf`; cada filtro sin valor no filtra; `matches` y `hasActive` siguen igual
  - Verificación: `./mvnw -q -DskipTests compile` y `./mvnw test`
  - Commit: "Agrega filtros por ciudad y por rangos de fecha a las Specifications de clientes"

- [x] **A-04** Utilidad `Pageables` con lista blanca de orden
  - Depende de: —
  - Cubre: RF-01-04, RNF-01-02
  - Archivos: `app/services/Pageables.java`, `test/services/PageablesTest.java`
  - Hecho cuando: `checked(Pageable, Set)` y `checked(Sort, Set)` lanzan `IllegalArgumentException` con los campos permitidos si el orden no está en la lista y agregan `id` como desempate
  - Verificación: `./mvnw test -Dtest=PageablesTest`
  - Commit: "Agrega validación de orden permitido y desempate para listados paginados"

- [x] **A-05** Service de clientes paginado y con validación de rangos
  - Depende de: A-03, A-04
  - Cubre: RF-01-02, RF-01-05, RNF-01-01, RNF-01-02
  - Archivos: `app/services/CustomerService.java`, `app/services/CustomerServiceImpl.java`, `test/services/CustomerServiceImplTest.java` (nuevo)
  - Hecho cuando: existen `Page<CustomerDto> findPage(CustomerFilter, Pageable)` y `List<CustomerDto> findAll(CustomerFilter, Sort)`; un rango con `from > to` lanza `IllegalArgumentException` (400)
  - Nota: `findAll(String, Boolean)` se conserva (delegando en el método nuevo) porque el controller aún lo usa; se elimina en A-06
  - Verificación: `./mvnw test`
  - Commit: "Agrega listado paginado y filtrado al servicio de clientes"

- [x] **A-06** Controller de clientes: JSON paginado y exportación completa
  - Depende de: A-05
  - Cubre: RF-01-02, RF-01-03, RF-01-04, RF-01-05, RF-01-06
  - Archivos: `app/controllers/CustomerController.java`, `src/main/resources/application.properties`, `app/config/WebConfig.java` (solo si hace falta el customizer), `test/controllers/CustomerControllerSecurityTest.java`
  - Hecho cuando: JSON devuelve `content` + `page`; `format=csv|xlsx|pdf|xml` llama a `export` con todo el resultado filtrado; `size=1000` llega como 100; `sort=password` y `birthFrom=ayer` responden 400; se elimina `CustomerService.findAll(String, Boolean)` (ya sin uso); Swagger revisado
  - Verificación: `./mvnw test`; con la app arriba, `curl -b cookies.txt "localhost:8080/api/customers?page=0&size=5&sort=lastName,desc&city=san"` y `curl -b cookies.txt -o c.csv "localhost:8080/api/customers?city=san&format=csv"`
  - Commit: "Agrega paginación y filtros por fecha y ciudad al listado de clientes"

- [x] **A-07** (Opcional) Repartir `created_at` en el seed
  - Depende de: A-06
  - Cubre: RF-01-05
  - Archivos: `src/main/resources/data.sql`, `README.md` (nota con el `UPDATE` para bases existentes)
  - Hecho cuando: en una base nueva los 20 clientes tienen fechas de alta distintas (`now() - make_interval(days => days_ago)`, de 0 a 120 días) y el filtro `createdFrom` devuelve un subconjunto
  - Verificación: `./mvnw test`; probar `?createdFrom=` con curl contra una base nueva
  - Nota: se verificó en una base temporal (`spring_api_a07`, borrada al terminar) para no recrear la base local del usuario; el `UPDATE` del README también se probó ahí
  - Commit: "Reparte las fechas de alta del seed para probar los filtros por fecha"

- [x] **A-08** Filtros y Specifications de usuarios
  - Depende de: A-02
  - Cubre: RF-01-07, RNF-01-01
  - Archivos: `app/dto/UserFilter.java`, `app/repositories/UserSpecifications.java`, `app/repositories/UserRepository.java`, `app/repositories/LikePatterns.java` (nuevo, escape de LIKE compartido), `app/repositories/CustomerSpecifications.java` (usa `LikePatterns`), `test/repositories/LikePatternsTest.java` (nuevo)
  - Hecho cuando: `UserRepository` extiende `JpaSpecificationExecutor<User>`; existen `emailContains` (LIKE escapado), `hasRole`, `hasEnabled` y `of(UserFilter)`
  - Verificación: `./mvnw -q -DskipTests compile` y `./mvnw test`
  - Commit: "Agrega filtros por email, rol y estado a los usuarios"

- [x] **A-09** Listado de usuarios paginado y filtrado
  - Depende de: A-04, A-08
  - Cubre: RF-01-04, RF-01-07, RNF-01-02
  - Archivos: `app/services/UserService.java`, `app/services/UserServiceImpl.java`, `app/controllers/UserController.java`, `app/exceptions/GlobalExceptionHandler.java` (mensaje de binding en español), `test/controllers/UserControllerTest.java`, `test/controllers/CustomerControllerSecurityTest.java` (mensaje de `birthFrom=ayer`)
  - Hecho cuando: `GET /api/users` devuelve `PagedModel<UserDto>` sin `password`; los filtros se enlazan al record; `role=FOO` responde 400 con "El valor 'FOO' no tiene un formato válido"; se elimina `UserService.findAll()`
  - Verificación: `./mvnw test`; `curl -b cookies.txt "localhost:8080/api/users?role=ADMIN&enabled=true"`
  - Commit: "Agrega búsqueda, filtros y paginación al listado de usuarios"

- [x] **A-10** Proteger al último administrador habilitado
  - Depende de: A-08
  - Cubre: RF-01-08, RNF-01-02
  - Archivos: `app/repositories/UserRepository.java`, `app/services/UserServiceImpl.java`, `test/services/UserServiceImplTest.java` (nuevo), `test/controllers/UserControllerTest.java`
  - Hecho cuando: borrar, degradar o deshabilitar al único ADMIN habilitado lanza `OperationNotAllowedException("Debe quedar al menos un administrador habilitado")` (409); con dos admins funciona; editar el email del último admin funciona; un admin deshabilitado no cuenta; las reglas de "uno mismo" conservan sus mensajes
  - Verificación: `./mvnw test` (unos 9 casos en `UserServiceImplTest` + 409 en el controller); prueba manual con curl creando un segundo admin
  - Nota: la prueba manual se hizo en una base temporal (`spring_api_a10`, borrada al terminar): A degrada a B; con su token aún ADMIN, B recibe 409 al borrar, degradar o deshabilitar a A
  - Commit: "Impide eliminar, degradar o deshabilitar al último administrador habilitado"

- [ ] **A-11** Documentar la fase A
  - Depende de: A-06, A-09, A-10
  - Cubre: RF-01-09
  - Archivos: `README.md`, `CLAUDE.md`, `specs/01-paginacion-filtros-admin/spec.md` (estado: Hecha), `4-react/specs/README.md` (estado)
  - Hecho cuando: el README documenta parámetros, respuesta paginada, aviso de cambio incompatible, fechas de auditoría y regla del último admin; `CLAUDE.md` explica los dos handlers por `produces`, los records `XFilter`, `Pageables`, `max-page-size`, la auditoría `READ_ONLY` y el último admin
  - Verificación: `./mvnw test`; revisar que los ejemplos curl del README funcionen
  - Commit: "Documenta paginación, filtros y protección del último administrador"
  - Al cerrar: preguntar al usuario si hace merge de `feature/paginacion-filtros` a `main` y push
