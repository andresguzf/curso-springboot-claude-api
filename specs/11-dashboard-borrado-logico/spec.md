# Spec 11 — Facturas por cliente, alta con autocompletado, dashboard de facturación y borrado lógico

| | |
|---|---|
| Fase | K |
| Repo | `3-spring-api` (K-01 … K-03, K-09) y `4-react` (K-04 … K-09) |
| Estado | En curso |
| Depende de | Spec 10 (facturación) |
| La necesitan | — |
| Constitución | [backend](../constitution.md) · [frontend](../../../4-react/specs/constitution.md) |

## Resumen

Cuatro cambios pedidos sobre la facturación de la spec 10:

1. **Facturas desde el listado de clientes**: un icono en las acciones de cada fila abre el listado de facturas de ese cliente.
2. **Dashboard de facturación**: el resumen (`/`) deja de ser solo una lista de cifras y suma una sección visual de facturación: tarjetas con balances, gráficos (facturación por mes, distribución por estado) y los clientes que más facturan.
3. **Nueva factura desde el listado de facturas**: botón «Nueva factura» en `/invoices`, con el cliente elegido en un campo de **autocompletado** que busca por nombre, apellido o email mientras se escribe.
4. **Borrado lógico de clientes**: eliminar un cliente ya no se bloquea si tiene facturas (409 de la spec 10). El cliente **y sus facturas** se marcan como eliminados (`deleted_at`) y desaparecen de la API y del dashboard; nada se borra físicamente y las líneas se quedan con su factura. Antes de eliminar, el modal avisa cuántas facturas se eliminarán con él. Eliminar **una factura** sigue siendo físico (sus líneas se borran en cascada).

Decisiones del usuario (25-11-2026): facturas del cliente también con borrado lógico; el email de un cliente eliminado se **libera** (se puede volver a registrar); eliminar una factura sola sigue siendo físico; los gráficos con **Recharts**.

## Historias de usuario

- **HU-11-1** Como USER o ADMIN quiero abrir las facturas de un cliente directamente desde su fila del listado de clientes.
- **HU-11-2** Como USER o ADMIN quiero ver en el resumen cuánto se ha facturado, cuánto está pagado, pendiente y vencido, cómo evoluciona por mes y qué clientes facturan más.
- **HU-11-3** Como ADMIN quiero crear una factura desde el listado de facturas buscando al cliente por nombre, apellido o email mientras escribo.
- **HU-11-4** Como ADMIN quiero eliminar un cliente aunque tenga facturas, sabiendo antes cuántas se eliminarán con él, sin perder los datos en la base.

## Criterios de aceptación

**CA-11-1 Icono de facturas en el listado de clientes**
- Cada fila del listado de clientes tiene, para USER y ADMIN, un icono «Ver facturas de Nombre Apellido» que navega a `/invoices?customerId={id}` (el chip «Cliente: …» de la spec 10 muestra el filtro).
- La columna de acciones existe ahora también para USER (solo con ese icono); la tabla sigue sin scroll horizontal en escritorio.

**CA-11-2 Dashboard de facturación**
- `GET /api/invoices/stats` (USER y ADMIN) devuelve: número de facturas, facturado (pagadas + pendientes), pagado, pendiente, vencido (pendientes con vencimiento anterior a hoy) y su cantidad; conteo e importe por estado; facturación de los últimos 12 meses por mes de emisión (pagado y pendiente, meses sin facturas en 0); y los 5 clientes con más facturado. Las anuladas no suman a los importes; los clientes y facturas eliminados no cuentan.
- El resumen muestra, para ambos roles, las cifras de clientes que ya tenía y una sección «Facturación» con: tarjetas (facturado, pagado, pendiente, vencido), un gráfico de barras apiladas por mes (pagado / pendiente), un gráfico de dona por estado y la lista de los 5 clientes con más facturado (cada uno enlaza a sus facturas).
- Cada cifra o elemento que corresponde a un listado enlaza a él filtrado (p. ej. «Pendiente» → `/invoices?status=PENDING`).
- Los gráficos tienen alternativa accesible (resumen en texto o tabla para lectores de pantalla), usan los tokens de color del tema y se ven bien en tema claro y oscuro, desde 375 px.
- Estados de carga (esqueletos) y error con «Reintentar»; sin facturas, un estado vacío que invita a crear la primera (ADMIN).
- Recharts se carga solo en la página del resumen (chunk propio).

**CA-11-3 Nueva factura con autocompletado**
- En `/invoices`, el ADMIN ve «Nueva factura»; abre `/invoices/new` sin cliente.
- En el formulario, el campo «Cliente» es un **combobox** accesible: al escribir (con pausa) busca en `/api/customers?search=` y lista hasta 8 coincidencias con nombre, apellido y email; se navega con ↑ ↓, Enter elige, Esc cierra; con el puntero se elige con un clic. Sin coincidencias: «Sin clientes con ese texto».
- El cliente es obligatorio («Elige un cliente»); elegido, se muestra con un botón para cambiarlo.
- Desde la ficha del cliente (`/invoices/new?customerId=`) el cliente llega ya elegido, como en la spec 10. En la edición el cliente sigue fijo.

**CA-11-4 Borrado lógico de clientes**
- `DELETE /api/customers/{id}` responde 204 aunque el cliente tenga facturas: el cliente y todas sus facturas quedan con `deleted_at` (fecha y hora); sus líneas no cambian. 404 si no existe o ya estaba eliminado.
- Un cliente o una factura eliminados no aparecen en ningún listado, detalle, exportación ni estadística (404 en el detalle).
- Se puede crear un cliente nuevo con el email de uno eliminado (la unicidad aplica solo a los no eliminados).
- Los números de factura no se reutilizan: el correlativo considera también las facturas eliminadas.
- En el dashboard, el modal de eliminar cliente dice cuántas facturas se eliminarán con él («También se eliminarán sus 3 facturas.»); sin facturas, el texto de siempre.
- `DELETE /api/invoices/{id}` no cambia: borrado físico con sus líneas en cascada.

## Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-11-01 | Columna `deleted_at` (`timestamp`, nula) en `customers` e `invoices`; las entities la mapean y filtran con `@SQLRestriction("deleted_at IS NULL")`. |
| RF-11-02 | `CustomerServiceImpl.delete`: marca el cliente y sus facturas (actualización masiva) con la misma fecha; deja de lanzar el 409 de la spec 10. |
| RF-11-03 | Email único solo entre clientes no eliminados: índice único parcial `customers_email_active_key ON customers (email) WHERE deleted_at IS NULL` en lugar de la restricción `ukrfbvkrffamfql7cjmen8v976v`. |
| RF-11-04 | Numeración de facturas que considera las eliminadas (consulta nativa sin el filtro). |
| RF-11-05 | Migración en Supabase (producción) con las columnas y el índice, aplicada con el MCP de Supabase y verificada con el perfil `prod`. |
| RF-11-06 | `GET /api/invoices/stats` con `InvoiceStatsDto` (totales, por estado, por mes, top clientes). |
| RF-11-07 | Frontend: icono «Ver facturas» en las acciones del listado de clientes (USER y ADMIN). |
| RF-11-08 | Frontend: modal de eliminar cliente con el aviso de cuántas facturas se eliminarán. |
| RF-11-09 | Frontend: combobox de clientes con autocompletado y «Nueva factura» en `/invoices`. |
| RF-11-10 | Frontend: sección «Facturación» del resumen con tarjetas, gráficos (Recharts) y top clientes, con dirección visual aprobada. |
| RF-11-11 | Documentación: README y CLAUDE.md de ambos repos, constituciones e índice del SDD. |

## Requisitos no funcionales

- **RNF-11-01** Integridad: el borrado lógico es atómico (cliente y facturas en la misma transacción) y no se pierde ningún dato.
- **RNF-11-02** Producción: la migración se aplica antes de desplegar el código nuevo; el perfil `prod` (`validate`) arranca contra Supabase con el esquema migrado; el código anterior sigue funcionando con las columnas nuevas (son nulas y no las usa).
- **RNF-11-03** Rendimiento: las estadísticas se calculan en la base con agregados (no se traen todas las facturas al frontend ni al service); Recharts va en su propio chunk.
- **RNF-11-04** Accesibilidad: combobox según el patrón ARIA (`role="combobox"`, `aria-expanded`, `aria-activedescendant`, `role="listbox"`); gráficos con alternativa en texto; contraste en ambos temas.
- **RNF-11-05** Interfaz: mismos tokens, componentes y reglas que el resto del dashboard; la dirección visual de la sección nueva se propone con la skill `frontend-design` y la aprueba el usuario antes de codificarla.

## Fuera de alcance

- Restaurar (deshacer) un cliente eliminado y una papelera en el dashboard.
- Borrado lógico de una factura sola.
- Filtros de fechas o de estado dentro del dashboard; exportar los gráficos.
- Crear un cliente nuevo desde el combobox de la factura.

## Preguntas abiertas

- Ninguna: las cuatro decisiones las tomó el usuario (ver Resumen).
