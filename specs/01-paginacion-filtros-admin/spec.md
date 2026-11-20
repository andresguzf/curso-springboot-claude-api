# Spec 01 — Paginación, filtros y protección del último administrador

| | |
|---|---|
| Fase | A (backend) |
| Repo | `3-spring-api` |
| Estado | En curso (9/11) |
| Depende de | — |
| La necesitan | Specs 04 (clientes) y 05 (usuarios) del frontend (`4-react/specs/`) |
| Constitución | [`../constitution.md`](../constitution.md) |

## Resumen

Preparar la API para un dashboard: listados paginados y ordenables, filtros por
texto, estado, ciudad y fechas, búsqueda de usuarios, fechas de auditoría visibles
y una regla que impida dejar el sistema sin administradores.

## Contexto

Hoy `GET /api/customers` y `GET /api/users` devuelven la lista completa (`List`), los
DTO no exponen `createdAt` / `updatedAt` (no se puede filtrar por fecha), los
usuarios no se pueden buscar y solo se impide que un admin se borre, se deshabilite
o se quite ADMIN a sí mismo. Como el rol viaja en un JWT stateless de 15 minutos,
dos administradores pueden degradarse entre sí y dejar el sistema sin ADMIN.

## Historias de usuario

- **HU-01-1** Como usuario del dashboard quiero ver los clientes por páginas y ordenarlos por columna, para no cargar cientos de filas a la vez.
- **HU-01-2** Como usuario quiero filtrar clientes por texto, estado, ciudad, fecha de nacimiento y fecha de alta, para encontrar rápido un grupo concreto.
- **HU-01-3** Como usuario quiero exportar exactamente lo que filtré (todas las páginas), para trabajar los datos fuera del sistema.
- **HU-01-4** Como ADMIN quiero buscar usuarios por email y filtrarlos por rol y estado, para administrarlos aunque sean muchos.
- **HU-01-5** Como ADMIN quiero ver cuándo se creó y se modificó cada registro, para auditar cambios.
- **HU-01-6** Como responsable del sistema quiero que siempre quede al menos un administrador habilitado, para no perder el acceso de administración.

## Criterios de aceptación

**CA-01-1 Listado paginado de clientes**
- Dado que hay 20 clientes, cuando pido `GET /api/customers?page=0&size=5&sort=lastName,desc` con `Accept: application/json`, entonces recibo 200 con `content` de 5 clientes ordenados por apellido descendente y `page` = `{size: 5, number: 0, totalElements: 20, totalPages: 4}`.
- Cuando no envío `page` ni `size`, entonces recibo la página 0 de 10 elementos ordenada por `id`.
- Cuando pido `size=1000`, entonces el tamaño aplicado es 100.

**CA-01-2 Orden permitido**
- Cuando pido `sort=password` (o cualquier campo fuera de la lista permitida), entonces recibo 400 `ProblemDetail` con un `detail` que indica los campos permitidos.

**CA-01-3 Filtros de clientes**
- Cuando combino `search`, `active`, `city`, `birthFrom`, `birthTo`, `createdFrom` y `createdTo`, entonces solo recibo clientes que cumplen **todos** los filtros enviados.
- Los rangos son inclusivos: `createdTo=2026-11-19` incluye clientes creados durante todo ese día.
- Cuando `birthFrom` es posterior a `birthTo` (o `createdFrom` a `createdTo`), entonces recibo 400.
- Cuando envío una fecha con formato inválido (`birthFrom=ayer`), entonces recibo 400.

**CA-01-4 Exportación intacta**
- Cuando pido `GET /api/customers?city=santiago&format=csv` (o `xlsx`, `pdf`, `xml`), entonces descargo **todos** los clientes filtrados (sin paginar), con el mismo formato de archivo que antes.
- Las URLs de exportación documentadas en el README siguen funcionando sin cambios.

**CA-01-5 Fechas de auditoría**
- Cuando leo un cliente o un usuario, entonces la respuesta incluye `createdAt` y `updatedAt` (este último puede ser `null`).
- Cuando envío `createdAt` en un POST o PUT, entonces el valor se ignora.

**CA-01-6 Usuarios paginados y filtrados**
- Cuando un ADMIN pide `GET /api/users?search=admin&role=ADMIN&enabled=true&page=0&size=10`, entonces recibe 200 con el mismo formato paginado y sin el campo `password`.
- Cuando pide `role=FOO`, entonces recibe 400.

**CA-01-7 Último administrador**
- Dado que hay un solo ADMIN habilitado, cuando otro usuario con token ADMIN aún vigente intenta borrarlo, degradarlo a USER o deshabilitarlo, entonces recibe 409 `ProblemDetail` con `title` "Operación no permitida" y `detail` "Debe quedar al menos un administrador habilitado", y el usuario no cambia.
- Dado que hay dos ADMIN habilitados, cuando uno degrada o borra al otro, entonces la operación funciona.
- Editar el email del último ADMIN, manteniendo rol y estado, funciona.
- Las reglas existentes sobre uno mismo siguen respondiendo con sus mensajes actuales.

## Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-01-01 | `CustomerDto` y `UserDto` exponen `createdAt` y `updatedAt` como solo lectura. |
| RF-01-02 | `GET /api/customers` en JSON devuelve una página (`content` + `page`) con parámetros `page` (desde 0), `size` y `sort` (`campo,asc|desc`, repetible). |
| RF-01-03 | Tamaño de página por defecto 10 y máximo 100. |
| RF-01-04 | El orden solo se acepta sobre una lista blanca de campos por recurso; otro campo responde 400. La paginación es estable (desempate por `id`). |
| RF-01-05 | Filtros de clientes `search`, `active`, `city`, `birthFrom`, `birthTo`, `createdFrom`, `createdTo`, combinados con AND, rangos inclusivos y validados (`from <= to`, formato ISO `yyyy-MM-dd`). |
| RF-01-06 | La exportación (`xml`, `csv`, `xlsx`, `pdf`) conserva sus URLs y exporta todo el resultado filtrado y ordenado, sin paginar. |
| RF-01-07 | `GET /api/users` devuelve una página con filtros `search` (email), `role` y `enabled`, y orden por lista blanca. |
| RF-01-08 | No se puede borrar, degradar ni deshabilitar al último ADMIN habilitado (409). |
| RF-01-09 | `README.md` y `CLAUDE.md` documentan el nuevo contrato, el cambio incompatible y la regla del último admin. |

## Requisitos no funcionales

| ID | Requisito |
|---|---|
| RNF-01-01 | El conteo y el filtrado se hacen en la base de datos (Specifications + `Pageable`), no en memoria. |
| RNF-01-02 | Todos los errores nuevos son `ProblemDetail` con mensajes en español. |
| RNF-01-03 | Los tests existentes siguen pasando (ajustados solo donde cambia el contrato). |

## Fuera de alcance

- Crear, editar o borrar roles: el rol sigue siendo el enum `USER` / `ADMIN`, uno por usuario.
- Exportación de usuarios.
- CORS (el frontend usa el mismo origen).
- Endpoint para cambiar la contraseña propia o para registrarse.

## Cambio incompatible

Los listados JSON pasan de `List` a objeto paginado. El único consumidor es el
frontend `4-react`, que se construye después de esta spec.

## Preguntas abiertas

- ~~¿Se reparte `created_at` en el seed (`data.sql`) para poder probar el filtro por fecha de alta?~~ Resuelta: sí (A-07). Las bases nuevas reciben fechas de alta repartidas en 120 días; el README trae un `UPDATE` para bases existentes.
