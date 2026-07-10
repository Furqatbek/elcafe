FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:resolve || mvn dependency:resolve || mvn dependency:resolve

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Set timezone to Tashkent (GMT+5)
ENV TZ=Asia/Tashkent
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /app/target/*.jar app.jar

# Run as a non-root user (fixed uid/gid 1000 so host-mounted ./logs can be chowned predictably).
# The mount points are created here and owned by the app user; a named volume inherits this ownership,
# and a host bind-mount for ./logs must be writable by uid 1000 on the host.
RUN groupadd -g 1000 app && useradd -u 1000 -g app -s /usr/sbin/nologin app \
    && mkdir -p /app/logs /app/uploads \
    && chown -R app:app /app
USER app

EXPOSE 8080

# Container-aware heap: size to the container's memory limit (set one in compose/k8s) instead of a fixed
# 512m that OOMs under real load, and dump the heap on OOM so a crash is diagnosable rather than silent.
# -XX:+ExitOnOutOfMemoryError lets the orchestrator restart cleanly. Override JAVA_OPTS to tune.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 \
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -XX:+ExitOnOutOfMemoryError \
-Duser.timezone=Asia/Tashkent"

# exec form so the JVM replaces the shell and becomes PID 1 — SIGTERM then reaches it and Spring's
# graceful shutdown (server.shutdown=graceful) can drain in-flight requests instead of being SIGKILLed.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
