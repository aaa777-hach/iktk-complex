# Прогресс по проекту iktk-complex

> Обновляйте в конце каждой сессии. Начинайте новый день с чтения — 2 минуты, и вы в контексте.

---

## 🎯 ТЕКУЩИЙ СТАТУС (2026-10-15, конец сессии)

### ✅ РАБОТАЕТ ПОЛНЫЙ ЖИЗНЕННЫЙ ЦИКЛ ПРОЕКТА

**Клиент → gateway (JWT + маршрутизация) → orchestrator (state machine) → PostgreSQL + RabbitMQ**

**Пройден полный цикл проекта через REST API:**
PROJECT_INIT
→ GEOMETRY_INPUT
→ PART_PROCESS_PLANNING
→ TOOLING_DESIGN
→ ASSEMBLY_PROCESS_PLANNING
→ TECHNICAL_CONTROL
→ COMPLETED

text

Каждый переход подтверждён в БД (`project_state`), журнал решений ведётся (`decision_log`).

### Что работает

**Инфраструктура (Docker):**
- `iktk-postgres` — Up, healthy, порт 5432, user=`iktk_app`, pass=`iktk_dev_password`
- `iktk-rabbitmq` — Up, healthy, порты 5672/15672, user=`iktk`, pass=`iktk_dev_password`, vhost=`/iktk`
- `iktk-redis` — Up, порт 6379
- `iktk-minio` — Up, порты 9000-9001

**Сервисы (каждый в отдельном окне PowerShell):**
- `auth` — 8081 (UP)
- `orchestrator` — 8082 (UP)
- `gateway` — 8080 (UP)

**Аутентификация:**
- JWT: HS256, issuer `iktk`, access-ttl `PT15M`, refresh-ttl `P30D`
- Пользователь `admin` / `admin` (роль ADMIN)
- Gateway валидирует JWT, подставляет `X-User-Id`, `X-User-Roles`

**Orchestrator:**
- State machine: 7 состояний + точки ветвления (`TECHNICAL_CONTROL` → `CONTROL_PASSED` / `CONTROL_REJECTED`)
- Эндпоинты: `POST /projects`, `POST /projects/{id}/run`, `GET .../state`, `GET .../summary`, `GET .../history`, `POST .../approve`, `POST .../events`, `POST .../rollback`
- RabbitMQ: слушает `iktk.orchestrator.results.queue`, отправляет в `iktk.commands`
- Аудит: `decision_log` (таблица в БД)

### Известные мелкие проблемы (не критичны)

1. **`completed: false` при `state: COMPLETED`** — нестыковка в `OrchestrationResponse.buildResponse`. Нужно:
   ```java
   .completed(sm.getState().getId() == ProjectState.COMPLETED)
actor: SYSTEM в аудите, хотя действия делает admin — в OrchestratorController (и OrchestrationService) не передаётся userId в decisionLogService.log(...). Нужно принимать @RequestHeader("X-User-Id") и передавать.

availableEvents: {} при COMPLETED — так и должно быть (нет доступных переходов).

Что не проверено
POST .../events — отправка явного события (например, CONTROL_REJECTED)

POST .../rollback — откат версии

POST .../recommendations/{id}/accept|adjust|outsource — точки рекомендаций

Интеграция orchestrator ↔ модули через RabbitMQ (модули не запущены)

🚀 БЫСТРЫЙ СТАРТ (следующая сессия)
powershell
# 1. Инфраструктура
docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio
docker ps

# 2. Остановить старые Java-процессы (перед любой пересборкой!)
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# 3. Пересборка (если меняли код)
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex
mvn clean package -DskipTests

# 4. Запуск трёх сервисов — каждый в СВОЁМ окне PowerShell
# Окно 1 — auth:
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\auth
java -jar .\target\auth.jar

# Окно 2 — orchestrator:
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\orchestrator
java -jar .\target\orchestrator.jar

# Окно 3 — gateway:
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\gateway
java -jar .\target\gateway.jar

# 5. Проверка (через 30 секунд)
Invoke-RestMethod -Uri http://localhost:8081/actuator/health
Invoke-RestMethod -Uri http://localhost:8082/actuator/health
Invoke-RestMethod -Uri http://localhost:8080/actuator/health

# 6. Тест полного цикла
$body = '{"username":"admin","password":"admin"}'
$response = Invoke-RestMethod -Uri http://localhost:8081/api/v1/auth/login -Method Post -Body $body -ContentType "application/json"
$token = $response.accessToken
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

$project = '{"projectName":"Test Project","designation":"TP-001","objectType":"ASSEMBLY","requestedBy":"admin"}'
$created = Invoke-RestMethod -Uri http://localhost:8080/api/v1/orchestrator/projects -Method Post -Body $project -Headers $headers
$projectId = $created.projectId

# Проходим до COMPLETED (4 approve)
for ($i = 1; $i -le 5; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/orchestrator/projects/$projectId/approve" -Method Post -Body '{"comment":"OK"}' -ContentType "application/json" -Headers $headers
        Write-Host "state: $($r.state)"
        if ($r.state -eq "COMPLETED") { break }
    } catch { break }
}
🔑 Ключевые данные
Что	Значение
JWT-секрет	change-me-super-secret-key-for-dev-only-min-32-bytes-long-0123456789
JWT issuer	iktk
PostgreSQL	iktk_app / iktk_dev_password
RabbitMQ	iktk / iktk_dev_password, vhost /iktk
Админ	admin / admin
Порты	gateway 8080, auth 8081, orchestrator 8082
Базы PostgreSQL: iktk, iktk_auth, iktk_orchestrator

RabbitMQ:

Queue: iktk.orchestrator.results.queue, iktk.orchestrator.results.dlq

Exchange (topic): iktk.commands, iktk.results

Binding: iktk.results → iktk.orchestrator.results.queue с ключом #

Полезные SQL для проверки:

powershell
# Все проекты
docker exec -e PGPASSWORD=iktk_dev_password -it iktk-postgres psql -U iktk_app -d iktk_orchestrator -c "SELECT project_id, project_name, state, updated_at FROM project_state;"

# Журнал решений
docker exec -e PGPASSWORD=iktk_dev_password -it iktk-postgres psql -U iktk_app -d iktk_orchestrator -c "SELECT event_type, actor, created_at FROM decision_log ORDER BY created_at DESC LIMIT 20;"

# Пользователи
docker exec -e PGPASSWORD=iktk_dev_password -it iktk-postgres psql -U iktk_app -d iktk_auth -c "SELECT username, enabled, locked FROM users;"
🔧 ВСЕ ПРАВКИ (накопительно)
auth
JPA-сущности — AuditEvent, User, Role, Permission: добавлены @Entity, @Id, @GeneratedValue, @Column, @Transient для Map

TokenService.java — написан с нуля (jjwt 0.11.x)

AuthService.java — написан с нуля (login, refresh, logout, getUserInfo, changePassword); getUserInfo ищет findByUsername(userId).or(() -> findById(userId))

SecurityConfig.java — создан (BCryptPasswordEncoder + permitAll)

AuthController.java — удалены 2 @PreAuthorize

orchestrator
JPA-сущности — ProjectRuntime: @GeneratedValue, @PrePersist, @PreUpdate, @Transient; DecisionLogEntry: @GeneratedValue, @PrePersist, @Transient

ModuleResultConsumer.java — containerFactory = "rabbitListenerContainerFactory" (2 места)

OrchestrationService.java — все вызовы dispatchEvent(...) и persistState(...) теперь с projectId (было sm.getId() = null)

SecurityConfig.java — создан в config/ (permitAll)

gateway
pom.xml — добавлен spring-cloud-starter-circuitbreaker-reactor-resilience4j

RoutesConfig.java — userKeyResolver() с @Primary

application.yml — создан (allow-bean-definition-overriding, JWT-секрет, маршруты)

Корневой проект
pom.xml — закомментированы services/* (4 модуля)

Созданы *Application.java: forging, machining, norming

forging/pom.xml — spring-boot-maven-plugin с <skip>true</skip>

Данные в БД
Роль ADMIN (11111111-...)

Пользователь admin (22222222-...) с BCrypt-хешем пароля admin

Связка user_roles

📚 ГРАБЛИ (важно помнить!)
1. BOM в файлах
PowerShell 5.1 при Set-Content -Encoding UTF8 добавляет BOM → компилятор падает с illegal character: '\ufeff'.

Только так:

powershell
[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding $false))
Проверка:

powershell
$bytes = [System.IO.File]::ReadAllBytes($path)[0..2]
"$($bytes -join ' ')"   # OK: 112 97 99 | BOM: 239 187 191
2. .NET-методы работают от CWD процесса
[System.IO.File]::ReadAllBytes(".\path") не найдёт файл, если PowerShell в C:\WINDOWS\system32. Только абсолютные пути.

3. Maven не перезаписывает занятые JAR
Перед mvn clean package:

powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
4. jjwt 0.11.x vs 0.12.x
0.11.x (у нас): setSubject, setIssuer, signWith(key, SignatureAlgorithm.HS256), Jwts.parserBuilder().setSigningKey(key)

0.12.x: subject, issuer, signWith(key), Jwts.parser().verifyWith(key)

5. @GeneratedValue обязателен
Без него Hibernate требует вручную задать id → IdentifierGenerationException.

6. @PrePersist для createdAt/updatedAt
Hibernate 6 не проставляет автоматически → DataIntegrityViolationException: null value in column "updated_at". Нужен @PrePersist / @PreUpdate.

7. sm.getId() возвращает null
Не полагаться на sm.getId() — передавать projectId явно в параметрах.

8. Дубликаты бинов
routes (GatewayApplication + RoutesConfig) → allow-bean-definition-overriding: true + @Primary

KeyResolver (user + project) → @Primary на user

9. Circuit breaker зависимость
Без spring-cloud-starter-circuitbreaker-reactor-resilience4j падает .circuitBreaker(...).

10. RabbitMQ vhost /iktk
Обязательно virtual-host: /iktk — иначе ACCESS_REFUSED.

11. Not a managed type
Репозиторий ссылается на класс без @Entity → падение. Добавить @Entity, @Id.

12. Кракозябры в консоли
Русский в логах как РћР№... — проблема кодировки PowerShell, данные в БД корректны. Не обращать внимания.

13. PowerShell и логи
Не вставлять строки лога в PowerShell — они не команды. Не копировать +, ~, CategoryInfo.

14. Запуск сервисов
Каждый — в своём окне PowerShell. Закрытие окна = остановка сервиса.

15. gateway -debug
Для диагностики:

powershell
java -jar .\target\gateway.jar --logging.level.org.springframework.cloud.gateway=DEBUG
🗺️ КАРТА ПРОЕКТА
text
iktk-complex/
├── core/               (библиотека: DTO, домен)
├── shared/             (библиотека: утилиты)
├── auth/               (сервис, 8081)  ✅ работает
├── gateway/            (сервис, 8080)  ✅ работает
├── orchestrator/       (сервис, 8082)  ✅ работает
├── modules/
│   ├── casting/        (собран, не запускался)      ← СЛЕДУЮЩИЙ
│   ├── forging/        (собран, не запускался)
│   ├── machining/      (собран, не запускался)
│   └── norming/        (собран, не запускался)
├── services/
│   ├── equipment-service/       (недописан, закомментирован в pom)
│   ├── bom-service/             (недописан, закомментирован)
│   ├── assembly-process-service/(недописан, закомментирован)
│   └── recommendation-service/  (недописан, закомментирован)
└── PROGRESS.md         ← этот файл
🎯 СЛЕДУЮЩИЕ ШАГИ
Ближайшее (30–60 мин)
1. Мелкие правки orchestrator:

completed: true при state: COMPLETED (в OrchestrationResponse.buildResponse)

actor = реальный userId из @RequestHeader("X-User-Id") вместо SYSTEM

2. Запустить modules/casting:
Создать modules/casting/application.yml:

yaml
server:
  port: 8083
spring:
  application:
    name: casting
  datasource:
    url: jdbc:postgresql://localhost:5432/iktk
    username: iktk_app
    password: iktk_dev_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
  rabbitmq:
    host: localhost
    port: 5672
    username: iktk
    password: iktk_dev_password
    virtual-host: /iktk
iktk:
  security:
    jwt:
      secret: "change-me-super-secret-key-for-dev-only-min-32-bytes-long-0123456789"
      issuer: iktk
Создать modules/casting/src/main/java/ru/iktk/casting/config/SecurityConfig.java (permitAll).

Запустить:

powershell
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\modules\casting
java -jar .\target\casting.jar
3. Проверить интеграцию orchestrator ↔ casting через RabbitMQ:

orchestrator отправит команду в iktk.commands

casting обработает и отправит результат в iktk.results

orchestrator получит через ModuleResultConsumer

Смотреть логи обоих, смотреть очереди в RabbitMQ UI (http://localhost:15672, iktk/iktk_dev_password).

Среднее (несколько сессий)
4. Дописать services/equipment-service, bom-service, assembly-process-service, recommendation-service — большая работа. Для каждого:

DTO (~7 классов)

Entity (~2–3)

Repository (~2–3)

Mapper (~1)

Service (~2)

Exception (~1)

application.yml + SecurityConfig

Добавить spring-boot-starter-security в pom

Раскомментировать <module> в корневом pom

Дальнее
5. MinIO — интеграция, если нужно хранить файлы.

6. Production-ready: вынести секреты в env, настроить нормальный JWT (RS256), добавить rate limiting на auth, ротация refresh-токенов.

📝 ЖУРНАЛ СЕССИЙ
2026-10-14 — Инфраструктура
Сборка → BUILD SUCCESS

Исключены недописанные services/*

Созданы Application.java для forging, machining, norming

Исправлены JPA-сущности auth

Созданы application.yml для auth, orchestrator

Созданы базы iktk_auth, iktk_orchestrator

2026-10-15 — ЗАВЕРШЕНО УСПЕШНО 🎉
✅ Поднят Redis

✅ Настроены очереди/биржи RabbitMQ

✅ Исправлены сущности orchestrator (@GeneratedValue, @PrePersist, @Transient)

✅ Исправлен gateway (circuit breaker, @Primary для KeyResolver, JWT-секрет)

✅ Реализованы TokenService и AuthService — аутентификация работает

✅ Созданы SecurityConfig для auth и orchestrator

✅ Создан пользователь admin/admin с ролью ADMIN

✅ Исправлена передача projectId в OrchestrationService

✅ Полная сквозная цепочка: клиент → gateway → orchestrator → БД

✅ Пройден полный жизненный цикл проекта до COMPLETED

Следующая сессия начнётся с:
«Читаю PROGRESS.md. Прошлая сессия закончилась прохождением проекта до COMPLETED. Сейчас: (1) хочу запустить modules/casting, (2) поправить completed: true и actor в аудите. Вот что получилось: ...»

🏆 ДОСТИЖЕНИЯ
Проект прошёл путь от «сборка падает» до работающей системы микросервисов:

Компонент	Статус
Maven-сборка (10 модулей)	✅
Docker-инфраструктура	✅
auth — JWT + BCrypt + RBAC	✅
orchestrator — state machine + audit	✅
gateway — маршрутизация + CB + rate limit	✅
Сквозной REST-запрос с JWT	✅
Полный жизненный цикл проекта	✅
Осталось: дописать модули (modules/*) и сервисы (services/*).

content,(New−ObjectSystem.Text.UTF8Encodingfalse))
"Записано: 
path(((Get-Item $path).Length) байт)"

text

## Что в этом `PROGRESS.md`

- **Текущий статус** — что работает, что не проверено
- **Известные мелкие проблемы** — `completed: false`, `actor: SYSTEM`, без деталей
- **Быстрый старт** — пошаговая инструкция для следующей сессии + скрипт для теста полного цикла
- **Ключевые данные** — пароли, порты, JWT-секрет, SQL-запросы
- **Все правки** — накопительно (по модулям)
- **Грабли** — 15 уроков с примерами
- **Карта проекта** — что где лежит
- **Следующие шаги** — приоритезированы
- **Журнал сессий** — короткая история

## Проверка

```powershell
Get-Content C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\PROGRESS.md | Select-Object -First 20
Должно показать заголовок и начало текущего статуса.

Как продолжить в следующий раз
Откройте PROGRESS.md, прочитайте 2 минуты.

Запустите docker start ... + три сервиса.

В новом чате начните с:

«Читаю PROGRESS.md. Прошлая сессия завершилась на COMPLETED. Сейчас хочу запустить modules/casting. Также надо поправить completed: true и actor в аудите.»

Сессия закрыта. Отличная работа! 🎉

## 🔗 GIT-РЕПОЗИТОРИЙ

### Расположение

| Что | Где |
|---|---|
| **Удалённый** | https://github.com/aaa777-hach/iktk-complex |
| **Локальный** | `C:\Users\Antipovich\Desktop\Оркестр\iktk-complex` |
| **Ветка** | `main` ↔ `origin/main` |

### Первый коммит

- **Хеш:** `db78faf`
- **Сообщение:** `Initial commit: working microservices with full project lifecycle`
- **Файлов:** 207
- **Строк:** 60 839

### Что НЕ попадает в репозиторий (`.gitignore`)

- `.env` — секреты (только `.env.example` коммитится)
- `target/` — артефакты Maven
- `*.jar`, `*.war`, `*.log`
- `node_modules/`, `dist/`, `build/`
- `.idea/`, `.vscode/`, `*.iml`
- `~$*` — временные файлы Word
- `application-local.yml`, `application-secret.yml`

### Соглашение о коммитах

| Тип | Назначение |
|---|---|
| `feat` | новая функциональность |
| `fix` | исправление бага |
| `docs` | документация |
| `refactor` | рефакторинг без смены поведения |
| `test` | тесты |
| `chore` | рутина (зависимости, конфиги) |

**Примеры:**
feat: add casting module integration
fix: orchestrator persists projectId correctly
docs: update PROGRESS.md with git section
chore: remove duplicate module files

text

### Рабочий процесс (перед каждым коммитом)

```powershell
# 1. Проверить, что не затесалось лишнего
git status

# 2. Убедиться, что .env не попал
git ls-files | Select-String "\.env$"

# 3. Добавить изменения
git add .

# 4. Коммит
git commit -m "feat: add casting module integration"

# 5. Отправить на GitHub
git push
Правила безопасности
Никогда не коммитить .env — только .env.example.

Перед git push проверять git status — не должно быть target/, *.jar, *.log.

Секреты в application.yml — только через ${ENV_VAR:default}, не хардкод.

Не коммитить большие бинарники (JAR, ZIP, образы дисков).

Полезные команды
powershell
git log --oneline
git status
git branch -vv
git remote -v
git fetch origin
git diff path/to/file.java
git checkout -- path/to/file.java
Типичные проблемы
Проблема	Решение
remote: Permission denied	Удалить старые креды (cmdkey /delete) и авторизоваться под правильным аккаунтом
Everything up-to-date, но файлы не пушатся	Проверить git branch -vv, что ветка main связана с origin/main
failed to push some refs	git pull origin main --allow-unrelated-histories, потом git push
BOM в файлах	Сохранять только через [System.IO.File]::WriteAllText(..., UTF8Encoding $false)
bytes=[System.IO.File]::ReadAllBytes(path)
existing=if(bytes[0] -eq 0xEF -and 
bytes[1]−eq0xBB−andbytes[2] -eq 0xBF) {
[System.Text.Encoding]::UTF8.GetString(
3..
(
bytes[3..(bytes.Length-1)])
} else {
[System.Text.Encoding]::UTF8.GetString($bytes)
}

[System.IO.File]::WriteAllText(
path,existing + 
addition,(New−ObjectSystem.Text.UTF8Encodingfalse))
"Дополнен PROGRESS.md (
((Get−Itempath).Length) байт)"

text

## 🆕 ОБНОВЛЕНИЯ (2026-10-16)

### Исправленные проблемы

Обе проблемы из раздела «Известные мелкие проблемы» **устранены**.

#### 1. `completed: false` при `state: COMPLETED` — РЕШЕНО ✅

**Причина:** `ProjectState.toString()` возвращал `"COMPLETED (Завершено)"`, а геттер `isCompleted()` в `OrchestrationResponse` проверял `"COMPLETED".equals(state)` — не совпадало.

**Правка** в `orchestrator/.../service/OrchestrationService.java`, метод `buildResponse`:

```java
private OrchestrationResponse buildResponse(ProjectRuntime runtime,
                                            StateMachine<ProjectState, ProjectEvent> sm) {
    ProjectState state = sm.getState().getId();
    return OrchestrationResponse.builder()
            .projectId(runtime.getProjectId())
            .projectName(runtime.getProjectName())
            .state(state.name())                        // вместо state.toString()
            .stateDisplayName(state.getDisplayName())
            .stateCategory(state.getCategory().name())
            .pointOfControl(state.isPointOfControl())
            .awaitingUserAction(state.isPointOfControl())
            .availableEvents(transitionHandler.availableEvents(state))
            .updatedAt(runtime.getUpdatedAt())
            .build();
}
Результат:

text
шаг 4: state=COMPLETED | display=Завершено | completed=True | awaiting=False
Коммит: e9adfb1 — fix(orchestrator): correct state/completed/awaitingUserAction in OrchestrationResponse

2. actor: SYSTEM вместо реального пользователя — РЕШЕНО ✅
Причина: decisionLogService.log(...) вызывался без actor → по умолчанию "SYSTEM".

Правка в orchestrator/.../service/OrchestrationService.java:

Добавлены импорты:

java
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
Добавлен метод:

java
private String currentActor() {
    try {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String userId = attrs.getRequest().getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }
    } catch (Exception ignored) {
        // вне HTTP-запроса (например, из RabbitMQ)
    }
    return "SYSTEM";
}
Все вызовы decisionLogService.log(projectId, event, payload) → decisionLogService.log(projectId, event, payload, currentActor()).

Результат в БД:

text
   event_type    | actor |          created_at
-----------------+-------+-------------------------------
 PROJECT_STARTED | admin | 2026-10-16 13:17:30.782218+00
 APPROVED        | admin | 2026-10-16 13:17:30.90268+00
Для RabbitMQ-консьюмера (вне HTTP) по-прежнему SYSTEM — это правильно.

Коммит: f0a68fc — fix(orchestrator): use real user as actor in audit log

История коммитов (GitHub)
text
f0a68fc fix(orchestrator): use real user as actor in audit log
e9adfb1 fix(orchestrator): correct state/completed/awaitingUserAction in OrchestrationResponse
322ea96 docs: add git repository section to PROGRESS.md
742a53f chore: move module files to correct Maven structure, exclude unfinished modules
db78faf Initial commit: working microservices with full project lifecycle
Все синхронизированы с GitHub: https://github.com/aaa777-hach/iktk-complex

Что изменено в структуре проекта (2026-10-16)
Файлы модулей forging, machining, norming перенесены в правильную Maven-структуру:

modules/forging/ForgingApplication.java → modules/forging/src/main/java/ru/iktk/modules/forging/ForgingApplication.java

modules/forging/service/ForgingProcessService.java → .../src/main/java/ru/iktk/modules/forging/service/ForgingProcessService.java

аналогично для machining, norming

Эти три модуля закомментированы в корневом pom.xml — недописаны (нет DTO, entity, repository, client, config)

Сборка сейчас: BUILD SUCCESS для core, shared, gateway, auth, orchestrator, modules/casting

Следующие шаги (актуальные)
Запустить modules/casting — модуль с полным набором классов. Потребуется:

modules/casting/application.yml (порт 8083, datasource iktk_casting, rabbitmq, jwt)

modules/casting/src/main/java/ru/iktk/modules/casting/config/SecurityConfig.java (permitAll)

создать БД iktk_casting в PostgreSQL

запустить java -jar .\target\casting.jar в отдельном окне

проверить RabbitMQ-интеграцию (orchestrator ↔ casting)

Дописать services/* (equipment, bom, assembly, recommendation) — большая работа.

Восстановить forging/machining/norming — когда будет время дописать недостающие классы (10+ на каждый).
bytes=[System.IO.File]::ReadAllBytes(path)
existing=if(bytes[0] -eq 0xEF -and 
bytes[1]−eq0xBB−andbytes[2] -eq 0xBF) {
[System.Text.Encoding]::UTF8.GetString(
3..
(
bytes[3..(bytes.Length-1)])
} else {
[System.Text.Encoding]::UTF8.GetString($bytes)
}

[System.IO.File]::WriteAllText(
path,existing + 
addition,(New−ObjectSystem.Text.UTF8Encodingfalse))
"Дополнен PROGRESS.md (
((Get−Itempath).Length) байт)"

text

## 📌 СЕССИЯ 2026-10-16 (вечер)

### Что сделано

#### 1. Git-репозиторий настроен и синхронизирован с GitHub

URL: https://github.com/aaa777-hach/iktk-complex

7 коммитов:
```
5e3e116 feat(casting): make JPA entities managed, add application.yml
8a6584a docs: update PROGRESS.md with fixes (completed flag, real actor in audit)
f0a68fc fix(orchestrator): use real user as actor in audit log
e9adfb1 fix(orchestrator): correct state/completed/awaitingUserAction in OrchestrationResponse
322ea96 docs: add git repository section to PROGRESS.md
742a53f chore: move module files to correct Maven structure, exclude unfinished modules
db78faf Initial commit: working microservices with full project lifecycle
```

#### 2. Правки orchestrator (обе проблемы из раздела «Известные мелкие проблемы» закрыты)

- completed: true при state: COMPLETED — исправлено в buildResponse
- actor = admin в decision_log (было SYSTEM) — через currentActor(), читающий X-User-Id из HTTP-запроса

#### 3. modules/casting собран и запущен

- Создан modules/casting/application.yml (порт 8083, PostgreSQL iktk_casting, RabbitMQ)
- В PostgreSQL создана база iktk_casting
- CastingProcess → @Entity (с @Id, @GeneratedValue, @PrePersist)
- GatingSystem, ShrinkageCalculation → @Embeddable
- List<Stage>, Object tooling, Object manufacturabilityReport → @Transient
- Запущен успешно: Started CastingApplication на порту 8083

#### 4. Файлы модулей приведены к Maven-структуре

- modules/forging, machining, norming — файлы перенесены в src/main/java/ru/iktk/modules/<name>/
- Эти три модуля закомментированы в корневом pom.xml (недописаны: нет DTO, entity, repository, client, config)

### Текущий статус

Работают 4 сервиса:
- auth — 8081 (UP)
- orchestrator — 8082 (UP)
- gateway — 8080 (UP)
- casting — 8083 (UP)

Сборка: mvn clean package -DskipTests → BUILD SUCCESS (core, shared, gateway, auth, orchestrator, modules/casting).

### Что НЕ сделано — на следующий раз

casting не имеет точки входа:
- Нет @RestController — не принимает HTTP-запросы
- Нет @RabbitListener — не слушает очередь iktk.casting.queue
- Методы зависят от GeometryClient → http://integration:8090 (сервис не запущен)

Возможные варианты продолжения:

A. Добавить CastingController (@RestController, ~30 строк) → REST-endpoint POST /api/v1/casting/design. Нужно также добавить маршрут в gateway (/api/v1/casting/** → http://localhost:8083).

B. Добавить @RabbitListener на iktk.casting.queue → приём команд от orchestrator через RabbitMQ.

C. Запустить остальные модули (forging, machining, norming) — сначала дописать недостающие классы.

D. Дописать services/* (equipment, bom, assembly, recommendation).

Приоритет: сначала A (быстрый REST-endpoint), потом B (RabbitMQ).

### Быстрый старт следующей сессии

```powershell
# 1. Docker
docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio

# 2. Четыре сервиса — каждый в своём окне
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\auth;              java -jar .\target\auth.jar
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\orchestrator;      java -jar .\target\orchestrator.jar
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\gateway;           java -jar .\target\gateway.jar
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\modules\casting;   java -jar .\target\casting.jar

# 3. Проверка
Invoke-RestMethod -Uri http://localhost:8081/actuator/health
Invoke-RestMethod -Uri http://localhost:8082/actuator/health
Invoke-RestMethod -Uri http://localhost:8080/actuator/health
Invoke-RestMethod -Uri http://localhost:8083/actuator/health
```

### Git workflow

```powershell
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex
git status
git add <файлы>
git commit -m "тип: описание"
git push
```

---

## 2026-10-17 (вечер) — `PART_PROCESS_PLANNING` → `casting`: маршрутизация + реальный ТП

**Проблема:** `PART_PROCESS_PLANNING` — точка контроля. `route()` блокировал отправку в модуль, проект «висел» в ожидании approve.

**Коммиты (в `origin/main`):**

| Hash | Что |
|---|---|
| `fdde572` | маршрутизация `PART_PROCESS_PLANNING` → `casting` |
| `37cea12` | casting: consumer + топология команд |
| `c2d8858` | orchestrator: топология результатов + DLQ |
| `9db43f5` | порты 8080/8090 |
| `6752ee7` | casting: реальный ТП + graceful fallback |
| `d6e9927` | casting: `saved.getId()` (консистентный `processId`) |

### Что изменилось относительно прошлой сессии

| Было (устарело) | Стало |
|---|---|
| orchestrator — **8082** | orchestrator — **8080** |
| casting — **8083** | casting — **8090** |
| «casting не имеет `@RabbitListener`» | `CastingCommandConsumer` слушает `iktk.casting.queue` |
| «Методы зависят от `GeometryClient`» | **fallback** на демо-геометрию (`demo-fallback: true`) |

### Актуальный Quick Start

```powershell
docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio

cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\orchestrator;      java -jar .\target\orchestrator.jar   # 8080
cd C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\modules\casting;   java -jar .\target\casting.jar        # 8090

Get-NetTCPConnection -LocalPort 8080,8090 -State Listen
```

### Что сделано

- **Маршрутизация (`fdde572`):** `TransitionHandler.ROUTES` + `route()` исключение + `ModuleCommandProducer.moduleFor`
- **Casting (`37cea12`):** `RabbitTopologyConfig` + `CastingCommandConsumer` + `@EnableRabbit` + `application.yml`
- **Orchestrator (`c2d8858`):** `RabbitTopologyConfig` (результаты + DLQ) + `@EnableRabbit`
- **Порты (`9db43f5`):** внешние `application.yml` → 8080/8090
- **Реальный ТП (`6752ee7`):** `designProcess(request)` + fallback + `success=false` при ошибке
- **Консистентность `processId` (`d6e9927`):** `saved.getId()` вместо `process.getId()`

### E2E — подтверждение

| Проверка | Статус |
|---|---|
| `PART_PROCESS_PLANNING` → `TOOLING_DESIGN` | OK |
| `casting` получает `iktk.command.casting` | OK |
| Реальный ТП: `INVESTMENT_CASTING`, 6 этапов | OK |
| Fallback при 404 `GeometryClient` | OK |
| `processId` = `id` в `casting_process` | OK |
| `decision_log` полная цепочка | OK |

### Что НЕ сделано

1. **`integration-service`** — не реализован. `casting` использует `demo-fallback: true`. Для прода поднять сервис с `GET /api/v1/geometry/{projectId}?objectRef=...`, выключить `demo-fallback`
2. **`equipment-service`** (8094) — не запущен, graceful degradation даёт `true`
3. **`objectRef` из оркестратора** — `payload=null` даёт `default-object`. Передавать `ProjectRuntime.geometryId`
4. **`application-prod.yml`** — вынести prod-настройки (`demo-fallback: false`, порты, RabbitMQ)
5. **Порты gateway** — уточнить схему
6. **`modules/forging, machining, norming`** — недописаны

### Приоритет следующей сессии

**Сначала `integration-service`** — иначе `casting` в проде строит ТП по демо-геометрии.

---

## 2026-10-18 — integration-service (реальная геометрия) + casting без fallback

**Проблема:** `casting` использовал `demo-fallback: true` (жёстко зашитая демо-геометрия), потому что `GeometryClient` обращался к несуществующему `integration-service` (404).

**Решение:**

- **Новый модуль `modules/integration`** (порт 8095): `GET /api/v1/geometry/{projectId}?objectRef=...` → JSON `GeometryData`. Stub: детерминированные значения по `objectRef.hashCode()`.
- **`casting/application.yml`**: `integration.url: http://localhost:8095`, `demo-fallback: false`.
- **`CastingProcessService.GeometryData`**: `@JsonCreator` + `@JsonProperty` — для надёжной десериализации Feign.

**Коммит:** `24a7f7f` — `feat(integration): geometry provider service + casting real geometry`

**E2E-подтверждение:**

| Проверка | Статус |
|---|---|
| `integration` отвечает `GeometryService: projectId=..., volume=1435.0, complexity=5` | OK |
| `casting` — **нет** `fallback to DEMO geometry` | OK |
| `designProcess done: method=INVESTMENT_CASTING, stages=6` | OK |
| `state = TOOLING_DESIGN` (projectId `265eabf2-...`) | OK |

### Что осталось (TODO)

1. **`equipment-service`** (8094) — не запущен, graceful degradation → `available=true`. Следующий приоритет.
2. **`objectRef` из оркестратора** — `payload=null` → `default-object`. Передавать `ProjectRuntime.geometryId`.
3. **`application-prod.yml`** — вынести prod-настройки (`demo-fallback: false`, порты, RabbitMQ).
4. **`integration` → реальные данные** (не stub): MinIO (STL/STEP) или БД `iktk_geometry`.

### Порядок запуска после перезагрузки Windows

1. `docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio` → дождаться `healthy` (`iktk-postgres`).
2. `integration` (8095) → `casting` (8090) → `orchestrator` (8080).

Если запустить `casting`/`orchestrator` до Postgres — падают на `Connection refused: localhost:5432`.

---

## 2026-10-18 — equipment-service (минимальный) + честный available в casting

**Проблема:** `casting` вызывал `EquipmentClient` → 8094, но `equipment-service` не был запущен (`Connection refused`). `isMethodAvailableSafe` глотал ошибку и возвращал `true` (graceful degradation).

**Решение:**

- **`services/equipment-service`** (порт 8094): минимальный модуль (`web` + `actuator`, без JPA/AMQP) с `GET /api/v1/equipment/is-method-available?projectId=...&method=...` → `boolean`. Stub: `SAND/INVESTMENT/DIE/GRAVITY_CASTING → true`, остальные → `false`.
- **`pom.xml`** (корневой): `services/equipment-service` раскомментирован.
- **Неполный скелет** удалён из репо (не компилировался). Полный бэкап — `services/equipment-service/.bak-*/` (gitignored).

**Коммит:** `adefc23` — `feat(equipment): minimal equipment-service + register in pom`

**E2E-подтверждение (projectId 29e294e3-..., 13:29:30):**

| Проверка | Статус |
|---|---|
| `equipment` лог: `isMethodAvailable: projectId=29e294e3-..., available=true` | OK |
| `casting` лог: **нет** `equipment-service недоступен` (для 29e294e3) | OK |
| `selectCastingMethod done: method=INVESTMENT_CASTING, available=true` | OK |
| `state = TOOLING_DESIGN` | OK |

### Что осталось (TODO)

1. **`objectRef` из оркестратора** — `payload=null` → `default-object`. Передавать `ProjectRuntime.geometryId`. **Следующий приоритет.**
2. **`application-prod.yml`** — вынести prod-настройки.
3. **`integration` → реальные данные** (MinIO или БД `iktk_geometry`).
4. **`equipment-service` → реальный реестр** (JPA, gap-analysis, matching — восстановить из `.bak/`).

### Порядок запуска (после перезагрузки Windows)

1. `docker start iktk-postgres iktk-rabbitmq iktk-redis iktk-minio` → дождаться `healthy`.
2. `integration` (8095) → `equipment-service` (8094) → `casting` (8090) → `orchestrator` (8080).

---

## 2026-10-18 — objectRef/geometryId из оркестратора в casting/integration

**Проблема:** `route()` шёл `send(..., null)` — `casting` получал `objectRef=default-object`, `integration` возвращал стаб по хешу `"default-object"` для всех проектов.

**Решение:**

- **`ProjectRuntime.payload`**: убран `@Transient` (он **отменял** `@Column`), добавлен `@JdbcTypeCode(SqlTypes.JSON)` — теперь `payload` **реально сохраняется** в `project_state.payload jsonb`.
- **`OrchestrationService.startProject`**: `.payload(request.getGeometryId() != null ? Map.of("objectRef", geometryId) : Map.of())` в builder.
- **`OrchestrationService.run`**: `route(projectId, state, sm, runtime.getPayload())`.
- **`TransitionHandler.route`**: 4-й параметр `Map<String, ?> payload` → `moduleCommandProducer.send(..., payload)`.
- **`OrchestrationServiceTest`**: `route(...)` вызовы обновлены на 4 аргумента (`any()` для payload).

**Коммит:** `4361bc0` — `feat(orchestrator): pass project payload (objectRef/geometryId) to module commands`

**E2E-подтверждение (projectId b0b7d8a4-..., geometryId=geo-12345):**

| Проверка | Статус |
|---|---|
| `project_state.payload` = `{"objectRef": "geo-12345"}` | OK |
| `casting` лог: `objectRef=geo-12345` (не `default-object`) | OK |
| `designProcess done: ... method=INVESTMENT_CASTING, stages=6` | OK |
| `state = TOOLING_DESIGN` | OK |

### Что осталось (TODO)

1. **`application-prod.yml`** — вынести prod-настройки. **Следующий приоритет.**
2. **`integration` → реальные данные** (MinIO или БД `iktk_geometry`).
3. **`equipment-service` → реальный реестр** (JPA, gap-analysis, matching).

---

## 2026-10-18 — application-prod.yml для всех сервисов + .env.example

**Проблема:** все настройки (пароли БД/RabbitMQ, JWT-секрет, `ddl-auto: update`, `localhost` для integration/equipment) были в `application.yml` — небезопасно для прода (секреты в репо, авто-миграции, локальные хосты).

**Решение:**

- **`application-prod.yml`** для orchestrator, casting, integration, equipment-service:
  - `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `IKTK_JWT_SECRET` — **без дефолтов** (fail-fast, если не заданы);
  - `POSTGRES_HOST`, `RABBITMQ_HOST` — **без дефолтов**;
  - `ddl-auto: validate` (только проверка схемы, без изменений);
  - `demo-fallback: false` явно (casting);
  - `iktk.equipment.url` / `iktk.integration.url` — из env (`IKTK_EQUIPMENT_URL`, `IKTK_INTEGRATION_URL`);
  - `management.endpoints` — только `health`;
  - логирование `root`/`ru.iktk` — `INFO`.
- **`.env.example`** — документирует все обязательные env-переменные.
- **`.gitignore`** — добавлен `.env` (локальные секреты не коммитятся).

**Коммит:** `60073ba` — `feat(prod): application-prod.yml for all services + .env.example`

**Тесты prod:**

| Проверка | Статус |
|---|---|
| `--spring.profiles.active=prod` **без** env → приложение падает (`UnknownHostException: ${POSTGRES_HOST}` / нет connection) | OK |
| `--spring.profiles.active=prod` **с** env → `Started OrchestratorApplication` | OK |
| dev (без профиля) → стартует как раньше | OK |

**Замечание:** unresolved placeholder в `spring.datasource.url` даёт `UnknownHostException: ${POSTGRES_HOST}` (relaxed binding), а не `Could not resolve placeholder`. Fail-fast **есть**, но сообщение менее читаемое. Если нужно «красивое» — добавить `@ConfigurationProperties` с `@NotBlank` (отдельная задача).

### Что осталось (TODO)

1. **`integration` → реальные данные** (MinIO или БД `iktk_geometry`) — сейчас stub по `objectRef.hashCode()`.
2. **`equipment-service` → реальный реестр** (JPA, gap-analysis, matching — восстановить из `.bak/`).
3. **`objectRef` → реальная геометрия** — сейчас `geo-12345` (тест), в проде — из `geometry-service`.
4. **Flyway/Liquibase** — вместо `ddl-auto: update` в dev и `validate` в prod (миграции как код).
5. **`application-prod.yml` в `core`** — общие настройки (если нужно).

---

## 2026-10-19 — equipment-service: JPA + Flyway + реальная БД

**Проблема:** `equipment-service` был **stub** — `isMethodAvailable` возвращал `true` для `SAND/INVESTMENT/DIE/GRAVITY_CASTING` **независимо** от оборудования.

**Решение:**

- **`V1__init.sql`** (iktk_equipment): `equipment`, `equipment_capabilities`, `equipment_capability_materials`;
- **`V2__seed.sql`**: 1 `equipment` (`eq-casting-01`, `CASTING_MACHINE`, `AVAILABLE`) + 5 `capabilities` (все `CastingMethod`);
- **`EquipmentRepository.existsAvailableByOperation`** — JPQL `JOIN equipment + capabilities WHERE operation_type = :method AND status = 'AVAILABLE'`;
- **`EquipmentService.isMethodAvailable`** — вызов репозитория (не stub);
- **`EquipmentController`** — `switch` → `equipmentService.isMethodAvailable(method)`;
- **`Equipment` entity** — `mappedBy = "equipment"`, `@OneToOne currentStatus` удалён;
- **`EquipmentStatusEnum`** — `AVAILABLE/BUSY/MAINTENANCE/DECOMMISSIONED`;
- **`pom.xml`** — JPA + PostgreSQL + Flyway + Validation;
- **`application.yml`** — datasource `iktk_equipment`, `ddl-auto: validate`, `flyway.enabled: true`.

**Коммиты:** `8e2d9b9` (equipment), `2b901f4` (flyway orchestrator/casting — восстановление), `a1c5198` (iktk-start.ps1).

**E2E-подтверждение (projectId 5ca4debb-...):**

| Проверка | Статус |
|---|---|
| Flyway: `Successfully applied 2 migrations` | OK |
| Hibernate `validate` — `Initialized JPA EntityManagerFactory` | OK |
| `equipment`: 1 строка (`CASTING_MACHINE`, `AVAILABLE`) | OK |
| `equipment_capabilities`: 5 строк (все `CastingMethod`) | OK |
| Лог `EquipmentService: isMethodAvailable operationType=INVESTMENT_CASTING, available=true` | OK |
| `state = TOOLING_DESIGN` | OK |

**Ключевое:** `available` теперь **из БД**. Старый stub всегда возвращал `true` — новый зависит от `equipment.status` + `capabilities.operation_type`. Цепочка `Controller -> Service -> Repository -> DB` видна в логах (3 уровня логирования).

### Что осталось (TODO)

1. **`integration` → реальные данные** (MinIO/БД `iktk_geometry`) — сейчас stub по `objectRef.hashCode()`. **Следующий приоритет.**
2. **`equipment-service` → полный реестр** (JPA `EquipmentStatus`, `EquipmentTooling`, `EquipmentRequirement`, `EquipmentGapAnalysis` — V3+).

---

## 2026-10-19 — integration: JPA + Flyway + реальная БД (geometry)

**Проблема:** `integration` был **stub** — `GeometryService` вычислял геометрию по `objectRef.hashCode()` (детерминированно, но не реальные данные).

**Решение:**

- **`V1__init.sql`** (iktk_geometry): таблица `geometry` (`object_ref` UNIQUE, `material`, `volume`, `mass`, `min_wall_thickness`, `min_fillet_radius`, `min_draft_angle`, `nominal_dimensions`, `complexity_score`, `undercuts`, `hot_spots`);
- **`V2__seed.sql`**: `geo-12345` (`Steel 20Kh`, 1000.0, complexity=2), `geo-test` (`AlSi10Mg`, 2500.0, complexity=4);
- **`Geometry` entity** + **`GeometryRepository.findByObjectRef`**;
- **`GeometryService.buildGeometry`** → `repository.findByObjectRef` (stub → БД); `null` если не найдено;
- **`GeometryController`** — `null` → `404 NOT_FOUND` (→ `casting` получает `FeignException.NotFound` → `success=false`);
- **`pom.xml`** — JPA + PostgreSQL + Flyway + Validation;
- **`application.yml`** — datasource `iktk_geometry`, `ddl-auto: validate`, `flyway.enabled: true`.

**E2E-подтверждение (projectId cbfd000a-..., geometryId=geo-12345):**

| Проверка | Статус |
|---|---|
| Flyway: `flyway_schema_history` = init 1, seed 2, success=t | OK |
| `geometry`: `geo-12345` (Steel 20Kh, 1000.0, complexity=2), `geo-test` (AlSi10Mg, 2500.0, complexity=4) | OK |
| Лог `GeometryService: found geometry objectRef=geo-12345, volume=1000.0000, complexity=2` | OK |
| `state = TOOLING_DESIGN` | OK |

**Ключевое:** `volume=1000.0, complexity=2` — **из БД** (seed). Stub по `hashCode("geo-12345")` вернул бы `1435.0, complexity=5`. **Stub заменён.**

### Что осталось (TODO)

1. **`equipment-service` → полный реестр** (JPA `EquipmentStatus`, `EquipmentTooling`, `EquipmentRequirement`, `EquipmentGapAnalysis` — V3+).
2. **MinIO** — хранилище CAD-файлов (STL/STEP) вместо JSON-полей в БД — отдельная фича.
3. **Flyway для остальных БД** — при добавлении новых миграций (все сервисы уже под Flyway).

---

## recommendation-service — состояние на 2026-10-23

**Коммит:** `08f4c28`. **Тесты:** 123/123. **Покрытие:** ~80% LINE / ~59% BRANCH (порог 70/50).
**Закрытый техдолг:** №1 (`matchRequirement` не маскирует ошибки), №2 (`REQUIRES_NEW` для analysis), №3 (`requiredCnc`/`quantity`/`notes` терялись), №4 (идемпотентность `run()`), №5 (логи `analysisId`), №6 (баннер UTF-8).

**Проверено живьём:**
- `OUTSOURCING_SELECTED` → `status: PENDING → SELECTED`, событие в `iktk.result.OUTSOURCING_SELECTED`.
- `EQUIPMENT_GAP_RESOLUTION` (`TURNING`, нет в capabilities) → `gaps=1`, 2 рекомендации (`ADJUST_PROCESS`, `OUTSOURCE`).
- `EQUIPMENT_GAP_RESOLUTION` (`DIE_CASTING`, есть в capabilities) → `gaps=0`, 0 рекомендаций.
- Идемпотентность: 2 команды с `projectId=p-idem-2` за 2 сек → **1 анализ** в БД (не 2).

### Runbook

| Сервис | Порт | БД | Контейнер |
|---|---|---|---|
| `recommendation-service` | 8098 | `iktk_recommendation` | `rec-pg` (PostgreSQL 5433) |
| `equipment-service` | 8094 | `iktk_equipment` | `iktk-postgres` (PostgreSQL 5432) |

**RabbitMQ:** контейнер `iktk-rabbitmq`, порт `5672` (management `15672`), vhost `/iktk`, user `iktk` / `iktk_dev_password`. Очередь команд `iktk.recommendation-service.queue`, exchange результатов `iktk.results`.

**Запуск (4 окна):**
```powershell
# Окно 1 — EQ-SERVICE (8094)
$Host.UI.RawUI.WindowTitle = "EQ-SERVICE (8094)"
cd C:\Users\Antipovich\Desktop\Orchestr\iktk-complex
mvn -pl services/equipment-service spring-boot:run 2>&1 | Tee-Object -FilePath run-eq.log

# Окно 2 — REC-SERVICE (8098)
$Host.UI.RawUI.WindowTitle = "REC-SERVICE (8098)"
cd C:\Users\Antipovich\Desktop\Orchestr\iktk-complex
mvn -pl services/recommendation-service spring-boot:run 2>&1 | Tee-Object -FilePath run-rec.log

# Окна 3–4 — публикация/psql/логи (здесь НЕ запускать spring-boot:run)

```

**Проверка:**
```powershell
"8094 EQ: " + (Test-NetConnection localhost -Port 8094 -InformationLevel Quiet)
"8098 REC:" + (Test-NetConnection localhost -Port 8098 -InformationLevel Quiet)
(Invoke-RestMethod http://localhost:8098/actuator/health).status   # UP
```

### Техдолг (открыт, низкий приоритет)

| # | Что | Время |
|---|---|---|
| 3b | `"requiredCnc":"true"` (строка) → Jackson; нужен кастомный десериализатор | 20 мин (если оркестратор шлёт строку) |
| 4b | Повторная публикация `RECOMMENDATIONS_READY` при идемпотентном повторе | by design, оставить |
| Javadoc | Кракозябры в комментариях `EquipmentRecommendation`, `EquipmentGapAnalysis`, `EquipmentRequirement` | 30 мин, косметика |
| ~~jacoco-eq~~ | ~~`equipment-service`: покрытие 60/40 при порогах 75/80~~ ✅ закрыт (lines 80.2%, branches 88.1%) | — |
| norming | **Сессия 1**: компилируется. **Сессия 2**: RabbitMQ + PostgreSQL + consumer + Flyway, end-to-end (demo-ТП). **Сессия 3**: реальный ТП из assembly (Feign + TechProcessMapper) + fallback, fallback проверен. Осталось: (а) поднять assembly (нужен application.yml — порт неизвестен, БД `iktk_assembly`); (б) проверить реальный путь end-to-end; (в) state machine `NORMING` → `POINT_OF_CONTROL` + route + nextAuto=null; (г) тесты + Javadoc; (д) справочники `NormingProperties`. То же для machining/forging | 6-10 ч (сессии 4+) |

### Грабли (не повторять)

1. **Порт `equipment-service` — 8094, не 8085.** `EquipmentClient` и `application.yml` должны указывать на 8094.
2. **CRLF в .NET-regex:** в PowerShell `-replace` с якорем `$` не матчит конец строки в CRLF-файлах. Использовать `\r?\n` или `code`.
3. **here-string в PowerShell 5.1 + кириллица:** Java/русский текст портится (CP1251 → UTF-8). Только `code` или `WriteAllText` с `UTF8Encoding $false`.
4. **`[System.IO.File]` игнорирует `cd` PowerShell:** рабочая папка процесса — `C:\WINDOWS\system32`. Использовать `(Resolve-Path ...).Path`.
5. **`Get-Content` в PowerShell 5.1 читает как CP1251** — UTF-8 файл показывает кракозябрами. Ложная тревога, проверять через `[System.IO.File]::ReadAllLines($path, [Text.Encoding]::UTF8)`.
6. **Дубликат бина `RecommendationProperties`:** `@Component` + `@EnableConfigurationProperties` = два бина. Оставить только `@ConfigurationProperties`.
7. **`scanBasePackages = "ru.iktk.shared"` тянет `StorageClient` (MinIO).** Для изолированного сервиса — сужать до своего пакета.
8. **`@EntityScan = "ru.iktk.core.domain"` тянет `projects`** (чужая таблица). Сужать до `model`.
9. **`VersioningService` из `shared` несовместим с VARCHAR-ID** (`UUID.fromString("r1")`). Либо UUID везде, либо свой лёгкий сервис.
10. **`generateForGap` фактически принимает `analysisId`**, не `gapId` (внутри `gapRepository.findById(gapId)` — ищет `EquipmentGapAnalysis` по id).
11. **`git log` показывает кракозябры в кириллице** — это CP1251-декодирование UTF-8 stdout в PowerShell 5.1, а не порча коммита. Лечится `[Console]::OutputEncoding = [Text.Encoding]::UTF8` перед вызовом git. Не путать с реальной порчей файлов (грабли №3, №5).
12. **Массовое преобразование CP1251→UTF8 по всему файлу может испортить уже корректные строковые литералы с кириллицей.** Если в файле смешанное состояние (Javadoc порчен, а литералы — нет), преобразование ломает литералы (`"Отклонено: "` → `"���������: "`). Решение: проверять diff на не-комментарные `+`-строки перед коммитом, либо применять преобразование точечно к блокам комментариев.
13. **`mvn verify` в `equipment-service` падает на `jacoco:check`** (60/40 vs 75/80) — предсуществующая проблема, не связана с правками документации. При работе с этим сервисом — либо добавлять тесты, либо временно `-Djacoco.skip=true` для проверки кода, либо чинить техдолг `jacoco-eq`.
14. **Java-код нельзя вставлять в PowerShell.** Символы `@DisplayName`, `void`, `verify(...)`, `&&` — синтаксис Java; PowerShell ругается `ParserError`/`UnexpectedToken`, и это **не** ошибка файла. Java-код правится через `code`, либо автоматической заменой строк по шаблону (ReadAllLines -> цикл -> WriteAllLines + UTF8Encoding $false). В PowerShell вставляй только команды.
15. **`git commit -m "русский текст"` из PowerShell 5.1 может испортить сообщение коммита** (двойное перекодирование UTF-8 → CP1251 → UTF-8: `—` превращается в `–≤–ВвАЭ`). При этом `git log` без `[Console]::OutputEncoding = UTF8` показывает кракозябры **и для корректных** коммитов (грабли №11) — легко перепутать. Решение: для длинных/русских сообщений писать в UTF-8-файл (`[System.IO.File]::WriteAllLines($path, $msg, (New-Object System.Text.UTF8Encoding $false))`) и делать `git commit -F commit-msg.txt`. Проверка: `[Console]::OutputEncoding = UTF8; git log -1 --format="%B"`.
16. **`application.yml` в корне модуля перекрывает `src/main/resources/application.yml`.** `spring-boot:run` читает `./application.yml` (рабочая папка = корень модуля) раньше, чем classpath. Встретилось дважды: `assembly` (сессия 4) и `casting` (сессия 8). В обоих случаях старый файл в корне содержал неверный URL/порт. Решение: удалить/переименовать `application.yml` в корне модуля, оставить только `src/main/resources/application.yml`. Проверка: `Get-ChildItem modules,services -Directory | % { if (Test-Path (Join-Path $_.FullName "application.yml")) { $_ } }` — не должно быть вывода.

### Правило на будущее
В конце каждой сессии: `mvn verify` → `EXIT: 0` → коммит кода → дописать в `PROGRESS.md` раздел `## 📌 СЕССИЯ <дата>` (5–10 строк) → `git commit -m "docs: PROGRESS.md — сессия <дата>"`.

### Обновление 2026-10-23 (вечер)
- 3b закрыт: `LenientBooleanDeserializer` (`05cf8ed`) — принимает `true`/`"true"`/`1`/`"yes"`/`"on"`. 16 тестов, 139/139.
- Smoke пройден: TURNING (gaps=1,recs=2), DIE_CASTING (0/0), идемпотентность (1 строка), строка `"true"` → матч (0/0).
- Осталось: Javadoc в 3 моделях (косметика, 1.5 ч) — вынести на отдельную сессию.

### Обновление 2026-10-23 (Javadoc закрыт)

- Восстановлен Javadoc в `EquipmentGapAnalysis` и `EquipmentRecommendation` (recommendation-service).
- Метод: CP1251→UTF-8 восстановление по всему файлу + ручная правка литерала `"Отклонено: "` (был корректным до преобразования — грабли №12).
- `EquipmentRequirement` уже был чистым — не трогали. В equipment-service Javadoc отсутствует — вне scope.
- `mvn -pl services/recommendation-service -am verify` → BUILD SUCCESS, 143/143, jacoco passed.
- **Техдолг «Javadoc в 3 моделях» закрыт.**

### Обновление 2026-10-23 (Javadoc equipment-service)

- Добавлен Javadoc в 6 моделей `equipment-service`: `EquipmentAssignment`, `EquipmentRequirement`, `EquipmentStatus`, `EquipmentTooling`, `EquipmentToolingId`, `EquipmentGapAnalysis`.
- Стиль — по эталону `Equipment.java` (назначение, `<ul>` атрибутов, `<p>Таблица: … миграция VN`). Версии миграций: assignments — V4, остальные — V3.
- `mvn -pl services/equipment-service -am verify` → **BUILD FAILURE по jacoco** (см. техдолг ниже). 84 теста — все зелёные, Javadoc к покрытию не относится.
- Проверено на HEAD (`git stash` → verify → `stash pop`): те же `Rule violated` — проблема предсуществующая.

| ~~jacoco-eq~~ | ~~`equipment-service`: покрытие 60/40 при порогах 75/80~~ ✅ закрыт (lines 80.2%, branches 88.1%) | — |

- Добавлены unit-тесты `CapabilityMatchingServiceTest` (42 теста, JUnit 5 + Mockito, `@ExtendWith(MockitoExtension.class)`).
- Покрыты: guards (`null`/blank/empty), фильтры (CNC, power, force, accuracy, material, dimensions), парсинг (`str`/`bool`/`dec`/`parseAccuracy`/`parseDims`), сортировка по score, `findAlternatives`, `assign` (existing/new/null).
- Покрытие: **lines 60.70% → 80.19%** (+19.5 п.п.), **branches 40.98% → 88.11%** (+47.1 п.п.).
- `mvn -pl services/equipment-service -am verify` → **BUILD SUCCESS**, 126/126, `jacoco:check` passed.
| ~~jacoco-eq~~ | ~~`equipment-service`: покрытие 60/40 при порогах 75/80~~ ✅ закрыт (lines 80.2%, branches 88.1%) | — |

### Обновление 2026-10-24 (orchestrator: обрыв RECOMMENDATION_REVIEW -> ACCEPTED закрыт)

- Фикс `TransitionHandler.route()`: `ASSEMBLY_PROCESS_PLANNING` добавлен в исключение из точки контроля (симметрично `PART_PROCESS_PLANNING`).
- Раньше после `acceptRecommendation` проект застревал в `ASSEMBLY_PROCESS_PLANNING`: команда в `assembly-process-service` не уходила (`AWAITING_DECISION`), `approve()` проскакивал этап через `ASSEMBLY_PROCESS_READY -> NORMING`.
- Теперь: `run()` -> `route(ASSEMBLY_PROCESS_PLANNING)` -> `assembly-process-service` -> `ASSEMBLY_PROCESS_READY` -> `NORMING` -> каскад до `TECHNICAL_CONTROL`.
- Тесты: `AssemblyCycleIntegrationTest.assemblyProcessPlanning_routedToModule` (переписан), `TransitionHandlerTest.route_assemblyProcessPlanning_sendsDespitePointOfControl` (новый), `route_partProcessPlanning_sendsDespitePointOfControl` (восстановлен).
- Предсуществующий баг `nextAuto_knownStates`: ожидания `EQUIPMENT_MATCHED`/`RECOMMENDATIONS_READY` противоречили `nextAuto() -> null` для развилки `EQUIPMENT_MATCHING` и внешнего `EQUIPMENT_GAP_RESOLUTION`; тест обновлён на `isNull()`.
- `mvn -pl orchestrator -am verify` -> **BUILD SUCCESS**, 103/103, jacoco passed.

### Обновление 2026-10-24 (norming: сессия 1 — компиляция достигнута)

**Цель:** модуль `norming` из «3 файла, 51 ошибка» → компилируется. **Достигнуто:** `mvn compile` → BUILD SUCCESS.

**Создано (15 файлов, коммит `cf902d2`):**
- `model/` (7): `TimeNormSource` (enum), `StageTimeNorm`, `TimeNorm`, `MaterialInfo`, `MaterialConsumption`, `ToolConsumption`, `MaterialNorm` — Lombok `@Builder`/`@Getter`/`@Setter`.
- `dto/` (4): `TimeNormRequest`, `TimeNormResponse`, `MaterialNormRequest`, `MaterialNormResponse` — по образцу вызовов в сервисах.
- `repository/` (2): `TimeNormRepository`, `MaterialNormRepository` — `JpaRepository` + `findByProcessId`/`findByProjectId`.
- `client/EquipmentClient` — пустой `@FeignClient` (методы не вызываются в коде).
- `config/NormingProperties` — `@ConfigurationProperties`, **44 метода** с разумными значениями по умолчанию (реальные справочники — отдельная сессия).

**Правки сервисов:**
- `MaterialNormService`: вложенный `public static class MaterialInfo` вынесен в `model.MaterialInfo` (было дублирование типов, ломало `MaterialNorm.builder().material(...)`); `DRILLING_NOT_USED_PLACEHOLDER` → `MACHINING, CNC_MACHINING` (такой константы нет в `Stage.OperationType`).
- `TimeNormService`: опечатка `BigDecimal totalTsh t` → `totalTshT`; в `calculateStage` разделены `Тшт.т` (= Тшт + Тпз/n, без коэффициента) и `Тшт.к` (= Тшт.т × коэффициент источника); в `StageTimeNorm` добавлено поле `tshT`; альтернативная трактовка `Тшт.т = Тшт` зафиксирована **комментарием в коде**.

**Что НЕ сделано (сессии 2+):**
- `norming` **всё ещё закомментирован** в корневом `pom.xml` — reactor его не видит, `mvn verify` не прогонялся.
- Нет `NormingCommandConsumer` (по образцу `CastingCommandConsumer`) — RabbitMQ ещё не подключён.
- State machine: `NORMING` пока `AUTOMATIC`; `nextAuto(NORMING) = NORMS_READY` проскакивает без модуля. Для реального нормирования нужно: `NORMING` → `POINT_OF_CONTROL` + `route()` (исключение) + `nextAuto = null` — но это **меняет текущий by-design auto-state**, обсуждать отдельно.
- Нет `application.yml` (Rabbit, БД, Feign), нет Javadoc, нет тестов.
- `NormingProperties` — с defaults; реальные справочники (нормы времени по операциям, коэффициенты по материалам) — отдельный проект с технологом.

### Обновление 2026-10-25 (norming: сессия 2 — RabbitMQ + БД, end-to-end)

**Цель:** подключить `norming` к RabbitMQ + PostgreSQL. **Достигнуто:** end-to-end работает.

**Что сделано:**
- `NormingCommandConsumer` (`messaging/`) — слушает `iktk.norming.queue`, строит demo-ТП, вызывает `TimeNormService.calculate` + `MaterialNormService.calculate`, публикует `NORMS_READY` в `iktk.results` (routing: `iktk.result.norming`).
- `RabbitTopologyConfig` (`config/`) — `TopicExchange iktk.commands` + `Queue iktk.norming.queue` + `Binding` по `iktk.command.norming`.
- `application.yml` — Rabbit, PostgreSQL (`iktk_norming`), Flyway, `server.port: 8095`, Feign (`equipment: http://localhost:8094`).
- `V1__init_norming.sql` — таблицы `time_norms`, `material_norms` (JSONB для `stage_norms`, `consumptions`, `tool_consumptions`, `material`).
- JPA на `TimeNorm`/`MaterialNorm` — `@Entity`, `@Table`, `@JdbcTypeCode(SqlTypes.JSON)`; `process` → `@Transient` (в БД только `process_id`); `source` → `String` (enum в DTO).
- `flyway-core` + `flyway-database-postgresql` в `norming/pom.xml` (без них Flyway не запускался, `validate` падал на `missing table`).
- `norming` раскомментирован в корневом `pom.xml` — теперь 4-й модуль в reactor.
- Фикс `totalTshT` в `TimeNorm.builder()` — `Тшт.т` сохраняется (раньше `total_tsh_t` был NULL).

**Проверено end-to-end (demo-project-2):**
- `Тшт=19.26`, `Тшт.т=21.26`, `Тшт.к=24.449` (коэф. CALCULATED=1.15) — математика верна.
- `blank_mass=0.785`, `part_mass=0.628`, `norm_per_part=0.8248`, `КИМ=0.7614` — расчёт материала верен.
- `NORMS_READY` ушёл в `iktk.orchestrator.results.queue` (12 → 13).

**Что НЕ сделано (сессии 3+):**
- **Demo-ТП** — `buildDemoProcess` строит 1 операцию `MACHINING` вручную. Реальный ТП — через Feign из `assembly-process-service` (есть `GET /api/v1/processes/{id}` → `AssemblyProcess`, но нет маппинга `AssemblyProcess → TechnologyProcess`).
- **State machine оркестратора:** `NORMING` пока `AUTOMATIC` → `nextAuto(NORMING)=NORMS_READY` проскакивает без модуля. Чтобы оркестратор реально ждал `norming`, нужно `NORMING` → `POINT_OF_CONTROL` + `route()` + `nextAuto=null` — **меняет by-design auto-state**, обсуждать отдельно.
- **12 старых сообщений** в `iktk.orchestrator.results.queue` — накоплены ранее, нужно разобрать (или purge).
- Нет тестов `norming` (`Skipping JaCoCo due to missing execution data`) — сессия 4.
- `NormingProperties` — defaults; реальные справочники норм — отдельный проект с технологом.

### Обновление 2026-10-25 (norming: сессия 3 — реальный ТП из assembly + fallback)

**Цель:** `norming` считает нормы по **реальному** ТП из `assembly-process-service`, а не только по demo. **Достигнуто:** код готов, fallback проверен, реальный путь ждёт запуска assembly.

**Что сделано:**
- **assembly**: `GET /api/v1/assembly/processes/{processId}/stages` — список `AssemblyStage` по `processId` (`AssemblyController.getStages` + `AssemblyProcessService.listStages`). До этого был только `GET /processes/{id}` без stages.
- **norming**: `AssemblyProcessClient` (Feign) — 2 метода: `getProcess(id)`, `getStages(id)`. DTO: `AssemblyProcessDto`, `AssemblyStageDto` (локальные копии для Feign).
- **norming**: `TechProcessMapper` — `AssemblyProcess + List<AssemblyStage>` -> `TechnologyProcess + List<Stage>`. `operationType` (String) -> `Stage.OperationType` (enum) с fallback на `AUXILIARY`.
- **norming**: `NormingCommandConsumer.resolveProcess(projectId, processId)`: если `processId` есть — Feign (assembly), если упало — **fallback на demo-ТП**. Если `processId` нет — сразу demo.
- **norming**: `application.yml` — `iktk.clients.assembly.url` (default `http://localhost:8097`, override через `ASSEMBLY_URL`).

**Проверено (fallback):**
- Команда с `processId=nonexistent-process-123` при **незапущенном** assembly:
  `assembly unavailable (Connection refused: GET http://localhost:8097/...)` -> fallback to demo TP -> расчёт -> `NORMS_READY`.
- БД `demo-fallback-1`: `total_tsh=19.26`, `total_tsh_t=21.26`, `total_tsh_k=24.449` — совпадает с demo.

**НЕ проверено (нужно поднять assembly):**
- Реальный путь: `assembly` запущен -> `AssemblyProcessClient.getProcess/getStages` -> `TechProcessMapper` -> расчёт по **реальным** операциям из `AssemblyStage`.
- Пока `assembly` не поднят, `norming` всегда идёт по fallback (demo-ТП).

**Что осталось (сессии 4+):**
- Поднять `assembly-process-service` (порт? — `application.yml` у assembly **отсутствует**, порт неизвестен; нужно создать `application.yml` с `server.port` и `spring.datasource` на `iktk_assembly`).
- Проверить реальный путь end-to-end.
- State machine: `NORMING` пока `AUTOMATIC` — оркестратор не ждёт `norming`. Обсуждать отдельно.
- Тесты `norming` (unit + integration).
- `NormingProperties` — defaults.

### Обновление 2026-10-25 (assembly: запущен, реальный ТП в norming)

**Цель:** поднять `assembly-process-service` и проверить реальный путь `assembly -> norming`. **Достигнуто:** assembly работает на 8097, реальный ТП загружается и рассчитывается.

**Что сделано в assembly:**
- `application.yml` — старый файл в **корне модуля** (порт 8096) переименован в `.bak`, объединён с новым в `src/main/resources/application.yml`: порт **8097**, `iktk.assembly.*`, `iktk.equipment.url`, `iktk.integration.url`, `baseline-on-migrate`, `missing-queues-fatal: false`.
- `scanBasePackages`: `"ru.iktk.shared"` -> `"ru.iktk.core"` — иначе `StorageClient` (MinIO) тянул `MinioClient` (грабли №7), приложение падало на старте.
- `RabbitTopologyConfig` — создан: `Queue iktk.assembly-process-service.queue` + `Binding` по `iktk.command.assembly-process-service`. До этого очередь не создавалась, consumer падал с `NOT_FOUND`.
- БД `iktk_assembly` создана, Flyway применил `V1__init.sql` (таблицы `assembly_process`, `assembly_stage`, `assembly_component`).

**Проверено end-to-end (реальный ТП):**
- `POST /api/v1/assembly/processes` -> `processId=1b959c6f-...`, `stageCount=5`, `componentCount=3`, `totalTimeMin=165`.
- `GET /api/v1/assembly/processes/{id}/stages` -> 5 операций: `PREPARATION`, `BASIC_ASSEMBLY`, `NODE_ASSEMBLY`, `GENERAL_ASSEMBLY`, `CONTROL`.
- Команда в `norming` с `processId=1b959c6f-...` -> `loaded real TP from assembly, stages=5` -> расчёт -> БД `demo-real-tp-1`: `total_tsh=93.625`, `total_tsh_t=103.625`, `total_tsh_k=119.170`.

**Технологические вопросы (для следующей сессии, с Анатолием):**
- **`operationType` из assembly не совпадает с `Stage.OperationType`:** `PREPARATION`/`BASIC_ASSEMBLY`/`NODE_ASSEMBLY`/`GENERAL_ASSEMBLY`/`CONTROL` -> все fallback в `AUXILIARY`. Нужна таблица соответствий (`PREPARATION -> BLANK_PREPARATION`, `*_ASSEMBLY -> ASSEMBLY`, `CONTROL -> TECHNICAL_CONTROL`) или новые константы в `Stage.OperationType`.
- **`AssemblyStage.timeNormMin` (готовые нормы 20/25/30/35/40 мин) не используются** — `TimeNormService` пересчитывает по формулам `NormingProperties` (defaults). Решить: `timeNormMin` — готовая норма (`source=ACTUAL`) или исходное машинное время (тогда пересчёт нужен).

**Что осталось:**
- Маппинг `operationType` + решение по `timeNormMin` (сессия 4).
- State machine: `NORMING` пока `AUTOMATIC` — оркестратор не ждёт `norming`.
- Тесты `norming`, Javadoc, реальные справочники `NormingProperties`.
- `machining`/`forging` — в том же состоянии, что был `norming` (компиляция + RabbitMQ).

### Обновление 2026-10-25 (norming: маппинг operationType + ACTUAL-режим)

**Цель:** правильный маппинг `operationType` assembly -> `Stage.OperationType` + корректный расчёт по реальным нормам. **Достигнуто:** `totalTsh=150`, `totalTshK=151.5` (было `93.625` / `251.45`).

**Правило (согласовано с Анатолием):**
- Применять рекомендованный расчёт, **обязательно с комментариями** — какой параметр и по какой формуле.
- Позже корректируем на конкретных примерах.

**Маппинг `operationType` (assembly -> Stage.OperationType):**
- `PREPARATION` -> `BLANK_PREPARATION`
- `BASIC_ASSEMBLY`, `NODE_ASSEMBLY`, `GENERAL_ASSEMBLY` -> `ASSEMBLY`
- `CONTROL` -> `TECHNICAL_CONTROL`
- Плюс прямые соответствия (`CASTING`, `MACHINING`, `ASSEMBLY`, `TECHNICAL_CONTROL`, …) — на случай, если assembly начнёт отдавать их напрямую.
- Таблица в `TechProcessMapper.OPERATION_TYPE_MAP`; fallback — `AUXILIARY` с WARN.

**`timeNormMin` / `setupTimeMin` по `source` (вариант «в», гибрид):**
- `source=ACTUAL` (реальный ТП из assembly):
  - `To = timeNormMin` (готовая норма assembly);
  - `Tpz = setupTimeMin` (готовая норма assembly);
  - `Tv = Tob = Totl = 0` — **assembly уже включает полную норму** (`assembly.totalTimeMin = Σ timeNormMin + Σ setupTimeMin`), повторный счёт = двойной.
  - `Tsh = To`, `Tsh.t = To + Tpz/n`, `Tsh.k = Tsh.t × k_source` (для ACTUAL `k=1.0`).
- `source=CALCULATED/PLANT/INDUSTRY/GENERAL` (demo-ТП, fallback): полная цепочка по формулам `TimeNormService` (`Tv`, `Tob`, `Totl` — по `NormingProperties`).

**`NormingCommandConsumer`:** `processSource` = `ACTUAL` если `processId` реальный (не demo), иначе `CALCULATED`.

**Проверка на трёх версиях (`processId=1b959c6f-...`, 5 операций):**
| Проект | Логика | total_tsh | total_tsh_t | total_tsh_k |
|---|---|---|---|---|
| `demo-real-tp-1` | все `AUXILIARY` (fallback) | 93.625 | 103.625 | 119.170 |
| `demo-real-tp-2` | маппинг ок, но `Tv=85` (двойной счёт) | 251.45 | 252.95 | 252.95 |
| `demo-real-tp-3` | маппинг + `Tv=0` для ACTUAL | **150.0** | **151.5** | **151.5** |

**Формулы (с комментариями в коде `TimeNormService.calculateStage`):**
- `To` — основное (машинное) время.
- `Tv` — вспомогательное (установка + обработка + контроль).
- `Top = To + Tv` — оперативное.
- `Tob = Top × %обсл/100` — обслуживание рабочего места.
- `Totl = Top × %отдых/100` — отдых и личные надобности.
- `Tsh = Top + Tob + Totl` — штучное.
- `Tpz` — подготовительно-заключительное.
- `Tsh.t = Tsh + Tpz/n` — технически обоснованное штучно-калькуляционное (без коэффициента).
- `Tsh.k = Tsh.t × k_source` — нормативное штучно-калькуляционное.

**Осталось:**
- State machine: `NORMING` пока `AUTOMATIC` — оркестратор не ждёт `norming`.
- Тесты `norming`, Javadoc.
- Реальные справочники `NormingProperties` (сейчас defaults).
- `machining`/`forging` — в том же состоянии, что был `norming`.

### Обновление 2026-10-25 (orchestrator: NORMING -> POINT_OF_CONTROL, оркестратор ждёт norming)

**Цель:** закрыть последний архитектурный обрыв — оркестратор **проскакивал** `NORMING` (auto `NORMS_READY` уходил раньше, чем `norming` реально считал). **Достигнуто:** оркестратор **ждёт** `NORMS_READY` от `norming`-модуля.

**Правки (5 файлов, коммит `a3cd7fe`):**
- `ProjectState.java`: `NORMING` — `AUTOMATIC` -> `POINT_OF_CONTROL`; `pointsOfControl() +NORMING`, `automaticStates() -NORMING`.
- `TransitionHandler.java`:
  - `nextAuto(NORMING)` -> `null` (ждём внешнего `NORMS_READY`);
  - `nextOnApprove(NORMING)` -> `NORMS_READY` (ручное «нормы приняты»);
  - `route()` — добавлено исключение для `NORMING` (по образцу `PART_PROCESS_PLANNING`, `ASSEMBLY_PROCESS_PLANNING`) — команда уходит в `norming`.
- `StateMachineConfig` — переход `NORMING -> DOCUMENTATION` по `NORMS_READY` **уже был**, не меняли.

**Поведение после правки** (симметрично `ASSEMBLY_PROCESS_PLANNING`):
- Вход в `NORMING` -> `route()` **не шлёт** команду (точка контроля, `AWAITING_DECISION`).
- `POST /run` -> `route()` -> команда в `iktk.norming.queue` -> `norming` считает -> `NORMS_READY` -> `DOCUMENTATION` (auto).
- **Или** `approve()` -> `NORMS_READY` -> `DOCUMENTATION` (ручное «нормы приняты», модуль не вызывается).

**Тесты (5 падений обновлены под новое поведение):**
- `TransitionHandlerTest:54`: `nextAuto(NORMING)` -> `isNull()` (было `isEqualTo(NORMS_READY)`).
- `ProjectStateTest:59`: `NORMING.getCategory() = POINT_OF_CONTROL` (было `AUTOMATIC`).
- `ProjectStateTest:76`: `@DisplayName("... 6 состояний ...")` (было 5).
- `ProjectStateTest:79`: `pointsOfControl().hasSize(6)` (было 5).
- `ProjectStateTest:85`: `containsExactlyInAnyOrder(... +NORMING)` (было без NORMING).
- `ProjectStateTest:95`: `automaticStates().hasSize(7)` (было 8).
- `AssemblyCycleIntegrationTest`: `assemblyProcessReady_cascadesToNorming` (было `cascadesToTechnicalControl`) — теперь каскад останавливается на `NORMING`.

**Результат:** `mvn -pl orchestrator -am verify` -> **BUILD SUCCESS**, **103/103**, jacoco passed.

**Осталось:**
- `machining`/`forging` — по образцу `norming` (компиляция + RabbitMQ).
- Тесты `norming`, Javadoc.
- Реальные справочники `NormingProperties`.
- **Smoke полного цикла** (orchestrator + casting + assembly + equipment + recommendation + norming) — увидеть всю цепочку вживую.

### Обновление 2026-10-25 (SMOKE полного цикла: PART_PROCESS_PLANNING -> COMPLETED)

**Цель:** пройти полный цикл от создания проекта до `COMPLETED` через реальные сервисы. **Достигнуто:** `state = COMPLETED`, `completed = true`.

**Сценарий (smoke-проект `ab59102a-...`):**
1. `POST /projects` -> `PART_PROCESS_PLANNING` (POINT_OF_CONTROL).
2. `approve` -> `TOOLING_DESIGN`.
3. `approve` -> `EQUIPMENT_MATCHING` (auto).
4. `POST /events?event=EQUIPMENT_GAP` -> `EQUIPMENT_GAP_RESOLUTION` (auto).
5. `recommendation-service` consumer -> `RECOMMENDATIONS_READY` -> `RECOMMENDATION_REVIEW`.
6. `approve` -> `ASSEMBLY_PROCESS_PLANNING`.
7. **`run`** -> `route()` -> `iktk.command.assembly-process-service` -> `AssemblyCommandConsumer` -> `designProcess` -> `processId=4f2b2291-...` (5 stages, 3 components) -> `ASSEMBLY_PROCESS_READY` -> **`NORMING`**.
8. **`run`** -> `route()` -> `iktk.command.norming` -> `NormingCommandConsumer` -> `NORMS_READY` -> `DOCUMENTATION` (auto) -> `TECHNICAL_CONTROL`.
9. `approve` -> **`COMPLETED`**.

**Что проверено (работает):**
- Полная state machine: 13 состояний, 5 точек контроля, auto-переходы.
- `route()` -> assembly (`ASSEMBLY_PROCESS_PLANNING` — наш фикс), реальный ТП спроектирован (`stages=5`).
- `route()` -> norming (`NORMING` — наш фикс), consumer отработал, `NORMS_READY` дошёл.
- `recommendation-service` — consumer отработал, `RECOMMENDATIONS_READY` дошёл.
- RabbitMQ: очереди `casting`, `assembly`, `norming`, `recommendation` — все с consumer'ами.

**НАЙДЕН БАГ (следующая задача):**
- **`processId` от assembly не доходит до norming.** В smoke-проекте `norming` посчитал **demo-ТП** (`process_id = demo-process-ab59102a-...`, `total_tsh = 19.26`), а не реальный ТП от assembly (`processId = 4f2b2291-...`, `stages = 5`).
- **Причина:** `AssemblyCommandConsumer` возвращает `ASSEMBLY_PROCESS_READY` с `processId` в payload, но `orchestrator.onModuleResult` **не сохраняет** `processId` в `runtime.payload`. При `route(NORMING)` payload пустой -> `norming` идёт по fallback (demo-ТП).
- **Решение:** в `orchestrator` — `onModuleResult` должен мержить `payload` из результата модуля в `runtime.payload`, чтобы `processId` (и другие артефакты) доходили до следующего модуля. Это архитектурная правка (сессия 5).

**Сервисы (запущены):**
- `orchestrator` — 8080 (H2 in-memory, при перезапуске данные теряются).
- `assembly-process-service` — 8097 (`iktk_assembly`, Flyway V1).
- `norming` — 8095 (`iktk_norming`, Flyway V1).
- `recommendation-service` — 8098.
- `equipment-service` — 8094.
- `casting` — не запущен (нет consumer на `iktk.casting.queue`), для smoke не потребовался (нет перехода в `casting` в этом сценарии).
- RabbitMQ, PostgreSQL, Redis, MinIO — docker.

**Важные детали сессии (для будущих smoke):**
- **Orchestrator перезапущен**: старый PID 2260 (от 23.10.2026) не содержал фиксов `route()` для `NORMING`/`ASSEMBLY_PROCESS_PLANNING` — smoke бы не прошёл. Убит, запущен новый (PID 9288).
- **Assembly перезапущен**: старый PID 8644 держал порт 8097, лог обрывался на `13:04` (после ручного `designProcess`). Убит, запущен новый (PID 9668).
- **Грабли (поиск java-процессов):** `Get-CimInstance Win32_Process | Where CommandLine -match "assembly-process-service"` находит **mvn-родителя**, но **не java-процесс** (у него `-cp @argfile`, без имени модуля). Искать по **порту** (`Get-NetTCPConnection -LocalPort 8097`), а не по `CommandLine`.
- **`run-norming.log` — старые записи** (PID 19960, `10:43`). Новый `norming` (PID 10904) пишет в **своё окно**, не в файл. При следующем запуске — `Tee-Object` для лога.
- **`casting` не проверен** в полном цикле: на `PART_PROCESS_PLANNING` сделан `approve` (ручное подтверждение), а не `run` → `casting`. Для проверки ветки `casting` — отдельный smoke (сессия 5+).

### Обновление 2026-10-25 (payload propagation: фикс + SMOKE v3)

**Цель:** закрыть баг из SMOKE v2 — `processId` от assembly не доходил до norming (fallback на demo-ТП). **Достигнуто:** `processId` доходит, `norming` считает по **реальному** ТП.

**Причина (из SMOKE v2):**
- `ModuleResultConsumer.onResult` извлекал `payload` (с `processId`) из сообщения модуля, `onModuleResult` передавал в `dispatchEvent`, но `dispatchEvent` **не сохранял** payload в `runtime`.
- `run()` берёт `payload` из `runtime.getPayload()` -> при `route(NORMING)` payload был `null` -> norming шёл по fallback (demo-ТП, `19.26`).

**Фикс (`4f901fa`, 1 файл, 16 insertions, 1 deletion):**
- `OrchestrationService.dispatchEvent`: перед `persistState` добавлен мерж `payload` в `runtime`:
  - `merged = runtime.getPayload() + payload` (новый payload перекрывает старый);
  - `runtime.setPayload(merged)` + `projectStateRepository.save(runtime)`;
  - работает только если `payload != null && !payload.isEmpty()` (не затирает существующий пустым).
- `import java.util.HashMap` добавлен (для `merged`).

**SMOKE v3 (проект `4bb222b5-...`):**
- Тот же сценарий, что v2: `PART_PROCESS_PLANNING -> … -> ASSEMBLY_PROCESS_PLANNING -> NORMING -> TECHNICAL_CONTROL -> COMPLETED`.
- `state = COMPLETED`, `completed = true`.
- **БД `iktk_norming`:** `process_id = 13409cf4-f92f-420e-a380-b32f5250460a` (**реальный UUID от assembly**, не `demo-process-...`), `total_tsh = 150.0`, `total_tsh_t = 151.5`, `total_tsh_k = 151.5`.

**Сравнение с SMOKE v2:**
| Проект | `process_id` | `total_tsh` | `total_tsh_k` | Статус |
|---|---|---|---|---|
| `ab59102a-...` (v2) | `demo-process-...` | 19.26 | 24.449 | fallback (баг) |
| **`4bb222b5-...` (v3)** | **`13409cf4-...`** (реальный) | **150.0** | **151.5** | **фикс работает** |

**Тесты:** `mvn -pl orchestrator -am verify` -> **BUILD SUCCESS**, **103/103**, jacoco passed.

**Что осталось:**
- `casting` не проверен в полном цикле (в smoke обходим через `approve`).
- `machining`/`forging` — по образцу `norming` (компиляция + RabbitMQ).
- Тесты `norming`, Javadoc.
- Реальные справочники `NormingProperties`.

### Обновление 2026-10-25 (bom-service: BOM из geometry -> iktk_bom, session 6)

**Цель:** `bom-service` должен извлекать BOM из `integration` (geometry) и сохранять в `iktk_bom`, публиковать `BOM_EXTRACTED`. **Достигнуто:** работает end-to-end.

**Проблемы, которые пришлось решить:**
- **Порт 8097** — конфликт `bom-service` и `assembly-process-service`. `bom-service` -> **8091**, `integration` -> **8092** (было 8095, занято `norming`).
- **`integration` без datasource** — `application.yml` был только с `server.port` + `logging`. Добавлены `spring.datasource` (БД `iktk_geometry`), `spring.jpa`, `spring.flyway`.
- **API не стыкуется** — `bom-service.IntegrationClient` (Feign) ждёт `/api/v1/integration/{cad-system,bom,connections}`, а `integration.GeometryController` отдаёт `/api/v1/geometry/{projectId}`. Создан `IntegrationController` (3 эндпоинта) + DTO (`RawBomDto`, `RawItemDto`, `RawConnectionDto`).
- **`@EntityScan` тянул `projects`** — `Schema-validation: missing table [projects]` (грабли №8). `bom-service/BomApplication`: `@EntityScan` сужен до `ru.iktk.bom.model` (без `ru.iktk.core.domain`).
- **`StorageClient` (MinIO) без бина** — `scanBasePackages = "ru.iktk.shared"` тянул `StorageClient`, а `MinioClient` не создавался (грабли №7). В `shared` добавлен `MinioConfig` (`@Bean MinioClient`, endpoint `http://localhost:9000`, `iktk-minio`/`iktk_dev_password`).
- **Нет `RabbitTopologyConfig`** — очередь `iktk.bom-service.queue` не создавалась, consumer падал с `NOT_FOUND`. Создан `bom-service/config/RabbitTopologyConfig`.
- **Нет `iktk.entity_versions`** — `VersioningService` (shared) пишет в `iktk.entity_versions`, но миграции не было. Создана `V2__entity_versions.sql` (схема `iktk` + таблица).
- **`projectId` должен быть UUID** — `VersioningService` вызывает `UUID.fromString(projectId)`. Тестовые `bom-smoke-1` падали; перешли на UUID.

**Проверено end-to-end (`projectId=addbafb1-...`):**
- `integration/bom?geometryId=geo-12345` -> `RawBom` (`rootId=ASSY-test`, `items=[{id: geo-12345, material: Steel 20Kh, type: PART}]`).
- `BomCommandConsumer` -> `analysis done, status=COMPLETED, positions=1`, `result sent, success=true`.
- **БД `iktk_bom`:**
  - `assembly_units`: `project_id=addbafb1-...`, `designation=ASSY-addbafb1-...`, `item_count=1`.
  - `bom_items`: `designation=geo-12345`, `quantity=1`, `material=Steel 20Kh`, `item_type=PART`.
  - `iktk.entity_versions`: `ASSEMBLY | v1 | CREATE | system`.

**Коммиты:**
- `7ccbfd7` — `feat(shared): MinioConfig` (40 insertions).
- `6c5633b` — `feat(bom-service,integration): bom-service end-to-end` (9 files, 266 insertions, 16 deletions).

**Что НЕ сделано (сессия 7+):**
- **Полный smoke через orchestrator** (`ASSEMBLY_ANALYSIS -> bom-service -> BOM_EXTRACTED -> PART_PROCESS_PLANNING`) — orchestrator убит (останавливали всё для install shared). Нужно перезапустить orchestrator (8080), equipment (8094), norming (8095), assembly (8097), recommendation (8098) — все были убиты.
- **`casting`** — не проверен (нет перехода `PART_PROCESS_PLANNING -> casting` в smoke).
- **`machining`/`forging`** — по образцу `norming` (компиляция + RabbitMQ).
- **Тесты `bom-service`/`integration`/`norming`** + Javadoc.
- **Реальные справочники** `NormingProperties`.
- **`integration`** — пока «1 геометрия = 1 деталь», нет многоуровневой сборки (для реального BOM из CAD).

### Обновление 2026-10-25 (orchestrator: objectType=ASSEMBLY -> bom-service -> PART_PROCESS_PLANNING)

**Цель:** полный smoke через orchestrator с `objectType=ASSEMBLY` — от `POST /projects` до `PART_PROCESS_PLANNING` через `bom-service` (без ручных `approve`). **Достигнуто:** автоматически.

**Две правки `orchestrator` (коммит `1302c6f`, 2 files, 30 insertions, 4 deletions):**
- `startProject`: `initialPayload` с `objectRef`, `objectType` (ASSEMBLY/PART), `assemblyId`. Раньше payload был пустой — `dispatchEvent` не мог узнать тип объекта.
- `dispatchEvent`: спецслучай `GEOMETRY_ANALYSIS` + `objectType=ASSEMBLY` -> `IS_ASSEMBLY` (вместо `IS_PART`). `nextAuto(GEOMETRY_ANALYSIS)` по-прежнему `IS_PART` (default), но при `ASSEMBLY` переопределяется.
- `TransitionHandler.nextAuto(ASSEMBLY_ANALYSIS)` -> `null` (было `BOM_EXTRACTED`). Ждём `BOM_EXTRACTED` от `bom-service`, а не проскакиваем в `PART_PROCESS_PLANNING`.

**Проверено end-to-end (`projectId=a0f1e051-...`):**
- `POST /projects` (`objectType=ASSEMBLY`) -> **`ASSEMBLY_ANALYSIS`** (`availableEvents=[]`, `pointOfControl=false`), не `PART_PROCESS_PLANNING`.
- `route(ASSEMBLY_ANALYSIS)` -> `iktk.command.bom-service` -> `BomCommandConsumer` -> `geometryId=null` -> `IntegrationController` default `geo-12345` -> `BomService.analyze`.
- `BOM_EXTRACTED` -> `PART_PROCESS_PLANNING` **автоматически** (без `approve`).
- **БД `iktk_bom`:**
  - `assembly_units`: `project_id=a0f1e051-...`, `designation=ASSY-a0f1e051-...`, `item_count=1`.
  - `bom_items`: `designation=geo-12345`, `material=Steel 20Kh`, `item_type=PART`.

**Тесты:** `mvn -pl orchestrator test` -> **103/103**, `mvn -pl orchestrator -am verify` -> **BUILD SUCCESS**, jacoco passed.

**Что осталось:**
- **`casting`** — не проверен (переход `PART_PROCESS_PLANNING -> casting`). Сейчас `PART_PROCESS_PLANNING` — точка контроля, обходится `approve`.
- **`machining`/`forging`** — по образцу `norming`.
- **Тесты `bom-service`/`integration`/`norming`** + Javadoc.
- **`integration`** — «1 геометрия = 1 деталь», нет многоуровневой сборки.
- **Реальные справочники `NormingProperties`.

### Обновление 2026-10-25 (casting: PART_PROCESS_PLANNING -> TOOLING_DESIGN автоматически)

**Цель:** `PART_PROCESS_PLANNING → casting → PART_PROCESS_READY → TOOLING_DESIGN` автоматически (без `approve`). **Достигнуто.**

**Две правки:**
- **orchestrator** (`TransitionHandler.nextAuto`): `PART_PROCESS_PLANNING` -> `null` (было `PART_PROCESS_READY`). Ждём `PART_PROCESS_READY` от `casting`, не проскакиваем в `TOOLING_DESIGN`.
- **casting**: `application.yml` — добавлен `spring.datasource` (БД `iktk_casting`), `spring.jpa`, `spring.flyway`, `iktk.integration.url = http://localhost:8092`, `iktk.equipment.url = http://localhost:8094`, `demo-fallback: true`.
- **casting**: старый `modules/casting/application.yml` (в **корне модуля**, с `integration.url = http://localhost:8095`) переименован в `.bak`.

**Причина бага (важно):** `spring-boot:run` читает `./application.yml` (рабочая папка = `modules/casting`) **раньше**, чем `src/main/resources/application.yml`. В корне лежал старый файл с `8095` (порт `norming`), из-за чего `casting` звонил в `norming`, получал 404. Тот же паттерн был с `assembly` (сессия 4).

**Проверено end-to-end (`projectId=498cb396-...`):**
1. `POST /projects` (`objectType=ASSEMBLY`, `geometryId=geo-12345`) -> `ASSEMBLY_ANALYSIS`.
2. `bom-service` (асинхронно) -> `BOM_EXTRACTED` -> `PART_PROCESS_PLANNING`.
3. `run()` -> `route()` -> `casting` -> `CastingCommandConsumer` -> `designProcess` (`objectRef=geo-12345`, через `integration:8092`) -> `INVESTMENT_CASTING`, `stages=6` -> `PART_PROCESS_READY` -> **`TOOLING_DESIGN`**.

**Ключевое:** `objectRef=geo-12345` **дошёл до casting** через `payload propagation` (сессия 5) + `startProject initialPayload` (сессия 7). Это доказывает, что цепочка передачи данных работает на всём пути.

**Коммиты:**
- `81b7061` — `fix(orchestrator): nextAuto(PART_PROCESS_PLANNING)=null`.
- `5869af3` — `fix(casting): datasource + integration.url=8092, старый application.yml в корне -> .bak`.

**Что осталось:**
- `machining`/`forging` — по образцу `norming`/`casting`.
- Полный smoke до `COMPLETED` (`TOOLING_DESIGN → … → TECHNICAL_CONTROL`).
- Тесты `bom-service`/`integration`/`norming`/`casting` + Javadoc.
- Реальные справочники `NormingProperties`.

### Обновление 2026-10-26 (ПОЛНЫЙ SMOKE до COMPLETED)

**Цель:** пройти полный цикл от `POST /projects` до `COMPLETED` через **все** реальные сервисы. **Достигнуто.**

**Сценарий (`projectId=2eaea50f-0f41-4027-8dd5-a26b5f4852cc`):**
1. `POST /projects` (`objectType=ASSEMBLY`, `geometryId=geo-12345`) -> `ASSEMBLY_ANALYSIS`.
2. `bom-service` (асинхронно) -> `BOM_EXTRACTED` -> `PART_PROCESS_PLANNING`.
3. `run` -> `casting` -> `PART_PROCESS_READY` -> `TOOLING_DESIGN`.
4. `approve` -> `EQUIPMENT_MATCHING` (auto).
5. `POST /events?event=EQUIPMENT_GAP` -> `EQUIPMENT_GAP_RESOLUTION`.
6. `recommendation-service` -> `RECOMMENDATIONS_READY` -> `RECOMMENDATION_REVIEW`.
7. `approve` -> `ASSEMBLY_PROCESS_PLANNING`.
8. `run` -> `assembly` -> `ASSEMBLY_PROCESS_READY` -> `NORMING`.
9. `norming` -> `NORMS_READY` -> `DOCUMENTATION` (auto) -> `TECHNICAL_CONTROL`.
10. `approve` -> **`COMPLETED`** (`completed=true`, `stateCategory=TERMINAL`).

**БД после smoke:**
- `iktk_bom.assembly_units`: `project_id=2eaea50f-...`, `designation=ASSY-2eaea50f-...`, `item_count=1`.
- `iktk_casting.casting_process`: `project_id=2eaea50f-...`, `method=INVESTMENT_CASTING`, `object_ref=geo-12345`.
- `iktk_norming.time_norms`: `project_id=2eaea50f-...`, `process_id=c399bcad-...`, `total_tsh=150.0`, `total_tsh_k=151.5` (реальные нормы, не demo).

**Что это доказывает:** вся система работает end-to-end — orchestrator, bom-service, casting, recommendation, assembly, norming, equipment, integration — через RabbitMQ, Feign, PostgreSQL, Flyway. **3 модуля** (bom, casting, norming) оставили реальные записи в своих БД.

**Замечание по процессу:** в шагах 7-9 потребовался повторный `run` (assembly обрабатывал >3 сек) — это нормально для асинхронной архитектуры. `norming` отработал (запись в БД с реальными числами).

### Обновление 2026-10-26 (forging: модуль компилируется и работает end-to-end)

**Цель:** `forging` из «2 файла, не компилируется» → компилируется + работает end-to-end (как `norming`/`casting`). **Достигнуто.**

**Создано (15 файлов, коммит `d205edc`):**
- `model/` (5): `ForgingMethod` (enum), `BilletCalculation`, `ForgingTransition` (+ `TransitionType`), `ForgingTooling`, `ForgingProcess` (`@Entity`, JSONB для вложенных).
- `dto/` (2): `ForgingRequest` (с `ProductionType`), `ForgingResponse`.
- `repository/` (1): `ForgingProcessRepository` (`JpaRepository`).
- `client/` (2): `GeometryClient` (Feign → `integration:8092`, возвращает `ForgingProcessService.GeometryData`), `EquipmentClient` (Feign → `equipment:8094`, `isMethodAvailable`).
- `config/` (2): `ForgingProperties` (13 методов, справочные значения), `RabbitTopologyConfig` (`iktk.forging.queue`).
- `messaging/` (1): `ForgingCommandConsumer` (`iktk.command.forging` → `designProcess` → `PART_PROCESS_READY` в `iktk.results`).
- `application.yml` (порт **8093**, БД `iktk_forging`, integration 8092, equipment 8094), `V1__init.sql` (`forging_process`).
- `ForgingApplication`: `@EntityScan("ru.iktk.modules.forging.model")` (грабли №8 — не тянуть `projects`).
- `pom.xml`: `flyway-core` + `flyway-database-postgresql`.
- Корневой `pom.xml`: `forging` раскомментирован (4-й модуль в reactor).

**Правки (2):**
- `ForgingProcess.method`: `String` → `ForgingMethod` (+ `@Enumerated(EnumType.STRING)`) — ошибка типов при компиляции.
- `ForgingRequest.productionType`: `String` → `TechnologyProcess.ProductionType` — ошибка сравнения с enum.

**Проверено end-to-end (`projectId=a097907a-...`):**
- `iktk.command.forging` → `ForgingCommandConsumer` → `designProcess` (`objectRef=geo-12345` через `integration:8092`) → `HOT_DIE_FORGING`, `transitions=4`, `stages=8` → `PART_PROCESS_READY`.
- **БД `iktk_forging`:** `project_id=a097907a-...`, `method=HOT_DIE_FORGING`, `object_ref=geo-12345`.
- **`mvn -pl modules/forging -am verify`** → **BUILD SUCCESS** (forging без тестов, jacoco skip).

**Грабли (важно, в этой сессии):**
- **`Resolve-Path` молча оставляет `$p` от предыдущей команды**, если путь не найден (в отличие от `cd`). Результат: SQL-миграция `V1__init.sql` записалась в `ForgingRequest.java` (из-за `$base = null` → `Join-Path` упал → `$p` остался от прошлой команды). Решение: создавать папки `-Force` **до** `Resolve-Path`, проверять `$p` перед `WriteAllLines`.

**Что осталось:**
- **`machining`** — 3 файла (1334 строки логики), **21 класс** (сложнее `forging`): `CncProgramService`, `ToolpathGenerator`, `PostprocessorRegistry`, `GCodeValidator` и др. Отдельная сессия.
- **Тесты** `bom-service`/`integration`/`norming`/`casting`/`forging` + Javadoc.
- **Реальные справочники** `NormingProperties`/`ForgingProperties`.
- **`integration`** — многоуровневая сборка (не «1 геометрия = 1 деталь»).

### Обновление 2026-10-26 (machining: модуль компилируется, BUILD SUCCESS)

**Цель:** `machining` из «3 файла, не компилируется» → **BUILD SUCCESS** (как `forging`). **Достигнуто.**

**Создано (30 файлов + правки):**
- `model/` (6): `MachiningMethod` (enum), `MachiningProcess` (`@Entity`, JSONB), `MachiningOperation`, `CuttingTool`, `CuttingMode`, `CncProgram` (`@Entity`, JSONB).
- `dto/` (7): `MachiningRequest`, `MachiningResponse`, `CncProgramRequest`, `CncProgramResponse`, `GeometryData`, `ManufacturabilityReport`, `Setup`, `EquipmentInfo` — последние 4 извлечены из сервисов.
- `repository/` (2): `MachiningProcessRepository`, `CncProgramRepository`.
- `client/` (4): `GeometryClient` (Feign → `integration:8092`), `EquipmentClient` (Feign → `equipment:8094`), `ToolingClient` (Feign → `tooling:8096`), `StorageClient` (заглушка).
- `config/` (2): `MachiningProperties` (14 методов, справочные заглушки), `RabbitTopologyConfig` (`iktk.machining.queue`).
- `postprocessor/` (2): `Postprocessor` (интерфейс), `PostprocessorRegistry`.
- `toolpath/` (3): `Toolpath`, `ToolpathPoint` (извлечены из CPS), `ToolpathGenerator` (заглушка).
- `validator/` (2): `GCodeValidator` (заглушка), `ValidationResult` (извлечён из CPS).
- `model/` (3 extracted): `OperationProgram`, `SetupSheet`, `ToolSpec`.

**Извлечено 10 nested-классов** из `MachiningProcessService` и `CncProgramService` → отдельные файлы по пакетам. Это архитектурно необходимо: `GeometryClient`/`EquipmentClient` возвращают `GeometryData`/`EquipmentInfo`, `ToolpathGenerator` — `Toolpath`, `GCodeValidator` — `ValidationResult`.

**Правки в существующем (3 файла):**
- `MachiningApplication`: `+@EntityScan("ru.iktk.modules.machining.model")`, порт `"8095"` (было `"8092"`), очередь `"iktk.command.machining"` (было `"iktk.machining.queue"`).
- `MachiningProcessService`: удалены 3 nested-класса, добавлены 3 импорта.
- `CncProgramService`: удалены 7 nested-классов, добавлены 6 импортов.
- `pom.xml` (корневой): раскомментирован `<module>modules/machining</module>`.

**Правки типов (грабли forging №1–2):**
- `MachiningMethod` — enum с `@Enumerated(EnumType.STRING)` в `MachiningProcess`.
- `productionType` в DTO **не добавляли** — исходник его не использует; когда понадобится — сразу `TechnologyProcess.ProductionType`.

**Исправлен 1 баг исходника:**
- `CncProgramService:326` вызывал `op.getCuttingMode()` на `OperationProgram` (у которого был только `getMode()`). Добавлен alias `getCuttingMode()` в `OperationProgram`.

**Проверено:**
- `mvn -pl modules/machining -am compile` → **BUILD SUCCESS**.
- `mvn -pl modules/machining -am verify` → **BUILD SUCCESS** (42.9 сек, тесты отсутствуют).

**Грабли (в этой сессии):**
- **`[System.IO.File]::WriteAllLines` с относительным путём** резолвит через `Directory.GetCurrentDirectory()` = `C:\WINDOWS\system32`, а не через `$PWD`. Поймали на A.6a: `WriteAllLines("modules\...\dto\X.java", ...)` → ошибка «путь не найден». Фикс: **всегда `Join-Path $root ...`**, где `$root = (Get-Location).Path`, с проверкой `Test-Path $root\pom.xml`.
- **PowerShell и Java-код в одной консоли.** Вставка `enum MachiningMethod { ... }` или `for (OperationProgram op : xs)` в PS даёт `ParserError` (Java-синтаксис не парсится). Фикс: копировать **только блоки `# ====`**, объясняющий текст с Java — отдельно.
- **`diff` (алиас `Compare-Object`) сравнивает имена, а не содержимое.** `diff f1 f2` → «есть f1, есть f2». Фикс: `Compare-Object (Get-Content f1) (Get-Content f2)` или `git diff --no-index`.
- **Порядок удаления nested-классов из файла** — **от конца к началу**. Иначе индексы сдвигаются, и `Remove-NestedClass` удаляет не то (или падает).
- **`$ErrorActionPreference = "Stop"` + `throw`** спасли при первом сбое A.6a: без них скрипт продолжил бы и оставил полу-состояние (часть файлов создана, часть — нет).

**Что осталось:**
- **E2E-запуск `machining`** (как `forging` в сессии 10): `iktk.command.machining` → `designProcess` → `PART_PROCESS_READY` → запись в `iktk_machining`.
- **`machining` — реализовать заглушки:** `ToolpathGenerator`, `GCodeValidator`, `StorageClient` (MinIO), `MachiningProperties` (реальные справочники).
- **Feign-контракты** — согласовать `@GetMapping` с реальными эндпоинтами `integration` / `equipment` / `tooling`.
- **Тесты** `machining` (как `forging` — отдельная сессия).
- **`machining` Messaging:** `MachiningCommandConsumer` (не создан в этой сессии; следующий шаг).
- **`integration`** — многоуровневая сборка (не «1 геометрия = 1 деталь»).

### Обновление 2026-10-26 (machining: messaging end-to-end, consumer готов)

**Цель:** `machining` из «компилируется, но не запускается» → **готов к E2E** (consumer слушает очередь, публикует результат). **Достигнуто.**

**Создано (4 файла, коммит `d6e0b21`):**
- `application.yml` (74 строки): порт **8095**, БД `iktk_machining`, RabbitMQ localhost:5672, Feign URL (integration:8092, equipment:8094, tooling:8096), `iktk.messaging.*` с `iktk.machining.queue`, справочные заглушки `iktk.machining.*`.
- `db/migration/V1__init.sql` (33 строки): таблицы `machining_process` и `cnc_program`, JSONB для вложенных, индексы по `project_id`/`object_ref`/`process_id`.
- `config/RabbitTopologyConfig.java` (44 строки): топология **по образцу forging** — exchange `iktk.commands` (topic), routing key `iktk.command.machining`, очередь `iktk.machining.queue`. DLQ не добавляли (как в forging).
- `messaging/MachiningCommandConsumer.java` (131 строка): `@RabbitListener` на `iktk.machining.queue` → `designProcess` → `PART_PROCESS_READY` в `iktk.results` (routing `iktk.result.machining`). Формат входа — как в forging: `Map<String,Object>` через `ObjectMapper`, поля `projectId` + `payload.objectRef`.

**Правки (1):**
- `MachiningApplication.main`: дефолт очереди в логе → `iktk.machining.queue` (для консистентности с forging).

**Исправление неточности PROGRESS.md (сессия 10):**
- Было записано «очередь `iktk.command.forging`», фактически в forging — **`iktk.forging.queue`**. Приведено к единому паттерну `<module>.queue`.

**Проверено:**
- `mvn -pl modules/machining -am compile` → **BUILD SUCCESS** (уже с consumer'ом).
- `mvn -pl modules/machining -am verify`  → **BUILD SUCCESS**.

**Грабли (в этой сессии):**
- **`mvn compile` = SUCCESS ≠ «все файлы созданы».** В прошлой попытке D.1–D.3 не выполнились, но compile прошёл — потому что компилировал старый код без consumer'а. Урок: после каждого создающего блока — явный `Test-Path` + `git status`.
- **A.3 (5 файлов) не были применены в сессии A.** Только `A.3-fix` (раскомментировать `pom.xml`). `application.yml`, `V1__init.sql`, `RabbitTopologyConfig.java` не создавались. Не блокировало компиляцию, но сломало бы E2E. Восстановлено в D-fix.

**Что осталось (перед E2E):**
- **Feign-контракты** — сверить `@GetMapping` пути с реальными эндпоинтами `integration:8092` / `equipment:8094` / `tooling:8096`. Без этого E2E упадёт на клиенте (404).
- **E2E-прогон** `machining`: `iktk.command.machining` → `PART_PROCESS_READY` → запись в `iktk_machining`.
- **Реальные реализации** заглушек (`ToolpathGenerator`, `GCodeValidator`, `StorageClient`/MinIO, `MachiningProperties` — справочники).
- **Тесты** `machining`.

### Обновление 2026-10-26 (machining: Feign-контракты сверены со stub'ами)

**Цель:** сверить Feign-клиенты `machining` с реальными эндпоинтами `integration`/`equipment`/`tooling`. **Достигнуто.**

**Реальные эндпоинты (по факту):**
- `integration`: `GET /api/v1/geometry/{projectId}?objectRef=` — **есть** (совпадает с forging/casting).
- `equipment-service`: `GET /api/v1/equipment/is-method-available?projectId=&method=` → `boolean` — **есть**. `GET /api/v1/equipment/{id}` (получить станок) и `GET /api/v1/equipment/find` (найти доступное) — **НЕТ**.
- `tooling`: **сервиса нет** (ни в `services/`, ни в `modules/`).

**Правки (3 файла):**
- `client/GeometryClient.java`: `@GetMapping("/api/v1/geometry/{projectId}")` с `@PathVariable projectId, @RequestParam objectRef` (было `/api/geometry/fetch?projectId=&objectRef=`). Feign остаётся, name = `integration`, url = `${iktk.machining.integration.url:http://localhost:8092}`.
- `client/EquipmentClient.java`: убран `@FeignClient`, оставлен интерфейс (методы `getEquipment`, `findAvailable` без изменений).
- `client/ToolingClient.java`: убран `@FeignClient`, оставлен интерфейс (метод `selectTool` без изменений).

**Создано (2 файла, заглушки):**
- `client/EquipmentClientStub.java` (`@Component`): `getEquipment` → фиктивный `EquipmentInfo` (model=CNC-DEFAULT, cncSystem=ISO6983, axes=3); `findAvailable` → `"EQ-" + method + (cnc ? "-CNC" : "")`.
- `client/ToolingClientStub.java` (`@Component`): `selectTool` → фиктивный `CuttingTool` (END_MILL, ⌀10, HSS, TiAlN).

**Правка `application.yml`:** убран `iktk.machining.tooling.url` (сервиса нет).

**Проверено:**
- `mvn -pl modules/machining -am compile` → **BUILD SUCCESS**.
- `mvn -pl modules/machining -am verify`  → **BUILD SUCCESS**.

**Что осталось:**
- **E2E-прогон** `machining`: команда → `PART_PROCESS_READY` → запись в `iktk_machining`. Потребуется поднять RabbitMQ + PostgreSQL (`iktk_machining`).
- **Когда появятся реальные эндпоинты** `equipment` для `{id}` и `find` — заменить `EquipmentClientStub` на `@FeignClient`.
- **Когда появится сервис `tooling`** — заменить `ToolingClientStub` на `@FeignClient`.
- **Маппинг `Map<String,Object>` → `GeometryData`** в `GeometryClient`: реальный `GeometryController` возвращает `Map`, Jackson десериализует в `GeometryData`. Если поля не совпадут — увидим на E2E.
- **Реальные реализации** заглушек (`ToolpathGenerator`, `GCodeValidator`, `StorageClient`/MinIO, `MachiningProperties` — справочники).
- **Тесты** `machining`.

### Обновление 2026-10-26 (machining: E2E-окружение + stub Geometry)

**Цель:** довести `machining` до состояния «можно запустить E2E». **Достигнуто (кроме самого прогона).**

**Окружение (по факту):**
- PostgreSQL 16 (`iktk-postgres`): `iktk_app / iktk_dev_password`, БД `iktk_machining` создана.
- RabbitMQ 3.13 (`iktk-rabbitmq`): user `iktk`, pass `iktk_dev_password`, **vhost `/iktk`** (не `/`!).
- Порты: `integration:8092` (up), `equipment:8094` (up), `norming:8095` (**занят**), `machining:8096` (свободен).

**Правки `application.yml`:**
- `server.port: 8096` (было 8095 — занят `NormingApplication`).
- `datasource.username: iktk_app`, `password: iktk_dev_password`.
- `spring.rabbitmq.username: iktk`, `password: iktk_dev_password`.
- **`spring.rabbitmq.virtual-host: /iktk`** — ключевое отличие от forging (там `/`).

**Правка `MachiningApplication.main`:** дефолт порта `8096`.

**Создано (1 файл):**
- `client/GeometryClientStub.java` (`@Component`): возвращает фиктивный `GeometryData` (Steel-45, complexityScore=3, holes=true).

**Правка `client/GeometryClient.java`:** убран `@FeignClient`, оставлен интерфейс (метод `fetchGeometry` без `@GetMapping`).

**Почему stub для Geometry (а не реальный Feign):**
- Реальный `integration:8092` возвращает `Map<String,Object>`, а `GeometryData` — без no-arg конструктора и `@JsonCreator`. Jackson не десериализует. Реальный контракт — задача C-real.

**Проверено:**
- `mvn -pl modules/machining -am compile` → **BUILD SUCCESS**.
- `mvn -pl modules/machining -am verify`  → **BUILD SUCCESS**.

**Что осталось:**
- **E2E-прогон** `machining`: `iktk.command.machining` → `PART_PROCESS_READY` → запись в `iktk_machining`.
- **C-real:** реальные заглушки (`GeometryClient`, `EquipmentClient`, `ToolingClient`, `ToolpathGenerator`, `GCodeValidator`, `StorageClient`, `MachiningProperties`).
- **Тесты** `machining`.

### Обновление 2026-10-26 (machining: E2E работает end-to-end)

**Цель:** machining из "компилируется, но не запускается" -> **работает end-to-end** (как forging/casting/norming). **Достигнуто.**

**Исправлено 3 бага (до E2E не доживали):**
- **flyway в machining/pom.xml отсутствовал.** Hibernate validate падал на missing table [cnc_program], миграция не применялась. Добавлены flyway-core + flyway-database-postgresql. Flyway Successfully applied 1 migration (V1__init.sql).
- **Двойной бин MachiningProperties** (@Component + @EnableConfigurationProperties). Spring падал с required a single bean, but 2 were found. Убран @Component из MachiningProperties.java - оставлен @EnableConfigurationProperties в MachiningApplication (идиоматичный паттерн).
- **CuttingMode.getSummary() - вычисляемый getter.** Jackson при сериализации в JSONB писал summary: "...", при десериализации падал UnrecognizedPropertyException: Unrecognized field "summary". Поставлен @JsonIgnore на getSummary().

**E2E-прогон (projectId=test-e2e-001, objectRef=geo-test-123):**
- iktk.command.machining -> MachiningCommandConsumer -> designProcess -> PART_PROCESS_READY.
- **БД iktk_machining:** machining_process.id=161d0f96-1818-477a-a95a-472b47210111, setups=2, operations=6, stages=6, passed=true.
- **JSONB operations[0]:** order=1, designation=005, method=MILLING, cnc=true, equipmentId=EQ-MILLING-CNC, tool=(END_MILL, 10mm, HSS, TiAlN), cuttingMode.spindleSpeedRpm=3819.72 (n=1000*V/(pi*D)=3819.7).
- **iktk.orchestrator.results.queue:** +1 (26->27->28).

**Правки (3 файла):**
- machining/pom.xml: flyway-core, flyway-database-postgresql.
- MachiningProperties.java: убран @Component.
- CuttingMode.java: @JsonIgnore на getSummary() + импорт.

**Проверено:**
- mvn -pl modules/machining -am compile -> BUILD SUCCESS.
- mvn -pl modules/machining -am verify -> BUILD SUCCESS.
- **machining запускается** (Started MachiningApplication, порт 8096, vhost /iktk).
- **E2E:** routed=true, consumer обработал, БД записана, результат отправлен.

**Грабли (в этой сессии):**
- **$pid - read-only в PowerShell.** $pid = $conn.OwningProcess молча проваливается (при ErrorActionPreference=Continue), $pid остаётся PID текущего PowerShell -> Stop-Process -Id $pid убивает **само окно**. Фикс: $targetPid, $procId, $portPid. **Не использовать $pid, $host, $home, $error, $matches как имена переменных.**
- **Ctrl+C в окне с mvn ... | Tee-Object** не останавливает Spring Boot (SIGINT идёт в Tee-Object). Запускать mvn spring-boot:run **без pipe** или закрывать окно целиком (X).
- **mvn compile = SUCCESS != все файлы созданы** (из сессии D). Всегда Test-Path + git status после создающего блока.
- **A.3 (5 файлов) не применялись в сессии A** - только A.3-fix. application.yml, V1__init.sql, RabbitTopologyConfig.java созданы в D-fix. Восстановлено, но впредь - явная проверка.
- **Java-код в PowerShell -> ParserError.** Копировать только блоки # ====, объяснения - отдельно.
- **[System.IO.File]::WriteAllLines с относительным путём** -> C:\WINDOWS\system32. Фикс: Join-Path $root ... с проверкой Test-Path $root\pom.xml.
- **Кракозябры в JSONB** (UTF-8 читается как cp1251 при передаче через docker exec psql). В БД сохранено правильно, но в PowerShell-выводе отображается неверно. Не влияет на работу. Для UI: chcp 65001 + [Console]::OutputEncoding.

**Что осталось:**
- **C-real:** реальные заглушки (GeometryClient, EquipmentClient, ToolingClient, ToolpathGenerator, GCodeValidator, StorageClient/MinIO, MachiningProperties - реальные справочники).
- **Тесты** machining (unit + integration, как в forging).
- **Кодировка консоли** - для корректного отображения кириллицы в JSONB.
- **iktk.orchestrator.results.queue** - 28 сообщений без consumerа (orchestrator остановлен). Разобрать/перезапустить orchestrator.
- **machining** - Feign-контракты EquipmentClient/ToolingClient - когда появятся реальные сервисы.

### Обновление 2026-10-26 (C.1: реальный StorageClient/MinIO, generate E2E)

**Цель:** StorageClient из заглушки -> реальный MinIO; расширить E2E на generate(). **Достигнуто.**

**Создано (1 файл):**
- postprocessor/Iso6983Postprocessor.java (@Component): getName()=ISO6983, header/footer/process. Реализация упрощённая (stub для toolpath), но формат G-кода корректный.

**Правки (5 файлов):**
- pom.xml: io.minio:minio:8.5.12.
- client/StorageClient.java: переписан на реальный MinIO (MinioClient, putObject, removeObject, ensureBucket в @PostConstruct). URI: s3://<bucket>/<projectId>/<category>/<name>.
- messaging/MachiningCommandConsumer.java: +CncProgramService; после designProcess, если requiresCncProgram=true, вызывает generate(); в results кладёт cncProgramId/cncProgramUri.
- model/OperationProgram.java: @JsonIgnore на getCuttingMode() (alias из A.6b-fix — та же грабля, что с CuttingMode.getSummary()).
- application.yml: креды MinIO iktk-minio/iktk_dev_password.

**E2E-прогон (projectId=test-e2e-001, objectRef=geo-test-123):**
- machining_process: id=dc3e2b7c..., ops=6.
- cnc_program: id=3404ad92..., program_uri=s3://iktk/test-e2e-001/cnc/O_dc3e2b7c..._CNC-DEFAULT_...nc, line_count=20, byte_size=705.
- JSONB cnc_program: ops=5 (все с tool, CONTROL без инструмента не попал), valid=true.
- results: +1 (29->30).
- Файл .nc физически в MinIO bucket iktk (проверено через uploadText OK в логе).

**Проверено:**
- mvn -pl modules/machining -am compile -> BUILD SUCCESS.
- mvn -pl modules/machining -am verify -> BUILD SUCCESS.
- E2E: routed=true, consumer -> designProcess -> generate -> MinIO -> БД, success=true.

**Грабли (в этой сессии):**
- **@JsonIgnore на все вычисляемые getter'ы.** Вторая грабля того же типа: OperationProgram.getCuttingMode() (alias) -> UnrecognizedPropertyException при десериализации CncProgram JSONB. Фикс: @JsonIgnore. **Урок: при добавлении alias-getter'а — сразу @JsonIgnore.**
- **Регэксп ^(import ) не находит ничего, если импортов нет.** В OperationProgram (все поля — один пакет) импортов не было, regex не сработал, import JsonIgnore не добавился, compile упал бы. Урок: проверять наличие import после правки через Get-Content -Head 10.
- **Косметика OperationProgram:** @JsonIgnore стоит, но import и Javadoc на одной строке, class с отступом 4 — работает, но некрасиво. Убрать при C.2.

**Что осталось:**
- **C.2-C.5:** GeometryClient (@JsonCreator в GeometryData + контракт integration), ToolpathGenerator (реальная генерация), GCodeValidator (синтаксис/границы), MachiningProperties (справочники).
- **Косметика OperationProgram** (отступы, Javadoc на одной строке с import).
- **Тесты** machining.
- **iktk.orchestrator.results.queue** — 30 сообщений без consumer'а.

### Обновление 2026-10-26 (C.2: GeometryClient + @JsonCreator + Router fallback)

**Цель:** GeometryClient из заглушки -> реальный Feign + fallback на stub; GeometryData десериализуется из Map. **Достигнуто.**

**Создано (1 файл):**
- client/GeometryClientRouter.java (@Component): try Feign, fallback stub. Инъектится в MachiningProcessService.

**Правки (4 файла):**
- dto/GeometryData.java: @JsonCreator + @JsonProperty (15 арг), @JsonIgnoreProperties(ignoreUnknown=true), дефолты для 8 полей, отсутствующих в integration.Geometry.
- client/GeometryClient.java: @FeignClient(name="integration", url="${iktk.machining.integration.url}"), GET /api/v1/geometry/{projectId}.
- client/GeometryClientStub.java: убран implements GeometryClient (Ambiguous bean).
- service/MachiningProcessService.java: inject GeometryClientRouter.

**E2E-прогон (projectId=test-e2e-001, objectRef=geo-test-123, integration:8092 НЕ запущен):**
- GeometryClientRouter: integration failed -> fallback to stub (в логе, до 15:52:12).
- machining_process: id=6080b50e..., ops=6, passed=true.
- cnc_program: id=d55eb660..., program_uri=s3://iktk/test-e2e-001/cnc/O_6080b50e..._CNC-DEFAULT_1793015532387.nc, line_count=20, byte_size=705.
- StorageClient.uploadText OK: bytes=705 -> файл в MinIO.
- results: +1 (30->31). success=true, cncProgramId=d55eb660-....

**Проверено:**
- mvn -pl modules/machining -am compile -> BUILD SUCCESS.
- mvn -pl modules/machining -am verify -> BUILD SUCCESS.
- E2E: routed=true, fallback сработал, success=true.

**Наблюдения из лога (не блокеры, но зафиксировать):**
- WARN "постпроцессор для модели CNC-DEFAULT не найден" — EquipmentClientStub возвращает model=CNC-DEFAULT, PostprocessorRegistry.find не находит, уходит в findByName("ISO6983"). Работает, но WARN каждый раз. Починить: либо EquipmentClientStub -> model=ISO6983, либо PostprocessorRegistry матчит по реальным моделям. Отложено.
- WARN "пустая траектория для операции X" x5 — ToolpathGenerator STUB. Норма для C.2. Реальная генерация -> C.3.

**Грабли (в этой сессии):**
- **integration:8092 нужно поднять для проверки реального Feign.** Без него Router всегда уходит в stub, E2E-цифры не доказывают реальный Feign. C.2.1b (опционально) — поднять integration, вставить geo-12345 в iktk_geometry, E2E-retry. Ожидаемо: GeometryClientRouter -> integration OK (вместо fallback), 7 полей реальные, 8 — дефолты.
- **GeometryClientStub без implements** — иначе Ambiguous bean с Feign-прокси. Запомнить для будущих Router'ов.
- **Дефолты для отсутствующих полей** в @JsonCreator — временное решение. Когда integration расширит контракт, убрать дефолты (пусть null сигнализирует о пропуске).

**Что осталось:**
- **C.2.1b (опционально):** поднять integration:8092, geo-12345 (уже в iktk_geometry), E2E с реальным Feign.
- **C.3-C.5:** ToolpathGenerator (реальная генерация), GCodeValidator (синтаксис/границы), MachiningProperties (справочники).
- **Косметика:** OperationProgram (отступы), Javadoc {@link GeometryClient} в MachiningProcessService (ссылается на интерфейс, поле — Router).
- **Тесты** machining.
- **iktk.orchestrator.results.queue** — 31 сообщение без consumer'а.

### Обновление 2026-10-26 (C.3: ToolpathGenerator — реальная генерация)

**Цель:** ToolpathGenerator из заглушки -> реальные Toolpath через стратегии. **Достигнуто.**

**Создано (4 файла):**
- toolpath/ToolpathStrategy.java (interface): supports(MachiningMethod), generate(op, equipment, properties)
- toolpath/MillingStrategy.java (@Component): зигзаг по заготовке, шаг 0.7*диаметр, G00 подвод, G01 зигзаг
- toolpath/DrillingStrategy.java (@Component): сетка 2x2 отверстий (1/3, 2/3 от L/W), G00-G01-G00 на отверстие
- toolpath/FallbackStrategy.java (без @Component): пустой Toolpath для CONTROL/GRINDING/EDM/LASER

**Правки (3 файла):**
- toolpath/ToolpathGenerator.java: router — выбирает первую подходящую стратегию, fallback при отсутствии
- config/MachiningProperties.java: +blankLength/blankWidth/blankHeight (100x100x20) + геттеры/сеттеры
- postprocessor/Iso6983Postprocessor.java: RAPID->G00 (без F), FEED->G01 (с F), убрано "toolpath empty (stub)"

**E2E-прогон (projectId=test-e2e-001, objectRef=geo-test-123, integration не запущен, Router -> stub):**
- ToolpathGenerator: зарегистрировано 2 стратегий (Milling + Drilling), FallbackStrategy отдельно
- MILLING: G00 X0 Y0 Z0 -> G01 X0 Y0 Z-2 F0.2 -> зигзаг X0..X100, Y0..Y98 шаг 7
- DRILLING: 4 отверстия (33.33/66.67 по X/Y), G00-G01-G00 на каждое
- GRINDING: FallbackStrategy, пустая траектория (ветка "траектория не сгенерирована")
- cnc_program: line_count=135 (было 20), byte_size=3688 (было 705)
- results: +1 (31->32), success=true

**Проверено:**
- mvn -pl modules/machining -am compile -> BUILD SUCCESS
- mvn -pl modules/machining -am verify -> BUILD SUCCESS
- E2E: toolpath реальный, RAPID/FEED корректно, координаты правильные

**Наблюдения (не блокеры):**
- **Дубль G01 X0 Y0 Z-2 F0.2 в начале MILLING** — подвод + первая точка зигзага (y=0). Косметика, не влияет на корректность. Убрать в C.3.10: в MillingStrategy не добавлять подвод отдельно, если первая точка зигзага совпадает.
- **`; tool=END_MILL` для DRILLING** — ToolingClientStub.selectTool всегда возвращает END_MILL. Исправится, когда ToolingClient станет реальным (C.5 или отдельно).

**Грабли (в этой сессии):**
- **PowerShell: `Format-Table` съедает предыдущие строки** — в выводе C.3.1-C.3.6 пропали блоки "MachiningProperties: blank" и "Iso6983: RAPID", хотя `git status` показал правки. Урок: после блока с `Format-Table` проверять статус через `git status`, а не только по промежуточным Write-Host.
- **ToolpathStrategy без Spring-контекста в FallbackStrategy** — если бы FallbackStrategy был @Component, был бы Ambiguous bean с Milling/Drilling (все поддерживают какие-то методы). FallbackStrategy создаётся вручную в ToolpathGenerator.

**Что осталось:**
- **C.3.10 (косметика):** убрать дубль G01 в начале MILLING, добавить DrillingStrategy tool type.
- **C.4:** GCodeValidator — реальная валидация (синтаксис, границы X/Y/Z, столкновения).
- **C.5:** MachiningProperties — реальные справочники (скорости, подачи, стойкости).
- **C.2.1b (опционально):** поднять integration:8092, E2E с реальным Feign.
- **Косметика OperationProgram** (отступы), Javadoc {@link GeometryClient}.
- **Тесты** machining.
- **iktk.orchestrator.results.queue** — 32 сообщения без consumer'а.

### Обновление 2026-10-26 (C.4: GCodeValidator — реальная валидация)

**Цель:** GCodeValidator из заглушки -> оркестратор 4 правил + 6 тестов. **Достигнуто.**

**Создано (6 файлов):**
- validator/GCodeRule.java (interface): check(gcode, equipment, props, fullProgram, errors, warnings)
- validator/SyntaxRule.java (@Component): regex на каждую строку ISO 6983
- validator/BoundsRule.java (@Component): X/Y/Z в пределах заготовки + marginXY=5, marginZ=5
- validator/StructureRule.java (@Component): для fullProgram — % в начале, M30 в конце; fragment — skip
- validator/FeedRule.java (@Component): G01 без F — warning, G00 с F — warning
- src/test/java/.../GCodeValidatorTest.java: 6 тестов (validFullProgram, missingM30, outOfBoundsX, syntaxError, feedWarning, validFragmentWithoutHeaderFooter)

**Правки (1 файл):**
- validator/GCodeValidator.java: оркестратор, List<GCodeRule>, run(fullProgram) с try/catch на каждое правило

**Правила (детали):**
- Syntax: ^\s*(;.*|%|O\d+.*|M\d+.*|G\d+(\s+[XYZF]-?\d+(\.\d+)?)*)?\s*$
- Bounds: X∈[-5,L+5], Y∈[-5,W+5], Z∈[-H,5]; marginXY=max(5, 5%), marginZ=5
- Structure: fullProgram — первая значимая строка = %, последняя = M30
- Feed: G01 без F / G00 с F — warning (не error)

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 6, Failures: 0, Errors: 0
- E2E: cnc_program (135 строк, 3688 байт) -> validation.valid=true, errors=[], warnings=[]

**Грабли:**
- **Правила в одном пакете с GCodeValidator** — меньше импортов, Spring сканирует подпакеты.
- **try/catch на каждое правило** — если одно падает, остальные продолжают; ошибка правила = error, не падение валидатора.
- **Штатный G-код должен проходить без warning'ов** — это критерий «правила не сломали E2E». Негативные тесты — только в JUnit, не через прод.

**Что осталось:**
- **C.5:** MachiningProperties — реальные справочники (V, S, t, T по материалам/инструментам).
- **C.3.10:** косметика (дубль G01 в MillingStrategy, DrillingStrategy tool type).
- **C.2.1b (опционально):** поднять integration:8092, E2E с реальным Feign.
- **Косметика OperationProgram** (отступы), Javadoc {@link GeometryClient}.
- **Тесты machining:** только validator (стратегии, сервисы — без тестов).
- **iktk.orchestrator.results.queue** — 33 сообщения без consumer'а.

### Обновление 2026-10-26 (C.5: MachiningProperties — реальные справочники V/S/t/T)

**Цель:** заглушки getCuttingSpeed/getFeed/getToolLife (120/0.2/60) -> lookup по (material, method). **Достигнуто.**

**Создано (1 файл):**
- config/CuttingData.java (record): cuttingSpeedMPerMin, feedMmPerRev, depthOfCutMm, toolLifeMin

**Правки (2 файла):**
- config/MachiningProperties.java: +cuttingData (Map<material, Map<method, CuttingData>>) + lookup + DEFAULT_CUTTING_DATA + TYPICAL_DEPTH. getCuttingSpeed/Feed — lookup по (material, method), getToolLife — lookup по (material, method), getDepthOfCut — min(TYPICAL_DEPTH, allowance).
- service/MachiningProcessService.java: getToolLife(method, geometry.getMaterial(), tool) — material теперь передаётся.

**Справочник (5 материалов x 5 методов, V/S/t/T):**
- steel-45, steel 20kh, alsi10mg, al-6061, stainless-304
- MILLING/TURNING/DRILLING/BORING/GRINDING с разными V/S/t/T

**Lookup-логика:** normalize (lowercase, trim) -> exact match -> substring match (в обе стороны) -> DEFAULT_CUTTING_DATA.

**getDepthOfCut:** min(TYPICAL_DEPTH[method], allowance) — не больше припуска. Физически корректно (было max, что могло снять больше припуска).

**Тесты (src/test/.../config/MachiningPropertiesTest.java, 8 тестов):**
- steel45_milling, alSi10Mg_drilling, steel20Kh_substringMatch, unknownMaterial_fallbackToDefault, depthOfCut_limitedByAllowance, cuttingData_override, stainless304_grinding, cuttingTool_doesNotAffectLookup

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 14 (6 GCodeValidatorTest + 8 MachiningPropertiesTest), Failures: 0
- E2E (projectId=test-e2e-001, Steel-45, geo-test-123):
  * MILLING: V=120/S=0.2/t=2/T=60 (не изменился — Steel-45 MILLING = дефолт)
  * DRILLING: V=25/S=0.15/t=1/T=45 (изменились с 120/0.2/2/60)
  * GRINDING: V=30/S=0.05/t=0.05/T=30 (изменились с 120/0.2/2/60)
  * cnc_program: line_count=135, byte_size=3692 (было 3688 — Z-координаты короче из-за t=0.05), valid=true
  * results: +1 (33->34)

**Грабли:**
- getDepthOfCut не принимает material — TYPICAL_DEPTH по методу, не по паре. Для точной глубины — отдельный метод с material (C.5.1b, опционально).
- Map<MachiningMethod, CuttingData> в YAML — enum-ключи Spring парсит, но требует @ConfigurationProperties + setter. Не тестировали — отложено.
- Нормализация материала: пробелы/дефисы/регистр. "Steel 20Kh (ГОСТ)" матчится с "steel 20kh" по substring.
- getToolLife изначально был без material — тест alSi10Mg_drilling это поймал (DRILLING DEFAULT = 45, а AlSi10Mg DRILLING = 60). Сигнатура исправлена на (method, material, tool) в C.5.5-fix. Урок: если параметр физически влияет — передавать его сразу, не упрощать.

**Что осталось:**
- **C.5.1b (опционально):** YAML-override cuttingData (сейчас только в коде).
- **C.3.10:** косметика (дубль G01, tool type DRILLING).
- **C.2.1b:** реальный integration:8092.
- **Тесты machining:** стратегии (MillingStrategy, DrillingStrategy), CncProgramService.
- **Косметика OperationProgram** (отступы), Javadoc {@link GeometryClient}.
- **iktk.orchestrator.results.queue** — 34 сообщения без consumer'а.

### Обновление 2026-10-27 (C.3.10: косметика — дубль G01, tool type, отступы, Javadoc)

**Цель:** закрыть 4 косметических пункта из «что осталось» C.3/C.2/C.5. **Достигнуто.**

**Правки (4 файла):**
- toolpath/MillingStrategy.java: убран дубль G01 (подвод + первая точка зигзага при y=0 совпадали). Теперь один RAPID-подвод, врезание даёт первая FEED-точка.
- client/ToolingClientStub.java: type по методу — MILLING/BORING→END_MILL ⌀10 HSS TiAlN, DRILLING/REAMING→DRILL ⌀5 HSS TiN, TURNING→TURNING_TOOL Carbide, GRINDING→GRINDING_WHEEL ⌀200 Al2O3, EDM/LASER→EDM_WIRE ⌀0.3, CONTROL→null.
- model/OperationProgram.java: переписан с чистыми отступами (import отдельно от Javadoc, class с отступом 0, поля/методы с 4). Логика не менялась.
- service/MachiningProcessService.java: Javadoc {@link GeometryClient} → {@link GeometryClientRouter}.

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS
- E2E (projectId=test-e2e-001, Steel-45):
  * line_count: 135 -> 132 (-3 = 3 MILLING × 1 дубль G01)
  * byte_size: 3692 -> 3605 (-87)
  * valid=true (GCodeValidator не сломался)
  * tool1=END_MILL (MILLING), tool2=DRILL (DRILLING), tool5=GRINDING_WHEEL (GRINDING)
  * G-код DRILLING: "; tool=DRILL" (было "; tool=END_MILL")
  * results: 34 -> 35

**Грабли:**
- Дубль G01 был «невидим» в C.3, потому что точки подвода и первой точки зигзага при y=0 совпадали (обе X=0 Y=0 Z=-depth). Валидатор пропускал (обе строки валидны), E2E проходил — но G-код содержал лишнюю строку.
- ToolingClientStub с END_MILL для DRILLING давал некорректный комментарий в G-коде. Не влияло на координаты, но визуально сбивало.

**Что осталось:**
- **C.2.1b:** поднять integration:8092, вставить данные в iktk_geometry, E2E с реальным Feign (не fallback).
- **Тесты machining:** стратегии (MillingStrategy, DrillingStrategy), CncProgramService.
- **C.5.1b (опционально):** YAML-override cuttingData.
- **iktk.orchestrator.results.queue** — 35 сообщений без consumer'а.

### Обновление 2026-10-27 (C.2.1b: реальный integration через Feign, Router OK)

**Цель:** GeometryClientRouter из fallback stub -> реальный Feign к integration:8092. **Достигнуто.**

**Окружение:**
- integration:8092 поднят (health=UP).
- GET /api/v1/geometry/{projectId}?objectRef= — работает (для test-e2e-001 / geo-12345).

**E2E (projectId=test-e2e-001, objectRef=geo-12345 — реальный integration):**
- GeometryClientRouter: integration OK (вместо fallback to stub) — ГЛАВНОЕ.
- material = Steel 20Kh (было Steel-45 из stub) -> lookup сработал.
- complexityScore = 2 (было 3) -> setups 2->1.
- holes = false (было true) -> DRILLING исчез, ops 6->5.
- MILLING V = 100 (Steel 20Kh), T=60.
- GRINDING V = 25 (Steel 20Kh), T=30.
- machining_process: setups=1, ops=5, stages=5, passed=true.
- results: 35 -> 36.
- cnc=0, requiresCnc=false, cncProgramId=null.

**By design:** cnc_program пусто. Причина: planOperations ставит cnc = isCncCapable(method) && complexityScore > 2. У geo-12345 complexityScore=2 (не > 2) -> cnc=false для всех операций -> requiresCncProgram=false -> generate не вызывается. Это разумная эвристика: простая деталь -> универсальный станок, сложная -> ЧПУ. Порог > 2 vs >= 2 — отдельная задача (C.2.2, если понадобится).

**Без изменений в коде** — только E2E-верификация. integration:8092 можно остановить (Router при отсутствии integration уйдёт в stub — by design).

**Что осталось:**
- **C.2.2 (опционально):** порог complexityScore > 2 vs >= 2 в planOperations.
- **Тесты machining:** стратегии (MillingStrategy, DrillingStrategy), CncProgramService.
- **C.5.1b (опционально):** YAML-override cuttingData.
- **iktk.orchestrator.results.queue** — 36 сообщений без consumer'а.

### Обновление 2026-10-27 (T: тесты стратегий ToolpathGenerator)

**Цель:** покрыть тестами MillingStrategy, DrillingStrategy, ToolpathGenerator. **Достигнуто.**

**Создано (3 файла):**
- src/test/.../toolpath/MillingStrategyTest.java (5 тестов): defaultBlank_zigzag_32points, customBlank_50x50_18points, nullTool_usesDefaultDiameter10_step7, depthFromCuttingMode, noDuplicateFeedAtOrigin_afterC310
- src/test/.../toolpath/DrillingStrategyTest.java (3 теста): fourHoles_12points, holePattern_rapidFeedRapid, depthFromMode
- src/test/.../toolpath/ToolpathGeneratorTest.java (4 теста): milling_routesToMillingStrategy, drilling_routesToDrillingStrategy, grinding_fallsBack_emptyToolpath, nullOperation_emptyToolpath

**Что покрыто:**
- MillingStrategy: L=W=100 -> 32 точки (1 RAPID + 30 FEED + 1 RAPID), L=W=50 -> 18 точек, step=0.7*диаметр (10 -> 7), X в {0,L}, все FEED на Z=-depth, RAPID подвод/отвод на Z=0, нет дубля FEED(0,0,-depth) после C.3.10, tool=null -> default ⌀10.
- DrillingStrategy: 4 отверстия x 3 точки = 12, координаты (33.33, 66.67) x (33.33, 66.67) (L/3, 2L/3, W/3, 2W/3), структура RAPID-FEED-RAPID на отверстие, Z=0 у RAPID, Z=-depth у FEED.
- ToolpathGenerator: MILLING -> MillingStrategy (не пусто), DRILLING -> DrillingStrategy (12 точек), GRINDING -> FallbackStrategy (пусто), op=null -> пустой Toolpath.

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 26 (8 properties + 5 milling + 3 drilling + 4 generator + 6 validator), Failures: 0

**Грабли:**
- BigDecimal.equals vs compareTo: BigDecimal.valueOf(100).stripTrailingZeros() = 1E+2, BigDecimal.ZERO.stripTrailingZeros() = 0, оба корректны для containsExactlyInAnyOrder (использован stripTrailingZeros для нормализации).
- В MillingStrategyTest noDuplicateFeedAtOrigin проверяет C.3.10-fix: было 2 FEED(0,0,-depth), стало 1.

**Что осталось:**
- **C.2.2 (опционально):** порог complexityScore > 2 vs >= 2 в planOperations.
- **C.5.1b (опционально):** YAML-override cuttingData (сейчас только в коде).
- **Тесты CncProgramService:** сервис generate (postprocessor, StorageClient, валидатор) — не покрыт. Отдельная сессия.
- **iktk.orchestrator.results.queue** — 36 сообщений без consumer'а.
- **machining**: тесты есть, E2E работает, MinIO, справочники, Router (Feign + fallback). Модуль готов к эксплуатации.


### Обновление 2026-10-27 (B: тесты CncProgramService, 9 тестов)

**Цель:** покрыть CncProgramService.generate() — последний сервис без тестов. **Достигнуто.**

**Создано (1 файл):**
- src/test/.../service/CncProgramServiceTest.java (9 тестов): generate_happyPath, generate_emptyOperations_throws, generate_noCncOperations_throws, generate_equipmentNull_throws, generate_nullRequest_throws, generate_missingProjectId_throws, regenerate_notFound_throws, delete_callsStorageAndRepo, delete_storageFailure_doesNotThrow

**Стратегия:** @ExtendWith(MockitoExtension.class), @Mock на CncProgramRepository, EquipmentClient, StorageClient, PostprocessorRegistry, ToolpathGenerator, GCodeValidator; реальные MachiningProperties (new) и Iso6983Postprocessor (new). ArgumentCaptor на repository.save.

**Покрыто:**
- generate: happy path (2 ops, uri=s3://..., model=CNC-DEFAULT, postprocessor=ISO6983, validation.valid=true), пустой список, все op.cnc=false, equipment=null, null request, missing projectId.
- regenerate: not found -> OrchestrationException.
- delete: storage.delete + repository.delete; storage падает -> repository.delete всё равно.

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 35 (26 + 9), Failures: 0

**Грабли:**
- \$ErrorActionPreference = "Stop" + git add + CRLF-warning -> падение. Для git-команд: Continue + проверка \$LASTEXITCODE.
- Перед создающим коммитом — проверка "нет ли уже раздела в PROGRESS.md" (Select-String) и git log -1. Иначе дубликаты.
- Mockito strict stubs: в generate_nullRequest_throws stubHappyPath() не вызывается, иначе UnnecessaryStubbingException.

**Что осталось:**
- **C.2.2 (опционально):** порог complexityScore > 2 vs >= 2.
- **C.5.1b (опционально):** YAML-override cuttingData.
- **iktk.orchestrator.results.queue** — 37 сообщений без consumer'а.
- **machining**: 35 тестов, E2E, MinIO, Router, справочники, валидатор, стратегии. Модуль готов.

### Обновление 2026-10-27 (E: orchestrator на орбите, purge, DLQ-политика)

**Цель:** разобрать iktk.orchestrator.results.queue, поднять orchestrator, настроить DLQ. **Достигнуто.**

**Окружение:**
- orchestrator:8080 (health=UP, PID 20004), таблицы project_state / decision_log / flyway_schema_history в iktk_orchestrator.
- 36 сообщений в orchestrator.results.queue — старые: OUTSOURCING_SELECTED (22.10, p-int-1), forging (a097907a-...), machining (test-e2e-001). Все без project_state -> OrchestrationException: Проект не найден -> requeue -> бесконечный цикл.

**Действия:**
- orchestrator остановлен.
- Purge очереди: HTTP DELETE /contents (async, не сразу) + rabbitmqctl purge_queue (контрольный). Итог: messages=0.
- application.yml: +spring.rabbitmq.listener.simple.default-requeue-rejected: false -> ошибки идут в DLQ, не requeue.
- orchestrator рестарт (PID 20004, health=UP).
- Проверка стабильности: orchestrator.results.queue=0, DLQ=0, machining.queue=0, consumers=1 (стабильно).

**Правки (1 файл):**
- orchestrator/src/main/resources/application.yml: +listener.simple.default-requeue-rejected: false

**Грабли:**
- ConditionalRejectingErrorHandler + defaultRequeueRejected=true (default) -> ошибка -> requeue -> бесконечный цикл. Симптом: unack=N, DLQ=0.
- HTTP DELETE /api/queues/.../contents асинхронен: возвращает 200, фактическое удаление позже. Start-Sleep 2 сек недостаточно. Фикс: poll-loop (проверять messages каждые 2 сек) или rabbitmqctl purge_queue после остановки consumer.
- Purge нельзя делать при работающем consumer: 36 unack -> purge возвращает OK, но сообщения остаются unack. Сначала stop orchestrator, потом purge.
- project_runtime / decision_log_entry — моя ошибка в E.2 (ожидал такие имена). Реальные: project_state / decision_log.
- $ErrorActionPreference="Stop" + git add + CRLF-warning -> падение (2-й раз). Для git: Continue + $LASTEXITCODE.

**Что осталось:**
- **E.5 (опционально):** полный E2E startProject -> PART_PROCESS_READY -> state transition (POST /api/v1/orchestrator/..., см. OrchestratorController).
- **C.2.2 (опционально):** порог complexityScore > 2 vs >= 2.
- **C.5.1b (опционально):** YAML-override cuttingData.
- **machining:** 35 тестов, E2E, MinIO, Router, справочники, валидатор, стратегии. Готов.
- **orchestrator:** на орбите, очередь пуста, DLQ-политика настроена.

### Обновление 2026-10-27 (Auth.1: модуль auth запущен, login работает)

**Цель:** auth из «не запускается» -> **login 200 + wrong 401**. **Достигнуто.**

**Окружение:**
- auth:8081 (PID 1840). Таблицы users / roles / permissions / audit_events / role_permissions / user_roles / role_inherited_roles + core (projects, stages, technology_processes) — УЖЕ были в БД.
- JWT: jjwt, HS256, issuer=iktk, access TTL=PT15M, refresh TTL=P30D.
- Admin seed: username=admin, roles=[ADMIN].

**Создано (2 файла):**
- auth/src/main/resources/application.yml: порт 8081, datasource iktk_auth (iktk_app/iktk_dev_password), ddl-auto=validate, iktk.security.jwt.{secret,issuer,access-ttl,refresh-ttl}, iktk.auth.{max-failed-attempts,lock-duration-minutes}.
- auth/src/main/java/.../exception/GlobalExceptionHandler.java (@RestControllerAdvice): @ExceptionHandler(AuthException) -> ResponseEntity с httpStatus из исключения + body {timestamp, code, message, context}.

**Правки (0 файлов):**
- auth-logic не менялась. Только yml + handler.

**Что нашли:**
- AuthException extends RuntimeException, содержит httpStatus (401), code, context — но НЕ было @ControllerAdvice -> Spring отдавал 500 вместо 401.
- Admin password_hash был $2y$10$5KA1nXTU (не от admin123) -> сбросили на $2a$10$x10DsH/z (admin123) через временный println + SQL UPDATE.
- TokenService использует iktk.security.jwt.*, AuthProperties — iktk.auth.* (два разных префикса).

**Проверено (E2E):**
- POST /api/v1/auth/login {admin, admin123} -> 200: accessToken (sub=admin, roles=[ADMIN], exp=+900s), refreshToken (type=refresh, exp=+30d), expiresIn=900, userInfo.roles=[ADMIN].
- POST /api/v1/auth/login {admin, wrong} -> 401: {"code":"INVALID_CREDENTIALS","message":"Неверный логин или пароль","context":{}} — доказывает GlobalExceptionHandler.

**Грабли:**
- $ErrorActionPreference="Stop" + git add + CRLF-warning -> падение (3-й раз). Для git: Continue + $LASTEXITCODE.
- BCrypt $2y$-hash не матчится -> сброс через временный println encode("admin123") + SQL UPDATE.
- GlobalExceptionHandler отсутствовал -> AuthException с httpStatus не читался.
- AuthProperties (iktk.auth.*) и TokenService (iktk.security.jwt.*) — два префикса в yml.

**Что осталось:**
- Auth.2: refresh, introspect, me, change-password.
- Auth.3: тесты AuthService, TokenService, RbacService.
- Auth.4: тесты AbacService (471 строка).
- Auth.5: gateway + JWT-фильтр.
- permissions пусто (0), roles только ADMIN.

### Обновление 2026-10-27 (Auth.2: E2E refresh / introspect / me / change-password)

**Цель:** E2E-верификация 4 эндпоинтов auth. **Достигнуто.**

**E2E-прогон (окно A, все зелёные):**
- POST /api/v1/auth/login {admin, admin123} -> 200 (access, refresh, userId).
- POST /api/v1/auth/refresh {refreshToken} -> 200: **ротация** (новая пара access + refresh), expiresIn=900, username=admin.
- POST /api/v1/auth/introspect {token: valid} -> 200: {"active":true,"username":"admin","roles":["ADMIN"],"issuer":"iktk","expiresAt":...}.
- POST /api/v1/auth/introspect {token: invalid} -> 200: {"active":false,"requestId":"..."}.
- GET /api/v1/auth/me (X-User-Id + X-User-Roles) -> 200: userId, username, displayName, roles=[ADMIN].
- GET /api/v1/auth/me без X-User-Id -> 401.
- POST /api/v1/auth/change-password (X-User-Id, {oldPassword:admin123,newPassword:admin456}) -> 200: {"message":"Пароль изменён","status":"OK"}.
- login admin/admin456 -> 200 (смена работает).
- POST /api/v1/auth/change-password (admin456->admin123) -> 200 (возврат).
- login admin/admin123 -> 200 (восстановлено).
- audit_events: 6 записей за прогон — LOGIN x3, REFRESH_TOKEN x1, CHANGE_PASSWORD x2, все result=SUCCESS.

**Правки:** 0 файлов. Только E2E-верификация.

**Что нашли:**
- JWT-ротация работает: refresh выдаёт НОВУЮ пару токенов (access + refresh).
- introspect принимает и валидный, и невалидный токен (не падает, возвращает active=false).
- me/changePassword — доверяют X-User-Id / X-User-Roles (от gateway). Без заголовка -> 401/ошибка.
- audit_events заполняется на LOGIN, REFRESH_TOKEN, CHANGE_PASSWORD (result=SUCCESS).
- displayName в консоли — кракозябры (cp1251), в БД — UTF-8 ("Администратор").

**Что осталось:**
- Auth.3: тесты AuthService, TokenService, RbacService.
- Auth.4: тесты AbacService (471 строка, самая сложная логика).
- Auth.5: интеграция с gateway (JWT-фильтр).
- permissions пусто (0), roles только ADMIN (6 остальных: MANAGER, ENGINEER, NORMER, OPERATOR, VIEWER, INTEGRATION).
- Seed permissions + role_permissions — отдельная задача (для RBAC/ABAC).

### Обновление 2026-10-27 (Auth.3: тесты TokenService, AuthService, RbacService — 39 тестов)

**Цель:** покрыть тестами три сервиса auth. **Достигнуто.**

**Создано (3 файла, 39 тестов):**
- auth/src/test/java/.../service/TokenServiceTest.java (7): generateAccessToken (claims sub/iss/uid/type/roles/exp), generateRefreshToken (type=refresh), extractUsername, isValid (valid/garbage/empty/null), getAccessTtlSeconds, конструктор с коротким secret -> IllegalStateException, expired-токен -> invalid (TTL=1сек + sleep 1.5сек).
- auth/src/test/java/.../service/AuthServiceTest.java (15): login_happyPath, login_null/missing/notFound/wrongPassword/lockedAfterMax/disabled/locked, refresh_happyPath (ротация), refresh_null/accessInsteadOfRefresh, changePassword_weak/wrongOld/happy/notFound.
- auth/src/test/java/.../service/RbacServiceTest.java (17): hasRole_true/false, hasAnyRole, hasAllRoles, getUserRoles (known/unknown), getUserPermissions, hasPermission (true/false/blank), requirePermission (throws/ok), requireRole, roleInherits (same/direct/none/null).

**Стратегия тестов:**
- TokenServiceTest: реальный TokenService(secret, issuer, accessTtl, refreshTtl) — 4 аргумента, secret >= 32 символов.
- AuthServiceTest: @Mock UserRepository; реальные BCryptPasswordEncoder(cost=4), TokenService, AuthProperties (defaults). 15 тестов.
- RbacServiceTest: @Mock UserRepository, RoleRepository, PermissionRepository. 17 тестов. roleInherits через roleRepository.findByName + getInheritedRoles().
- Mockito strict stubs: @ExtendWith(MockitoExtension.class).

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 39 (7 + 15 + 17), Failures: 0, Errors: 0
- AuthServiceTest: 5.861s (BCrypt cost=4 + one expired-token sleep).

**Грабли:**
- **Mockito strict stubs (UnnecessaryStubbingException):** в `refresh_accessTokenInsteadOfRefresh_throwsWrongType` был `when(userRepository.findByUsername("admin"))`, но AuthService.refresh падает на type!=refresh ДО findByUsername. Фикс: убрать when. Урок: stub только для того, что точно вызовется.
- **BCrypt cost=4** — для тестов, чтобы 15 тестов с encode/matches не тормозили (production cost=10).
- **Thread.sleep(1500)** в TokenServiceTest.expiredToken — 1.5 сек, допустимо для одного теста.
- ReflectionTestUtils.setField — для User (поля private, но сеттеры есть — можно было через сеттеры, но reflection надёжнее для тех полей, что могут быть immutable).

**Что осталось:**
- Auth.4: тесты AbacService (471 строка, самая сложная логика — SubjectAttributes, ResourceAttributes, правила доступа).
- Auth.5: gateway + JWT-фильтр.
- permissions пусто (0), roles только ADMIN (6 остальных нет: MANAGER, ENGINEER, NORMER, OPERATOR, VIEWER, INTEGRATION).
- Seed permissions + role_permissions — для полноценного RBAC/ABAC.

### Обновление 2026-10-27 (Seed: 6 ролей + 15 permissions + 52 role_permissions)

**Цель:** заполнить RBAC-справочники (roles / permissions / role_permissions) для работы RbacService/AbacService. **Достигнуто.**

**Что сделано (SQL через stdin, идемпотентно):**
- +6 ролей: MANAGER, ENGINEER, NORMER, OPERATOR, VIEWER, INTEGRATION (ADMIN уже был).
- +15 permissions: user:read/write/delete, project:read/write/delete, process:read/design, norm:read/write, report:read, equipment:read/write, admin:all, integration:call.
- +52 role_permissions:
  * ADMIN -> 15 (все)
  * INTEGRATION -> 15 (все)
  * MANAGER -> 6 (project:*, user:read, report:read, equipment:read, process:read)
  * VIEWER -> 6 (все *:read)
  * ENGINEER -> 4 (process:read/design, project:read, report:read)
  * NORMER -> 4 (norm:read/write, process:read, report:read)
  * OPERATOR -> 2 (process:read, equipment:read)

**Создано (1 файл):**
- auth/src/main/resources/db/seed/seed-permissions-roles.sql — тот же SQL для воспроизведения (psql -f). Не подключён к Flyway (auth не использует Flyway: pom без flyway, ddl-auto=validate, схема создана вне Flyway).

**Проверено (SQL-аналог getUserPermissions):**
- roles=7, permissions=15, role_permissions=52.
- admin: 15 permissions (через user_roles -> role_permissions).
- RbacService.getUserPermissions("admin-id") теперь вернёт 15 (а не 0, как до Seed).

**Грабли:**
- auth БЕЗ Flyway -> seed применён вручную (psql через docker exec -i stdin). Сохранён как .sql для воспроизведения.
- ON CONFLICT DO NOTHING -> идемпотентно (повторный запуск не падает, INSERT 0 0).

**Что осталось:**
- Auth.4: тесты AbacService (471 строка, самая сложная логика — SubjectAttributes, ResourceAttributes, isAllowed, allowedActions).
- Auth.5: gateway + JWT-фильтр.
- User'ы с не-ADMIN ролями: только admin (ADMIN). Для E2E на MANAGER/ENGINEER — нужен seed пользователей (отдельная задача).

### Обновление 2026-10-27 (Auth.4: тесты AbacService — 24 теста)

**Цель:** покрыть AbacService (471 строка, ABAC-логика). **Достигнуто.**

**Создано (1 файл, 24 теста):**
- auth/src/test/java/.../service/AbacServiceTest.java (24):
  * isAllowed: nullSubject, nullAction, nullResource, otherEnterprise (noAdmin), adminBypasses, integrationBypasses, owner_READ, owner_WRITE, owner_WRITE_stageCompleted (short-circuit), otherUser_stageCompleted_WRITE, otherUser_stageCompleted_READ, otherUser_stageArchived_APPROVE.
  * allowedActions: owner (все 6), otherEnterprise (пусто).
  * require: allowed (no throw), notAllowed (AccessDeniedException).
  * explain: 11 ключей, nullSubject, rbacAllowed для ADMIN/VIEWER+WRITE/MANAGER+DELETE/ENGINEER+EXPORT/MANAGER+APPROVE.
  * buildSubject: unknownUser (пустые роли).

**Логика AbacService (по коду):**
- isAllowed = evaluate (ABAC), НЕ checkRbac. checkRbac доступен через explain(...).get("rbacAllowed").
- evaluate: evaluateEnterprise -> evaluateOwnership (short-circuit, если owner и action != DELETE/ADMIN) -> evaluateStage -> evaluateClassification -> evaluateDepartment.
- evaluateEnterprise: resource null или enterpriseCode null -> true; ADMIN/INTEGRATION -> true; иначе ==.
- evaluateOwnership: resource null/ownerId null -> false; userId != ownerId -> false; action == DELETE/ADMIN -> false; иначе true (short-circuit).
- evaluateStage: resource null/stage null -> true; action != WRITE/DELETE/APPROVE -> true; COMPLETED/ARCHIVED -> false.
- evaluateClassification: всегда true (закомментировано, по решению C в final_TZ.md).
- evaluateDepartment: всегда true.
- checkRbac: ADMIN -> все; READ: MANAGER/ENGINEER/NORMER/OPERATOR/VIEWER/INTEGRATION; WRITE: MANAGER/ENGINEER/NORMER/INTEGRATION; APPROVE: MANAGER; EXPORT: MANAGER/ENGINEER; DELETE: false (только ADMIN); ADMIN: false (только ADMIN).

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 63 (7 + 15 + 17 + 24), Failures: 0, Errors: 0

**Грабли:**
- isAllowed != checkRbac: ABAC-часть без RBAC. RBAC только через explain. Это by design (isAllowed используется в бизнес-логике после RBAC-проверки на gateway/контроллере).
- evaluateClassification и evaluateDepartment — заглушки (true). При изменении политики (например, запрет EXPORT для CONFIDENTIAL) — раскомментировать.

**Что осталось:**
- Auth.5: gateway + JWT-фильтр (gateway не настроен — yml, routes, фильтр).
- Seed users с не-ADMIN ролями (для E2E MANAGER/ENGINEER/VIEWER).
- UserRepository: findByUsernameOrEmail (для login) — уже покрыто.

### Обновление 2026-10-27 (F: тесты forging — 34 теста)

**Цель:** покрыть тестами модуль forging (0 тестов до этого). **Достигнуто.**

**Создано (6 файлов, 34 теста):**
- forging/src/test/.../model/ForgingMethodTest.java (4): values_5, valueOf, valueOf_unknown_throws, listOf.
- forging/src/test/.../model/ForgingTransitionTest.java (4): builder, noArgs, allArgs, transitionType_6Values.
- forging/src/test/.../model/ForgingToolingTest.java (3): builder, builderDefault, noArgsConstructor.
- forging/src/test/.../model/BilletCalculationTest.java (2): builder, noArgsConstructor.
- forging/src/test/.../service/ForgingProcessServiceTest.java (16): analyze (5: passed/issueFillet/issueWall/warningDraft/warnings), planTransitions (4: minimal4/complex5_drawing/holes_piercing/complex5AndHoles_6), calculateForces (2: perTransition/projectedArea), buildStages (2: hotDie_8/coldDie_7), designProcess (3: nullRequest/missingProjectId/geometryNull).
- forging/src/test/.../messaging/ForgingCommandConsumerTest.java (5): onCommand happyPath/invalidJson/serviceThrows/defaultObjectRef/payloadObjectRef.

**Логика forging (по коду):**
- designProcess: validate -> fetchGeometry -> analyze -> selectForgingMethod -> calculateBillet -> planTransitions -> calculateForces -> designTooling -> buildStages -> save.
- analyze: issues = minFilletRadius, minWallThickness; warnings = minDraftAngle, undercuts, thinRibs, heightToDiameterRatio; passed = issues.isEmpty.
- planTransitions: UPSETTING + (DRAWING если complexity>3) + (PIERCING если hasHoles) + BLOCKING + FINISHING + TRIMMING = 4..6.
- calculateForces: forceKn = yieldStrength * contactArea * coefficient / 1000.
- buildStages: BLANK_PREPARATION + (HEAT_TREATMENT если HOT_DIE) + perTransition (TRIMMING/PIERCING -> MACHINING, иначе HOT_FORGING) + COLD_FORGING + TECHNICAL_CONTROL.

**Проверено:**
- mvn compile -> BUILD SUCCESS
- mvn verify -> BUILD SUCCESS, Tests run: 34 (4+4+3+2+16+5), Failures: 0, Errors: 0

**Грабли:**
- Lombok @Getter @Setter БЕЗ @EqualsAndHashCode -> equals/hashCode = Object. Тесты equalsAndHashCode_lombok (3 шт) удалены — тестировали несуществующее.
- RabbitTemplate.convertAndSend(String, String, Object, MessagePostProcessor) — 4-й аргумент MessagePostProcessor (Spring AMQP), НЕ java.util.function.Consumer. Заменено any(Consumer.class) -> any(MessagePostProcessor.class) в ForgingCommandConsumerTest (3 места).
- ForgingProcessService nested-классы: GeometryData (16 полей, public ctor), ManufacturabilityReport (3 поля, public ctor), ForceCalculation (5 полей, private ctor + builder) — используются в JSONB @Entity ForgingProcess.

**Что осталось:**
- casting (0 тестов), norming (0 тестов) — аналогичная работа (1 сессия каждый).
- auth: Auth.5 gateway, seed users с не-ADMIN ролями.
- assembly-process-service: 5 тестов (есть), но 694 строки AssemblyProcessService — мало.
- integration: многоуровневая сборка.
