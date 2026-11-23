# Spec 09 — Docker y configuración para la nube (Render + Vercel)

| | |
|---|---|
| Fase | I |
| Repo | `3-spring-api` (I-01 … I-04, I-06) y `4-react` (I-05, I-06) |
| Estado | En curso (4/6) |
| Depende de | Spec 01 (backend) y spec 08 (frontend) |
| La necesitan | — (el despliegue real será una fase posterior) |
| Constitución | [backend](../constitution.md) · [frontend](../../../4-react/specs/constitution.md) |

## Resumen

El backend ya se empaqueta como JAR y corre con `java -jar`, pero toda su configuración
supone la máquina de desarrollo: lee un `.env` del disco, apunta a `localhost`, carga el seed
en cada arranque y muestra el SQL en el log. Esta fase lo **dockeriza** y deja la configuración
lista para la nube, de modo que pasar de local a la nube sea **cambiar variables de entorno**,
sin tocar código:

- **Local**: `docker compose up` levanta backend + PostgreSQL; el frontend sigue con `npm run dev` y el proxy de Vite.
- **Nube (preparada, no desplegada)**: el backend en **Render** (Docker) con la base en Supabase y el frontend en **Vercel**.

Frontend y backend quedarán en dominios distintos (`*.vercel.app` y `*.onrender.com`), que el
navegador trata como **sitios distintos**: las cookies `SameSite=Strict` no viajarían, Safari
bloquea las cookies de terceros y JavaScript no podría leer la cookie `XSRF-TOKEN`. Por decisión
del usuario se resuelve con un **rewrite de Vercel**: Vercel reenvía `/api/*` al backend en Render,
así el navegador solo ve el dominio de Vercel (igual que hoy con el proxy de Vite) y la seguridad
del backend no cambia: sin CORS, `SameSite=Strict` y CSRF por cookie.

## Historias de usuario

- **HU-09-1** Como desarrollador quiero levantar el backend y su base con un solo comando en Docker, sin instalar Java ni PostgreSQL.
- **HU-09-2** Como desarrollador quiero que la misma imagen sirva en local y en la nube cambiando solo variables de entorno.
- **HU-09-3** Como responsable del sistema quiero que en producción no se cargue el seed, no se muestre el SQL ni Swagger, y que la app no arranque con credenciales por defecto.
- **HU-09-4** Como usuario quiero iniciar sesión desde el frontend en Vercel en cualquier navegador (Safari incluido), aunque el backend esté en otro dominio.

## Criterios de aceptación

**CA-09-1 Perfil de producción**
- Dado `SPRING_PROFILES_ACTIVE=prod`, cuando arranca el backend, entonces no ejecuta `data.sql`, valida el esquema en vez de modificarlo (`ddl-auto=validate`), no escribe el SQL en el log y `/swagger-ui.html` y `/v3/api-docs` responden 404.
- Con el perfil `prod`, si falta `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` o `JWT_SECRET`, la app **no arranca** (sin valores por defecto de desarrollo).
- Sin perfil (desarrollo), todo sigue como hoy: `.env`, seed, SQL en el log y Swagger.

**CA-09-2 Puerto configurable**
- Dado `PORT=10000` (lo que asigna Render), el backend escucha en 10000; sin `PORT`, en 8080.

**CA-09-3 Imagen Docker**
- `docker build` genera la imagen sin necesitar base de datos (los tests corren fuera de la imagen).
- La imagen no contiene `.env` ni otros secretos; el proceso corre con un usuario sin privilegios.
- `docker stop` detiene la app de forma ordenada (Java recibe la señal).
- La zona horaria del contenedor es `America/Santiago` (como en desarrollo) y se puede cambiar con `TZ`.
- La JVM ajusta su memoria al límite del contenedor (plan gratis de Render: 512 MB).

**CA-09-4 Docker en local**
- `docker compose up --build` levanta PostgreSQL y el backend; con la base vacía se cargan los 20 clientes de ejemplo y el admin.
- El backend queda en `http://localhost:8080`, así que el frontend con `npm run dev` funciona sin cambios.
- Los secretos (`JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`) salen del `.env` local; el compose no los repite.
- La misma imagen se conecta a Supabase con `docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod`.

**CA-09-5 Render preparado**
- Un blueprint `render.yaml` declara el servicio Docker, el plan, el health check (`/actuator/health`) y **todas** las variables que necesita, con los secretos marcados para cargarlos en el panel (nunca en el repo).

**CA-09-6 Vercel preparado**
- `vercel.json` reenvía `/api/*` al backend en Render y devuelve `index.html` para cualquier otra ruta (las rutas de React Router funcionan al recargar o al abrir un enlace directo).
- El frontend no cambia su código: `VITE_API_URL` queda vacía (mismo origen).

## Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-09-01 | Perfil `prod` (`application-prod.properties`): `ddl-auto=validate`, sin seed, sin SQL en el log, Swagger desactivado, datasource y JWT sin valores por defecto, `forward-headers-strategy=framework` y pool de conexiones acotado. |
| RF-09-02 | Puerto del servidor desde `PORT`, con 8080 por defecto. |
| RF-09-03 | `Dockerfile` multi-stage (JDK para compilar, JRE para correr), usuario sin privilegios, `ENTRYPOINT` en forma exec, memoria relativa al contenedor y `TZ` configurable; `.dockerignore` que excluye `.env` y `target/`. |
| RF-09-04 | `compose.yaml` local con PostgreSQL (healthcheck y volumen) y el backend, que toma los secretos del `.env`. |
| RF-09-05 | Blueprint `render.yaml` con el servicio Docker y sus variables de entorno. |
| RF-09-06 | `vercel.json` en el frontend con el rewrite de `/api/*` a Render y el fallback de la SPA a `index.html`. |
| RF-09-07 | Documentación: variables por entorno (local, Docker, Render, Vercel), comandos de Docker y la decisión del rewrite, en README, CLAUDE.md y constituciones. |

## Requisitos no funcionales

- **RNF-09-01** Seguridad: ningún secreto en la imagen, el repo, el compose ni el blueprint; la seguridad de sesión (cookies `HttpOnly`, `SameSite=Strict`, CSRF) no cambia.
- **RNF-09-02** Paridad: una sola imagen para local y nube; las diferencias viven solo en variables de entorno y en el perfil.
- **RNF-09-03** Recursos: arranca y responde dentro de los 512 MB del plan gratis de Render y con pocas conexiones a Supabase.
- **RNF-09-04** El desarrollo actual (`./mvnw spring-boot:run`, `java -jar` con `.env`, `npm run dev`) sigue funcionando igual.

## Fuera de alcance

- Desplegar de verdad en Render y Vercel (crear cuentas o servicios, dominios): fase posterior.
- Migraciones con Flyway: con `validate`, un cambio de esquema futuro necesitará migraciones; se especificará cuando haga falta.
- CORS y cookies `SameSite=None`: descartado al elegir el rewrite de Vercel.
- Dockerizar el frontend (Vercel compila el build de Vite) y CI/CD.
- Evitar que la instancia gratis de Render se duerma.

## Preguntas abiertas

- La URL final del backend en Render depende del nombre libre del servicio; `vercel.json` usa la del blueprint (`cartera-api.onrender.com`) y se confirma al desplegar.
