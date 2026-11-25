# Spec 10 — Facturación de clientes

| | |
|---|---|
| Fase | J |
| Repo | `3-spring-api` (J-01 … J-03, J-09) y `4-react` (J-04 … J-09) |
| Estado | Pendiente |
| Depende de | Spec 01 (backend) y specs 04, 07 y 08 (frontend) |
| La necesitan | — |
| Constitución | [backend](../constitution.md) · [frontend](../../../4-react/specs/constitution.md) |

## Resumen

Cada cliente puede tener **facturas**, cada una con sus **líneas de detalle**. Las tablas ya
existen en Supabase (las creó una migración, no Hibernate) y tienen datos de ejemplo:

- `invoices`: número único (`FAC-2026-0001`), cliente, fecha de emisión y de vencimiento,
  subtotal, IVA, total, estado (`PENDING`, `PAID`, `CANCELLED`), notas y fechas de auditoría.
- `invoice_items`: líneas numeradas (`line_number` único por factura) con descripción, cantidad,
  precio unitario y `line_total`, una **columna generada** por PostgreSQL
  (`round(quantity * unit_price, 2)`). Se borran en cascada con su factura.

Esta fase expone las facturas en la API y en el dashboard:

- **ADMIN** administra las facturas: las lista, las crea desde la ficha de un cliente, las edita
  y las elimina.
- **USER** solo las consulta: ve las facturas de cada cliente en su ficha, el listado general y
  el detalle de cada factura, sin acciones de escritura.

El backend calcula los importes (subtotal, IVA del 19 % y total) y asigna el número de factura:
el cliente de la API solo envía las líneas.

## Historias de usuario

- **HU-10-1** Como USER quiero ver las facturas de un cliente desde su ficha, con su estado y su total, para responder consultas sin poder modificarlas.
- **HU-10-2** Como USER o ADMIN quiero un listado de todas las facturas con búsqueda, filtros (estado, cliente, fecha de emisión), orden y paginación.
- **HU-10-3** Como USER o ADMIN quiero ver el detalle de una factura: datos del cliente, fechas, estado, notas, líneas e importes.
- **HU-10-4** Como ADMIN quiero crear una factura para un cliente agregando líneas (descripción, cantidad y precio) y ver el subtotal, el IVA y el total antes de guardar.
- **HU-10-5** Como ADMIN quiero editar una factura (fechas, estado, notas y líneas) y eliminarla con una confirmación.
- **HU-10-6** Como ADMIN no quiero poder eliminar por error un cliente que tiene facturas.

## Criterios de aceptación

**CA-10-1 Lectura por rol**
- Dado un USER o un ADMIN con sesión, cuando pide `GET /api/invoices` o `GET /api/invoices/{id}`, entonces recibe 200.
- Dado un USER, cuando intenta `POST`, `PUT` o `DELETE` sobre `/api/invoices/**`, entonces recibe 403 «Acceso denegado» y nada cambia.
- Sin sesión, cualquier ruta de `/api/invoices` responde 401.
- En el dashboard, el USER no ve los botones de crear, editar ni eliminar facturas, y `/invoices/new` o `/invoices/{id}/edit` lo llevan a «Sin permiso».

**CA-10-2 Listado**
- `GET /api/invoices` devuelve una página `{content, page}` como clientes y usuarios, ordenada por defecto por fecha de emisión descendente (las más recientes primero).
- Filtros opcionales: `search` (contenido en el número de factura o en el nombre, apellido o email del cliente), `customerId`, `status`, `issuedFrom` e `issuedTo` (inclusivos). Un rango con «desde» posterior a «hasta», un estado inexistente o un orden fuera de la lista blanca responden 400.
- Cada factura del listado trae el nombre del cliente y sus líneas, sin consultas N+1.
- En el dashboard, «Facturas» aparece en el menú para ambos roles; la tabla muestra número, cliente, emisión, vencimiento, estado y total; los filtros viven en la URL y en el sidebar, como en clientes.

**CA-10-3 Facturas en la ficha del cliente**
- La ficha del cliente muestra una sección «Facturas» con sus facturas (las más recientes primero), un enlace al listado filtrado por ese cliente y, para ADMIN, el botón «Nueva factura».
- Sin facturas, la sección muestra un estado vacío (con «Nueva factura» solo para ADMIN).

**CA-10-4 Detalle**
- `GET /api/invoices/{id}` devuelve la factura con el cliente, las líneas ordenadas por `lineNumber` y los importes; 404 si no existe.
- La página de detalle muestra cabecera (número, cliente con enlace a su ficha, emisión, vencimiento, estado, notas), la tabla de líneas y el resumen subtotal / IVA 19 % / total, más las fechas de auditoría.

**CA-10-5 Alta (ADMIN)**
- Dado un cliente existente, cuando el ADMIN envía la factura con al menos una línea, entonces recibe 201 con `Location`, un número asignado (`FAC-<año de emisión>-<NNNN>`, correlativo por año), las líneas numeradas 1…n en el orden enviado y los importes calculados por el backend.
- Los importes y el número que envíe el cliente de la API se ignoran (son de solo lectura).
- Sin estado, la factura queda `PENDING`.
- Validación (400 con `errors` por campo, mensajes en español): cliente obligatorio, emisión obligatoria, vencimiento no anterior a la emisión, notas ≤ 255, al menos una línea y como máximo 100; por línea, descripción obligatoria (≤ 255), cantidad > 0 (hasta 8 enteros y 2 decimales) y precio ≥ 0 (hasta 10 enteros y 2 decimales).
- Un cliente inexistente responde 404.
- En el dashboard, «Nueva factura» abre el formulario con el cliente fijo; las líneas se agregan y quitan, el subtotal, el IVA y el total se recalculan mientras se escribe, y al guardar se vuelve al detalle de la factura con el flash «Factura «FAC-…» creada».

**CA-10-6 Edición (ADMIN)**
- `PUT /api/invoices/{id}` reemplaza fechas, estado, notas y líneas, recalcula los importes y conserva el número; 404 si no existe.
- Cambiar de 3 a 2 líneas, de 2 a 4 o reordenarlas no choca con la restricción única `(invoice_id, line_number)`.
- En el dashboard el formulario de edición llega precargado y, al guardar, vuelve al detalle con el flash «Factura «FAC-…» actualizada».

**CA-10-7 Eliminación (ADMIN)**
- `DELETE /api/invoices/{id}` responde 204 y borra también sus líneas; 404 si no existe.
- En el dashboard, «Eliminar» (listado, detalle y ficha del cliente) abre el `ConfirmDialog` con el número de la factura; al confirmar se muestra el flash «Factura eliminada».

**CA-10-8 Cliente con facturas**
- Dado un cliente con facturas, cuando el ADMIN intenta eliminarlo, entonces recibe 409 «Operación no permitida» con el detalle «No se puede eliminar el cliente: tiene N factura(s)», y el modal de confirmación del dashboard muestra ese mensaje.

## Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-10-01 | Entities `Invoice` e `InvoiceItem` y enum `InvoiceStatus` mapeados **exactamente** al esquema de Supabase (nombres, tipos, largos, precisión, columna generada, índices y restricciones con sus nombres), para que el perfil `prod` (`ddl-auto=validate`) arranque y el de desarrollo (`update`) no los altere. |
| RF-10-02 | `InvoiceDto` e `InvoiceItemDto` (`record`) con Bean Validation en español; número, nombre del cliente, `lineNumber`, importes y fechas de auditoría de solo lectura. `InvoiceFilter` para los query params del listado. |
| RF-10-03 | `InvoiceService` + `InvoiceServiceImpl`: listado paginado con `InvoiceSpecifications` y `Pageables.checked`, detalle, alta, edición y eliminación; cálculo de importes con IVA 19 % y redondeo a 2 decimales; numeración correlativa por año; actualización de líneas sin violar la restricción única. |
| RF-10-04 | `InvoiceController` en `/api/invoices` (`GET` paginado, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`) y reglas en `SecurityConfig`: `GET` → USER/ADMIN, resto → ADMIN. |
| RF-10-05 | `CustomerServiceImpl.delete` rechaza con `OperationNotAllowedException` (409) un cliente con facturas. |
| RF-10-06 | Seed local: `data.sql` agrega facturas de ejemplo solo si la tabla `invoices` está vacía (en Supabase no inserta nada). |
| RF-10-07 | Frontend: esquemas Zod, `invoiceService`, `invoiceKeys` y hooks de TanStack Query; formato de montos en pesos chilenos (`es-CL`). |
| RF-10-08 | Frontend: página `/invoices` con tabla, búsqueda, filtros en el sidebar, orden y paginación, y la entrada «Facturas» en el menú. |
| RF-10-09 | Frontend: página `/invoices/:id` (detalle) y sección «Facturas» en la ficha del cliente. |
| RF-10-10 | Frontend: formulario de alta (`/invoices/new?customerId=`) y edición (`/invoices/:id/edit`) con líneas dinámicas y totales en vivo, solo ADMIN. |
| RF-10-11 | Frontend: eliminación de facturas con `ConfirmDialog` y mensaje del 409 al eliminar un cliente con facturas. |
| RF-10-12 | Documentación: README y CLAUDE.md de ambos repos, constitución del backend (reglas de acceso) e índice del SDD. |

## Requisitos no funcionales

- **RNF-10-01** Seguridad: las reglas de acceso viven en `SecurityConfig` (no `@PreAuthorize`); las escrituras exigen CSRF; la UI solo anticipa los permisos.
- **RNF-10-02** Integridad: el backend es la única fuente de los importes y del número; los importes usan `BigDecimal` (nunca `double`) y el redondeo `HALF_UP`, igual que el `round()` de PostgreSQL.
- **RNF-10-03** Rendimiento: el listado no hace consultas N+1 (cliente con `@EntityGraph`, líneas con `@BatchSize`).
- **RNF-10-04** Compatibilidad: el contrato actual de clientes y usuarios no cambia, salvo el nuevo 409 al eliminar un cliente con facturas.
- **RNF-10-05** Interfaz: mismas piezas y reglas que clientes (tabla compacta con `minWidth`, `IconButton` / `IconLink`, flash, `ConfirmDialog`, tema oscuro, accesibilidad).

## Fuera de alcance

- Exportar facturas (PDF, Excel, CSV) y enviarlas por correo.
- Reasignar una factura a otro cliente desde el dashboard (la API lo permite con `customerId` en el `PUT`).
- Reglas de transición de estado (p. ej. impedir editar una factura pagada) y facturas «vencidas» calculadas.
- Tasas de IVA distintas del 19 %, descuentos, monedas distintas del peso chileno.
- Facturas en el resumen (`DashboardPage`).
- Migraciones con Flyway: el esquema de facturas ya existe en Supabase.

## Preguntas abiertas

- Ninguna. Si más adelante se necesita editar el número o la tasa de IVA, será otra fase.
