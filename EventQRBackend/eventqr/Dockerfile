FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src ./src
# Tests are NOT run here: the image build skips them to keep the package layer fast and
# hermetic. CI runs `mvn verify` on every push/PR (.github/workflows/ci.yml) and the
# OWASP dependency-check weekly (dependency-check.yml). Never gate a deploy on the image build
# alone without a green test run upstream.
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN useradd --create-home --shell /bin/bash appuser

COPY --from=build /workspace/target/eventqr-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 10000

# JVM sizing for a 512MB container (Render free tier, 0.1 CPU). Without a cap the JVM
# takes only 25% of RAM for heap (~128MB), which is too small for Spring + Hibernate.
# 65% heap leaves ~180MB for metaspace, code cache, and thread stacks so the container is
# not OOM-killed by the kernel. SerialGC is the cheapest collector on a fraction of a CPU;
# ExitOnOutOfMemoryError makes a heap OOM crash the process so Render restarts it instead
# of leaving a half-dead JVM serving errors. JAVA_TOOL_OPTIONS is read by the JVM itself,
# so setting it in the Render environment replaces these defaults without a rebuild.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:ReservedCodeCacheSize=64m -Xss512k"

# Container-level health probe against the app's own endpoint (it also checks the DB).
# The JRE image is not guaranteed to ship curl/wget, so this speaks HTTP over bash's
# /dev/tcp instead of installing a package. Render ignores Docker HEALTHCHECK and uses
# its own "Health Check Path" setting (/api/v1/health); this one is for local/other hosts.
# start-period is long because Spring + Flyway startup on 0.1 CPU can take minutes.
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT:-10000} && printf 'GET /api/v1/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && timeout 8 head -n1 <&3 | grep -q ' 200'"]

USER appuser

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -Dserver.address=0.0.0.0 -jar /app/app.jar"]