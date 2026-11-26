# Spec 12 — Filtros del dashboard: cliente y rango de fechas

| | |
|---|---|
| Fase | L |
| Repo | `3-spring-api` (L-01, L-04) y `4-react` (L-02 … L-04) |
| Estado | Pendiente |
| Depende de | Spec 11 (dashboard de facturación) |
| La necesitan | — |
| Constitución | [backend](../constitution.md) · [frontend](../../../4-react/specs/constitution.md) |

## Resumen

La sección «Facturación» del resumen (spec 11) siempre muestra todas las facturas de los últimos
12 meses. Esta fase agrega dos filtros que cambian **toda** la sección a la vez:

1. **Cliente**, con el mismo autocompletado de la nueva factura (nombre, apellido o email): el balance, los gráficos y los totales pasan a ser los de ese cliente.
2. **Rango de fechas de emisión** (desde / hasta), con atajos para los rangos más usados: «Este mes», «Últimos 3 meses», «Este año» y «Últimos 12 meses» (el de siempre).

Los filtros viven en la URL (como en los listados): un enlace copiado reproduce el resumen
filtrado. Los enlaces de la sección (leyendas, barras, estados, ranking) llevan los mismos filtros
al listado de facturas. La sección «Cartera» (cifras de clientes) no cambia.

## Historias de usuario

- **HU-12-1** Como USER o ADMIN quiero ver el balance y los gráficos de un solo cliente, buscándolo por nombre, apellido o email.
- **HU-12-2** Como USER o ADMIN quiero acotar el resumen a un rango de fechas (p. ej. este año o el último trimestre).
- **HU-12-3** Como USER o ADMIN quiero que, al abrir un listado desde el resumen filtrado, el listado traiga los mismos filtros.

## Criterios de aceptación

**CA-12-1 API**
- `GET /api/invoices/stats` acepta `customerId`, `issuedFrom` e `issuedTo` (opcionales, combinables). Todos los datos de la respuesta (totales, por estado, vencidas, por mes y top clientes) respetan los filtros.
- Por mes: sin rango, los últimos 12 meses (como hoy); con rango, un elemento por mes desde el mes de `issuedFrom` hasta el de `issuedTo` (solo «desde»: hasta el mes actual; solo «hasta»: los 12 meses que terminan en ese mes). Meses sin facturas en 0.
- 400 en español si «desde» es posterior a «hasta» o si el rango abarca más de 24 meses; un `customerId` o una fecha con formato inválido dan 400 por campo, como en los listados.
- Un cliente inexistente o eliminado no es un error: la respuesta queda en cero (como un filtro sin resultados).

**CA-12-2 Filtros en el resumen**
- Sobre la sección «Facturación» hay una barra de filtros: «Cliente» (autocompletado; elegido, se muestra como chip «Cliente: Nombre Apellido» con × para quitarlo), «Emisión» (desde / hasta, con la validación de siempre) y los atajos de rango (el activo se marca). Con algún filtro aplicado, «Limpiar filtros».
- Al cambiar un filtro, la sección se actualiza mostrando los datos anteriores atenuados hasta que llegan los nuevos (sin parpadeo ni esqueleto).
- La URL guarda los filtros (`/?customerId=3&issuedFrom=2026-01-01&issuedTo=2026-11-30`); recargar o abrir el enlace muestra lo mismo; el botón atrás vuelve al filtro anterior. Un parámetro inválido se ignora.
- El título del gráfico mensual describe el rango («Por mes, enero a noviembre de 2026») y el resumen dice qué se está viendo («Facturado a Ana Pérez entre el 01-01-2026 y el 30-11-2026»).
- Con cliente elegido, el ranking de clientes se oculta (sería una sola fila).
- Sin resultados con los filtros: estado vacío «No hay facturas con estos filtros» con «Limpiar filtros» (no el «Todavía no hay facturas» de la base vacía).
- Los enlaces de la sección agregan los filtros al listado: «Pendiente» → `/invoices?status=PENDING&customerId=3&issuedFrom=…&issuedTo=…`; una barra mensual usa el mes (acotado al rango) y el cliente.
- Accesible con teclado y lector de pantalla; se ve bien en ambos temas y desde 375 px (los filtros se apilan).

## Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-12-01 | `InvoiceStatsFilter` (`customerId`, `issuedFrom`, `issuedTo`) en `GET /api/invoices/stats`, con validación de rango (orden y máximo de 24 meses). |
| RF-12-02 | Agregaciones con filtros opcionales sin `@Query` con parámetros nulos: Criteria API en un fragmento de repositorio que reutiliza `InvoiceSpecifications.hasCustomer` / `issuedBetween`. |
| RF-12-03 | Meses de la serie mensual según el rango (o los 12 últimos sin rango). |
| RF-12-04 | Frontend: filtros del resumen en la URL con esquema Zod y `useInvoiceStats(filters)` con datos previos mientras carga. |
| RF-12-05 | Frontend: barra de filtros (autocompletado reutilizado, rango de emisión, atajos, chip del cliente, limpiar) y textos que describen el filtro. |
| RF-12-06 | Frontend: enlaces de la sección con los filtros; ranking oculto con cliente; estado vacío con filtros. |
| RF-12-07 | Documentación: README y CLAUDE.md de ambos repos e índice del SDD. |

## Requisitos no funcionales

- **RNF-12-01** Constitución: consultas dinámicas con Specifications / Criteria, nunca `(:param is null or …)`.
- **RNF-12-02** Rendimiento: las estadísticas siguen calculándose en la base (agregados), una consulta por pieza.
- **RNF-12-03** Interfaz: sin piezas visuales nuevas; reutiliza `CustomerCombobox`, `DateRangeField`, el chip del listado de facturas y la dirección visual aprobada en K-07.

## Fuera de alcance

- Filtrar la sección «Cartera».
- Filtro por estado en el resumen (los estados ya son parte de los gráficos).
- Comparar dos periodos o dos clientes.
- Agrupar por año para rangos de más de 24 meses.

## Preguntas abiertas

- Ninguna: los atajos de rango y el máximo de 24 meses son propuesta de esta spec; se ajustan si el usuario lo pide al revisarla.
