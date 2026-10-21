FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

COPY modules modules
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid appuser --create-home --home-dir /app appuser \
    && mkdir -p /var/lib/greenhouse/slip-photos \
    && chown appuser:appuser /var/lib/greenhouse/slip-photos

COPY --from=build /workspace/build/libs/*.jar app.jar

USER appuser

ENV SALES_PHOTO_STORAGE_ROOT=/var/lib/greenhouse/slip-photos

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
