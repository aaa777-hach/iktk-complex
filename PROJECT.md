# iktk-complex — конституция проекта

> Стабильные факты. Обновлять редко (архитектура, порты, креды, соглашения).
> Живое состояние — в `## 🔄 HANDOFF` в начале `PROGRESS.md`.
> Снимок на конец сессии — `handoff-*.md` (генерируется `handoff.ps1`).

## 1. Что это
Платформа проектирования техпроцессов (ТП) для машиностроения.
Микросервисы: Spring Boot 3.3.5, Java 21, Maven multi-module.
Инфраструктура: PostgreSQL, RabbitMQ (vhost `/iktk`), Redis, MinIO, Flyway, JWT (HS256), Spring Cloud Gateway.

## 2. Стек и версии
- Spring Boot 3.3.5, Spring Cloud 2023.0.3, Spring Statemachine 4.0.0
- Java 21, Maven
- JJWT 0.11.5, MinIO 8.5.12, MapStruct 1.6.2, Lombok 1.18.34
- Flyway — во всех сервисах с БД, кроме `auth` (там seed вручную)

## 3. Карта репозитория
iktk-complex/
├── core/                  библиотека: домен, DTO
├── shared/                библиотека: утилиты, MinioConfig
├── gateway/               API-шлюз, JWT-фильтр
├── auth/                  JWT, RBAC/ABAC, users/roles/permissions
├── orchestrator/          state machine, decision_log
├── modules/
│   ├── casting/           литьё (port 8090)
│   ├── forging/           ковка (8093)
│   ├── machining/         механообработка (8096)
│   ├── norming/           нормирование (8095)
│   ├── integration/       геометрия (8092)
│   └── assembly-process-service/  сборка (8097)
├── services/
│   ├── equipment-service/ (8094)
│   ├── bom-service/       (8091)
│   └── recommendation-service/ (8098)
└── PROGRESS.md            журнал + HANDOFF

## 4. Порты (DEV)
| Сервис | Порт | БД |
|---|---|---|
| gateway | **проверить** (был 8080, конфликт с orchestrator) | — |
| orchestrator | 8080 | iktk_orchestrator |
| auth | 8081 | iktk_auth |
| casting | 8090 | iktk_casting |
| bom-service | 8091 | iktk_bom |
| integration | 8092 | iktk_geometry |
| forging | 8093 | iktk_forging |
| equipment-service | 8094 | iktk_equipment |
| norming | 8095 | iktk_norming |
| machining | 8096 | iktk_machining |
| assembly-process-service | 8097 | iktk_assembly |
| recommendation-service | 8098 | iktk_recommendation |
| tooling | не реализован | — |

## 5. Креды (DEV — не для прода)
| Что | Значение |
|---|---|
| PostgreSQL | host `localhost:5432`, user `iktk_app` / `iktk_dev_password` |
| RabbitMQ | `localhost:5672`, mgmt 15672, user `iktk` / `iktk_dev_password`, **vhost `/iktk`** |
| Redis | `localhost:6379` |
| MinIO | `localhost:9000` / `localhost:9001`, user `iktk-minio` / `iktk_dev_password`, bucket `iktk` |
| JWT | HS256, issuer `iktk`, secret в `application.yml` (в prod — `IKTK_JWT_SECRET`) |
| Admin | `admin` / `admin123` (было `admin`, сброшен в Auth.1) |

## 6. Порядок запуска (после перезагрузки Windows)
1. `docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio`
2. Дождаться `iktk-postgres` → `healthy` (иначе `Connection refused: localhost:5432`)
3. Модули (каждый в своём окне): `integration:8092` → `equipment:8094` → `bom:8091` → `casting:8090` → `forging:8093` → `machining:8096` → `assembly:8097` → `recommendation:8098` → `norming:8095`
4. `auth:8081` → `orchestrator:8080` → `gateway:?`
5. Smoke: `Invoke-RestMethod http://localhost:8081/actuator/health`

## 7. Грабли (топ-10 из 16 в PROGRESS.md)
1. **BOM в файлах** — только `[System.IO.File]::WriteAllText(..., UTF8Encoding $false)` или VS Code.
2. **`[System.IO.File]` игнорирует `cd`** — рабочая папка процесса = `C:\WINDOWS\system32`. Использовать абсолютные пути (`Join-Path $root ...`).
3. **`$pid` — read-only.** Использовать `$ownPid`, `$portPid`.
4. **`mvn clean package` при запущенной JVM** — сначала `Get-Process java | Stop-Process -Force`.
5. **`spring-boot:run` читает `./application.yml` в корне модуля раньше, чем classpath** — в корне не должно быть `application.yml`, только в `src/main/resources/`.
6. **`$ErrorActionPreference="Stop"` + git + CRLF-warning** = падение. Для git: `Continue` + проверка `$LASTEXITCODE`.
7. **Java-код в PowerShell** — `ParserError`. Копировать только блоки `# ====` или через `code`.
8. **`@JsonIgnore` на вычисляемые getter'ы** (`getSummary()`, `getCuttingMode()` alias) — иначе `UnrecognizedPropertyException` при JSONB-десериализации.
9. **RabbitMQ vhost `/iktk` обязателен** — иначе `ACCESS_REFUSED`.
10. **`default-requeue-rejected: false`** в orchestrator — иначе битое сообщение зацикливается (requeue → бесконечный цикл). Ошибки идут в DLQ.

## 8. Соглашения
- Ветка `main` ↔ `origin/main`. Репо: https://github.com/aaa777-hach/iktk-complex
- Коммиты: `feat:` `fix:` `docs:` `refactor:` `test:` `chore:`
- **Перед коммитом** — `mvn -pl <модули> -am verify` → `BUILD SUCCESS`.
- **В конце сессии** — обновить `## 🔄 HANDOFF` в `PROGRESS.md`, прогнать `.\handoff.ps1`, закоммитить.
- `.env` не коммитить (только `.env.example`); `*.log`, `target/` — в `.gitignore`.
- Секреты в `application.yml` — через `${ENV_VAR:default}`, не хардкод (в prod — без default).

## 9. Ссылки
- GitHub: https://github.com/aaa777-hach/iktk-complex
- PROGRESS.md — журнал сессий + HANDOFF
- `.env.example` — обязательные env-переменные для prod