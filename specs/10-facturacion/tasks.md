# Tareas — Spec 10 (Fase J)

Se ejecutan **una a una** con la skill `sdd-next-task`. Definición de "hecho" en la
constitución de cada repo: [backend](../constitution.md#5-calidad-definición-de-hecho) y
[frontend](../../../4-react/specs/constitution.md). Rama `feature/facturacion` en los dos repos,
creada desde `main` al empezar la fase (antes de J-01, junto con el commit de esta spec).

Las pruebas que escriben datos se hacen contra el PostgreSQL local (`3-spring-api-db`), no contra
Supabase: el backend se levanta con `DB_URL=jdbc:postgresql://localhost:5432/spring_api` (y usuario
y contraseña locales) sobrescritos en la línea de comandos.

- [x] **J-01** Entities de facturas mapeadas al esquema de Supabase y seed local
  - Depende de: A-11, I-06
  - Cubre: RF-10-01, RF-10-06, RNF-10-03
  - Archivos: `models/{Invoice,InvoiceItem,InvoiceStatus}.java`, `repositories/InvoiceRepository.java`, `src/main/resources/data.sql`, `specs/constitution.md` (rama de la spec 10, §6)
  - Hecho cuando: las entities copian tipos, largos, precisión, columna generada, restricciones e índices (con sus nombres) de Supabase; con la base local vacía se crean las tablas y el seed carga facturas y líneas; un segundo arranque no crea restricciones ni índices nuevos; con `prod` contra Supabase la app arranca (`validate`)
  - Verificación: `./mvnw test`; arrancar dos veces contra la base local y comparar `pg_constraint` / `pg_indexes` de `invoices` e `invoice_items`; `SPRING_PROFILES_ACTIVE=prod` con el `.env` (Supabase): arranca sin errores de validación del esquema; `update` en seco contra Supabase sin DDL (ver plan §7)
  - Commit: "Agrega las entities de facturas mapeadas al esquema de Supabase"

- [x] **J-02** DTOs, mapper, filtros y service de facturas
  - Depende de: J-01
  - Cubre: RF-10-02, RF-10-03, RNF-10-02, RNF-10-03
  - Archivos: `dto/{InvoiceDto,InvoiceItemDto,InvoiceFilter}.java`, `mappers/InvoiceMapper.java`, `repositories/InvoiceSpecifications.java`, `services/{InvoiceService,InvoiceServiceImpl}.java`, `src/test/.../services/InvoiceServiceImplTest.java`
  - Hecho cuando: el service calcula subtotal, IVA 19 % y total con `HALF_UP`; numera `FAC-<año>-<NNNN>` correlativo por año; edita las líneas en el lugar (3→2, 2→4); valida vencimiento, rango de fechas, total máximo y orden; 404 por factura o cliente inexistente
  - Verificación: `./mvnw test` (tests Mockito del service: importes, numeración, líneas al editar, validaciones)
  - Commit: "Agrega el service de facturas con cálculo de importes y numeración"

- [ ] **J-03** Endpoints de facturas, reglas de acceso y cliente con facturas
  - Depende de: J-02
  - Cubre: RF-10-04, RF-10-05, RF-10-12 (constitución), RNF-10-01, RNF-10-04
  - Archivos: `controllers/InvoiceController.java`, `config/SecurityConfig.java`, `services/CustomerServiceImpl.java`, `specs/constitution.md` (§3 reglas de acceso), `src/test/.../controllers/InvoiceControllerTest.java`, `src/test/.../services/CustomerServiceImplTest.java`
  - Hecho cuando: USER y ADMIN leen; USER recibe 403 en escrituras; sin sesión 401; ADMIN crea (201 + `Location`), edita, elimina (204); los campos de solo lectura se ignoran en el request; validación con `errors` por campo (también `items[0].quantity`); eliminar un cliente con facturas → 409
  - Verificación: `./mvnw test`; con la app contra la base local: login como admin y como user, curl de cada endpoint (listado con filtros, detalle, alta, edición 3→2 líneas, eliminación, cliente con facturas)
  - Commit: "Expone los endpoints de facturas con lectura para USER y administración para ADMIN"

- [ ] **J-04** Esquemas, servicio, hooks y formato de montos de facturas
  - Depende de: J-03
  - Cubre: RF-10-07, RNF-10-02
  - Repo: `4-react`
  - Archivos: `src/schemas/invoice.ts`, `src/services/invoiceService.ts`, `src/hooks/{useInvoices,useInvoiceFilters}.ts`, `src/lib/format.ts` (`formatMoney`, `formatQuantity`), `src/lib/serverErrors.ts` (rutas `items[n].campo`)
  - Hecho cuando: las respuestas de `/api/invoices` se validan con Zod; `invoiceTotals` da los mismos importes que el backend; `applyServerErrors` asigna `items[0].description` a la línea 1
  - Verificación: `npm run build && npm run lint`
  - Commit: "Agrega esquemas, servicio y hooks de facturas"

- [ ] **J-05** Listado de facturas con filtros y entrada en el menú
  - Depende de: J-04
  - Cubre: RF-10-08, RNF-10-05
  - Repo: `4-react`
  - Archivos: `src/components/invoices/{InvoiceStatusBadge,InvoiceTable,InvoiceFilters}.tsx`, `src/pages/InvoicesPage.tsx`, `src/App.tsx`, `src/components/layout/Navbar.tsx`
  - Hecho cuando: `/invoices` muestra la tabla paginada con búsqueda, filtros (estado, rango de emisión, cliente desde la URL con un chip para quitarlo), orden y estados de carga, vacío y error; «Facturas» en el menú para ambos roles; USER sin acciones de escritura
  - Verificación: `npm run build && npm run lint`; en el navegador (backend contra la base local) como admin y como user, a 375 px y en tema oscuro
  - Commit: "Agrega el listado de facturas con búsqueda, filtros y paginación"

- [ ] **J-06** Detalle de factura y facturas en la ficha del cliente
  - Depende de: J-05
  - Cubre: RF-10-09, RNF-10-05
  - Repo: `4-react`
  - Archivos: `src/components/invoices/{InvoiceItemsTable,InvoiceTotals,CustomerInvoices}.tsx`, `src/pages/{InvoiceDetailPage,CustomerDetailPage}.tsx`, `src/App.tsx`
  - Hecho cuando: `/invoices/:id` muestra cabecera, líneas, totales y auditoría (404 y error con reintento); la ficha del cliente lista sus facturas con enlace al listado filtrado; USER solo lectura
  - Verificación: `npm run build && npm run lint`; en el navegador como admin y como user
  - Commit: "Agrega el detalle de factura y las facturas en la ficha del cliente"

- [ ] **J-07** Alta y edición de facturas con líneas dinámicas
  - Depende de: J-06
  - Cubre: RF-10-10, RNF-10-01, RNF-10-05
  - Repo: `4-react`
  - Archivos: `src/components/invoices/InvoiceForm.tsx`, `src/pages/{InvoiceCreatePage,InvoiceEditPage}.tsx`, `src/components/invoices/CustomerInvoices.tsx` («Nueva factura»), `src/pages/InvoiceDetailPage.tsx` («Editar»), `src/App.tsx` (rutas ADMIN)
  - Hecho cuando: agregar y quitar líneas, totales en vivo, validación equivalente al backend (también por línea), 400 del servidor en su campo, flash y navegación al detalle; USER → `/forbidden`
  - Verificación: `npm run build && npm run lint`; en el navegador: crear con 3 líneas, editar dejando 2, vencimiento anterior a la emisión, línea vacía, teclado
  - Commit: "Agrega alta y edición de facturas con líneas y totales en vivo"

- [ ] **J-08** Eliminación de facturas y cliente con facturas
  - Depende de: J-07
  - Cubre: RF-10-11, RNF-10-05
  - Repo: `4-react`
  - Archivos: `src/hooks/useInvoices.ts` (`useDeleteInvoice`), `src/hooks/useCustomers.ts` (invalida facturas), `src/pages/{InvoicesPage,InvoiceDetailPage}.tsx`, `src/components/invoices/CustomerInvoices.tsx`
  - Hecho cuando: «Eliminar» abre el `ConfirmDialog` con el número; al confirmar, flash y la lista se actualiza (retrocede de página si quedó vacía; desde el detalle vuelve al listado); eliminar un cliente con facturas muestra el 409 dentro del modal
  - Verificación: `npm run build && npm run lint`; en el navegador: cancelar, confirmar desde las tres pantallas, cliente con facturas
  - Commit: "Agrega la eliminación de facturas con confirmación"

- [ ] **J-09** Documentar la fase J
  - Depende de: J-08
  - Cubre: RF-10-12
  - Repos: `3-spring-api` y `4-react`
  - Archivos: backend `README.md`, `CLAUDE.md`, `specs/10-facturacion/spec.md` (estado: Hecha); frontend `README.md`, `CLAUDE.md`, `specs/README.md` (estado)
  - Hecho cuando: el README del backend documenta `/api/invoices` con ejemplos, reglas de acceso e importes; los CLAUDE.md resumen entities, reglas del service y piezas del frontend
  - Verificación: `./mvnw test`; `npm run build && npm run lint`; enlaces revisados
  - Commit: backend "Documenta la API de facturas"; frontend "Documenta la facturación en el dashboard"
  - Al cerrar: preguntar al usuario si hace merge a `main` y push en ambos repos
