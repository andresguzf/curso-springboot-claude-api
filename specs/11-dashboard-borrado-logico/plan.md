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
| `repositories/InvoiceRepository` | Consultas de agregación JPQL con proyecciones a records (`select new …(i.status, count(i), coalesce(sum(i.total), 0)) … group by i.status`; mensual con `year(i.issueDate)`, `month(i.issueDate)` desde una fecha; top 5 con `group by i.customer.id, i.customer.name, i.customer.lastName order by sum(i.total) desc` + `Limit`/`Pageable`). Respetan el `@SQLRestriction`. |
| `services/InvoiceService.stats()` | `@Transactional(readOnly = true)`; arma el DTO, completa los meses sin facturas con 0 y calcula `billed` y `overdue` (hoy = `LocalDate.now()`, zona del servidor `America/Santiago`). Anuladas fuera de los importes. |
| `controllers/InvoiceController` | `@GetMapping("/stats")`. |
| Tests | Service con Mockito (meses completos, anuladas excluidas, vencidas) y controller (`@WebMvcTest`: USER 200, sin sesión 401). |

## 3. Frontend

| Pieza | Cómo |
|---|---|
| `components/customers/CustomerTable.tsx` | La columna «Acciones» existe para todos: `IconLink` con `ReceiptText` («Ver facturas de Nombre Apellido» → `/invoices?customerId={id}`); editar y eliminar siguen solo para ADMIN. |
| `pages/CustomersPage.tsx` | Al abrir el modal de eliminar, `useInvoicesPage({ customerId, size: 1 })` da `page.totalElements`: descripción «También se eliminarán sus N facturas. Esta acción no se puede deshacer desde el dashboard.» (mientras carga, el texto genérico). Se quita el caso 409 de la spec 10. |
| `components/invoices/CustomerCombobox.tsx` | Combobox ARIA (input `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`, `aria-controls`, `aria-activedescendant`; `ul role="listbox"` con `li role="option"`). Texto con `useDebouncedText`; consulta `useQuery` con `customerService.page({ search, size: 8 })` y `keepPreviousData`, habilitada con 1+ caracteres. Teclado: ↑ ↓ mueven, Enter elige, Esc cierra; `onMouseDown` + `preventDefault` en las opciones para que el blur no cierre antes del clic. Elegido: muestra «Nombre Apellido · email» con «Cambiar». |
| `schemas/invoice.ts` | `invoiceFormSchema` suma `customerId: z.number({ error: 'Elige un cliente' }).int().positive()`; `INVOICE_FORM_FIELDS` incluye `customerId`. `toBody` y los hooks toman el cliente de los valores del formulario (desaparece `{ customerId, values }`). |
| `components/invoices/InvoiceForm.tsx` | Prop `customer?: { id, name }` (fijo: ficha o edición) o el combobox si no viene. |
| `pages/InvoicesPage.tsx` | «Nueva factura» (ADMIN) en el `PageHeader` → `/invoices/new`. `InvoiceCreatePage` sin `customerId` ya no muestra «Elige un cliente»: muestra el formulario con el combobox. |
| `services/invoiceService.ts` + `hooks/useInvoiceStats.ts` | `stats()` → `GET /api/invoices/stats` validado con `invoiceStatsSchema`; clave `invoiceKeys.stats` (bajo `invoiceKeys.all`, así las mutaciones de facturas y clientes la refrescan). |
| `components/dashboard/` | `StatCard` (balance con icono y enlace), `MonthlyChart` (barras apiladas pagado/pendiente, Recharts), `StatusChart` (dona), `TopCustomers` (lista con barra proporcional), `BillingSection` (orquesta y estados). Colores desde las variables CSS del tema (`var(--color-…)`); tooltips con `formatMoney`; `<figure>` + `<figcaption>` y una tabla `sr-only` con los mismos datos. |
| `pages/DashboardPage.tsx` | Cifras de clientes (compactadas en tarjetas) + `BillingSection`. |
| `vite.config.ts` | `recharts` (y sus dependencias) en un grupo de `codeSplitting` propio. |

Dirección visual (K-07): propuesta con `frontend-design` sobre los tokens existentes (`specs/02-base-frontend/design.md`), aprobada por el usuario antes de K-08. Colores nuevos para series de gráficos solo si hace falta, definidos en los dos temas con contraste medido.

## 4. Decisiones

- **Facturas de un cliente eliminado: lógico** (usuario): se conserva la facturación histórica en la base; una restauración futura no pierde nada.
- **Email liberado** (usuario): índice único parcial; la regla del service (`existsByEmail`) ya filtra eliminados con `@SQLRestriction`.
- **Factura sola: físico** (usuario): se mantiene la spec 10; por eso `@SQLRestriction` y no `@SoftDelete`.
- **Recharts** (usuario): gráficos interactivos (tooltips, leyendas) sin dibujar SVG a mano; chunk propio.
- **Estadísticas en el backend**: un endpoint agregado en vez de varias consultas de listado; los importes salen de la base (`BigDecimal`), no de sumar en el navegador.
- **Migración por MCP antes del código**: producción usa `validate`; las columnas nuevas son nulas y el código anterior las ignora, así que migrar primero no rompe nada. Aplicar la migración modifica producción: K-02 pide la confirmación explícita del usuario.

## 5. Riesgos

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
