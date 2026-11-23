# Tareas — Spec 09 (Fase I)

Se ejecutan **una a una** con la skill `sdd-next-task`. Definición de "hecho" en la
constitución de cada repo: [backend](../constitution.md#5-calidad-definición-de-hecho) y
[frontend](../../../4-react/specs/constitution.md). Ramas: `feature/docker` en `3-spring-api`
(la crea I-01) y `feature/dashboard` en `4-react`.

- [x] **I-01** Perfil `prod` y puerto configurable
  - Depende de: A-11, H-04
  - Cubre: RF-09-01, RF-09-02, RNF-09-03, RNF-09-04
  - Archivos: `src/main/resources/application.properties`, `src/main/resources/application-prod.properties`, `src/test/java/.../ProdProfileTest.java`
  - Hecho cuando: con `SPRING_PROFILES_ACTIVE=prod` no corre el seed, `ddl-auto` es `validate`, no hay SQL en el log y Swagger da 404; sin `DB_URL` la app no arranca; `PORT` cambia el puerto; sin perfil, todo sigue igual
  - Verificación: `./mvnw test`; `./mvnw -DskipTests package` y `java -jar` con el `.env` (Supabase) y `SPRING_PROFILES_ACTIVE=prod PORT=9090`: `/actuator/health` → UP en 9090, `/swagger-ui.html` → 404, login → 200, log sin SQL; y otra vez sin `DB_URL` → no arranca
  - Commit: "Agrega el perfil prod y el puerto configurable con PORT"

- [x] **I-02** Imagen Docker del backend
  - Depende de: I-01
  - Cubre: RF-09-03, RNF-09-01, RNF-09-02, RNF-09-03
  - Archivos: `Dockerfile`, `.dockerignore`
  - Hecho cuando: la imagen se construye sin base de datos, no contiene `.env`, corre como usuario sin privilegios, con `TZ=America/Santiago` y se detiene de forma ordenada
  - Verificación: `./mvnw test`; `docker build -t cartera-api .`; `docker run --rm cartera-api` con `id` y `ls -a /app` (sin `.env`, usuario no root); `docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 cartera-api` contra Supabase: health UP y login 200; `docker stop` muestra el apagado ordenado en el log
  - Commit: "Agrega el Dockerfile multi-stage del backend"

- [x] **I-03** Docker Compose local con PostgreSQL
  - Depende de: I-02
  - Cubre: RF-09-04, RNF-09-01, RNF-09-02, RNF-09-04
  - Archivos: `compose.yaml`
  - Hecho cuando: `docker compose up --build` levanta base y backend; con la base vacía carga los 20 clientes y el admin; el frontend con `npm run dev` inicia sesión y lista clientes contra el contenedor
  - Verificación: `./mvnw test`; `docker compose up --build -d`; `docker compose ps` (db healthy); login y `GET /api/customers?size=1` → `totalElements` 20; prueba en el navegador desde `http://localhost:5173`; `docker compose down` (y `down -v` para borrar el volumen de prueba)
  - Commit: "Agrega Docker Compose con el backend y PostgreSQL"

- [x] **I-04** Blueprint de Render
  - Depende de: I-02
  - Cubre: RF-09-05, RNF-09-01
  - Archivos: `render.yaml`
  - Hecho cuando: el blueprint declara el servicio Docker, el plan gratis, el health check y todas las variables, con los secretos en `sync: false`
  - Verificación: `./mvnw test`; YAML válido y revisado contra la referencia de blueprints de Render; `grep` sin secretos
  - Commit: "Agrega el blueprint de Render para el backend"

- [x] **I-05** Rewrite de Vercel en el frontend
  - Depende de: I-04
  - Cubre: RF-09-06
  - Repo: `4-react`
  - Archivos: `vercel.json`, `.env.example`, `specs/constitution.md` (integración: rewrite de Vercel en la nube)
  - Hecho cuando: `vercel.json` reenvía `/api/*` a la URL del blueprint y hace fallback a `index.html`; el código del frontend no cambia
  - Verificación: `npm run build && npm run lint`; JSON válido y revisado contra la documentación de rewrites de Vercel
  - Commit: "Agrega el rewrite de Vercel hacia la API en Render"

- [ ] **I-06** Documentar la fase I
  - Depende de: I-03, I-05
  - Cubre: RF-09-07
  - Repos: `3-spring-api` y `4-react`
  - Archivos: backend `README.md`, `CLAUDE.md`, `specs/09-docker-despliegue/spec.md` (estado: Hecha); frontend `README.md`, `CLAUDE.md`, `specs/README.md` (estado)
  - Hecho cuando: los README explican Docker local, el perfil `prod`, la tabla de variables por entorno, Render y Vercel con el rewrite; los CLAUDE.md lo resumen
  - Verificación: `./mvnw test`; `npm run build && npm run lint`; enlaces revisados
  - Commit: backend "Documenta Docker y la configuración para Render"; frontend "Documenta el despliegue en Vercel con rewrite a Render"
  - Al cerrar: preguntar al usuario si hace merge a `main` y push en ambos repos
