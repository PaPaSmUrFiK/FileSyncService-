# Dockerfile для FileService
# Multi-stage build для оптимизации размера образа

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Копируем pom.xml и загружаем зависимости (кэширование слоя)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY src ./src

# Компилируем protobuf и собираем приложение
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаем пользователя для запуска приложения (security best practice)
RUN addgroup -S spring && adduser -S spring -G spring

# Копируем jar из build stage
COPY --from=build /app/target/*.jar app.jar

# Создаем директорию для логов
RUN mkdir -p /var/log/file-service && \
  chown -R spring:spring /var/log/file-service

# Переключаемся на непривилегированного пользователя
USER spring:spring

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Expose ports
EXPOSE 8083 9093

# JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Запуск приложения
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]

