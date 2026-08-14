FROM eclipse-temurin:21-jdk-alpine-3.23@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine-3.23@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c AS runtime

RUN addgroup -S homelab && adduser -S -G homelab -h /app homelab
WORKDIR /app
COPY --from=build --chown=homelab:homelab /workspace/target/*.jar app.jar
USER homelab
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
