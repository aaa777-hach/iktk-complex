-- =============================================================================
-- ИКТК: начальная схема базы данных (PostgreSQL)
-- Миграция: V1__init_schema.sql
-- Назначение: базовая структура БД, расширения, общие таблицы, справочники.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Расширения PostgreSQL
-- -----------------------------------------------------------------------------

-- UUID-генерация (pgcrypto).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Триграммы для нечёткого поиска по наименованиям.
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Функции для работы с JSONB (опционально, для отчётности).
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- -----------------------------------------------------------------------------
-- 2. Схемы
-- -----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS iktk;
CREATE SCHEMA IF NOT EXISTS iktk_audit;
CREATE SCHEMA IF NOT EXISTS iktk_ref;

COMMENT ON SCHEMA iktk       IS 'Основные бизнес-данные ИКТК';
COMMENT ON SCHEMA iktk_audit IS 'Аудит и журналирование';
COMMENT ON SCHEMA iktk_ref   IS 'Справочники и классификаторы';

-- -----------------------------------------------------------------------------
-- 3. Общие типы (ENUM)
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'object_type') THEN
        CREATE TYPE iktk.object_type AS ENUM ('PART', 'ASSEMBLY');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'project_status') THEN
        CREATE TYPE iktk.project_status AS ENUM (
            'DRAFT', 'ACTIVE', 'COMPLETED', 'REJECTED', 'ARCHIVED'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'process_type') THEN
        CREATE TYPE iktk.process_type AS ENUM (
            'CASTING', 'FORGING', 'MACHINING', 'HEAT_TREATMENT',
            'COATING', 'ASSEMBLY', 'TECHNICAL_CONTROL', 'NORMING', 'AUXILIARY'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'process_status') THEN
        CREATE TYPE iktk.process_status AS ENUM (
            'DRAFT', 'APPROVED', 'REJECTED', 'ARCHIVED'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'production_type') THEN
        CREATE TYPE iktk.production_type AS ENUM ('SINGLE', 'SERIAL', 'MASS');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'operation_type') THEN
        CREATE TYPE iktk.operation_type AS ENUM (
            'BLANK_PREPARATION', 'CASTING', 'HOT_FORGING', 'COLD_FORGING',
            'SHEET_STAMPING', 'MACHINING', 'CNC_MACHINING', 'HEAT_TREATMENT',
            'COATING', 'ASSEMBLY', 'TECHNICAL_CONTROL', 'NORMING', 'AUXILIARY'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'stage_kind') THEN
        CREATE TYPE iktk.stage_kind AS ENUM ('MAIN', 'AUXILIARY', 'CONTROL', 'TRANSPORT');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'audit_result') THEN
        CREATE TYPE iktk_audit.audit_result AS ENUM ('SUCCESS', 'FAILURE');
    END IF;
END $$;

COMMENT ON TYPE iktk.object_type       IS 'Тип объекта изготовления';
COMMENT ON TYPE iktk.project_status    IS 'Бизнес-статус проекта';
COMMENT ON TYPE iktk.process_type      IS 'Тип технологического процесса';
COMMENT ON TYPE iktk.process_status    IS 'Статус технологического процесса';
COMMENT ON TYPE iktk.production_type   IS 'Вид производства';
COMMENT ON TYPE iktk.operation_type    IS 'Тип операции';
COMMENT ON TYPE iktk.stage_kind        IS 'Вид этапа ТП';
COMMENT ON TYPE iktk_audit.audit_result IS 'Результат события аудита';

-- -----------------------------------------------------------------------------
-- 4. Общие функции
-- -----------------------------------------------------------------------------

-- Автообновление updated_at.
CREATE OR REPLACE FUNCTION iktk.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.set_updated_at() IS 'Устанавливает updated_at = NOW() при UPDATE';

-- Генерация UUID (обёртка над gen_random_uuid).
CREATE OR REPLACE FUNCTION iktk.new_uuid()
RETURNS UUID AS $$
BEGIN
    RETURN gen_random_uuid();
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION iktk.new_uuid() IS 'Возвращает новый UUID v4';

-- Проверка корректности JSONB.
CREATE OR REPLACE FUNCTION iktk.is_jsonb_valid(value JSONB)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN value IS NULL OR jsonb_typeof(value) IS NOT NULL;
EXCEPTION WHEN OTHERS THEN
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- -----------------------------------------------------------------------------
-- 5. Справочники (schema iktk_ref)
-- -----------------------------------------------------------------------------

-- 5.1. Предприятия
CREATE TABLE IF NOT EXISTS iktk_ref.enterprises (
    code            VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    full_name       VARCHAR(512),
    address         VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk_ref.enterprises IS 'Справочник предприятий/филиалов';
COMMENT ON COLUMN iktk_ref.enterprises.code IS 'Код предприятия (PK)';

CREATE TRIGGER trg_enterprises_updated_at
    BEFORE UPDATE ON iktk_ref.enterprises
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- 5.2. Подразделения
CREATE TABLE IF NOT EXISTS iktk_ref.departments (
    code            VARCHAR(64) PRIMARY KEY,
    enterprise_code VARCHAR(64) NOT NULL REFERENCES iktk_ref.enterprises(code),
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE iktk_ref.departments IS 'Справочник подразделений';

CREATE INDEX IF NOT EXISTS idx_departments_enterprise
    ON iktk_ref.departments(enterprise_code);

CREATE TRIGGER trg_departments_updated_at
    BEFORE UPDATE ON iktk_ref.departments
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- 5.3. Материалы
CREATE TABLE IF NOT EXISTS iktk_ref.materials (
    code            VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    grade           VARCHAR(128),
    standard        VARCHAR(128),
    density_kg_m3   NUMERIC(12,3) NOT NULL,
    cost_per_kg     NUMERIC(14,4),
    material_group  VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk_ref.materials IS 'Справочник материалов';
COMMENT ON COLUMN iktk_ref.materials.density_kg_m3 IS 'Плотность, кг/м³';

CREATE INDEX IF NOT EXISTS idx_materials_group
    ON iktk_ref.materials(material_group);

CREATE INDEX IF NOT EXISTS idx_materials_name_trgm
    ON iktk_ref.materials USING gin (name gin_trgm_ops);

CREATE TRIGGER trg_materials_updated_at
    BEFORE UPDATE ON iktk_ref.materials
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- 5.4. Единицы измерения
CREATE TABLE IF NOT EXISTS iktk_ref.units (
    code            VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    short_name      VARCHAR(32) NOT NULL,
    dimension       VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE iktk_ref.units IS 'Справочник единиц измерения';

-- 5.5. Классификатор операций
CREATE TABLE IF NOT EXISTS iktk_ref.operation_catalog (
    code            VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    operation_type  iktk.operation_type NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE iktk_ref.operation_catalog IS 'Классификатор операций';

CREATE INDEX IF NOT EXISTS idx_operation_catalog_type
    ON iktk_ref.operation_catalog(operation_type);

CREATE TRIGGER trg_operation_catalog_updated_at
    BEFORE UPDATE ON iktk_ref.operation_catalog
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 6. Пользователи и роли (schema iktk)
-- -----------------------------------------------------------------------------

-- 6.1. Роли
CREATE TABLE IF NOT EXISTS iktk.roles (
    name            VARCHAR(32) PRIMARY KEY,
    display_name    VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE iktk.roles IS 'Роли пользователей (RBAC)';

-- 6.2. Разрешения
CREATE TABLE IF NOT EXISTS iktk.permissions (
    code            VARCHAR(64) PRIMARY KEY,
    description     VARCHAR(512),
    resource        VARCHAR(64) NOT NULL,
    action          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE iktk.permissions IS 'Разрешения (атомарные операции)';

CREATE INDEX IF NOT EXISTS idx_permissions_resource
    ON iktk.permissions(resource);

-- 6.3. Связь ролей и разрешений
CREATE TABLE IF NOT EXISTS iktk.role_permissions (
    role_name       VARCHAR(32) NOT NULL REFERENCES iktk.roles(name) ON DELETE CASCADE,
    permission_code VARCHAR(64) NOT NULL REFERENCES iktk.permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role_name, permission_code)
);

-- 6.4. Наследование ролей
CREATE TABLE IF NOT EXISTS iktk.role_inheritance (
    parent_role     VARCHAR(32) NOT NULL REFERENCES iktk.roles(name) ON DELETE CASCADE,
    child_role      VARCHAR(32) NOT NULL REFERENCES iktk.roles(name) ON DELETE CASCADE,
    PRIMARY KEY (parent_role, child_role),
    CHECK (parent_role <> child_role)
);

COMMENT ON TABLE iktk.role_inheritance IS 'Наследование ролей: parent наследует child';

-- 6.5. Пользователи
CREATE TABLE IF NOT EXISTS iktk.users (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    username          VARCHAR(128) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255),
    email             VARCHAR(255) UNIQUE,
    enterprise_code   VARCHAR(64) REFERENCES iktk_ref.enterprises(code),
    department_code   VARCHAR(64) REFERENCES iktk_ref.departments(code),
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    locked            BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts   INT NOT NULL DEFAULT 0,
    last_login_at     TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.users IS 'Пользователи ИКТК';
COMMENT ON COLUMN iktk.users.password_hash IS 'Хэш пароля (bcrypt/argon2)';

CREATE INDEX IF NOT EXISTS idx_users_username     ON iktk.users(username);
CREATE INDEX IF NOT EXISTS idx_users_email        ON iktk.users(email);
CREATE INDEX IF NOT EXISTS idx_users_enterprise   ON iktk.users(enterprise_code);
CREATE INDEX IF NOT EXISTS idx_users_department   ON iktk.users(department_code);
CREATE INDEX IF NOT EXISTS idx_users_enabled      ON iktk.users(enabled) WHERE enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_users_locked       ON iktk.users(locked) WHERE locked = TRUE;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON iktk.users
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- 6.6. Связь пользователей и ролей
CREATE TABLE IF NOT EXISTS iktk.user_roles (
    user_id         UUID NOT NULL REFERENCES iktk.users(id) ON DELETE CASCADE,
    role_name       VARCHAR(32) NOT NULL REFERENCES iktk.roles(name) ON DELETE CASCADE,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by      UUID REFERENCES iktk.users(id),
    PRIMARY KEY (user_id, role_name)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role
    ON iktk.user_roles(role_name);

-- 6.7. Токены (для refresh/revoke)
CREATE TABLE IF NOT EXISTS iktk.tokens (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    user_id         UUID NOT NULL REFERENCES iktk.users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL,
    token_type      VARCHAR(32) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    device_info     VARCHAR(512)
);

COMMENT ON TABLE iktk.tokens IS 'Выпущенные токены (access/refresh)';
COMMENT ON COLUMN iktk.tokens.token_hash IS 'Хэш токена';

CREATE INDEX IF NOT EXISTS idx_tokens_user     ON iktk.tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_hash     ON iktk.tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_tokens_expires  ON iktk.tokens(expires_at) WHERE revoked = FALSE;

-- -----------------------------------------------------------------------------
-- 7. Проекты (schema iktk)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.projects (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    designation           VARCHAR(128) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           VARCHAR(2000),
    object_type           iktk.object_type NOT NULL,
    status                iktk.project_status NOT NULL DEFAULT 'DRAFT',
    enterprise_code       VARCHAR(64) REFERENCES iktk_ref.enterprises(code),
    requested_by          VARCHAR(128),
    responsible_engineer  VARCHAR(128),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.projects IS 'Проекты ТПП';
COMMENT ON COLUMN iktk.projects.object_type IS 'Тип объекта: деталь или сборка';

CREATE INDEX IF NOT EXISTS idx_projects_designation   ON iktk.projects(designation);
CREATE INDEX IF NOT EXISTS idx_projects_status        ON iktk.projects(status);
CREATE INDEX IF NOT EXISTS idx_projects_enterprise    ON iktk.projects(enterprise_code);
CREATE INDEX IF NOT EXISTS idx_projects_created_at    ON iktk.projects(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_projects_object_type   ON iktk.projects(object_type);

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON iktk.projects
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 8. Версии проектов (снапшоты для отката)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.project_versions (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id      UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    version         VARCHAR(64) NOT NULL,
    state           VARCHAR(64) NOT NULL,
    snapshot        JSONB NOT NULL,
    comment         VARCHAR(1000),
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, version)
);

COMMENT ON TABLE  iktk.project_versions IS 'Версии/снапшоты проектов для отката';
COMMENT ON COLUMN iktk.project_versions.snapshot IS 'Полный снапшот состояния проекта';

CREATE INDEX IF NOT EXISTS idx_project_versions_project
    ON iktk.project_versions(project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_versions_state
    ON iktk.project_versions(state);

-- -----------------------------------------------------------------------------
-- 9. Аудит (schema iktk_audit)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk_audit.audit_log (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    user_id         VARCHAR(128) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    result          iktk_audit.audit_result NOT NULL,
    reason          VARCHAR(256),
    request_id      VARCHAR(64),
    correlation_id  VARCHAR(64),
    ip              VARCHAR(64),
    user_agent      VARCHAR(512),
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk_audit.audit_log IS 'Журнал аудита (append-only)';
COMMENT ON COLUMN iktk_audit.audit_log.details IS 'Детали события (JSONB)';

CREATE INDEX IF NOT EXISTS idx_audit_user          ON iktk_audit.audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action        ON iktk_audit.audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_result        ON iktk_audit.audit_log(result);
CREATE INDEX IF NOT EXISTS idx_audit_created_at    ON iktk_audit.audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_request_id    ON iktk_audit.audit_log(request_id);
CREATE INDEX IF NOT EXISTS idx_audit_user_action   ON iktk_audit.audit_log(user_id, action, created_at DESC);

-- Запрет UPDATE и DELETE на audit_log (append-only).
CREATE OR REPLACE FUNCTION iktk_audit.deny_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log является append-only, изменение и удаление запрещены';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE ON iktk_audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION iktk_audit.deny_modification();

CREATE TRIGGER trg_audit_no_delete
    BEFORE DELETE ON iktk_audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION iktk_audit.deny_modification();

-- -----------------------------------------------------------------------------
-- 10. Журнал решений оркестратора (schema iktk)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.decision_log (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id      UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,
    actor           VARCHAR(128) NOT NULL DEFAULT 'SYSTEM',
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.decision_log IS 'Журнал решений оркестратора';

CREATE INDEX IF NOT EXISTS idx_decision_project
    ON iktk.decision_log(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_decision_event
    ON iktk.decision_log(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_decision_actor
    ON iktk.decision_log(actor);

-- -----------------------------------------------------------------------------
-- 11. Runtime-состояние проектов (для оркестратора)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.project_state (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id      UUID NOT NULL UNIQUE REFERENCES iktk.projects(id) ON DELETE CASCADE,
    project_name    VARCHAR(255) NOT NULL,
    requested_by    VARCHAR(128),
    state           VARCHAR(64) NOT NULL,
    version         VARCHAR(64),
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.project_state IS 'Runtime-состояние проекта (конечный автомат оркестратора)';
COMMENT ON COLUMN iktk.project_state.state IS 'Текущее состояние ProjectState';

CREATE INDEX IF NOT EXISTS idx_project_state_state
    ON iktk.project_state(state);
CREATE INDEX IF NOT EXISTS idx_project_state_updated
    ON iktk.project_state(updated_at DESC);

CREATE TRIGGER trg_project_state_updated_at
    BEFORE UPDATE ON iktk.project_state
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 12. Начальные данные: роли
-- -----------------------------------------------------------------------------

INSERT INTO iktk.roles (name, display_name, description) VALUES
    ('ADMIN',       'Администратор',   'Полный доступ, управление пользователями и настройками'),
    ('MANAGER',     'Руководитель',    'Аналитика, утверждение, точки контроля'),
    ('ENGINEER',    'Инженер-технолог','Создание и правка проектов и ТП'),
    ('NORMER',      'Нормировщик',     'Работа с нормами времени и материалов'),
    ('OPERATOR',    'Оператор',        'Просмотр, запуск расчётов'),
    ('VIEWER',      'Наблюдатель',     'Только просмотр'),
    ('INTEGRATION', 'Интеграция',      'Служебная роль для внешних сервисов')
ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 13. Начальные данные: единицы измерения
-- -----------------------------------------------------------------------------

INSERT INTO iktk_ref.units (code, name, short_name, dimension) VALUES
    ('MM',   'Миллиметр',       'мм',   'LENGTH'),
    ('M',    'Метр',            'м',    'LENGTH'),
    ('MM2',  'Квадратный мм',   'мм²',  'AREA'),
    ('M2',   'Квадратный метр', 'м²',   'AREA'),
    ('MM3',  'Кубический мм',   'мм³',  'VOLUME'),
    ('KG',   'Килограмм',       'кг',   'MASS'),
    ('G',    'Грамм',           'г',    'MASS'),
    ('MIN',  'Минута',          'мин',  'TIME'),
    ('S',    'Секунда',         'с',    'TIME'),
    ('H',    'Час',             'ч',    'TIME'),
    ('PCS',  'Штука',           'шт',   'QUANTITY'),
    ('KWH',  'Киловатт-час',    'кВт·ч','ENERGY')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 14. Начальные данные: разрешения
-- -----------------------------------------------------------------------------

INSERT INTO iktk.permissions (code, description, resource, action) VALUES
    ('PROJECT_READ',       'Просмотр проектов',              'PROJECT', 'READ'),
    ('PROJECT_CREATE',     'Создание проектов',              'PROJECT', 'CREATE'),
    ('PROJECT_UPDATE',     'Изменение проектов',             'PROJECT', 'UPDATE'),
    ('PROJECT_DELETE',     'Удаление проектов',              'PROJECT', 'DELETE'),
    ('PROJECT_APPROVE',    'Утверждение проектов',           'PROJECT', 'APPROVE'),
    ('PROJECT_EXPORT',     'Экспорт проектов',               'PROJECT', 'EXPORT'),
    ('TP_READ',            'Просмотр ТП',                    'TP', 'READ'),
    ('TP_CREATE',          'Создание ТП',                    'TP', 'CREATE'),
    ('TP_UPDATE',          'Изменение ТП',                   'TP', 'UPDATE'),
    ('TP_APPROVE',         'Утверждение ТП',                 'TP', 'APPROVE'),
    ('EQUIPMENT_READ',     'Просмотр оборудования',          'EQUIPMENT', 'READ'),
    ('EQUIPMENT_EDIT',     'Редактирование оборудования',    'EQUIPMENT', 'EDIT'),
    ('BOM_READ',           'Просмотр BOM',                   'BOM', 'READ'),
    ('BOM_EDIT',           'Редактирование BOM',             'BOM', 'EDIT'),
    ('RECOMMENDATION_READ','Просмотр рекомендаций',          'RECOMMENDATION', 'READ'),
    ('RECOMMENDATION_ACCEPT','Принятие рекомендаций',        'RECOMMENDATION', 'APPROVE'),
    ('NORM_READ',          'Просмотр норм',                  'NORM', 'READ'),
    ('NORM_EDIT',          'Редактирование норм',            'NORM', 'EDIT'),
    ('USER_READ',          'Просмотр пользователей',         'USER', 'READ'),
    ('USER_EDIT',          'Редактирование пользователей',   'USER', 'EDIT'),
    ('ROLE_ASSIGN',        'Назначение ролей',               'ROLE', 'ASSIGN'),
    ('AUDIT_READ',         'Просмотр аудита',                'AUDIT', 'READ'),
    ('SETTINGS_EDIT',      'Изменение настроек',             'SETTINGS', 'EDIT')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 15. Начальные данные: связь ролей и разрешений
-- -----------------------------------------------------------------------------

-- ADMIN — все разрешения
INSERT INTO iktk.role_permissions (role_name, permission_code)
SELECT 'ADMIN', code FROM iktk.permissions
ON CONFLICT DO NOTHING;

-- MANAGER
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('MANAGER', 'PROJECT_READ'),
    ('MANAGER', 'PROJECT_UPDATE'),
    ('MANAGER', 'PROJECT_APPROVE'),
    ('MANAGER', 'PROJECT_EXPORT'),
    ('MANAGER', 'TP_READ'),
    ('MANAGER', 'TP_APPROVE'),
    ('MANAGER', 'EQUIPMENT_READ'),
    ('MANAGER', 'BOM_READ'),
    ('MANAGER', 'RECOMMENDATION_READ'),
    ('MANAGER', 'RECOMMENDATION_ACCEPT'),
    ('MANAGER', 'NORM_READ'),
    ('MANAGER', 'AUDIT_READ')
ON CONFLICT DO NOTHING;

-- ENGINEER
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('ENGINEER', 'PROJECT_READ'),
    ('ENGINEER', 'PROJECT_CREATE'),
    ('ENGINEER', 'PROJECT_UPDATE'),
    ('ENGINEER', 'PROJECT_EXPORT'),
    ('ENGINEER', 'TP_READ'),
    ('ENGINEER', 'TP_CREATE'),
    ('ENGINEER', 'TP_UPDATE'),
    ('ENGINEER', 'EQUIPMENT_READ'),
    ('ENGINEER', 'BOM_READ'),
    ('ENGINEER', 'BOM_EDIT'),
    ('ENGINEER', 'RECOMMENDATION_READ'),
    ('ENGINEER', 'NORM_READ')
ON CONFLICT DO NOTHING;

-- NORMER
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('NORMER', 'PROJECT_READ'),
    ('NORMER', 'TP_READ'),
    ('NORMER', 'EQUIPMENT_READ'),
    ('NORMER', 'BOM_READ'),
    ('NORMER', 'NORM_READ'),
    ('NORMER', 'NORM_EDIT')
ON CONFLICT DO NOTHING;

-- OPERATOR
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('OPERATOR', 'PROJECT_READ'),
    ('OPERATOR', 'TP_READ'),
    ('OPERATOR', 'EQUIPMENT_READ'),
    ('OPERATOR', 'BOM_READ'),
    ('OPERATOR', 'NORM_READ')
ON CONFLICT DO NOTHING;

-- VIEWER
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('VIEWER', 'PROJECT_READ'),
    ('VIEWER', 'TP_READ'),
    ('VIEWER', 'BOM_READ'),
    ('VIEWER', 'NORM_READ')
ON CONFLICT DO NOTHING;

-- INTEGRATION
INSERT INTO iktk.role_permissions (role_name, permission_code) VALUES
    ('INTEGRATION', 'PROJECT_READ'),
    ('INTEGRATION', 'PROJECT_UPDATE'),
    ('INTEGRATION', 'TP_READ'),
    ('INTEGRATION', 'TP_CREATE'),
    ('INTEGRATION', 'TP_UPDATE'),
    ('INTEGRATION', 'EQUIPMENT_READ'),
    ('INTEGRATION', 'BOM_READ'),
    ('INTEGRATION', 'BOM_EDIT')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 16. Начальные данные: наследование ролей
-- -----------------------------------------------------------------------------

-- ADMIN наследует MANAGER, ENGINEER
INSERT INTO iktk.role_inheritance (parent_role, child_role) VALUES
    ('ADMIN', 'MANAGER'),
    ('ADMIN', 'ENGINEER'),
    ('ADMIN', 'NORMER'),
    ('ADMIN', 'OPERATOR'),
    ('ADMIN', 'VIEWER')
ON CONFLICT DO NOTHING;

-- MANAGER наследует ENGINEER, VIEWER
INSERT INTO iktk.role_inheritance (parent_role, child_role) VALUES
    ('MANAGER', 'ENGINEER'),
    ('MANAGER', 'VIEWER')
ON CONFLICT DO NOTHING;

-- ENGINEER наследует OPERATOR
INSERT INTO iktk.role_inheritance (parent_role, child_role) VALUES
    ('ENGINEER', 'OPERATOR')
ON CONFLICT DO NOTHING;

-- NORMER наследует VIEWER
INSERT INTO iktk.role_inheritance (parent_role, child_role) VALUES
    ('NORMER', 'VIEWER')
ON CONFLICT DO NOTHING;

-- OPERATOR наследует VIEWER
INSERT INTO iktk.role_inheritance (parent_role, child_role) VALUES
    ('OPERATOR', 'VIEWER')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- 17. Права доступа к схемам
-- -----------------------------------------------------------------------------

-- Роли БД (создаются администратором СУБД):
--   iktk_app     — приложения ИКТК (чтение/запись бизнес-данных);
--   iktk_read    — только чтение (аналитика);
--   iktk_migrate — миграции (DDL).

-- Пример выдачи прав (выполняется администратором):
-- GRANT USAGE ON SCHEMA iktk, iktk_ref, iktk_audit TO iktk_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA iktk TO iktk_app;
-- GRANT SELECT ON ALL TABLES IN SCHEMA iktk_ref TO iktk_app;
-- GRANT INSERT, SELECT ON ALL TABLES IN SCHEMA iktk_audit TO iktk_app;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA iktk, iktk_audit TO iktk_app;

-- -----------------------------------------------------------------------------
-- 18. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V1__init_schema.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V1__init_schema.sql
-- =============================================================================