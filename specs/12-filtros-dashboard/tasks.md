# Tareas — Spec 12 (Fase L)

Se ejecutan **una a una** con la skill `sdd-next-task`. Definición de "hecho" en la
constitución de cada repo: [backend](../constitution.md#5-calidad-definición-de-hecho) y
[frontend](../../../4-react/specs/constitution.md). Rama `feature/filtros-dashboard` en los dos
repos, creada desde `main` al empezar la fase (junto con el commit de esta spec).

Como en las specs 10 y 11, las pruebas en el navegador se hacen con el backend contra la base local.

- [ ] **L-01** Filtros en el endpoint de estadísticas
  - Depende de: K-09
  - Cubre: RF-12-01, RF-12-02, RF-12-03, RNF-12-01, RNF-12-02
  - Archivos: `dto/InvoiceStatsFilter.java`, `repositories/{InvoiceStatsRepository,InvoiceStatsRepositoryImpl,InvoiceRepository,InvoiceSpecifications}.java`, `services/{InvoiceService,InvoiceServiceImpl}.java`, `controllers/InvoiceController.java`, tests (`InvoiceStatsTest`, `InvoiceControllerTest`)
  - Hecho cuando: los tres filtros cambian todas las piezas de la respuesta; la serie mensual sigue la tabla del plan; rango invertido y más de 24 meses dan 400; sin filtros responde lo mismo que hoy
  - Verificación: `./mvnw test`; curl contra la base local con `customerId`, con rango y con ambos, comparado con SQL directo
  - Commit: "Agrega los filtros de cliente y fechas a las estadísticas de facturación"

- [ ] **L-02** Filtros del resumen en la URL y consulta filtrada
  - Depende de: L-01
  - Cubre: RF-12-04
  - Repo: `4-react`
  - Archivos: `src/schemas/invoice.ts`, `src/hooks/{useStatsFilters,useInvoices}.ts`, `src/services/invoiceService.ts`, `src/lib/statsRanges.ts`
  - Hecho cuando: los filtros se leen y escriben en la URL (inválidos ignorados); `useInvoiceStats(filters)` conserva los datos previos mientras carga; los atajos calculan sus rangos
  - Verificación: `npm run build && npm run lint`; cálculo de los atajos con Node
  - Commit: "Agrega los filtros del resumen en la URL"

- [ ] **L-03** Barra de filtros del resumen
  - Depende de: L-02
  - Cubre: RF-12-05, RF-12-06, RNF-12-03
  - Repo: `4-react`
  - Archivos: `src/components/dashboard/{StatsFilters,BillingSection,BalanceBar,MonthlyChart,StatusChart,TopCustomers}.tsx`, `src/pages/DashboardPage.tsx`
  - Hecho cuando: cliente con autocompletado y chip, rango de emisión, atajos con el activo marcado y «Limpiar filtros» cambian toda la sección (datos previos atenuados); textos describen el filtro; ranking oculto con cliente; estado vacío con filtros; los enlaces llevan los filtros al listado
  - Verificación: `npm run build && npm run lint`; navegador como admin y como user: filtrar por cliente, por rango y ambos, recargar, atrás, enlaces, 375 px, ambos temas; Lighthouse de accesibilidad del resumen
  - Commit: "Agrega los filtros de cliente y fechas al resumen"

- [ ] **L-04** Documentar la fase L
  - Depende de: L-03
  - Cubre: RF-12-07
  - Repos: `3-spring-api` y `4-react`
  - Archivos: backend `README.md` (parámetros de `/stats`), `CLAUDE.md`, `specs/12-filtros-dashboard/spec.md` (estado: Hecha); frontend `README.md`, `CLAUDE.md`, `specs/README.md` (estado)
  - Hecho cuando: los README documentan los filtros del endpoint y del resumen; los CLAUDE.md resumen el fragmento Criteria y `useStatsFilters`
  - Verificación: `./mvnw test`; `npm run build && npm run lint`; enlaces revisados
  - Commit: backend "Documenta los filtros de las estadísticas"; frontend "Documenta los filtros del resumen"
  - Al cerrar: preguntar al usuario si hace merge a `main` y push en ambos repos
