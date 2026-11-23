# Plan técnico — Spec 09

Cómo se implementa [`spec.md`](spec.md) respetando las constituciones del
[backend](../constitution.md) y del [frontend](../../../4-react/specs/constitution.md).

## 1. Arquitectura por entorno

```
Local (desarrollo)       navegador → Vite :5173 ──proxy /api──→ backend :8080 → PostgreSQL local
Local (Docker)           navegador → Vite :5173 ──proxy /api──→ contenedor api :8080 → contenedor db
Nube (preparada)         navegador → Vercel (*.vercel.app) ──rewrite /api──→ Render (Docker) → Supabase
```

En los tres casos el navegador habla con **un solo origen**, así que la API no cambia: sin CORS,
cookies `SameSite=Strict` y CSRF por cookie `XSRF-TOKEN`.

## 2. Variables de entorno

| Variable | Desarrollo (`.env`) | Docker local (`compose.yaml`) | Render | Notas |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | — | `prod` | Activa `application-prod.properties`. |
| `PORT` | — (8080) | — (8080) | la asigna Render | `server.port=${PORT:8080}`. |
| `DB_URL` | local o Supabase | `jdbc:postgresql://db:5432/spring_api` | Supabase (Session pooler) | Obligatoria con `prod`. |
| `DB_USERNAME` / `DB_PASSWORD` | `.env` | `postgres` / `POSTGRES_PASSWORD` del `.env` (por defecto `postgres`) | panel de Render (secreto) | Obligatorias con `prod`. |
| `DB_POOL_SIZE` | — | — | opcional (5) | Conexiones de Hikari con `prod`. |
| `JWT_SECRET` | `.env` | del `.env` | panel (secreto, **distinta** a la de desarrollo) | Obligatoria siempre. |
| `JWT_COOKIE_SECURE` | `false` | `false` | `true` | Render y Vercel sirven HTTPS. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `.env` | del `.env` | panel (secreto), solo para el primer arranque | `AdminUserInitializer`. |
| `TZ` | — (la del sistema) | `America/Santiago` (imagen) | `America/Santiago` (imagen) | Fechas de auditoría `LocalDateTime`. |
| `JAVA_TOOL_OPTIONS` | — | `-XX:MaxRAMPercentage=75` (imagen) | igual | Memoria relativa al contenedor. |
| `VITE_API_URL` (frontend) | vacía | vacía | — (Vercel: vacía) | Mismo origen gracias al proxy o al rewrite. |

## 3. Piezas

| Pieza | Repo | Cómo |
|---|---|---|
| `src/main/resources/application.properties` | backend | Agrega `server.port=${PORT:8080}`. El resto no cambia (es el perfil de desarrollo). |
| `src/main/resources/application-prod.properties` | backend | `spring.datasource.url=${DB_URL}` (y usuario y contraseña) sin valor por defecto, `spring.datasource.hikari.maximum-pool-size=${DB_POOL_SIZE:5}`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.show-sql=false`, `hibernate.format_sql=false`, `spring.sql.init.mode=never`, `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`, `server.forward-headers-strategy=framework`. |
| `src/test/.../ProdProfileTest.java` | backend | Test unitario que lee `application-prod.properties` y comprueba esos valores (que nadie reactive el seed o Swagger en prod sin querer). |
| `Dockerfile` | backend | Etapa `build` en `eclipse-temurin:25-jdk`: copia `mvnw`, `.mvn` y `pom.xml`, descarga dependencias (capa cacheable), copia `src` y ejecuta `./mvnw -DskipTests package`. Etapa final en `eclipse-temurin:25-jre`: usuario `spring` sin privilegios, copia solo el JAR como `app.jar`, `ENV TZ=America/Santiago`, `ENV JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, `EXPOSE 8080`, `ENTRYPOINT ["java","-jar","/app/app.jar"]`. |
| `.dockerignore` | backend | Excluye `.env`, `.env.*` (salvo `.env.example`), `target/`, `.git`, `.idea`, `docker/`, `specs/` y archivos del IDE. |
| `compose.yaml` | backend | Servicio `db` (`postgres:18`, `POSTGRES_DB=spring_api`, volumen `db-data`, healthcheck `pg_isready`, **sin** publicar el 5432 para no chocar con el contenedor local que ya existe). Servicio `api` (`build: .`, puerto `8080:8080`, `depends_on: db: condition: service_healthy`, `DB_URL` hacia `db`, `JWT_COOKIE_SECURE=false` y `JWT_SECRET: ${JWT_SECRET:?…}`, `ADMIN_EMAIL` y `ADMIN_PASSWORD` interpolados del `.env`, que compose lee solo). Sin perfil `prod`: en local se quiere el seed. |
| `render.yaml` | backend | Blueprint: `type: web`, `name: cartera-api`, `runtime: docker`, `plan: free`, `healthCheckPath: /actuator/health`, `envVars` con `SPRING_PROFILES_ACTIVE=prod` y `JWT_COOKIE_SECURE=true` como valores, y `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` con `sync: false` (se piden en el panel al crear el servicio). |
| `vercel.json` | frontend | `rewrites`: primero `/api/:path*` → `https://cartera-api.onrender.com/api/:path*`; después `/(.*)` → `/index.html` (fallback de la SPA). Vercel sirve primero los archivos del build, así que los assets no pasan por el fallback. |
| `.env.example` | frontend | Comentario: `VITE_API_URL` queda vacía también en Vercel (el rewrite da el mismo origen). |

## 4. Decisiones

- **Rewrite de Vercel en vez de dominios separados** (decisión del usuario): con dominios separados habría que pasar las cookies a `SameSite=None`, agregar CORS con credenciales, cambiar el CSRF del frontend para leer el token del cuerpo y, aun así, Safari y Brave bloquearían las cookies de terceros. El rewrite replica en la nube lo que el proxy de Vite hace en local. Costo: la URL de Render queda en `vercel.json` (los rewrites no leen variables de entorno); no es un secreto.
- **Perfil `prod` en vez de muchas variables sueltas**: lo que cambia siempre junto (seed, `ddl-auto`, SQL, Swagger, proxy) va en un archivo versionado; las variables quedan solo para lo que depende del entorno (credenciales, URL, puerto). Spring igual permite sobrescribir cualquier propiedad con una variable (`SPRING_JPA_SHOW_SQL=true`).
- **Sin valores por defecto en `prod`**: con `${DB_URL}` sin `:` la app falla al arrancar si falta la variable, en vez de intentar `localhost` con `postgres/postgres`.
- **`ddl-auto=validate` en `prod`**: el esquema de Supabase ya existe (lo creó `update`); `validate` impide que Hibernate lo altere en producción. Los cambios de esquema futuros necesitarán Flyway (fuera de alcance).
- **`spring.config.import=optional:file:.env` se queda**: en el contenedor no hay `.env` y `optional:` lo ignora; las variables llegan del entorno.
- **Tests fuera de la imagen**: `ApplicationTests` necesita la base del `.env`; en `docker build` no hay base, así que la imagen compila con `-DskipTests` y `./mvnw test` se ejecuta antes, como en cada tarea.
- **JAR completo, sin capas extraídas**: más simple de explicar en el curso; la caché de dependencias de Maven ya acelera los builds repetidos.
- **`TZ=America/Santiago` en la imagen**: el contenedor arranca en UTC y `createdAt` (`LocalDateTime`) se guardaría corrido 3–4 h respecto de los datos ya cargados desde desarrollo, lo que afectaría los filtros por fecha y «nuevos este mes».
- **`forward-headers-strategy=framework`**: Render (y Vercel delante) terminan el HTTPS; con esto Spring respeta `X-Forwarded-Proto` y `X-Forwarded-Host` en redirecciones y URLs generadas.
- **Pool de 5 conexiones en `prod`**: el Session pooler de Supabase en plan gratis tiene pocas conexiones y la instancia gratis de Render es pequeña.
- **Compose sin publicar el 5432**: el contenedor `3-spring-api-db` del README ya usa ese puerto; la base del compose solo la necesita el backend dentro de la red de compose.

## 5. Riesgos

- **Arranque en frío de Render (plan gratis)**: la instancia se duerme tras 15 min sin tráfico y Spring Boot tarda en levantar con poca CPU. Vercel espera hasta 120 s al backend en un rewrite; si se pasa, la primera petición da 504 y la siguiente funciona.
- **Caché de rewrites en Vercel**: desde abril de 2026 los rewrites externos respetan el `Cache-Control` del origen. Spring Security responde `no-cache, no-store` por defecto, así que las respuestas con `Set-Cookie` no se cachean; se revisará al desplegar.
- **Cookies `Secure` en `http://localhost`**: con `JWT_COOKIE_SECURE=true` en local, Chrome y Firefox las aceptan, pero curl y Postman no; por eso el compose usa `false`.
- **Cambios de esquema con `validate`**: un campo nuevo en una entity impedirá arrancar en `prod` hasta que exista la migración.

## 6. Trazabilidad

| Requisito | Tareas |
|---|---|
| RF-09-01 | I-01 |
| RF-09-02 | I-01 |
| RF-09-03 | I-02 |
| RF-09-04 | I-03 |
| RF-09-05 | I-04 |
| RF-09-06 | I-05 |
| RF-09-07 | I-06 |
| RNF-09-01 | I-02, I-03, I-04 |
| RNF-09-02 | I-02, I-03 |
| RNF-09-03 | I-01, I-02 |
| RNF-09-04 | I-01, I-03 |
