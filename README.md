# API REST de clientes con Spring Boot

API REST para gestionar clientes (`Customer`) construida con Spring Boot 4, Spring Data JPA y PostgreSQL, con validación de datos y manejo de errores en formato estándar `ProblemDetail` (RFC 9457).

Proyecto del curso de Spring Boot con Claude Code.

## Stack

- Java 25
- Spring Boot 4.1.2 (Spring Web MVC, Validation, Actuator, DevTools)
- Spring Data JPA + Hibernate 7
- PostgreSQL
- springdoc-openapi (Swagger UI)
- Maven (con Maven Wrapper)

## Requisitos

- JDK 25
- PostgreSQL, o Docker para levantarlo en un contenedor

## Puesta en marcha

1. Clona el repositorio:

   ```bash
   git clone https://github.com/andresguzf/curso-springboot-claude-api.git
   cd curso-springboot-claude-api
   ```

2. Crea tu archivo de variables de entorno a partir de la plantilla y ajusta los valores:

   ```bash
   cp .env.example .env
   ```

   | Variable | Descripción | Valor por defecto |
   |---|---|---|
   | `DB_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/spring_api` |
   | `DB_USERNAME` | Usuario de la base de datos | `postgres` |
   | `DB_PASSWORD` | Contraseña de la base de datos | `postgres` |

   La aplicación lee el `.env` de la raíz del proyecto al arrancar. Este archivo no se sube al repositorio.

3. Levanta PostgreSQL (si no lo tienes instalado, con Docker):

   ```bash
   docker run --name 3-spring-api-db -e POSTGRES_DB=spring_api -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:18
   ```

4. Ejecuta la aplicación desde la raíz del proyecto:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows usa `mvnw.cmd spring-boot:run`.

Hibernate crea la tabla `customers` automáticamente al arrancar (`ddl-auto=update`).

## Endpoints

Base: `http://localhost:8080/api/customers`

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/customers` | Lista todos los clientes | 200 |
| `GET` | `/api/customers/{id}` | Obtiene un cliente | 200, 404 |
| `POST` | `/api/customers` | Crea un cliente | 201 + `Location`, 400, 409 |
| `PUT` | `/api/customers/{id}` | Actualiza un cliente | 200, 400, 404, 409 |
| `DELETE` | `/api/customers/{id}` | Elimina un cliente | 204, 404 |

Documentación interactiva en **Swagger UI**: http://localhost:8080/swagger-ui.html

Estado de la aplicación: http://localhost:8080/actuator/health

### Ejemplo: crear un cliente

```bash
curl -i -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Ana",
        "lastName": "Pérez",
        "email": "ana@ejemplo.com",
        "phone": "+56 9 0000 0000",
        "address": "Calle Falsa 123",
        "city": "Santiago",
        "birthDate": "1990-05-20"
      }'
```

Campos del cliente:

| Campo | Obligatorio | Reglas |
|---|---|---|
| `name` | Sí | máx. 80 caracteres |
| `lastName` | Sí | máx. 80 caracteres |
| `email` | Sí | formato email, único, máx. 150 |
| `phone` | No | máx. 30 |
| `address` | No | máx. 200 |
| `city` | No | máx. 80 |
| `birthDate` | No | fecha pasada (`yyyy-MM-dd`) |
| `active` | No | `true` por defecto |

Las fechas de creación y edición las asigna el sistema y no se envían en el JSON.

## Manejo de errores

Todos los errores se responden en formato `ProblemDetail`:

| Situación | Status |
|---|---|
| El cliente no existe | 404 Not Found |
| El email ya está registrado | 409 Conflict |
| Campos inválidos | 400 Bad Request, con el detalle por campo |

Ejemplo de error de validación:

```json
{
  "type": "about:blank",
  "title": "Error de validación",
  "status": 400,
  "detail": "Hay campos con errores de validación",
  "instance": "/api/customers",
  "errors": {
    "name": "El nombre es obligatorio",
    "email": "El email no tiene un formato válido"
  }
}
```

## Estructura

```
src/main/java/com/andres/course/claude/springboot/springapi/app/
├── controllers/   # endpoints REST
├── services/      # lógica de negocio (interfaz + implementación)
├── repositories/  # acceso a datos con Spring Data JPA
├── models/        # entidades JPA
├── dto/           # records de entrada/salida con validación
├── mappers/       # conversión entidad <-> DTO
└── exceptions/    # excepciones de dominio y manejador global
```

## Tests

```bash
./mvnw test
```

El test de contexto (`ApplicationTests`) necesita PostgreSQL disponible.
