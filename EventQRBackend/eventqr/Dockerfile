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

# JVM sizing for a 512MB container (Render free tier, ~0.1 CPU). Without a cap the JVM
# takes only 25% of RAM for heap (~128MB), which is too small for Spring + Hibernate.
# 65% heap leaves ~180MB for metaspace, code cache, and thread stacks so the container is
# not OOM-killed by the kernel. SerialGC is the cheapest collector on a fraction of a CPU;
# ExitOnOutOfMemoryError makes a heap OOM crash the process so Render restarts it instead
# of leaving a half-dead JVM serving errors. JAVA_TOOL_OPTIONS is read by the JVM itself,
# so setting it in the Render environment replaces these defaults without a rebuild.
#
# -XX:ActiveProcessorCount was dropped: virtual threads are off in prod
# (spring.threads.virtual.enabled=false), so it no longer serves its original purpose, and on a
# cgroup-quota host (Render free tier) Java 21 already reports 1 processor -- the flag would only
# RAISE that perceived count. It does not affect JIT tuning either. Tomcat (20/2), Hikari (5) and
# the scheduler (1) are all explicitly sized, so nothing depends on it.
#
# GC logging is intentionally on stderr (fd 2) at the JVM's info level as one line per collection,
# plus the heap summary. It is deliberately the 'gc' tag set and NOT 'gc*': enough to tell whether
# GC pauses were responsible for a missed probe, without flooding the log with per-phase noise.
# stderr is chosen to keep stdout JSON-only: the only logback appender is LogstashEncoder JSON on
# stdout (see logback-spring.xml), so GC lines on fd 1 would corrupt the structured stream that
# Render/Loki ingest. Render captures stderr as well, so GC visibility is preserved.
# Revert to stdout only if the pipeline proves to capture stdout exclusively.
# Local measurement on DEVELOPER HARDWARE (NOT the 0.1-vCPU container -- do not read these as
# production figures): 23 collections / 326 ms total pause during a cold start (max 80 ms, a
# metadata-threshold full GC). -XX:TieredStopAtLevel=1 was measured and NOT added: it made the
# cold start slower (12.9 s vs 11.9 s), because startup is class-loading bound, not JIT bound.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:ReservedCodeCacheSize=64m -Xss512k -Xlog:gc:stderr:time,uptime,level,tags"

# Container-level probe against the SAME path Render probes (/actuator/health/readiness), so a
# local or other-host check exercises what production exercises. The readiness group is pinned
# to in-process state only (readinessState) and never touches the DB, mail or a remote service —
# measured locally: ~3 ms with no database reachable, against ~5 s for a DB-backed check (the
# Hikari connection-timeout, i.e. the whole 5 s Render budget). The JRE image is not guaranteed
# to ship curl/wget, so this speaks HTTP over bash's /dev/tcp instead of installing a package.
# Render ignores Docker HEALTHCHECK and uses its own "Health Check Path" setting; keep that set
# to /actuator/health/readiness too. start-period is long because Spring + Flyway startup on
# 0.1 CPU can take minutes.
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT:-10000} && printf 'GET /actuator/health/readiness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && timeout 8 head -n1 <&3 | grep -q ' 200'"]

USER appuser

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -Dserver.address=0.0.0.0 -jar /app/app.jar"]