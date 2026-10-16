
---

## 3. Справочники (`iktk_ref`)

### 3.1. `enterprises`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(64) | PK | Код предприятия |
| `name` | VARCHAR(255) | NOT NULL | Краткое наименование |
| `full_name` | VARCHAR(512) | | Полное наименование |
| `address` | VARCHAR(512) | | Адрес |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | Создание |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | Обновление |

**Связи:** 1 → N с `departments`, 1 → N с `projects`, 1 → N с `users`.

### 3.2. `departments`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(64) | PK | Код подразделения |
| `enterprise_code` | VARCHAR(64) | FK → enterprises | Предприятие |
| `name` | VARCHAR(255) | NOT NULL | Наименование |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Обновление |

### 3.3. `materials`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(64) | PK | Код материала |
| `name` | VARCHAR(255) | NOT NULL | Наименование |
| `grade` | VARCHAR(128) | | Марка |
| `standard` | VARCHAR(128) | | ГОСТ/ТУ |
| `density_kg_m3` | NUMERIC(12,3) | NOT NULL | Плотность, кг/м³ |
| `cost_per_kg` | NUMERIC(14,4) | | Стоимость за кг |
| `material_group` | VARCHAR(64) | | Группа |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

**Индексы:** `gin_trgm_ops` по `name` — нечёткий поиск.

### 3.4. `units`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(32) | PK | Код единицы |
| `name` | VARCHAR(128) | NOT NULL | Наименование |
| `short_name` | VARCHAR(32) | NOT NULL | Краткое обозначение |
| `dimension` | VARCHAR(32) | NOT NULL | Размерность |

### 3.5. `operation_catalog`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(64) | PK | Код операции |
| `name` | VARCHAR(255) | NOT NULL | Наименование |
| `operation_type` | `iktk.operation_type` | NOT NULL | Тип операции |
| `description` | TEXT | | Описание |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

---

## 4. Пользователи и роли (`iktk`)

### 4.1. `roles`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `name` | VARCHAR(32) | PK | Имя роли (`ADMIN`, `MANAGER`, ...) |
| `display_name` | VARCHAR(128) | NOT NULL | Отображаемое имя |
| `description` | VARCHAR(512) | | Описание |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

### 4.2. `permissions`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `code` | VARCHAR(64) | PK | Код разрешения |
| `description` | VARCHAR(512) | | Описание |
| `resource` | VARCHAR(64) | NOT NULL | Ресурс |
| `action` | VARCHAR(32) | NOT NULL | Действие |

### 4.3. `role_permissions`

| Поле | Тип | Ограничения |
|---|---|---|
| `role_name` | VARCHAR(32) | FK → roles, PK (составной) |
| `permission_code` | VARCHAR(64) | FK → permissions, PK (составной) |

### 4.4. `role_inheritance`

| Поле | Тип | Ограничения |
|---|---|---|
| `parent_role` | VARCHAR(32) | FK → roles, PK (составной) |
| `child_role` | VARCHAR(32) | FK → roles, PK (составной) |

CHECK: `parent_role <> child_role`.

### 4.5. `users`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `username` | VARCHAR(128) | NOT NULL, UNIQUE | Логин |
| `password_hash` | VARCHAR(255) | NOT NULL | Хэш пароля |
| `display_name` | VARCHAR(255) | | Отображаемое имя |
| `email` | VARCHAR(255) | UNIQUE | Email |
| `enterprise_code` | VARCHAR(64) | FK → enterprises | Предприятие |
| `department_code` | VARCHAR(64) | FK → departments | Подразделение |
| `enabled` | BOOLEAN | NOT NULL, DEFAULT TRUE | Активность |
| `locked` | BOOLEAN | NOT NULL, DEFAULT FALSE | Блокировка |
| `failed_attempts` | INT | NOT NULL, DEFAULT 0 | Неудачные попытки |
| `last_login_at` | TIMESTAMPTZ | | Последний вход |
| `password_changed_at` | TIMESTAMPTZ | | Смена пароля |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

### 4.6. `user_roles`

| Поле | Тип | Ограничения |
|---|---|---|
| `user_id` | UUID | FK → users, PK (составной) |
| `role_name` | VARCHAR(32) | FK → roles, PK (составной) |
| `granted_at` | TIMESTAMPTZ | NOT NULL |
| `granted_by` | UUID | FK → users |

### 4.7. `tokens`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `user_id` | UUID | FK → users | Пользователь |
| `token_hash` | VARCHAR(128) | NOT NULL | Хэш токена |
| `token_type` | VARCHAR(32) | NOT NULL | `ACCESS` / `REFRESH` |
| `issued_at` | TIMESTAMPTZ | NOT NULL | Выпуск |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Истечение |
| `revoked` | BOOLEAN | NOT NULL, DEFAULT FALSE | Отозван |
| `revoked_at` | TIMESTAMPTZ | | Время отзыва |
| `device_info` | VARCHAR(512) | | Информация об устройстве |

---

## 5. Проекты и ТП (`iktk`)

### 5.1. `projects`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `designation` | VARCHAR(128) | NOT NULL | Обозначение |
| `name` | VARCHAR(255) | NOT NULL | Наименование |
| `description` | VARCHAR(2000) | | Описание |
| `object_type` | `iktk.object_type` | NOT NULL | `PART` / `ASSEMBLY` |
| `status` | `iktk.project_status` | NOT NULL, DEFAULT 'DRAFT' | Статус |
| `enterprise_code` | VARCHAR(64) | FK → enterprises | Предприятие |
| `requested_by` | VARCHAR(128) | | Инициатор |
| `responsible_engineer` | VARCHAR(128) | | Ответственный |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

### 5.2. `project_versions`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `version` | VARCHAR(64) | NOT NULL | Метка версии |
| `state` | VARCHAR(64) | NOT NULL | Состояние |
| `snapshot` | JSONB | NOT NULL | Снапшот |
| `comment` | VARCHAR(1000) | | Комментарий |
| `created_by` | VARCHAR(128) | | Автор |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

UNIQUE: `(project_id, version)`.

### 5.3. `geometry_models`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `object_ref` | VARCHAR(128) | NOT NULL | Ссылка на объект |
| `version` | VARCHAR(64) | NOT NULL, DEFAULT 'v1' | Версия |
| `format` | VARCHAR(32) | NOT NULL | Исходный формат |
| `canonical_format` | VARCHAR(32) | | Канонический формат |
| `file_uri` | VARCHAR(1024) | NOT NULL | URI в MinIO/S3 |
| `file_hash` | VARCHAR(128) | | SHA-256 |
| `byte_size` | BIGINT | | Размер |
| `units` | VARCHAR(16) | NOT NULL, DEFAULT 'mm' | Единицы |
| `is_assembly` | BOOLEAN | NOT NULL, DEFAULT FALSE | Признак сборки |
| `bounding_box` | JSONB | | Габариты |
| `mass_properties` | JSONB | | Массовые свойства |
| `material_code` | VARCHAR(64) | FK → materials | Материал |
| `source_system` | VARCHAR(32) | | CAD-система |
| `source_external_id` | VARCHAR(255) | | Внешний ID |
| `created_by` | VARCHAR(128) | | Автор |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

UNIQUE: `(project_id, object_ref, version)`.

### 5.4. `technology_processes`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `designation` | VARCHAR(128) | NOT NULL | Обозначение |
| `name` | VARCHAR(255) | | Наименование |
| `version` | VARCHAR(64) | NOT NULL, DEFAULT 'v1' | Версия |
| `project_id` | UUID | FK → projects | Проект |
| `object_ref` | VARCHAR(128) | NOT NULL | Ссылка на объект |
| `object_type` | `iktk.object_type` | NOT NULL | Тип объекта |
| `process_type` | `iktk.process_type` | NOT NULL | Тип ТП |
| `status` | `iktk.process_status` | NOT NULL, DEFAULT 'DRAFT' | Статус |
| `production_type` | `iktk.production_type` | | Вид производства |
| `geometry_id` | UUID | FK → geometry_models | Геометрия |
| `parent_process_id` | UUID | FK → technology_processes | Родительский ТП |
| `author` | VARCHAR(128) | | Автор |
| `approved_by` | VARCHAR(128) | | Утвердил |
| `approved_at` | TIMESTAMPTZ | | Дата утверждения |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

UNIQUE: `(project_id, designation, version)`.

### 5.5. `stages`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `technology_process_id` | UUID | FK → technology_processes | ТП |
| `designation` | VARCHAR(64) | NOT NULL | Обозначение |
| `name` | VARCHAR(255) | NOT NULL | Наименование |
| `description` | VARCHAR(2000) | | Описание |
| `stage_order` | INT | NOT NULL | Порядок |
| `operation_type` | `iktk.operation_type` | NOT NULL | Тип операции |
| `stage_kind` | `iktk.stage_kind` | NOT NULL, DEFAULT 'MAIN' | Вид этапа |
| `work_grade` | INT | | Разряд |
| `equipment_id` | VARCHAR(64) | | Оборудование |
| `tooling_id` | VARCHAR(64) | | Оснастка |
| `cnc_program_id` | VARCHAR(64) | | Программа ЧПУ |
| `time_norm_min` | NUMERIC(12,3) | | Тшт |
| `setup_time_min` | NUMERIC(12,3) | | Тпз |
| `tariff_code` | VARCHAR(64) | | Тариф |
| `required_accuracy` | VARCHAR(32) | | Точность |
| `max_dimensions` | VARCHAR(64) | | Габариты |
| `required_power_kw` | NUMERIC(10,3) | | Мощность |
| `material` | VARCHAR(128) | | Материал |
| `comment` | VARCHAR(1000) | | Комментарий |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

UNIQUE: `(technology_process_id, stage_order)`.

### 5.6. `stage_equipment_requirements`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `stage_id` | UUID | FK → stages | Этап |
| `requirement_type` | VARCHAR(32) | NOT NULL | Тип требования |
| `required_equipment_type` | VARCHAR(64) | | Тип оборудования |
| `required_accuracy` | VARCHAR(32) | | Точность |
| `required_power_kw` | NUMERIC(10,3) | | Мощность |
| `max_dimensions` | VARCHAR(64) | | Габариты |
| `required_materials` | JSONB | | Материалы |
| `required_operations` | JSONB | | Операции |
| `notes` | VARCHAR(1000) | | Примечания |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

### 5.7. `tooling`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `process_id` | UUID | FK → technology_processes | ТП |
| `stage_id` | UUID | FK → stages | Этап |
| `designation` | VARCHAR(128) | NOT NULL | Обозначение |
| `name` | VARCHAR(255) | | Наименование |
| `tooling_type` | VARCHAR(64) | NOT NULL | Тип оснастки |
| `material_code` | VARCHAR(64) | FK → materials | Материал |
| `file_uri` | VARCHAR(1024) | | Файл |
| `estimated_cost` | NUMERIC(14,2) | | Стоимость |
| `status` | VARCHAR(32) | NOT NULL, DEFAULT 'DRAFT' | Статус |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

### 5.8. `stage_tooling`

| Поле | Тип | Ограничения |
|---|---|---|
| `stage_id` | UUID | FK → stages, PK (составной) |
| `tooling_id` | UUID | FK → tooling, PK (составной) |
| `usage_note` | VARCHAR(512) | |

### 5.9. `cnc_programs`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `process_id` | UUID | FK → technology_processes | ТП |
| `equipment_id` | VARCHAR(64) | NOT NULL | Станок |
| `equipment_model` | VARCHAR(128) | | Модель |
| `postprocessor` | VARCHAR(64) | NOT NULL | Постпроцессор |
| `program_name` | VARCHAR(255) | NOT NULL | Имя УП |
| `program_uri` | VARCHAR(1024) | NOT NULL | URI файла |
| `line_count` | INT | | Строк |
| `byte_size` | BIGINT | | Размер |
| `valid` | BOOLEAN | NOT NULL, DEFAULT FALSE | Валидность |
| `validation_log` | JSONB | | Лог валидации |
| `setup_sheet` | JSONB | | Карта наладки |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

### 5.10. `time_norms`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `process_id` | UUID | FK → technology_processes | ТП |
| `source` | VARCHAR(64) | NOT NULL | Источник норм |
| `batch_size` | NUMERIC(12,2) | NOT NULL, DEFAULT 1 | Партия |
| `stage_norms` | JSONB | NOT NULL | Детализация по этапам |
| `total_tpz` | NUMERIC(12,3) | | Σ Тпз |
| `total_tsh` | NUMERIC(12,3) | | Σ Тшт |
| `total_tsh_k` | NUMERIC(12,3) | | Σ Тшт-к |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

### 5.11. `material_norms`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `process_id` | UUID | FK → technology_processes | ТП |
| `material_code` | VARCHAR(64) | FK → materials | Материал |
| `blank_mass` | NUMERIC(14,4) | | Масса заготовки |
| `part_mass` | NUMERIC(14,4) | | Масса детали |
| `norm_per_part` | NUMERIC(14,4) | | Норма на деталь |
| `norm_per_batch` | NUMERIC(14,4) | | Норма на партию |
| `batch_size` | NUMERIC(12,2) | NOT NULL, DEFAULT 1 | Партия |
| `material_utilization` | NUMERIC(8,4) | | КИМ |
| `tech_loss_coefficient` | NUMERIC(8,4) | | Ктех |
| `defect_coefficient` | NUMERIC(8,4) | | Кбр |
| `consumptions` | JSONB | | Пооперационные потери |
| `tool_consumptions` | JSONB | | Расход инструмента |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

---

## 6. Оркестрация и версионность (`iktk`)

### 6.1. `project_state`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | UNIQUE, FK → projects | Проект |
| `project_name` | VARCHAR(255) | NOT NULL | Имя |
| `requested_by` | VARCHAR(128) | | Инициатор |
| `state` | VARCHAR(64) | NOT NULL | Состояние автомата |
| `version` | VARCHAR(64) | | Версия |
| `payload` | JSONB | | Дополнительно |
| `created_at`, `updated_at` | TIMESTAMPTZ | NOT NULL | Метки времени |

### 6.2. `decision_log`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `event_type` | VARCHAR(64) | NOT NULL | Тип события |
| `actor` | VARCHAR(128) | NOT NULL, DEFAULT 'SYSTEM' | Актор |
| `payload` | JSONB | | Данные |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

### 6.3. `entity_versions`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `entity_type` | VARCHAR(64) | NOT NULL | Тип сущности |
| `entity_id` | UUID | NOT NULL | ID сущности |
| `project_id` | UUID | FK → projects | Проект |
| `version` | VARCHAR(64) | NOT NULL | Метка |
| `version_number` | INT | NOT NULL | Номер |
| `state` | VARCHAR(64) | | Состояние |
| `snapshot` | JSONB | NOT NULL | Снапшот |
| `diff` | JSONB | | Diff |
| `change_type` | VARCHAR(32) | NOT NULL | Тип изменения |
| `change_reason` | VARCHAR(512) | | Причина |
| `created_by` | VARCHAR(128) | | Автор |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |
| `is_current` | BOOLEAN | NOT NULL, DEFAULT FALSE | Актуальная |

UNIQUE: `(entity_type, entity_id, version)`.

### 6.4. `rollback_points`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `entity_type` | VARCHAR(64) | | Тип сущности |
| `entity_id` | UUID | | ID сущности |
| `name` | VARCHAR(255) | NOT NULL | Имя точки |
| `description` | VARCHAR(1000) | | Описание |
| `state` | VARCHAR(64) | | Состояние |
| `snapshot_ref` | UUID | FK → entity_versions | Снапшот |
| `created_by` | VARCHAR(128) | | Автор |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |
| `is_automatic` | BOOLEAN | NOT NULL, DEFAULT FALSE | Автоматическая |

### 6.5. `rollback_history`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `project_id` | UUID | FK → projects | Проект |
| `rollback_point_id` | UUID | FK → rollback_points | Точка отката |
| `from_version` | VARCHAR(64) | | Из версии |
| `to_version` | VARCHAR(64) | NOT NULL | В версию |
| `from_state` | VARCHAR(64) | | Из состояния |
| `to_state` | VARCHAR(64) | NOT NULL | В состояние |
| `reason` | VARCHAR(1000) | | Причина |
| `performed_by` | VARCHAR(128) | | Выполнил |
| `performed_at` | TIMESTAMPTZ | NOT NULL | Время |
| `success` | BOOLEAN | NOT NULL, DEFAULT TRUE | Успех |
| `details` | JSONB | | Детали |

### 6.6. `entity_locks`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `entity_type` | VARCHAR(64) | PK (составной) | Тип сущности |
| `entity_id` | UUID | PK (составной) | ID сущности |
| `version` | BIGINT | NOT NULL, DEFAULT 0 | Версия блокировки |
| `locked_by` | VARCHAR(128) | | Кто заблокировал |
| `locked_at` | TIMESTAMPTZ | | Когда |
| `lock_type` | VARCHAR(32) | NOT NULL, DEFAULT 'OPTIMISTIC' | Тип |
| `lock_expires_at` | TIMESTAMPTZ | | Истечение |

---

## 7. Аудит (`iktk_audit`)

### 7.1. `audit_log`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `user_id` | VARCHAR(128) | NOT NULL | Пользователь |
| `action` | VARCHAR(64) | NOT NULL | Действие |
| `result` | `audit_result` | NOT NULL | `SUCCESS` / `FAILURE` |
| `reason` | VARCHAR(256) | | Причина |
| `request_id` | VARCHAR(64) | | Request ID |
| `correlation_id` | VARCHAR(64) | | Correlation ID |
| `ip` | VARCHAR(64) | | IP |
| `user_agent` | VARCHAR(512) | | User-Agent |
| `details` | JSONB | | Детали |
| `created_at` | TIMESTAMPTZ | NOT NULL | Создание |

**Append-only:** триггеры запрещают UPDATE и DELETE.

### 7.2. `change_log`

| Поле | Тип | Ограничения | Назначение |
|---|---|---|---|
| `id` | UUID | PK | Идентификатор |
| `entity_type` | VARCHAR(64) | NOT NULL | Тип сущности |
| `entity_id` | UUID | NOT NULL | ID сущности |
| `project_id` | UUID | FK → projects | Проект |
| `operation` | VARCHAR(32) | NOT NULL | `INSERT` / `UPDATE` / `DELETE` |
| `field_name` | VARCHAR(128) | | Имя поля |
| `old_value` | TEXT | | Старое значение |
| `new_value` | TEXT | | Новое значение |
| `changed_by` | VARCHAR(128) | | Автор |
| `changed_at` | TIMESTAMPTZ | NOT NULL | Время |
| `request_id` | VARCHAR(64) | | Request ID |
| `correlation_id` | VARCHAR(64) | | Correlation ID |
| `transaction_id` | VARCHAR(64) | | ID транзакции |

---

## 8. Представления

| Представление | Назначение |
|---|---|
| `iktk.v_project_processes` | Связь проектов и ТП |
| `iktk.v_stages_with_equipment` | Этапы ТП с требованиями к оборудованию |
| `iktk.v_project_norms` | Сводные нормы времени и материалов по проекту |
| `iktk.v_current_versions` | Актуальные версии сущностей |
| `iktk.v_project_version_history` | История версий проекта |
| `iktk.v_rollback_summary` | Сводка по точкам отката |

---

## 9. Функции и процедуры

| Функция | Назначение |
|---|---|
| `iktk.set_updated_at()` | Триггерная: `updated_at = NOW()` |
| `iktk.new_uuid()` | Генерация UUID v4 |
| `iktk.is_jsonb_valid(jsonb)` | Проверка JSONB |
| `iktk.on_project_change()` | Версионность проектов |
| `iktk.on_process_change()` | Версионность ТП |
| `iktk.on_stage_change()` | Версионность этапов |
| `iktk.rollback_project(uuid, varchar, varchar, varchar)` | Откат проекта |
| `iktk.create_rollback_point(...)` | Создание точки отката |
| `iktk.cleanup_old_versions(int)` | Очистка старых версий |
| `iktk_audit.deny_modification()` | Запрет UPDATE/DELETE в аудите |

---

## 10. ENUM-типы

| Тип | Значения |
|---|---|
| `iktk.object_type` | `PART`, `ASSEMBLY` |
| `iktk.project_status` | `DRAFT`, `ACTIVE`, `COMPLETED`, `REJECTED`, `ARCHIVED` |
| `iktk.process_type` | `CASTING`, `FORGING`, `MACHINING`, `HEAT_TREATMENT`, `COATING`, `ASSEMBLY`, `TECHNICAL_CONTROL`, `NORMING`, `AUXILIARY` |
| `iktk.process_status` | `DRAFT`, `APPROVED`, `REJECTED`, `ARCHIVED` |
| `iktk.production_type` | `SINGLE`, `SERIAL`, `MASS` |
| `iktk.operation_type` | `BLANK_PREPARATION`, `CASTING`, `HOT_FORGING`, `COLD_FORGING`, `SHEET_STAMPING`, `MACHINING`, `CNC_MACHINING`, `HEAT_TREATMENT`, `COATING`, `ASSEMBLY`, `TECHNICAL_CONTROL`, `NORMING`, `AUXILIARY` |
| `iktk.stage_kind` | `MAIN`, `AUXILIARY`, `CONTROL`, `TRANSPORT` |
| `iktk_audit.audit_result` | `SUCCESS`, `FAILURE` |

---

## 11. Планируемые миграции

| Миграция | Содержание |
|---|---|
| `V4__equipment.sql` | Таблицы оборудования: `equipment`, `equipment_capabilities`, `equipment_status`, `equipment_tooling` |
| `V5__bom.sql` | Таблицы BOM и сборок: `assembly_units`, `bom_items`, `assembly_connections` |
| `V6__recommendations.sql` | Таблицы рекомендаций: `equipment_requirements`, `equipment_gap_analysis`, `equipment_recommendations`, `vendor_catalog`, `outsourcing_options` |

---

## 12. Принципы проектирования

1. **Нормализация до 3НФ** — все сущности приведены к третьей нормальной форме.
2. **UUID как PK** — обеспечивает распределённую генерацию и защиту от перебора.
3. **JSONB для гибких данных** — снапшоты, детализация норм, BOM, карты наладки.
4. **Append-only аудит** — `audit_log` защищён от UPDATE/DELETE.
5. **Версионность через снапшоты** — `entity_versions` хранит полные снапшоты.
6. **Каскадное удаление** — `ON DELETE CASCADE` для связанных сущностей.
7. **Индексы по горячим полям** — `status`, `created_at`, `project_id`, `entity_id`.
8. **Триггеры для `updated_at`** — единый механизм через `set_updated_at()`.
9. **Идемпотентность миграций** — `IF NOT EXISTS`, `ON CONFLICT DO NOTHING`.
10. **Разделение схем** — `iktk` (бизнес), `iktk_ref` (справочники), `iktk_audit` (аудит).

---

**Конец документа `erd.md`.**