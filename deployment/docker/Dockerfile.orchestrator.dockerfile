# =============================================================================
# ИКТК: Dockerfile для сервиса-оркестратора (Java Spring Boot)
# Путь: deployment/docker/Dockerfile.orchestrator
# Назначение: многостадийная сборка production-образа оркестратора
#             с минимальным runtime-слоем и защитой контейнера.
# =============================================================================

# -----------------------------------------------------------------------------
# Стадия 1: base — общие настройки Java
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS base

LABEL maintainer="ИКТК Team" \
      description="Интегрированный конструкторско-технологический комплекс — оркестратор"

ENV LANG=C.UTF-8 \
    LANGUAGE=C.UTF-8 \
    LC_ALL=C.UTF-8

WORKDIR /build

# -----------------------------------------------------------------------------
# Стадия 2: deps — загрузка зависимостей Maven/Gradle
# -----------------------------------------------------------------------------
FROM base AS deps

# Копируем манифесты и wrapper для эффективного кэширования
COPY orchestrator/pom.xml ./orchestrator/pom.xml
COPY core/pom.xml ./core/pom.xml
COPY pom.xml ./pom.xml
COPY .mvn/ ./.mvn/ 2>/dev/null || true
COPY mvnw mvnw.cmd ./ 2>/dev/null || true

# Загрузка зависимостей в локальный репозиторий (кэшируется)
RUN if [ -f mvnw ]; then \
        chmod +x mvnw && ./mvnw -B -q dependency:go-offline -DskipTests; \
    elif command -v mvn >/dev/null 2>&1; then \
        mvn -B -q dependency:go-offline -DskipTests; \
    else \
        apk add --no-cache maven && \
        mvn -B -q dependency:go-offline -DskipTests; \
    fi

# -----------------------------------------------------------------------------
# Стадия 3: builder — сборка приложения
# -----------------------------------------------------------------------------
FROM deps AS builder

# Аргументы сборки (передаются из CI/CD)
ARG APP_VERSION=1.0.0
ARG BUILD_NUMBER=local
ARG GIT_COMMIT=unknown

ENV APP_VERSION=${APP_VERSION} \
    BUILD_NUMBER=${BUILD_NUMBER} \
    GIT_COMMIT=${GIT_COMMIT}

# Копируем исходники
COPY core/ ./core/
COPY orchestrator/ ./orchestrator/
COPY pom.xml ./pom.xml

# Сборка оркестратора (без тестов — тесты прогоняются в CI отдельно)
RUN if [ -f mvnw ]; then \
        ./mvnw -B -q clean package -DskipTests \
            -Dmaven.test.skip=true \
            -Dapp.version=${APP_VERSION} \
            -Dbuild.number=${BUILD_NUMBER} \
            -Dgit.commit=${GIT_COMMIT} \
            -pl orchestrator -am; \
    else \
        mvn -B -q clean package -DskipTests \
            -Dmaven.test.skip=true \
            -Dapp.version=${APP_VERSION} \
            -Dbuild.number=${BUILD_NUMBER} \
            -Dgit.commit=${GIT_COMMIT} \
            -pl orchestrator -am; \
    fi

# Проверка, что JAR создан
RUN test -f orchestrator/target/orchestrator.jar || \
    (echo "ОШИБКА: JAR не создан" && ls -la orchestrator/target/ && exit 1)

# -----------------------------------------------------------------------------
# Стадия 4: runtime — минимальный JRE-образ
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="iktk-orchestrator" \
      org.opencontainers.image.description="ИКТК — сервис-оркестратор ТПП" \
      org.opencontainers.image.vendor="ИКТК" \
      org.opencontainers.image.licenses="Proprietary"

# Устанавливаем утилиты и временную зону
RUN apk add --no-cache \
        tzdata \
        curl \
        tini \
        dumb-init \
        ca-certificates && \
    cp /usr/share/zoneinfo/Europe/Moscow /etc/localtime && \
    echo "Europe/Moscow" > /etc/timezone && \
    apk del tzdata && \
    rm -rf /var/cache/apk/*

# Создаём непривилегированного пользователя
RUN addgroup -g 1000 -S iktk && \
    adduser -u 1000 -S iktk -G iktk && \
    mkdir -p /app /var/log/iktk /tmp/iktk-storage && \
    chown -R iktk:iktk /app /var/log/iktk /tmp/iktk-storage

WORKDIR /app

# Копируем JAR из стадии builder
COPY --from=builder --chown=iktk:iktk \
    /build/orchestrator/target/orchestrator.jar \
    /app/orchestrator.jar

# Переменные окружения
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/iktk/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=Europe/Moscow" \
    SPRING_PROFILES_ACTIVE=k8s \
    SERVER_PORT=8080 \
    MANAGEMENT_SERVER_PORT=8081 \
    TZ=Europe/Moscow

# Переключение на непривилегированного пользователя
USER iktk:iktk

# Порты
EXPOSE 8080 8081

# Health-check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1

# Точка входа: tini/dumb-init как PID 1 для корректной обработки сигналов
ENTRYPOINT ["/sbin/tini", "--"]

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/orchestrator.jar"]