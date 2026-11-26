# Plan técnico — Spec 11

Cómo se implementa [`spec.md`](spec.md) respetando las constituciones del
[backend](../constitution.md) y del [frontend](../../../4-react/specs/constitution.md).

## 1. Borrado lógico (backend)

### Esquema

| Cambio | Supabase (producción) | Base local / desarrollo |
|---|---|---|
| `customers.deleted_at timestamp NULL` | migración (K-02) | Hibernate `update` la agrega |
| `invoices.deleted_at timestamp NULL` | migración (K-02) | Hibernate `update` la agrega |
| Email único solo entre no eliminados | migración: `DROP CONSTRAINT ukrfbvkrffamfql7cjmen8v976v` + `CREATE UNIQUE INDEX customers_email_active_key ON customers (email) WHERE deleted_at IS NULL` | las mismas dos sentencias, idempotentes (`IF EXISTS` / `IF NOT EXISTS`), al inicio de `data.sql` |

- La restricción vieja se llama igual en Supabase y en local (`ukrfbvkrffamfql7cjmen8v976v`, nombre que generó Hibernate para `email`; verificado con el MCP y con `psql`).
- JPA no puede declarar un índice parcial: `Customer.email` pierde `unique = true` (si no, `update` volvería a crear la restricción completa) y el índice lo crean la migración (prod) y `data.sql` (desarrollo, también una base nueva de Docker Compose).
- Migración de Supabase `soft_delete_customers_invoices` (MCP `apply_migration`, K-02), con `IF NOT EXISTS` porque un arranque de desarrollo contra Supabase puede haber agregado ya las columnas:

```sql
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE invoices  ADD COLUMN IF NOT EXISTS deleted_at timestamp;
ALTER TABLE customers DROP CONSTRAINT IF EXISTS ukrfbvkrffamfql7cjmen8v976v;
CREATE UNIQUE INDEX IF NOT EXISTS customers_email_active_key ON customers (email) WHERE deleted_at IS NULL;
```

### Código

| Pieza | Cómo |
|---|---|
| `models/Customer`, `models/Invoice` | Campo `deletedAt` (`@Column(name = "deleted_at")`) y `@SQLRestriction("deleted_at IS NULL")` en la clase: toda consulta de Hibernate (repositorios, Specifications, `@EntityGraph`, JPQL) excluye los eliminados sin tocar cada consulta. |
| `services/CustomerServiceImpl.delete` | `getOrThrow` (404 si no existe o ya está eliminado); `now = LocalDateTime.now()`; `invoiceRepository.softDeleteByCustomerId(id, now)`; `customer.setDeletedAt(now)`. Se quita la regla del 409 de la spec 10. Devuelve el número de facturas eliminadas (para el log; la API responde 204). |
| `repositories/InvoiceRepository` | `@Modifying @Query("update Invoice i set i.deletedAt = :now where i.customer.id = :customerId and i.deletedAt is null") int softDeleteByCustomerId(...)` (JPQL de actualización, no el patrón prohibido de parámetros nulos). Numeración: `@Query(value = "select max(invoice_number) from invoices where invoice_number like :prefix%", nativeQuery = true)` — nativa para **no** aplicar el `@SQLRestriction` y no reutilizar el número de una factura eliminada (el `UNIQUE` lo rechazaría con 409). Reemplaza a `findTopByInvoiceNumberStartingWith…`. |
| `CustomerRepository.existsByEmail` / `findByEmail` | Sin cambios: con `@SQLRestriction` ya miran solo los no eliminados, igual que el índice parcial. |
| `specs/constitution.md` | §3: el cliente con facturas se elimina con borrado lógico en cascada (reemplaza la regla de la spec 10). |

Decisión: `@SQLRestriction` + actualización explícita en vez de `@SoftDelete` de Hibernate: `@SoftDelete` convierte **todo** `delete` de la entity en lógico, y la eliminación de una factura sola debe seguir siendo física (decisión del usuario).

Riesgo: `@SQLRestriction` no se aplica a SQL nativo ni a `data.sql` (por eso el seed sigue usando `WHERE NOT EXISTS (SELECT 1 FROM customers)`, que cuenta también los eliminados: correcto, la base no está vacía).

### Resultado de la migración (K-02, 25-11-2026)

Aplicada con el MCP de Supabase (`apply_migration`, versión `20261125164514`, nombre `soft_delete_customers_invoices`) con el OK explícito del usuario al SQL de arriba. Verificado:

- Antes y después: 23 clientes, 7 facturas, 17 líneas, 3 usuarios; ningún `deleted_at` asignado; sin emails duplicados (condición para el índice único).
- Esquema: `customers.deleted_at` e `invoices.deleted_at` (`timestamp`); sin `ukrfbvkrffamfql7cjmen8v976v`; `customers_email_active_key` `UNIQUE … (email) WHERE (deleted_at IS NULL)`.
- `SPRING_PROFILES_ACTIVE=prod` contra Supabase arranca (`validate` OK); `update` en seco sin DDL; `./mvnw test` con el `.env` (Supabase) pasa y no cambia el esquema ni los datos.

Desde aquí vuelve a ser seguro arrancar en desarrollo y correr los tests contra Supabase.

## 2. Estadísticas (backend)

`GET /api/invoices/stats` (ya cubierto por `GET /api/invoices/**` → USER/ADMIN; la ruta literal gana a `/{id}`).

```json
{
  "invoiceCount": 7,
  "billed": 865370.00,          // pagadas + pendientes
  "paid": 543200.00,
  "pending": 322170.00,
  "overdue": 0.00,              // pendientes con dueDate < hoy
  "overdueCount": 0,
  "byStatus": [ { "status": "PENDING", "count": 3, "total": 322170.00 }, … los 3 estados ],
  "monthly": [ { "month": "2025-12", "paid": 0, "pending": 0 }, … 12 meses hasta el actual ],
  "topCustomers": [ { "customerId": 2, "customerName": "Ana Pérez", "invoiceCount": 2, "total": 279650.00 }, … hasta 5 ]
}
```

| Pieza | Cómo |
|---|---|
| `dto/InvoiceStatsDto` | `record` con records anidados `StatusTotal`, `MonthTotal`, `CustomerTotal` (solo respuesta, no lleva validación). |
| `repositories/InvoiceRepository` | (K-03: proyecciones por **interfaz** en `repositories/InvoiceStatsViews`, no `select new` de records anidados; top 5 con `Limit.of(5)`; año y mes con `extract(year/month from …)`). Consultas de agregación JPQL con proyecciones a records (`select new …(i.status, count(i), coalesce(sum(i.total), 0)) … group by i.status`; mensual con `year(i.issueDate)`, `month(i.issueDate)` desde una fecha; top 5 con `group by i.customer.id, i.customer.name, i.customer.lastName order by sum(i.total) desc` + `Limit`/`Pageable`). Respetan el `@SQLRestriction`. |
| `services/InvoiceService.stats()` | (K-03: `InvoiceServiceImpl` recibe un `Clock` en un segundo constructor para fijar "hoy" en los tests; Spring usa el que lleva `@Autowired`.) `@Transactional(readOnly = true)`; arma el DTO, completa los meses sin facturas con 0 y calcula `billed` y `overdue` (hoy = `LocalDate.now()`, zona del servidor `America/Santiago`). Anuladas fuera de los importes. |
| `controllers/InvoiceController` | `@GetMapping("/stats")`. |
| Tests | Service con Mockito (meses completos, anuladas excluidas, vencidas) y controller (`@WebMvcTest`: USER 200, sin sesión 401). |

## 3. Frontend

| Pieza | Cómo |
|---|---|
| `components/customers/CustomerTable.tsx` | La columna «Acciones» existe para todos: `IconLink` con `ReceiptText` («Ver facturas de Nombre Apellido» → `/invoices?customerId={id}`); editar y eliminar siguen solo para ADMIN. |
| `pages/CustomersPage.tsx` | Al abrir el modal de eliminar, `useInvoicesPage({ customerId, size: 1 })` da `page.totalElements`: descripción «También se eliminarán sus N facturas. Esta acción no se puede deshacer desde el dashboard.» (mientras carga, el texto genérico). Se quita el caso 409 de la spec 10. |
| `components/invoices/CustomerCombobox.tsx` | Combobox ARIA (input `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`, `aria-controls`, `aria-activedescendant`; `ul role="listbox"` con `li role="option"`). Texto con `useDebouncedText`; consulta `useQuery` con `customerService.page({ search, size: 8 })` y `keepPreviousData`, habilitada con 1+ caracteres. Teclado: ↑ ↓ mueven, Enter elige, Esc cierra; `onMouseDown` + `preventDefault` en las opciones para que el blur no cierre antes del clic. Elegido: muestra «Nombre Apellido · email» con «Cambiar». |
| `schemas/invoice.ts` | `invoiceFormSchema` suma `customerId: z.number({ error: 'Elige un cliente' }).int().positive()`; `INVOICE_FORM_FIELDS` incluye `customerId`. `toBody` y los hooks toman el cliente de los valores del formulario (desaparece `{ customerId, values }`). |
| `components/invoices/InvoiceForm.tsx` | Prop `customerName?` (fijo: ficha o edición) o, si no viene, el combobox dentro de un `Controller` de `customerId` (0 = sin elegir); el `ref` del `Controller` llega al input para que «Elige un cliente» reciba el foco al enviar (K-06). El cliente elegido se muestra en un input de solo lectura (la etiqueta sigue asociada) con «Cambiar»; `Input` acepta `ref` (`ComponentProps<'input'>`). |
| `pages/InvoicesPage.tsx` | «Nueva factura» (ADMIN) en el `PageHeader` → `/invoices/new`. `InvoiceCreatePage` sin `customerId` ya no muestra «Elige un cliente»: muestra el formulario con el combobox. |
| `services/invoiceService.ts` + `hooks/useInvoiceStats.ts` | `stats()` → `GET /api/invoices/stats` validado con `invoiceStatsSchema`; clave `invoiceKeys.stats` (bajo `invoiceKeys.all`, así las mutaciones de facturas y clientes la refrescan). |
| `components/dashboard/` | `StatCard` (balance con icono y enlace), `MonthlyChart` (barras apiladas pagado/pendiente, Recharts), `StatusChart` (dona), `TopCustomers` (lista con barra proporcional), `BillingSection` (orquesta y estados). Colores desde las variables CSS del tema (`var(--color-…)`); tooltips con `formatMoney`; `<figure>` + `<figcaption>` y una tabla `sr-only` con los mismos datos. |
| `pages/DashboardPage.tsx` | Cifras de clientes (compactadas en tarjetas) + `BillingSection`. |
| `vite.config.ts` | `recharts` (y sus dependencias) en un grupo de `codeSplitting` propio. |

### Dirección visual del resumen (K-07) — **aprobada por el usuario (25-11-2026)**

Hecha con la skill `frontend-design` sobre la dirección ya aprobada (`4-react/specs/02-base-frontend/design.md`: «el fichero», banda grafito, un solo acento, Atkinson Hyperlegible, radios por jerarquía). No se agregan fuentes ni colores de marca: el resumen tiene que verse de la misma familia que las tablas.

**Idea**: el resumen deja de ser una lista de cifras y abre con un **balance**: una sola barra horizontal, ancha como la página, que reparte lo facturado entre pagado y pendiente, con lo vencido marcado dentro de lo pendiente. Es lo que un administrativo mira primero («¿cuánto me deben?») y reemplaza la fila de cuatro tarjetas iguales con número grande, que es el recurso por defecto de cualquier dashboard. Esa barra es el único elemento llamativo; lo demás queda sobrio.

**Composición** (escritorio, contenido alineado a la izquierda, `max-w-screen-2xl`):

```
Resumen                                              (h1, como hoy)
La facturación y la cartera al día de hoy.

┌ Facturación ─────────────────────────────────────────────────────────────┐  panel (radio 10)
│ Facturado  $690.200           6 facturas emitidas, 1 anulada no suma        │
│ ████████████████████████████████████▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░                 │  barra de balance (24 px)
│ Pagado $464.100   67 %  │  Pendiente $226.100   33 %  │  Vencido $0       │  tres leyendas-enlace
├──────────────────────────────────────────────┬──────────────────────────┤
│ Por mes · últimos 12 meses                   │ Facturas por estado       │
│  ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ █ ▆                      │      ◯  6 facturas         │
│  dic ene … sep oct nov▲ (mes actual: marca)  │  3 Pendientes  2 Pagadas  │
│  barras apiladas: pagado abajo, pendiente    │  1 Anulada                │
├──────────────────────────────────────────────┴──────────────────────────┤
│ Clientes con más facturado                                                │
│ 1  Bruno González   ████████████████████  $285.600   1 factura            │
│ 2  Ana Pérez        ███████████████████   $279.650   2 facturas           │
│ …                                                                          │
└───────────────────────────────────────────────────────────────────────────┘
┌ Cartera ──────────────────────────────────────────────────────────────────┐
│ Clientes 22  │  Activos 20  │  Nuevos en noviembre 3  │  Usuarios 3 (ADMIN) │  las 4 cifras de hoy, en una fila
└───────────────────────────────────────────────────────────────────────────┘
```

Móvil (375 px): todo en una columna; la barra de balance sigue a todo el ancho con las leyendas apiladas; el gráfico mensual muestra 6 meses (los más recientes) para que las barras no queden de 4 px; la dona queda al lado de su leyenda; la cartera en 2 × 2.

**Color** (solo tokens existentes; cada uno en los dos temas, con contraste ≥ 3:1 de objeto gráfico contra `surface`):

| Uso | Claro | Oscuro | Por qué |
|---|---|---|---|
| Pagado | `success` #2D6A4F (alerce) | #6FD6A0 (menta) | ya significa «bien / activo» en los badges |
| Pendiente | `warning` #8A4B12 (cobre) | #EFBE6A (miel) | ya es el badge «Pendiente» |
| Vencido | rayado diagonal de `danger` #A3262D sobre el tramo pendiente | #FF8F87 (coral) | rojo solo comunica estado (principio 1); el rayado no depende solo del color |
| Anulada (dona) | `line-strong` #7D8692 | #788296 | queda fuera de los importes: neutro |
| Mes actual | marca `accent` (ámbar / lavanda) bajo la etiqueta del eje | | el acento marca «dónde estás», como la pestaña activa |
| Ejes, rejilla | `ink-muted` para etiquetas, `line` para la base | | sin rejilla vertical; 3 líneas horizontales suaves |

Los colores se pasan a Recharts como `var(--color-…)`, así cambian solos con `data-theme`.

**Tipografía**: misma escala. «Facturado» y su monto son la única cifra grande de la sección (1.75 rem, 600, `tabular-nums`, al tamaño del h1 pero sin competir: va dentro del panel). Montos de leyendas a 1 rem / 600; etiquetas de ejes a 13 px; montos abreviados en el eje («$464 mil»), completos en el tooltip y en la leyenda (`formatMoney`).

**Interacción y enlaces**: cada tramo y leyenda del balance lleva a su listado filtrado (Pagado → `/invoices?status=PAID`, Pendiente → `?status=PENDING`); cada barra mensual a `/invoices?issuedFrom=…&issuedTo=…`; cada estado de la dona a su filtro; cada cliente del ranking a `/invoices?customerId=`. Tooltip de Recharts con `surface`, borde `line` y `shadow-popover`. Sin animación de entrada de los gráficos (`isAnimationActive={false}`), coherente con «movimiento mínimo y con causa».

**Accesibilidad**: cada gráfico es un `<figure>` con `<figcaption>` y una tabla `sr-only` con los mismos números; la barra de balance es una lista de enlaces con su texto completo («Pagado: $464.100, 67 % de lo facturado»); foco visible en tramos y barras.

**Estados**: carga con esqueletos de la forma de cada pieza (barra, columnas, círculo, filas); error con «Reintentar»; sin facturas, la sección muestra «Todavía no hay facturas» con «Nueva factura» (ADMIN) en vez de gráficos vacíos.

**Revisión contra los defaults** (paso de autocrítica de la skill):

| Default | Decisión |
|---|---|
| Fila de 4 tarjetas iguales «número grande + etiqueta chica» | Reemplazada por la barra de balance: un solo número grande (Facturado) y el reparto proporcional |
| Gradientes y sombras en tarjetas | Sin gradientes; un solo panel con borde, sombra solo en el tooltip |
| Paleta arcoíris en los gráficos | Solo los colores de estado que la app ya usa; anuladas en neutro |
| Numeración decorativa (01 / 02) | Solo en el ranking de clientes, que sí es un orden |
| Metadatos con «·» | Separadores de leyenda como celdas, no puntos medios |

**Implementación (K-08)**: `components/dashboard/{BalanceBar,MonthlyChart,StatusChart,TopCustomers,BillingSection}.tsx`; la cartera pasó de lista a una fila de cifras (`StatTile`). Detalles:

- «Vencido» enlaza a `/invoices?status=PENDING&sort=dueDate,asc` (no hay filtro de vencidas; ordenadas por vencimiento, las vencidas quedan primero).
- Recharts con `accessibilityLayer={false}` y `Pie rootTabIndex={-1}`: sin eso deja el SVG enfocable dentro de un contenedor `aria-hidden` (Lighthouse `aria-hidden-focus`). La alternativa accesible son las tablas `sr-only` y las leyendas-enlace, cuyo nombre accesible empieza con el texto visible («5 pendientes, $351.050»).
- Eje con `formatCompactMoney` («$464 mil», con espacio no separable: Recharts parte las etiquetas en los espacios); meses con `formatMonthShort` / `formatMonthLong`; un clic en una barra abre el listado de ese mes (`monthRange`).
- Recharts y sus dependencias en el chunk `charts` (434 kB, 122 kB comprimido), solo en la precarga del `import()` del resumen.
- Verificado: datos iguales a `/api/invoices/stats`, rayado de vencidas, tema claro y oscuro, 375 px sin scroll (6 meses), enlaces del balance y de las barras, USER sin «Usuarios»; Lighthouse accesibilidad 100.

Primera versión revisada: las 4 tarjetas de KPI que había pensado primero (facturado, pagado, pendiente, vencido) eran exactamente el default de la tabla de arriba; se cambiaron por la barra de balance, que además muestra la proporción, que es la información útil.

## 4. Decisiones

- **Facturas de un cliente eliminado: lógico** (usuario): se conserva la facturación histórica en la base; una restauración futura no pierde nada.
- **Email liberado** (usuario): índice único parcial; la regla del service (`existsByEmail`) ya filtra eliminados con `@SQLRestriction`.
- **Factura sola: físico** (usuario): se mantiene la spec 10; por eso `@SQLRestriction` y no `@SoftDelete`.
- **Recharts** (usuario): gráficos interactivos (tooltips, leyendas) sin dibujar SVG a mano; chunk propio.
- **Estadísticas en el backend**: un endpoint agregado en vez de varias consultas de listado; los importes salen de la base (`BigDecimal`), no de sumar en el navegador.
- **Migración por MCP antes del código**: producción usa `validate`; las columnas nuevas son nulas y el código anterior las ignora, así que migrar primero no rompe nada. Aplicar la migración modifica producción: K-02 pide la confirmación explícita del usuario.

## 5. Riesgos

- **(Resuelto en K-02) Entre K-01 y K-02, no arrancar contra Supabase en desarrollo** (ni `./mvnw test`, cuyo `ApplicationTests` usa la base del `.env`): `update` agregaría las columnas y `data.sql` cambiaría el índice del email en producción sin la migración revisada. En K-01 los tests y las pruebas se corrieron con `DB_URL` de la base local; el `update` en seco contra Supabase mostró solo los dos `add column deleted_at` (el índice parcial lo hacen `data.sql` y la migración).
- (K-01) `InvoiceDto.items` pasó de `@Valid List<…>` a `List<@Valid InvoiceItemDto>`: Hibernate Validator 9 avisa (HV000271) que `@Valid` sobre el contenedor está deprecado.

- `data.sql` con DDL: corre también en desarrollo contra Supabase; las sentencias son idempotentes y equivalentes a la migración.
- `update` en desarrollo contra Supabase antes de K-02 agregaría las columnas por su cuenta; la migración usa `IF NOT EXISTS`. Antes de arrancar contra Supabase, el `update` en seco de la spec 10 (§7 de su plan) muestra el DDL.
- Recharts y el tema oscuro: los colores van por variables CSS para que cambien con `data-theme`; se revisan ambos temas.
- Rendimiento de las estadísticas: con pocos datos es trivial; los agregados usan los índices existentes (`idx_invoices_customer_id`).

## 6. Trazabilidad

| Requisito | Tareas |
|---|---|
| RF-11-01, RF-11-02, RF-11-03, RF-11-04 | K-01 |
| RF-11-05 | K-02 |
| RF-11-06 | K-03 |
| RF-11-07 | K-04 |
| RF-11-08 | K-05 |
| RF-11-09 | K-06 |
| RF-11-10 | K-07, K-08 |
| RF-11-11 | K-01 (constitución), K-09 |
| RNF-11-01 | K-01 |
| RNF-11-02 | K-02 |
| RNF-11-03 | K-03, K-08 |
| RNF-11-04 | K-06, K-08 |
| RNF-11-05 | K-04 … K-08 |
