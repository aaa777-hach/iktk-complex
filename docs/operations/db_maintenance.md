# DB Maintenance — регламент обслуживания БД ИКТК

**Проект:** Интегрированный конструкторско-технологический комплекс (ИКТК)  
**Кодовое имя:** `iktk-complex`  
**Версия ТЗ:** `final_TZ.md` (ред. 3)  
**Версия документа:** 1.0  
**Статус:** актуально  
**Ответственный:** Администратор БД, DevOps-инженер

---

## 1. Назначение

Документ описывает регламент обслуживания базы данных ИКТК (PostgreSQL 16). Используется для:

- обеспечения стабильной работы БД;
- поддержания производительности;
- своевременного выявления деградаций;
- управления ростом данных;
- предотвращения сбоев;
- планирования мощностей.

**Область действия:** PostgreSQL-кластер ИКТК, схемы `iktk`, `iktk_ref`, `iktk_audit`, связанные сервисы и интеграции.

---

## 2. Архитектура БД

### 2.1. Компоненты

| Компонент | Назначение |
|---|---|
| PostgreSQL 16 | Основная СУБД |
| Primary | Основной узел (read/write) |
| Replica (Релиз 2) | Реплика для чтения и отказоустойчивости |
| WAL-архив | Непрерывное архивирование для PITR |
| `postgres-exporter` | Метрики для Prometheus |
| Backup CronJob | Ежедневные дампы (см. `backup_policy.md`) |

### 2.2. Схемы

| Схема | Назначение |
|---|---|
| `iktk` | Бизнес-данные (проекты, ТП, этапы, BOM, оборудование, рекомендации, версии, решения) |
| `iktk_ref` | Справочники (предприятия, подразделения, материалы, единицы, операции) |
| `iktk_audit` | Аудит (`audit_log`, `change_log`) |

### 2.3. Ключевые таблицы

**Бизнес:**
- `projects`, `project_state`, `project_versions`;
- `geometry_models`;
- `technology_processes`, `stages`;
- `tooling`, `stage_tooling`, `cnc_programs`;
- `time_norms`, `material_norms`;
- `equipment`, `equipment_capabilities`, `equipment_status`, `equipment_tooling`;
- `assembly_units`, `bom_items`, `assembly_connections`;
- `assembly_processes`, `assembly_operations`, `assembly_tooling`;
- `equipment_requirements`, `equipment_gap_analysis`;
- `equipment_recommendations`, `vendor_catalog`, `outsourcing_options`;
- `entity_versions`, `rollback_points`, `rollback_history`, `entity_locks`;
- `decision_log`;
- `users`, `roles`, `permissions`, `user_roles`, `tokens`.

**Аудит:**
- `audit_log` (append-only);
- `change_log` (детальный аудит полей).

**Справочники:**
- `enterprises`, `departments`, `materials`, `units`, `operation_catalog`.

---

## 3. Роли и привилегии

### 3.1. Роли БД

| Роль | Назначение | Привилегии |
|---|---|---|
| `iktk_app` | Приложения ИКТК | DML (SELECT/INSERT/UPDATE/DELETE) |
| `iktk_read` | Аналитика, отчёты | SELECT |
| `iktk_migrate` | Миграции | DDL (CREATE/ALTER/DROP) |
| `postgres` | Суперпользователь | Все (только администратор) |

### 3.2. Правила

- Приложения используют только `iktk_app`.
- Миграции выполняются от `iktk_migrate` (в CI/CD).
- `postgres` — только для административных задач (бэкап, восстановление, диагностика).
- Прямой доступ к БД извне — только через bastion.
- Аудит подключений — `log_connections`, `log_disconnections`.

### 3.3. Проверка прав

```sql
-- Пользователи и их роли
SELECT usename, usesuper, usecreatedb, usebypassrls
FROM pg_user
ORDER BY usename;

-- Привилегии на таблицы
SELECT grantee, table_schema, table_name, privilege_type
FROM information_schema.role_table_grants
WHERE table_schema IN ('iktk', 'iktk_ref', 'iktk_audit')
ORDER BY grantee, table_schema, table_name;