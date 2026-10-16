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
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
RUN chown spring:spring /app/app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
