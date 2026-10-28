# Builds one backend module. Usage: docker build --build-arg MODULE=asset-service -f backend/Dockerfile backend
FROM maven:3.9-eclipse-temurin-25 AS build
ARG MODULE
WORKDIR /workspace
COPY pom.xml .
COPY asset-service/pom.xml asset-service/pom.xml
COPY telemetry-service/pom.xml telemetry-service/pom.xml
COPY alert-service/pom.xml alert-service/pom.xml
COPY maintenance-service/pom.xml maintenance-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY ai-insights-service/pom.xml ai-insights-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline
COPY . .
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/${MODULE}-*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
