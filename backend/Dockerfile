FROM eclipse-temurin:21-jdk-alpine-3.23@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-noble@sha256:ca397720325ceefe39ce397f186759fc87d9efafb2dc4ce53315980844c2f4f2 AS runtime

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
