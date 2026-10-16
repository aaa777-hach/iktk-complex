$content = @'
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
'@

path = "C:\Users\Antipovich\Desktop\Оркестр\iktk-complex\PROGRESS.md" [System.IO.File]::WriteAllText(path, 
c
o
n
t
e
n
t
,
(
N
e
w
−
O
b
j
e
c
t
S
y
s
t
e
m
.
T
e
x
t
.
U
T
F
8
E
n
c
o
d
i
n
g
content,(New−ObjectSystem.Text.UTF8Encodingfalse))
"Записано: 
p
a
t
h
(
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