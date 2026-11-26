# Plan técnico — Spec 12

Cómo se implementa [`spec.md`](spec.md) respetando las constituciones del
[backend](../constitution.md) y del [frontend](../../../4-react/specs/constitution.md).

## 1. Contrato

`GET /api/invoices/stats?customerId=&issuedFrom=&issuedTo=` (USER y ADMIN, sin cambios de seguridad).
La respuesta es el mismo `InvoiceStatsDto` de la spec 11; solo cambian los datos.

| Caso | `monthly` |
|---|---|
| Sin `issuedFrom` ni `issuedTo` | 12 meses hasta el actual (como hoy) |
| Solo `issuedFrom` | del mes de `issuedFrom` al mes actual |
| Solo `issuedTo` | los 12 meses que terminan en el mes de `issuedTo` |
| Ambos | del mes de `issuedFrom` al de `issuedTo` |

Errores (400, `IllegalArgumentException` → «Solicitud inválida»): «desde» posterior a «hasta»;
más de 24 meses en la serie (p. ej. solo `issuedFrom` de hace 3 años). Formato inválido → 400 por
campo (binding del record, como `InvoiceFilter`).

Ejemplos: `?customerId=1`, `?issuedFrom=2026-01-01&issuedTo=2026-11-30`, los tres juntos.

## 2. Backend

| Pieza | Cómo |
|---|---|
| `dto/InvoiceStatsFilter` | `record(Long customerId, @DateTimeFormat(iso = DATE) LocalDate issuedFrom, … issuedTo)` + `empty()`. Como `InvoiceFilter`: no es un DTO de recurso, se enlaza por constructor y `@ParameterObject` lo muestra en Swagger. |
| `repositories/InvoiceStatsRepository` + `InvoiceStatsRepositoryImpl` | Fragmento de repositorio (`InvoiceRepository extends …, InvoiceStatsRepository`) con `EntityManager` y Criteria API: `totalsByStatus(spec)`, `totalsByMonth(spec, statuses)`, `dueBefore(spec, status, today)`, `topCustomers(spec, statuses, limit)`. El `Specification<Invoice>` (filtro) se aplica con `spec.toPredicate(root, query, cb)` y se combina con los predicados propios; `group by` y `sum` en Criteria. Devuelven records internos del paquete (`StatusTotal`, …) en vez de las interfaces de proyección (Criteria con `cb.construct`). `@SQLRestriction` se aplica igual (Criteria es HQL). |
| `InvoiceRepository` | Se quitan las cuatro `@Query` de estadísticas (las reemplaza el fragmento). |
| `InvoiceSpecifications.forStats(filter)` | `Specification.allOf(hasCustomer(…), issuedBetween(…))`. |
| `InvoiceServiceImpl.stats(filter)` | Valida el rango, calcula `firstMonth` / `lastMonth` según la tabla, pide la serie entre `firstMonth.atDay(1)` y `lastMonth.atEndOfMonth()` **y** dentro del filtro, y completa los meses con 0. |
| `InvoiceController.stats(@ParameterObject InvoiceStatsFilter filter)` | — |
| Tests | Service con Mockito (los 4 casos de meses, 24 meses, rango invertido, el filtro llega al repositorio); controller (`?customerId=x` → 400 por campo, params enlazados); y un test de integración de las consultas Criteria con la base local no es necesario: se verifica con curl contra SQL directo, como en K-03. |

Implementado en L-01: las filas pasaron de interfaces de proyección a **records** en `InvoiceStatsViews` (Criteria los arma con `cb.construct`, que necesita constructor: tipos `Long` / `Integer` / `BigDecimal` como los devuelve la base); año y mes con `cb.extract(LocalDateField.YEAR/MONTH, …)` (JPA 3.2); `topCustomers` con `setMaxResults`. `InvoiceServiceImpl.statsMonths(filter, today)` (estática, probada sola) arma la serie y valida. Verificado con curl contra SQL directo en la base local: sin filtro (igual que la spec 11), por cliente, por mes, cliente + desde, cliente inexistente (ceros) y rango invertido (400).

Decisión: **Criteria en un fragmento** y no `@Query` con `(:customerId is null or …)`, que la constitución prohíbe (en PostgreSQL falla con parámetros nulos) y no `JpaSpecificationExecutor`, que no hace `group by`. Reutilizar las Specifications de los listados asegura que «Pendiente en el resumen» y «el listado filtrado por Pendiente» cuenten lo mismo.

## 3. Frontend

| Pieza | Cómo |
|---|---|
| `schemas/invoice.ts` | `statsFiltersSchema` (URL): `customerId` (entero positivo), `issuedFrom`, `issuedTo` (fechas ISO), cada uno con `.catch(undefined)`. |
| `hooks/useStatsFilters.ts` | Lee y escribe `useSearchParams` del resumen (`setFilters(patch)`, `clearFilters()`, `hasFilters`, `resetKey`). No reutiliza `useUrlFilters` porque ese hook supone página y búsqueda de un listado. |
| `services/invoiceService.stats(filters)` + `useInvoiceStats(filters)` | Query string con `toQuery`; clave `invoiceKeys.stats(filters)` (sigue bajo `all`); `placeholderData: keepPreviousData` y `isPlaceholderData` → sección atenuada (`opacity-60`, `aria-busy`). |
| `lib/statsRanges.ts` | Atajos puros: `thisMonth`, `last3Months`, `thisYear`, `last12Months` (este último = sin rango). Calculan `{ issuedFrom, issuedTo }` desde hoy (en el handler, no en el render); `activePreset(filters, today)` marca el atajo activo. |
| (L-02, implementado) | `statsFiltersSchema` descarta un rango invertido entero (en vez de enviarlo y recibir 400); los atajos solo fijan «desde» (`issuedFrom` del 1 del mes; «Últimos 12 meses» = sin rango) y la API completa hasta el mes actual. En enero «Este mes» y «Este año» coinciden: se marca el primero. |
| `components/dashboard/StatsFilters.tsx` | Fila de filtros: chip del cliente o `CustomerCombobox` (al elegir, `setFilters({ customerId })`; el nombre del chip con `useCustomer`), `DateRangeField` «Emisión», los atajos como botones (`aria-pressed` en el activo) y «Limpiar filtros». En móvil se apilan. `key={resetKey}` en los campos para descartar lo que se escribía al limpiar. |
| `BillingSection` | Recibe los filtros: texto de qué se ve, ranking oculto con cliente, estado vacío con filtros («No hay facturas con estos filtros» + «Limpiar filtros»). |
| `BalanceBar`, `MonthlyChart`, `StatusChart`, `TopCustomers` | Prop `filters` para armar los enlaces con `toQuery({ status, customerId, issuedFrom, issuedTo })`; `MonthlyChart` acota el mes al rango (`max(monthStart, issuedFrom)` / `min(monthEnd, issuedTo)`), muestra todos los meses que devuelve la API (en móvil, los 6 últimos solo si no hay rango) y titula con el rango. |

## 4. Decisiones

- **Filtros en la URL** (constitución §2): compartibles y con historial, como los listados.
- **Datos previos atenuados al filtrar** en vez de esqueleto: el usuario ve qué cambió.
- **24 meses como máximo**: más barras no se leen; agrupar por año queda fuera de alcance.
- **Cliente eliminado o inexistente → ceros**, no 404: es un filtro, igual que en el listado.
- **«Últimos 12 meses» = sin rango**: el resumen por defecto no escribe parámetros en la URL.

## 5. Riesgos

- Criteria con `group by` sobre expresiones (`extract(year …)`): se verifica contra SQL directo (L-01).
- La fecha de «hoy» de los atajos es la del navegador y la de las vencidas, la del servidor (`America/Santiago`): coinciden para usuarios en Chile.

## 6. Trazabilidad

| Requisito | Tareas |
|---|---|
| RF-12-01, RF-12-02, RF-12-03, RNF-12-01, RNF-12-02 | L-01 |
| RF-12-04 | L-02 |
| RF-12-05, RF-12-06, RNF-12-03 | L-03 |
| RF-12-07 | L-04 |
