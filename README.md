# API REST de clientes con Spring Boot

API REST para gestionar clientes (`Customer`) construida con Spring Boot 4, Spring Data JPA y PostgreSQL, con validación de datos, manejo de errores en formato estándar `ProblemDetail` (RFC 9457) y seguridad con **Spring Security + JWT en cookies HttpOnly**, usuarios y roles.

Proyecto del curso de Spring Boot con Claude Code.

**Frontend:** el dashboard que consume esta API es [Cartera](https://github.com/andresguzf/curso-springboot-claude-react), una aplicación React + TypeScript (en local, en la carpeta hermana `../4-react`). Usa el login con cookies, los listados paginados con filtros, la exportación y la administración de usuarios descritos aquí; en desarrollo se conecta por el proxy de Vite (`/api` → `http://localhost:8080`), así que no hace falta CORS.

## Stack

- Java 25
- Spring Boot 4.1.2 (Spring Web MVC, Validation, Actuator, DevTools)
- Spring Data JPA + Hibernate 7
- Spring Security 7 + JWT (jjwt) en cookies HttpOnly, con refresh token y protección CSRF
- PostgreSQL
- springdoc-openapi (Swagger UI)
- Jackson XML, Apache POI (Excel) y OpenPDF (PDF) para la exportación
- Maven (con Maven Wrapper)

## Requisitos

- JDK 25
- PostgreSQL, o Docker para levantarlo en un contenedor

## Puesta en marcha

1. Clona el repositorio:

   ```bash
   git clone https://github.com/andresguzf/curso-springboot-claude-api.git
   cd curso-springboot-claude-api
   ```

2. Crea tu archivo de variables de entorno a partir de la plantilla y ajusta los valores:

   ```bash
   cp .env.example .env
   ```

   | Variable | Descripción | Valor por defecto |
   |---|---|---|
   | `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/spring_api` |
   | `DB_USERNAME` | Usuario de la base de datos | `postgres` |
   | `DB_PASSWORD` | Contraseña de la base de datos | `postgres` |
   | `JWT_SECRET` | Clave HMAC en Base64 (mín. 256 bits) para firmar los JWT. **Obligatoria** | — |
   | `JWT_COOKIE_SECURE` | Atributo `Secure` de las cookies (`false` para probar con Postman/curl en `http://localhost`) | `true` |
   | `ADMIN_EMAIL` | Email del administrador inicial | `admin@ejemplo.com` |
   | `ADMIN_PASSWORD` | Contraseña del administrador inicial (sin ella no se crea) | — |

   Genera la clave del JWT con:

   ```bash
   openssl rand -base64 32
   ```

   La aplicación lee el `.env` de la raíz del proyecto al arrancar. Este archivo no se sube al repositorio.

3. Levanta PostgreSQL (si no lo tienes instalado, con Docker):

   ```bash
   docker run -d --name 3-spring-api-db --restart unless-stopped -e POSTGRES_DB=spring_api -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18
   ```

   Con `--restart unless-stopped` el contenedor vuelve a levantarse solo si se cae o al reiniciar Docker, salvo que lo hayas detenido tú con `docker stop`.

   Opcional: **pgAdmin** para explorar la base desde el navegador. Comparte una red de Docker con PostgreSQL y trae el servidor ya registrado (`docker/pgadmin/servers.json`):

   ```bash
   docker network create spring-api-net
   docker network connect spring-api-net 3-spring-api-db
   docker run -d --name 3-spring-api-pgadmin --restart unless-stopped --network spring-api-net -p 5050:80 \
     -e PGADMIN_DEFAULT_EMAIL=admin@example.com -e PGADMIN_DEFAULT_PASSWORD=postgres \
     -v 3-spring-api-pgadmin-data:/var/lib/pgadmin \
     -v "$PWD/docker/pgadmin/servers.json:/pgadmin4/servers.json:ro" \
     dpage/pgadmin4:latest
   ```

   Entra en http://localhost:5050 con `admin@example.com` / `postgres`. El servidor **3-spring-api (PostgreSQL)** aparece en la lista; al abrirlo pide la contraseña de la base (`DB_PASSWORD`).

4. Ejecuta la aplicación desde la raíz del proyecto:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows usa `mvnw.cmd spring-boot:run`.

Hibernate crea las tablas `customers`, `users` y `refresh_tokens` automáticamente al arrancar (`ddl-auto=update`). Si no existe un usuario con `ADMIN_EMAIL`, se crea como administrador con `ADMIN_PASSWORD`. Luego se carga un seed de **20 clientes de ejemplo** desde `src/main/resources/data.sql`. El seed solo se inserta cuando la tabla está vacía: en los arranques siguientes no hace nada, así que no duplica clientes ni consume IDs.

Las fechas de alta (`created_at`) del seed se reparten en los últimos 120 días, para que el filtro por fecha de alta (`createdFrom` / `createdTo`) devuelva resultados distintos. Si tu base se creó con una versión anterior del seed (todos los clientes con la misma fecha), puedes repartirlas sin borrar nada; supone que los clientes del seed son los de id 1 a 20:

```bash
docker exec -i 3-spring-api-db psql -U postgres -d spring_api <<'SQL'
UPDATE customers c
SET created_at = now() - make_interval(days => d.days_ago)
FROM (VALUES (1, 120), (2, 110), (3, 100), (4, 90), (5, 80), (6, 70), (7, 62), (8, 55), (9, 48), (10, 41),
             (11, 35), (12, 29), (13, 23), (14, 18), (15, 14), (16, 10), (17, 7), (18, 4), (19, 2), (20, 0))
     AS d (id, days_ago)
WHERE c.id = d.id;
SQL
```

## Seguridad

La API usa **JWT en cookies HttpOnly**: el token nunca aparece en el cuerpo de las respuestas ni es accesible desde JavaScript, lo que evita que un script malicioso (XSS) lo robe.

| Cookie | Contenido | Atributos |
|---|---|---|
| `access_token` | JWT firmado (HS256) con el email y el rol; dura **15 min** | `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/` |
| `refresh_token` | Token opaco aleatorio; dura **7 días**. En la base solo se guarda su hash SHA-256 | `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth` |
| `XSRF-TOKEN` | Token CSRF, legible por JavaScript | `SameSite=Strict`, `Path=/` |

- **CSRF**: como el navegador envía las cookies solo, todo `POST`/`PUT`/`DELETE` (salvo el login) debe traer la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`. Un sitio externo no puede leer esa cookie, así que no puede falsificar la petición.
- **Refresh con rotación**: cada `POST /api/auth/refresh` invalida el refresh token usado y entrega uno nuevo. Si alguien reutiliza un refresh token ya usado (posible robo), se cierran todas las sesiones de ese usuario.
- Al cambiar el rol, la contraseña o deshabilitar un usuario se revocan sus refresh tokens.

### Roles

| Rol | Clientes | Usuarios |
|---|---|---|
| `USER` | Ver, buscar y exportar | — |
| `ADMIN` | Ver, crear, editar y borrar | Administrar |

Un administrador no puede borrarse, deshabilitarse ni quitarse el rol `ADMIN` a sí mismo (409).

Además, **siempre debe quedar al menos un administrador habilitado**: borrar, degradar a `USER` o deshabilitar al último `ADMIN` habilitado responde 409 con el detalle "Debe quedar al menos un administrador habilitado". Esta regla cubre un caso que la anterior no alcanza: como el rol viaja en el JWT, un admin recién degradado conserva el rol `ADMIN` en su token hasta 15 minutos y podría intentar degradar o borrar al otro admin.

### Autenticación: `/api/auth`

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | Devuelve el token CSRF (y crea la cookie `XSRF-TOKEN`) | 200 |
| `POST` | `/api/auth/login` | Inicia sesión con `email` y `password`; pone las cookies | 200 + usuario, 400, 401 |
| `POST` | `/api/auth/refresh` | Renueva ambos tokens (rotación) | 200, 401, 403 sin CSRF |
| `POST` | `/api/auth/logout` | Revoca el refresh token y borra las cookies | 204 |
| `GET` | `/api/auth/me` | Usuario autenticado | 200, 401 |

### Probar con curl

El cookie jar (`-c`/`-b`) guarda las cookies como lo haría el navegador (usa `JWT_COOKIE_SECURE=false` en el `.env`):

```bash
# 1. Login: guarda access_token, refresh_token y XSRF-TOKEN
curl -c cookies.txt -H "Content-Type: application/json" \
  -d '{"email":"admin@ejemplo.com","password":"tu-password"}' \
  http://localhost:8080/api/auth/login

# 2. Lecturas: basta con enviar las cookies
curl -b cookies.txt "http://localhost:8080/api/customers?search=santiago"

# 3. Escrituras: además, la cabecera X-XSRF-TOKEN con el valor de la cookie
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' cookies.txt)
curl -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"email":"lector@ejemplo.com","password":"password123","role":"USER"}' \
  http://localhost:8080/api/users
```

En **Swagger UI** basta con ejecutar `POST /api/auth/login` desde "Try it out": el navegador guarda las cookies y Swagger envía la cabecera CSRF automáticamente.

## Endpoints

### Clientes: `/api/customers`

Lectura: roles `USER` y `ADMIN`. Escritura: solo `ADMIN`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/customers` | Lista paginada de clientes, con filtros y orden opcionales | 200, 400 |
| `GET` | `/api/customers/{id}` | Obtiene un cliente | 200, 404 |
| `POST` | `/api/customers` | Crea un cliente | 201 + `Location`, 400, 409 |
| `PUT` | `/api/customers/{id}` | Actualiza un cliente | 200, 400, 404, 409 |
| `DELETE` | `/api/customers/{id}` | Elimina un cliente | 204, 404 |

#### Listado paginado, búsqueda y filtros

> **Cambio incompatible:** el listado en JSON ya no devuelve un arreglo, sino una página (`content` + `page`). Las URLs de exportación (`?format=csv`, `xlsx`, `pdf`, `xml`) no cambian.

`GET /api/customers` acepta estos parámetros opcionales; los filtros se combinan (todos deben cumplirse):

| Parámetro | Ejemplo | Descripción |
|---|---|---|
| `search` | `pérez` | Texto que se busca, sin distinguir mayúsculas, en nombre, apellido, email y ciudad |
| `active` | `true` | `true` activos, `false` inactivos |
| `city` | `san` | Texto contenido en la ciudad |
| `birthFrom`, `birthTo` | `1985-01-01` | Rango de fecha de nacimiento (`yyyy-MM-dd`, inclusivo) |
| `createdFrom`, `createdTo` | `2026-11-01` | Rango de fecha de alta (`yyyy-MM-dd`, inclusivo: `createdTo` incluye todo ese día) |
| `page` | `0` | Número de página, desde 0 (solo JSON) |
| `size` | `20` | Tamaño de página: 10 por defecto, máximo 100 (un valor mayor se recorta a 100) (solo JSON) |
| `sort` | `lastName,desc` | Orden `campo,asc\|desc`; se puede repetir. Campos permitidos: `id`, `name`, `lastName`, `email`, `city`, `birthDate`, `active`, `createdAt`, `updatedAt`. Por defecto `id` |

Respuesta JSON:

```json
{
  "content": [
    {
      "id": 1, "name": "Ana", "lastName": "Pérez", "email": "…", "phone": "+56 9 1000 0001",
      "address": "Av. Siempre Viva 101", "city": "Santiago", "birthDate": "1990-05-20", "active": true,
      "createdAt": "2026-07-23T10:15:30", "updatedAt": null
    }
  ],
  "page": { "size": 10, "number": 0, "totalElements": 20, "totalPages": 2 }
}
```

```bash
# -G + --data-urlencode codifica la "é"; sin codificar, Tomcat rechaza la URL con 400
curl -b cookies.txt -G "http://localhost:8080/api/customers" --data-urlencode "search=pérez" -d active=true
curl -b cookies.txt "http://localhost:8080/api/customers?page=0&size=5&sort=lastName,desc&city=san"
curl -b cookies.txt "http://localhost:8080/api/customers?birthFrom=1985-01-01&birthTo=1990-12-31"
curl -b cookies.txt -o clientes.csv "http://localhost:8080/api/customers?city=santiago&format=csv"
```

- Si se pide ordenar por un campo no permitido, si un rango tiene "desde" posterior a "hasta" o si una fecha no tiene formato `yyyy-MM-dd`, responde **400** con el detalle.
- Los formatos de exportación (CSV, Excel, PDF, XML) exportan **todo el resultado filtrado y ordenado**, no solo una página: `page` y `size` se ignoran.
- La paginación es estable: si dos clientes empatan en el campo de orden, se desempata por `id`.

### Usuarios: `/api/users` (solo `ADMIN`)

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/users` | Lista paginada de usuarios, con búsqueda y filtros | 200, 400 |
| `GET` | `/api/users/{id}` | Obtiene un usuario | 200, 404 |
| `POST` | `/api/users` | Crea un usuario | 201 + `Location`, 400, 409 |
| `PUT` | `/api/users/{id}` | Actualiza un usuario | 200, 400, 404, 409 |
| `DELETE` | `/api/users/{id}` | Elimina un usuario | 204, 404, 409 |

El listado devuelve el mismo formato paginado que el de clientes (`content` + `page`) y acepta:

| Parámetro | Ejemplo | Descripción |
|---|---|---|
| `search` | `admin` | Texto contenido en el email, sin distinguir mayúsculas |
| `role` | `ADMIN` | `USER` o `ADMIN` |
| `enabled` | `true` | `true` habilitados, `false` deshabilitados |
| `page`, `size` | `0`, `10` | Igual que en clientes (10 por defecto, máximo 100) |
| `sort` | `createdAt,desc` | Campos permitidos: `id`, `email`, `role`, `enabled`, `createdAt`. Por defecto `email` |

```bash
curl -b cookies.txt "http://localhost:8080/api/users?role=ADMIN&enabled=true"
```

| Campo | Obligatorio | Reglas |
|---|---|---|
| `email` | Sí | formato email, único, máx. 150 |
| `password` | Al crear | 8 a 72 caracteres; solo de escritura (nunca sale en las respuestas). Al editar, si no se envía se conserva |
| `role` | Sí | `USER` o `ADMIN` |
| `enabled` | No | `true` por defecto; un usuario deshabilitado no puede iniciar sesión |
| `createdAt`, `updatedAt` | — | Solo de lectura: salen en las respuestas y se ignoran si se envían |

La contraseña se guarda hasheada con BCrypt. `PUT` y `DELETE` responden 409 si se intenta quitar permisos a uno mismo o dejar el sistema sin administradores habilitados (ver [Roles](#roles)).

Documentación interactiva en **Swagger UI**: http://localhost:8080/swagger-ui.html

Estado de la aplicación: http://localhost:8080/actuator/health

### Ejemplo: crear un cliente (como `ADMIN`)

```bash
curl -i -X POST http://localhost:8080/api/customers \
  -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Ana",
        "lastName": "Pérez",
        "email": "ana@ejemplo.com",
        "phone": "+56 9 0000 0000",
        "address": "Calle Falsa 123",
        "city": "Santiago",
        "birthDate": "1990-05-20"
      }'
```

Campos del cliente:

| Campo | Obligatorio | Reglas |
|---|---|---|
| `name` | Sí | máx. 80 caracteres |
| `lastName` | Sí | máx. 80 caracteres |
| `email` | Sí | formato email, único, máx. 150 |
| `phone` | Sí | máx. 30 |
| `address` | Sí | máx. 200 |
| `city` | Sí | máx. 80 |
| `birthDate` | Sí | fecha pasada (`yyyy-MM-dd`) |
| `active` | No | `true` por defecto |

Las fechas de alta y de última edición (`createdAt`, `updatedAt`) las asigna el sistema: salen en las respuestas como solo lectura y, si se envían en un `POST` o `PUT`, se ignoran. `updatedAt` es `null` mientras el cliente no se haya editado nunca.

## Formatos de exportación (content negotiation)

El listado y el detalle de clientes responden en distintos formatos según lo que pida el cliente HTTP. **JSON es el formato por defecto.** El formato se elige con la cabecera `Accept` o con el parámetro `?format=` (útil para descargar desde el navegador).

| Formato | `Accept` | `?format=` | Listado `/api/customers` | Detalle `/api/customers/{id}` |
|---|---|---|---|---|
| JSON | `application/json` | `json` | ✅ (por defecto) | ✅ (por defecto) |
| XML | `application/xml` | `xml` | ✅ `<customers><customer>…` | ✅ `<customer>…` |
| CSV | `text/csv` | `csv` | ✅ descarga `clientes.csv` | — |
| Excel | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `xlsx` | ✅ descarga `clientes.xlsx` | — |
| PDF | `application/pdf` | `pdf` | ✅ `clientes.pdf` (tabla horizontal) | ✅ ficha `cliente-{id}.pdf` |

Un formato no disponible para ese endpoint responde **406 Not Acceptable**.

El listado en JSON es paginado; los demás formatos exportan **todo el resultado** con los filtros y el orden de la petición (ver [Listado paginado, búsqueda y filtros](#listado-paginado-búsqueda-y-filtros)). En Swagger UI ambos aparecen como una sola operación `GET /api/customers` con todos los formatos de respuesta.

```bash
curl -b cookies.txt -H "Accept: application/xml" http://localhost:8080/api/customers
curl -b cookies.txt -o clientes.xlsx "http://localhost:8080/api/customers?format=xlsx"
curl -b cookies.txt -o clientes.csv  "http://localhost:8080/api/customers?format=csv"
curl -b cookies.txt -o clientes.pdf  "http://localhost:8080/api/customers?format=pdf"
curl -b cookies.txt -o cliente-1.pdf "http://localhost:8080/api/customers/1?format=pdf"
```

- **XML**: los mismos campos que el JSON, incluidas las fechas de auditoría `createdAt` y `updatedAt`.
- **CSV**: separado por punto y coma (`;`), el separador que usa Excel con configuración regional en español, en UTF-8 (con BOM para que Excel muestre bien los acentos) y con los mismos nombres de campo que el JSON, salvo las fechas de auditoría (igual que Excel y PDF).
- **Excel**: hoja *Clientes* con encabezado fijo, autofiltro y fechas como celdas de fecha.
- **PDF**: diseño minimalista; el listado es una tabla con todas las columnas y el detalle es una ficha con todos los datos del cliente, su estado y su edad.

## Manejo de errores

Todos los errores se responden en formato `ProblemDetail`:

| Situación | Status |
|---|---|
| El cliente o usuario no existe | 404 Not Found |
| El email ya está registrado | 409 Conflict |
| Campos inválidos | 400 Bad Request, con el detalle por campo |
| Filtro con formato inválido (p. ej. `?birthFrom=ayer` o `?role=FOO`) | 400 Bad Request, con el detalle por campo ("El valor 'ayer' no tiene un formato válido") |
| Orden no permitido (`?sort=password`) o rango de fechas invertido | 400 Bad Request |
| Sin sesión, token inválido o expirado, login fallido | 401 Unauthorized |
| Rol sin permiso o falta el token CSRF | 403 Forbidden |
| Operación no permitida (un admin que se borra o se quita permisos a sí mismo, o dejar el sistema sin administradores habilitados) | 409 Conflict |

Ejemplo de error de validación:

```json
{
  "type": "about:blank",
  "title": "Error de validación",
  "status": 400,
  "detail": "Hay campos con errores de validación",
  "instance": "/api/customers",
  "errors": {
    "name": "El nombre es obligatorio",
    "email": "El email no tiene un formato válido"
  }
}
```

## Estructura

```
src/main/java/com/andres/course/claude/springboot/springapi/app/
├── config/        # Spring MVC (conversores), Spring Security, admin inicial, scheduling
├── controllers/   # endpoints REST (clientes, usuarios, auth)
├── security/      # JWT, filtro de la cookie, cookies, respuestas 401/403
├── converters/    # conversores de exportación: XML, CSV, Excel y PDF
├── services/      # lógica de negocio (interfaz + implementación)
├── repositories/  # acceso a datos con Spring Data JPA
├── models/        # entidades JPA
├── dto/           # records de entrada/salida con validación
├── mappers/       # conversión entidad <-> DTO
└── exceptions/    # excepciones de dominio y manejador global
```

## Tests

```bash
./mvnw test
```

El test de contexto (`ApplicationTests`) necesita PostgreSQL disponible. El resto corre sin base de datos, con la clave de prueba de `src/test/resources/application-test.properties`: seguridad, paginación y filtros de los controllers con `@WebMvcTest`, reglas de los services con Mockito (paginación de clientes, último administrador), validación del orden (`Pageables`), JWT y refresh tokens.

## Especificaciones (SDD)

Los cambios para el frontend React (paginación, filtros, fechas de auditoría y protección del último administrador) se especificaron con desarrollo guiado por especificaciones en [`specs/`](specs/): la [constitución](specs/constitution.md) del backend y la spec [`01-paginacion-filtros-admin`](specs/01-paginacion-filtros-admin/spec.md), con su plan técnico y sus tareas. Las specs del frontend (fases 02 a 06) y el índice de todas las fases están en el [repositorio del frontend](https://github.com/andresguzf/curso-springboot-claude-react/blob/main/specs/README.md).
