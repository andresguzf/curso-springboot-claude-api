# Tareas — Spec 11 (Fase K)

Se ejecutan **una a una** con la skill `sdd-next-task`. Definición de "hecho" en la
constitución de cada repo: [backend](../constitution.md#5-calidad-definición-de-hecho) y
[frontend](../../../4-react/specs/constitution.md). Rama `feature/dashboard-borrado-logico` en
los dos repos, creada desde `main` al empezar la fase (junto con el commit de esta spec).

Como en la spec 10, las pruebas que escriben datos se hacen contra el PostgreSQL local
(`3-spring-api-db`); contra Supabase solo `prod` (`validate`), lecturas y la migración de K-02.

- [x] **K-01** Borrado lógico de clientes y facturas
  - Depende de: J-09
  - Cubre: RF-11-01, RF-11-02, RF-11-03, RF-11-04, RF-11-11 (constitución), RNF-11-01
  - Archivos: `models/{Customer,Invoice}.java`, `repositories/InvoiceRepository.java`, `services/{CustomerServiceImpl,InvoiceServiceImpl}.java`, `src/main/resources/data.sql`, `specs/constitution.md` (§3 y §6), tests de `CustomerServiceImplTest`, `InvoiceServiceImplTest` y `CustomerControllerSecurityTest`
  - Hecho cuando: eliminar un cliente con facturas responde 204 y marca cliente y facturas con la misma `deleted_at`; ninguno aparece en listados, detalle, exportación ni conteos; se puede crear otro cliente con el mismo email; el siguiente número de factura no reutiliza el de una eliminada; eliminar una factura sola sigue siendo físico
  - Verificación: `./mvnw test`; contra la base local, dos arranques sin DDL nuevo y curl de: eliminar cliente con facturas, listar, detalle 404, recrear con el mismo email, crear factura tras eliminar; `update` en seco contra Supabase para ver el DDL pendiente (debe coincidir con la migración de K-02)
  - Commit: "Agrega el borrado lógico de clientes y sus facturas"

- [x] **K-02** Migración del borrado lógico en Supabase
  - Depende de: K-01
  - Cubre: RF-11-05, RNF-11-02
  - Archivos: `specs/11-dashboard-borrado-logico/plan.md` (resultado), sin código
  - Pide aprobación: modifica la base de **producción**. Se muestra el SQL y se aplica con el MCP (`apply_migration`) solo con el OK explícito del usuario.
  - Hecho cuando: Supabase tiene `deleted_at` en `customers` e `invoices` y el índice parcial `customers_email_active_key` en lugar de la restricción vieja; `prod` (`validate`) arranca contra Supabase; `update` en seco no genera DDL; los datos no cambiaron
  - Verificación: `list_migrations` y consultas de esquema con el MCP; arranque `prod`; conteos de filas antes y después
  - Commit: "Documenta la migración del borrado lógico en Supabase"

- [x] **K-03** Endpoint de estadísticas de facturación
  - Depende de: K-01
  - Cubre: RF-11-06, RNF-11-03
  - Archivos: `dto/InvoiceStatsDto.java`, `repositories/InvoiceRepository.java`, `services/{InvoiceService,InvoiceServiceImpl}.java`, `controllers/InvoiceController.java`, tests
  - Hecho cuando: `GET /api/invoices/stats` devuelve totales, por estado (los 3), 12 meses completos y top 5, sin anuladas en los importes ni eliminados; USER y ADMIN 200, sin sesión 401
  - Verificación: `./mvnw test`; curl contra la base local comparando con SQL directo
  - Commit: "Agrega el endpoint de estadísticas de facturación"

- [x] **K-04** Icono de facturas en el listado de clientes
  - Depende de: J-09
  - Cubre: RF-11-07, RNF-11-05
  - Repo: `4-react`
  - Archivos: `src/components/customers/CustomerTable.tsx`, `src/pages/CustomersPage.tsx` (columnas del esqueleto)
  - Hecho cuando: cada fila tiene «Ver facturas de …» para USER y ADMIN y lleva a `/invoices?customerId=`; sin scroll horizontal en escritorio
  - Verificación: `npm run build && npm run lint`; navegador como admin y como user, 1280 px y 375 px
  - Commit: "Agrega el acceso a las facturas desde el listado de clientes"

- [x] **K-05** Aviso de borrado lógico al eliminar un cliente
  - Depende de: K-01, K-04
  - Cubre: RF-11-08
  - Repo: `4-react`
  - Archivos: `src/pages/CustomersPage.tsx`
  - Hecho cuando: el modal dice cuántas facturas se eliminarán con el cliente; al confirmar, el cliente y sus facturas desaparecen del listado de clientes, de facturas y del resumen
  - Verificación: `npm run build && npm run lint`; navegador contra la base local: cliente con y sin facturas
  - Commit: "Avisa las facturas que se eliminan con un cliente"

- [x] **K-06** Nueva factura desde el listado con autocompletado de clientes
  - Depende de: J-09
  - Cubre: RF-11-09, RNF-11-04
  - Repo: `4-react`
  - Archivos: `src/components/invoices/{CustomerCombobox,InvoiceForm}.tsx`, `src/schemas/invoice.ts`, `src/services/invoiceService.ts`, `src/hooks/useInvoices.ts`, `src/pages/{InvoicesPage,InvoiceCreatePage,InvoiceEditPage}.tsx`
  - Hecho cuando: «Nueva factura» en `/invoices` (ADMIN); el combobox busca por nombre, apellido o email con pausa, se usa con teclado y puntero, y el cliente es obligatorio; desde la ficha llega elegido; la edición no cambia
  - Verificación: `npm run build && npm run lint`; navegador: buscar por apellido y por email, ↑ ↓ Enter Esc, sin resultados, enviar sin cliente, crear
  - Commit: "Agrega la nueva factura con autocompletado de clientes"

- [x] **K-07** Propuesta visual del dashboard de facturación
  - Depende de: K-03
  - Cubre: RF-11-10, RNF-11-05
  - Repo: `4-react`
  - Pide aprobación: con la skill `frontend-design`, propuesta de composición, tarjetas, gráficos, colores de series (en ambos temas) y textos, sobre `specs/02-base-frontend/design.md`. Se muestra y se espera el OK del usuario; recién entonces K-08.
  - Archivos: `../3-spring-api/specs/11-dashboard-borrado-logico/plan.md` (§3: la propuesta aprobada)
  - Commit: "Documenta la dirección visual del dashboard de facturación"

- [x] **K-08** Dashboard de facturación con gráficos
  - Depende de: K-07
  - Cubre: RF-11-10, RNF-11-03, RNF-11-04, RNF-11-05
  - Repo: `4-react`
  - Archivos: `package.json` (`recharts@latest`), `vite.config.ts`, `src/schemas/invoice.ts` (`invoiceStatsSchema`), `src/services/invoiceService.ts` (`stats`), `src/hooks/useInvoiceStats.ts`, `src/components/dashboard/*`, `src/pages/DashboardPage.tsx`, `src/index.css` (colores de series si hacen falta)
  - Hecho cuando: el resumen muestra las tarjetas, el gráfico mensual, la dona por estado y el top 5 con enlaces a los listados filtrados; estados de carga, vacío y error; alternativa accesible de los gráficos; Recharts en su propio chunk; se ve bien en ambos temas y a 375 px
  - Verificación: `npm run build && npm run lint`; navegador como admin y como user, claro/oscuro, 375 px; Lighthouse de accesibilidad del resumen
  - Commit: "Agrega el dashboard de facturación con gráficos"

- [x] **K-09** Documentar la fase K
  - Depende de: K-02, K-05, K-06, K-08
  - Cubre: RF-11-11
  - Repos: `3-spring-api` y `4-react`
  - Archivos: backend `README.md`, `CLAUDE.md`, `specs/11-dashboard-borrado-logico/spec.md` (estado: Hecha); frontend `README.md`, `CLAUDE.md`, `specs/constitution.md` (Recharts en el stack), `specs/README.md` (estado)
  - Hecho cuando: los README documentan el borrado lógico, `/api/invoices/stats`, el icono de facturas, el autocompletado y el dashboard; los CLAUDE.md resumen las reglas nuevas
  - Verificación: `./mvnw test`; `npm run build && npm run lint`; enlaces revisados
  - Commit: backend "Documenta el borrado lógico y las estadísticas de facturación"; frontend "Documenta el dashboard de facturación y el autocompletado"
  - Al cerrar: preguntar al usuario si hace merge a `main` y push en ambos repos
