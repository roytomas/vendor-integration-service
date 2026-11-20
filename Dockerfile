# syntax=docker/dockerfile:1

# --- Build stage ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so they're cached in their own layer and only
# re-downloaded when pom.xml actually changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Datadog APM tracer, fetched via Maven so no extra tooling (curl/wget) is
# needed in this already-Maven-equipped build stage. Bump DD_JAVA_AGENT_VERSION
# to pick up newer dd-trace-java releases.
ARG DD_JAVA_AGENT_VERSION=1.69.1
RUN mvn -B -q dependency:copy \
    -Dartifact=com.datadoghq:dd-java-agent:${DD_JAVA_AGENT_VERSION} \
    -DoutputDirectory=/workspace -Dmdep.stripVersion=true

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/target/vendor-integration-service-*.jar app.jar
COPY --from=build /workspace/dd-java-agent.jar dd-java-agent.jar

# Datadog APM/log-correlation defaults. The agent is always attached; if no
# Datadog Agent is reachable at DD_AGENT_HOST, trace/metric export just goes
# nowhere (fire-and-forget), so the image still runs standalone. Override any
# of these at `docker run`/deployment time - see README.md#observability.
ENV DD_SERVICE=vendor-integration-service \
    DD_ENV=dev \
    DD_VERSION=0.0.1-SNAPSHOT \
    DD_AGENT_HOST=localhost \
    DD_TRACE_AGENT_PORT=8126 \
    DD_LOGS_INJECTION=true \
    DD_RUNTIME_METRICS_ENABLED=true \
    DD_METRICS_ENABLED=false \
    DD_DOGSTATSD_PORT=8125

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/dd-java-agent.jar", "-jar", "/app/app.jar"]
