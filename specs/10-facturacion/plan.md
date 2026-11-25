# Plan técnico — Spec 10

Cómo se implementa [`spec.md`](spec.md) respetando las constituciones del
[backend](../constitution.md) y del [frontend](../../../4-react/specs/constitution.md).

## 1. Esquema existente (Supabase)

Leído con el MCP de Supabase (`list_tables`, `information_schema`, `pg_constraint`, `pg_indexes`).
Migraciones: `create_invoices_table`, `enable_rls_public_tables`, `create_invoice_items_table`.

**`invoices`**

| Columna | Tipo | Nulo | Default / restricción |
|---|---|---|---|
| `id` | `bigint` identity | no | PK `invoices_pkey` |
| `invoice_number` | `varchar(30)` | no | `UNIQUE` `invoices_invoice_number_key` |
| `customer_id` | `bigint` | no | FK `invoices_customer_id_fkey` → `customers(id)` (**sin** `ON DELETE`), índice `idx_invoices_customer_id` |
| `issue_date` | `date` | no | `CURRENT_DATE` |
| `due_date` | `date` | sí | `chk_invoices_due_date`: `due_date IS NULL OR due_date >= issue_date` |
| `subtotal`, `tax`, `total` | `numeric(12,2)` | no | `0`, `CHECK (>= 0)` |
| `status` | `varchar(20)` | no | `'PENDING'`, `CHECK IN ('PENDING','PAID','CANCELLED')` |
| `notes` | `varchar(255)` | sí | |
| `created_at` | `timestamp` | no | `now()` |
| `updated_at` | `timestamp` | sí | |

**`invoice_items`**

| Columna | Tipo | Nulo | Default / restricción |
|---|---|---|---|
| `id` | `bigint` identity | no | PK `invoice_items_pkey` |
| `invoice_id` | `bigint` | no | FK `invoice_items_invoice_id_fkey` → `invoices(id)` `ON DELETE CASCADE`, índice `idx_invoice_items_invoice_id` |
| `line_number` | `integer` | no | `CHECK (> 0)`; `UNIQUE (invoice_id, line_number)` `uq_invoice_items_line` |
| `description` | `varchar(255)` | no | |
| `quantity` | `numeric(10,2)` | no | `CHECK (> 0)` |
| `unit_price` | `numeric(12,2)` | no | `CHECK (>= 0)` |
| `line_total` | `numeric(12,2)` | sí | `GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED` |
| `created_at` | `timestamp` | no | `now()` |

Sin triggers. RLS activo en todas las tablas (como `customers`): la app se conecta con el rol
`postgres` del pooler, que no pasa por RLS. Datos de ejemplo: 7 facturas, IVA = 19 % del subtotal.

## 2. Contrato de la API

Reglas en `SecurityConfig`: `GET /api/invoices/**` → USER/ADMIN; resto de `/api/invoices/**` → ADMIN.

| Método y ruta | Respuesta |
|---|---|
| `GET /api/invoices?search=&customerId=&status=&issuedFrom=&issuedTo=&page=&size=&sort=` | 200 `PagedModel<InvoiceDto>`; orden por defecto `issueDate,desc`; 400 por rango invertido, estado u orden inválido |
| `GET /api/invoices/{id}` | 200 `InvoiceDto`; 404 |
| `POST /api/invoices` | 201 + `Location`; 400 validación; 404 cliente inexistente; 409 número duplicado (carrera) |
| `PUT /api/invoices/{id}` | 200; 400; 404 (factura o cliente) |
| `DELETE /api/invoices/{id}` | 204; 404 |
| `DELETE /api/customers/{id}` (cambia) | 409 «No se puede eliminar el cliente: tiene N factura(s)» si tiene facturas |

`InvoiceDto` (request y response; `RO` = solo lectura, se ignora en el request):

```json
{
  "id": 1,                              // RO
  "invoiceNumber": "FAC-2026-0001",     // RO: lo asigna el backend
  "customerId": 2,                      // obligatorio
  "customerName": "Ana Pérez",          // RO
  "issueDate": "2026-10-05",            // obligatorio
  "dueDate": "2026-11-04",              // opcional, >= issueDate
  "status": "PAID",                     // opcional al crear (PENDING)
  "notes": "Desarrollo de API REST",    // opcional, ≤ 255
  "items": [                            // 1..100 líneas
    { "lineNumber": 1,                  // RO: 1..n según el orden enviado
      "description": "Diseño de endpoints REST",
      "quantity": 1.00, "unitPrice": 50000.00,
      "lineTotal": 50000.00 }           // RO
  ],
  "subtotal": 150000.00,                // RO
  "tax": 28500.00,                      // RO: 19 % del subtotal
  "total": 178500.00,                   // RO
  "createdAt": "2026-10-05T10:15:30",   // RO
  "updatedAt": null                     // RO
}
```

Lista blanca de orden (`InvoiceServiceImpl.SORT_FIELDS`): `id`, `invoiceNumber`, `issueDate`,
`dueDate`, `status`, `total`, `createdAt`.

## 3. Piezas del backend

| Pieza | Cómo |
|---|---|
| `models/InvoiceStatus` | Enum `PENDING`, `PAID`, `CANCELLED`. |
| `models/Invoice` | `@Table(name = "invoices", uniqueConstraints = @UniqueConstraint(name = "invoices_invoice_number_key", …), indexes = @Index(name = "idx_invoices_customer_id", …))`. `@ManyToOne(fetch = LAZY, optional = false) customer` con `@JoinColumn(name = "customer_id")`. Importes `BigDecimal` con `precision = 12, scale = 2`. `@Enumerated(STRING) status` (largo 20, `PENDING` por defecto). `@OneToMany(mappedBy = "invoice", cascade = ALL, orphanRemoval = true) @OrderBy("lineNumber") @BatchSize(size = 50) List<InvoiceItem> items`. Auditoría con `@PrePersist` / `@PreUpdate` (`created_at`, `updated_at`). Métodos `addItem` / `removeItem` que mantienen las dos puntas de la relación. |
| `models/InvoiceItem` | `@Table(name = "invoice_items", uniqueConstraints = @UniqueConstraint(name = "uq_invoice_items_line", columnNames = {"invoice_id", "line_number"}), indexes = @Index(name = "idx_invoice_items_invoice_id", …))`. `@ManyToOne(LAZY) invoice` con `@OnDelete(CASCADE)`. `quantity` (10,2), `unitPrice` (12,2). `lineTotal` con `@Generated(event = {INSERT, UPDATE})` y `@Column(insertable = false, updatable = false, precision = 12, scale = 2, columnDefinition = "numeric(12,2) generated always as (round(quantity * unit_price, 2)) stored")`: la calcula PostgreSQL, Hibernate nunca la escribe y en una base local la crea `STORED` como en Supabase (J-01: `@GeneratedColumn` no agrega `STORED`, así PostgreSQL 18 la crea virtual y PostgreSQL 17 rechaza la sintaxis; `precision`/`scale` se repiten porque `update` las compara y, si faltan, intenta un `ALTER COLUMN` en cada arranque). `calculateLineTotal()` hace el mismo cálculo en Java para los importes de la factura y la respuesta. `@PrePersist` para `created_at`. |
| `repositories/InvoiceRepository` | `JpaRepository<Invoice, Long>` + `JpaSpecificationExecutor<Invoice>`. `findAll(Specification, Pageable)` sobrescrito con `@EntityGraph(attributePaths = "customer")`; `findWithCustomerAndItemsById` (`@EntityGraph` con `customer` e `items`); `countByCustomerId`; `findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)` para la numeración. |
| `repositories/InvoiceSpecifications` | `matches(search)` (número o nombre, apellido o email del cliente con `LikePatterns` + `ESCAPE`, con `join` al cliente), `hasCustomer`, `hasStatus`, `issuedBetween`; `of(filter)` con `Specification.allOf`. |
| `dto/InvoiceDto`, `dto/InvoiceItemDto` | `record` con las validaciones de CA-10-5 (`@NotNull`, `@NotEmpty`, `@Size(max = 100)`, `@Valid` en la lista, `@DecimalMin`, `@Digits`). Campos `RO` con `@JsonProperty(access = READ_ONLY)`. |
| `dto/InvoiceFilter` | `record(String search, Long customerId, InvoiceStatus status, @DateTimeFormat(iso = DATE) LocalDate issuedFrom, … issuedTo)` + `empty()`. Un `status` inválido llega como error de binding → 400 por campo (ya resuelto en `GlobalExceptionHandler`). |
| `mappers/InvoiceMapper` | `toDto` (incluye `customerName` y líneas con `lineTotal = calculateLineTotal()`), `updateEntity(dto, entity)` (fechas, estado si viene, notas; nunca número, importes ni auditoría). Las líneas y el cliente los resuelve el service. |
| `services/InvoiceService` + `InvoiceServiceImpl` | Ver §4. `@Transactional(readOnly = true)` en lecturas. |
| `controllers/InvoiceController` | Como `UserController`: `PagedModel` con `@ParameterObject` y `@PageableDefault(sort = "issueDate", direction = DESC)`; `POST` con `Location`; `DELETE` 204. |
| `config/SecurityConfig` | Dos `requestMatchers` nuevos antes de `anyRequest()`. |
| `services/CustomerServiceImpl` | Recibe `InvoiceRepository`; `delete` cuenta facturas y lanza `OperationNotAllowedException`. |
| `src/main/resources/data.sql` | Facturas de ejemplo con `INSERT … SELECT … WHERE NOT EXISTS (SELECT 1 FROM invoices)`, buscando el cliente por email; las líneas, buscando la factura por número. Columnas reales (`invoice_number`, `line_number`, …), `created_at` a mano, nunca `line_total` (es generada). |

## 4. Reglas del service

- **Importes**: `subtotal = Σ calculateLineTotal()`; `tax = subtotal × 0.19` con `setScale(2, HALF_UP)`;
  `total = subtotal + tax`. Constante `TAX_RATE = new BigDecimal("0.19")`. Si `total` supera
  `9 999 999 999,99` (máximo de `numeric(12,2)`) → `IllegalArgumentException` (400).
- **Fechas**: `dueDate` anterior a `issueDate` → `IllegalArgumentException` (400), antes de llegar al `CHECK` de la base.
- **Numeración**: prefijo `FAC-<año de issueDate>-`; el siguiente correlativo es el último de ese
  prefijo + 1, con 4 dígitos (`FAC-2026-0008`). Una carrera entre dos altas simultáneas la frena el
  `UNIQUE` → `DataIntegrityViolationException` → 409 (aceptado, como el email de clientes). El número
  no cambia al editar, aunque cambie el año de emisión.
- **Líneas al editar**: se actualizan **en el lugar** por posición: las primeras `min(n, m)` líneas
  existentes reciben los datos nuevos (mismo `lineNumber`), las sobrantes se quitan de la lista
  (`orphanRemoval`) y las que faltan se agregan con el siguiente número (el mayor + 1). Las que se
  conservan no se renumeran: si una factura tenía las líneas 1 y 3, la nueva es la 4 (renumerar 3 → 2
  también podría chocar con la restricción, según el orden de los `UPDATE`). Así nunca hay dos filas con
  el mismo `(invoice_id, line_number)`: Hibernate ejecuta los `INSERT` antes que los `DELETE` al hacer
  flush, por eso borrar todo y volver a insertar 1…n chocaría con `uq_invoice_items_line`.
- **Respuesta del `PUT`**: `saveAndFlush`, para que `@PreUpdate` asigne `updatedAt` antes de armar el DTO
  (con `save` el flush llega al hacer commit y la respuesta saldría sin la fecha; J-03).
- **Cliente**: `customerRepository.findById(dto.customerId())` o `ResourceNotFoundException("Customer", id)` (404).
- **Rangos y orden**: `issuedFrom` posterior a `issuedTo` → 400; `Pageables.checked(pageable, SORT_FIELDS)`.

## 5. Piezas del frontend

| Pieza | Cómo |
|---|---|
| `src/schemas/invoice.ts` | `invoiceStatusSchema`, `invoiceItemSchema`, `invoiceSchema` (montos como `number`), `invoicePageSchema`; `INVOICE_SORT_FIELDS`; `invoiceFiltersSchema` (URL: `search`, `customerId`, `status`, `issuedFrom`, `issuedTo`, `page`, `size`, `sort` por defecto `issueDate,desc`); `invoiceFormSchema` (mismas reglas que el DTO, `items` con `min(1)`/`max(100)`, refinamiento `dueDate >= issueDate`); `STATUS_LABELS` (Pendiente, Pagada, Anulada); `TAX_RATE` y `invoiceTotals(items)` (vista previa; el backend manda). |
| `src/services/invoiceService.ts` | `page`, `get`, `create`, `update`, `remove`, como `customerService` (página desde 1 en la URL, desde 0 en la API). |
| `src/hooks/useInvoices.ts` | `invoiceKeys` (`all`, `list(filters)`, `detail(id)`); `useInvoicesPage`, `useInvoice`, `useCreateInvoice`, `useUpdateInvoice` (reciben `{ customerId, values }` en `mutate`), `useDeleteInvoice` (flash + invalidar `invoiceKeys.all`). Al eliminar un cliente también se invalida `invoiceKeys.all`. |
| `src/hooks/useInvoiceFilters.ts` | `useUrlFilters(invoiceFiltersSchema, …)`; `textFields: ['search']`. |
| `src/lib/dates.ts` | `todayIsoDate()` (J-07): la fecha de emisión por defecto del alta; antes vivía privada en `schemas/customer.ts`, que ahora la importa. |
| `src/lib/format.ts` | `formatMoney(value)` con `Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 2 })` y `formatQuantity`. |
| `src/lib/serverErrors.ts` | Normaliza las rutas de Bean Validation (`items[0].description` → `items.0.description`) y acepta los errores cuyo campo empieza con uno de `fields` (`items`), para mostrarlos en la línea correspondiente. |
| `src/components/invoices/` | `InvoiceStatusBadge` (tonos del `Badge`), `InvoiceTable` (número, cliente, emisión, vencimiento con `minWidth`, estado, total, acciones), `InvoiceFilters` (estado, rango de emisión), `InvoiceItemsTable` (detalle), `InvoiceTotals`, `InvoiceForm` (`useFieldArray`, totales en vivo con `useWatch`), `CustomerInvoices` (sección de la ficha). |
| `src/pages/` | `InvoicesPage`, `InvoiceDetailPage`, `InvoiceCreatePage` (lee `customerId` de la URL y carga el cliente; sin cliente válido → estado vacío con enlace a clientes), `InvoiceEditPage` (`key={id}`). Todas con `lazy` en `App.tsx`; alta y edición dentro de `<RequireRole role="ADMIN" />`. |
| `src/components/layout/Navbar.tsx` | Entrada «Facturas» para todos los roles. |
| `src/pages/CustomerDetailPage.tsx` | Agrega `<CustomerInvoices customerId={…} />` debajo de la ficha. |

## 6. Decisiones

- **El esquema lo manda Supabase**: las tablas ya existen y las creó una migración, así que las
  entities se escriben a partir del esquema real y no al revés. Los nombres de restricciones e
  índices se copian en las anotaciones para que `ddl-auto=update` (desarrollo contra Supabase) los
  reconozca y no cree duplicados, y una base local nueva quede igual a la de producción. En `prod`,
  `validate` comprueba el mapeo.
- **`line_total` generada**: se mapea como columna de solo lectura con su definición completa
  (`… generated always as (…) stored`, ver §3), así Hibernate no la inserta ni actualiza y la crea igual en una base local, pero los importes se calculan en Java con la misma fórmula: así el
  DTO no depende de releer la fila después de cada `flush` y los tests del service no necesitan base.
- **Importes calculados por el backend**: evita facturas con totales incoherentes; la UI muestra una
  vista previa con la misma fórmula y el backend devuelve los definitivos.
- **IVA fijo del 19 %**: es lo que usan todas las facturas de ejemplo; se deja como constante con nombre.
- **Número asignado por el backend**: formato y correlativo de los datos existentes; no se pide al usuario.
- **Un endpoint plano `/api/invoices` con filtro `customerId`** en vez de `/api/customers/{id}/invoices`:
  un solo controller, las reglas de `SecurityConfig` quedan simples y la ficha del cliente reutiliza
  el listado (`customerId=…&size=…`).
- **Las líneas viajan dentro de la factura** (también en el listado): una factura sin líneas no tiene
  sentido y el listado las necesita para mostrar el total coherente; `@BatchSize` evita el N+1.
- **Alta desde la ficha del cliente**: «a cada cliente le podamos crear facturas». El formulario no
  necesita un buscador de clientes; el cliente queda fijo también al editar.
- **Cliente con facturas → 409 explícito**: la FK no tiene `ON DELETE`; sin la regla, el borrado
  llegaría como `DataIntegrityViolationException` con un mensaje genérico.
- **Verificación contra el PostgreSQL local**: el `.env` apunta a Supabase (producción). Las pruebas
  que escriben datos se hacen con `DB_URL` del contenedor local `3-spring-api-db`; contra Supabase
  solo se arranca con el perfil `prod` (`validate`, sin seed) y se hacen lecturas.

## 7. Riesgos

- **`ddl-auto=update` contra Supabase**: si un nombre de restricción no coincide, Hibernate podría
  crear una restricción o un índice duplicado. J-01 lo verificó: tres arranques contra la base local
  (el segundo y el tercero sin DDL y con `pg_constraint` / `pg_indexes` idénticos), `prod` contra
  Supabase (`validate` OK) y un `update` **en seco** contra Supabase (`ddl-auto=none` +
  `jakarta.persistence.schema-generation.scripts.action=update` y `…scripts.create-target=<archivo>`):
  no genera DDL. Ese mismo `update` en seco sirve para revisar cualquier cambio futuro de entities
  antes de arrancar contra Supabase (también `ApplicationTests`, que usa la base del `.env`).
- **Numeración con altas simultáneas**: dos altas pueden calcular el mismo número; la segunda recibe
  409 y basta con reintentar. Aceptado (como el email de clientes).
- **Montos con decimales en CLP**: los pesos no usan decimales, pero las columnas sí (cantidad 1,5
  horas); `formatMoney` muestra hasta 2 decimales solo cuando existen.

## 8. Trazabilidad

| Requisito | Tareas |
|---|---|
| RF-10-01 | J-01 |
| RF-10-02 | J-02 |
| RF-10-03 | J-02 |
| RF-10-04 | J-03 |
| RF-10-05 | J-03 |
| RF-10-06 | J-01 |
| RF-10-07 | J-04 |
| RF-10-08 | J-05 |
| RF-10-09 | J-06 |
| RF-10-10 | J-07 |
| RF-10-11 | J-08 |
| RF-10-12 | J-03 (constitución), J-09 |
| RNF-10-01 | J-03, J-07 |
| RNF-10-02 | J-02, J-04 |
| RNF-10-03 | J-01, J-02 |
| RNF-10-04 | J-03 |
| RNF-10-05 | J-05 … J-08 |
