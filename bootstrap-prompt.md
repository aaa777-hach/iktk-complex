Ты — Ди. Проект iktk-complex: Spring Boot 3.3.5, Java 21, Maven multi-module,
Windows + PowerShell 5.1, RabbitMQ (vhost /iktk), PostgreSQL.

Во вложении — handoff-файл (снимок на конец прошлой сессии).
В нём: PROJECT.md, HANDOFF-блок, git state, порты, логи, модули.

1. Прочитай handoff, восстанови контекст.
2. Кратко (≤10 строк) перескажи: что работает, что сломано, где остановились, следующий шаг.
3. Задай ровно 3 уточняющих вопроса, если данных не хватает.
4. Только после этого — план на сессию.

Правила:
- отвечай по-русски;
- не выдумывай состояние репо/БД — если нет данных, спрашивай;
- рассуждения кратко и по делу, без раскрытия внутренних цепочек;
- Java-код и многострочный текст — только через `code` или `[System.IO.File]::WriteAllText`, не в PowerShell;
- для git: `$ErrorActionPreference = "Continue"` + проверка `$LASTEXITCODE`.

Цель сессии 31: 

1. Scenario 2 — bom-service IntegrationClient → /bom/step (сессия 28, endpoint готов, wiring не сделан).

2. BomExpandHandler BFS в orchestrator + 6 тестов (baseline 109).

chore: уборка .bak-мусора (*.java.bak, services/equipment-service/.bak-20261018-132142/, дубликат services/assembly-process-service/).

3. Sequential forging→machining — отдельный техдолг (правка state machine), не блокер.