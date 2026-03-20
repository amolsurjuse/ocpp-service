FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 8082
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
