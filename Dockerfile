FROM maven:3.10.0-eclipse-temurin-25-noble AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=build /workspace/target/star-guard-core-0.1.0-SNAPSHOT.jar app.jar

USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

# F3-6: индекс укладывается в 1 ГБ (app.index.memory-budget-mb), heap — 1280 МБ с запасом ~20%.
# Переопределяется через JAVA_TOOL_OPTIONS при запуске контейнера.
ENV JAVA_TOOL_OPTIONS="-Xmx1280m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
