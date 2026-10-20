# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
# Resolve dependencies first so this layer is cached while only src/ changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in the GitHub Actions "test" job, so they are skipped here
RUN mvn -B -q -DskipTests package && cp target/jobtrack-*.jar /src/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system jobtrack \
    && useradd --system --gid jobtrack --home-dir /app --no-create-home jobtrack \
    && mkdir -p /app/data/files \
    && chown -R jobtrack:jobtrack /app
WORKDIR /app
COPY --from=build --chown=jobtrack:jobtrack /src/app.jar /app/app.jar
USER jobtrack

ENV PORT=5566 \
    JOBTRACK_DATA_DIR=/app/data
EXPOSE 5566
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
