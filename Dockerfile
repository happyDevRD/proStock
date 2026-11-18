# Build (imagen Gradle oficial: no requiere gradle-wrapper.jar en el repo)
FROM gradle:8.12.1-jdk17-alpine AS builder
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon

# Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Fuentes para dibujar texto con Java (marca de agua de las previas del portal de selección)
RUN apk add --no-cache fontconfig ttf-dejavu
# exiftool: saca de los RAW (ARW, CR3…) la JPG de vista previa para el portal de selección
RUN apk add --no-cache exiftool
# Zona del negocio también para el sistema (logs, `date`). La JVM la fija en main() con APP_TIMEZONE.
RUN apk add --no-cache tzdata
ENV TZ=America/Santo_Domingo \
    APP_TIMEZONE=America/Santo_Domingo
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
RUN chown spring:spring /app/app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
