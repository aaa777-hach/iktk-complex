# =============================================================================
# ИКТК: Dockerfile для веб-клиента (React + TypeScript + Vite)
# Путь: deployment/docker/Dockerfile.ui
# Назначение: многостадийная сборка production-образа веб-клиента
#             с раздачей статики через nginx.
# =============================================================================

# -----------------------------------------------------------------------------
# Стадия 1: base — общие настройки Node.js
# -----------------------------------------------------------------------------
FROM node:20-alpine AS base

LABEL maintainer="ИКТК Team" \
      description="Интегрированный конструкторско-технологический комплекс — веб-клиент"

ENV NODE_ENV=production \
    NPM_CONFIG_LOGLEVEL=warn \
    NPM_CONFIG_FUND=false \
    NPM_CONFIG_AUDIT=false

WORKDIR /app

# -----------------------------------------------------------------------------
# Стадия 2: deps — установка зависимостей
# -----------------------------------------------------------------------------
FROM base AS deps

# Копируем манифесты для эффективного кэширования слоёв
COPY ui/web/package.json ui/web/package-lock.json* ./

# Установка зависимостей: npm ci (воспроизводимо) или npm install (fallback)
RUN if [ -f package-lock.json ]; then \
        npm ci --prefer-offline --no-audit --no-fund; \
    else \
        npm install --prefer-offline --no-audit --no-fund; \
    fi

# -----------------------------------------------------------------------------
# Стадия 3: builder — сборка приложения
# -----------------------------------------------------------------------------
FROM deps AS builder

# Аргументы сборки (передаются из CI/CD или docker-compose)
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_APP_VERSION=dev
ARG VITE_BUILD_NUMBER=local

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_APP_VERSION=${VITE_APP_VERSION} \
    VITE_BUILD_NUMBER=${VITE_BUILD_NUMBER}

# Копируем исходники веб-клиента
COPY ui/web/ ./

# Сборка production-бандла (Vite)
RUN npm run build

# Проверка, что сборка создала артефакты
RUN test -d dist && test -f dist/index.html || \
    (echo "ОШИБКА: сборка не создала dist/index.html" && exit 1)

# -----------------------------------------------------------------------------
# Стадия 4: runtime — nginx с собранной статикой
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="iktk-ui" \
      org.opencontainers.image.description="ИКТК — веб-клиент" \
      org.opencontainers.image.vendor="ИКТК"

# Устанавливаем временную зону и утилиты
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Europe/Moscow /etc/localtime && \
    echo "Europe/Moscow" > /etc/timezone && \
    apk del tzdata

# Удаляем дефолтную конфигурацию nginx
RUN rm -f /etc/nginx/conf.d/default.conf

# Копируем кастомную конфигурацию nginx
COPY deployment/docker/nginx/ui.conf /etc/nginx/conf.d/ui.conf

# Копируем собранную статику
COPY --from=builder /app/dist /usr/share/nginx/html

# Права: nginx работает от пользователя nginx
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html

# Health-check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:80/healthz || exit 1

EXPOSE 80

# Проверка конфигурации nginx и запуск
CMD ["nginx", "-g", "daemon off;"]