# Imagen del backend en dos etapas: se compila con el JDK y se ejecuta solo con el JRE.
# La configuración llega por variables de entorno (ver specs/09-docker-despliegue/plan.md):
#   docker build -t cartera-api .
#   docker run --env-file .env -e SPRING_PROFILES_ACTIVE=prod -p 8080:8080 cartera-api

# --- Etapa 1: compilar el JAR ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Primero solo el wrapper y el pom: mientras no cambien las dependencias,
# Docker reutiliza esta capa y no las vuelve a descargar.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Después el código. Los tests no corren aquí: necesitan la base de datos del .env
# y se ejecutan antes, con ./mvnw test.
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# --- Etapa 2: ejecutar ---
FROM eclipse-temurin:25-jre
WORKDIR /app

# Usuario sin privilegios: si alguien compromete la app, no es root dentro del contenedor.
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
USER spring

# Misma zona horaria que en desarrollo: las fechas de auditoría (LocalDateTime) usan la de la JVM.
ENV TZ=America/Santiago
# La JVM usa hasta el 75 % de la memoria del contenedor (plan gratis de Render: 512 MB).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Puerto por defecto; Render lo cambia con la variable PORT.
EXPOSE 8080

# Forma exec (con corchetes): java es el proceso principal y recibe la señal de docker stop.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
