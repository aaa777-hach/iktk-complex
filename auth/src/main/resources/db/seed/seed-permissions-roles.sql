-- =====================================================================
-- ИКТК auth: seed ролей и разрешений (RBAC).
-- Применение: psql -U iktk_app -d iktk_auth -f seed-permissions-roles.sql
-- Идемпотентно: ON CONFLICT DO NOTHING.
-- =====================================================================

-- 1. Roles (6 новых; ADMIN уже есть)
INSERT INTO roles (id, name, display_name, description)
VALUES
  (gen_random_uuid()::text, 'MANAGER',     'Менеджер',        'Управление проектами и командой'),
  (gen_random_uuid()::text, 'ENGINEER',    'Инженер-технолог','Проектирование ТП'),
  (gen_random_uuid()::text, 'NORMER',      'Нормировщик',     'Нормирование труда и материалов'),
  (gen_random_uuid()::text, 'OPERATOR',    'Оператор',        'Просмотр и исполнение'),
  (gen_random_uuid()::text, 'VIEWER',      'Наблюдатель',     'Только чтение'),
  (gen_random_uuid()::text, 'INTEGRATION', 'Интеграция',      'Сервисный аккаунт')
ON CONFLICT (name) DO NOTHING;

-- 2. Permissions (15)
INSERT INTO permissions (id, code, resource, action, description)
VALUES
  (gen_random_uuid()::text, 'user:read',        'user',        'read',   'Просмотр пользователей'),
  (gen_random_uuid()::text, 'user:write',       'user',        'write',  'Создание/изменение пользователей'),
  (gen_random_uuid()::text, 'user:delete',      'user',        'delete', 'Удаление пользователей'),
  (gen_random_uuid()::text, 'project:read',     'project',     'read',   'Просмотр проектов'),
  (gen_random_uuid()::text, 'project:write',    'project',     'write',  'Создание/изменение проектов'),
  (gen_random_uuid()::text, 'project:delete',   'project',     'delete', 'Удаление проектов'),
  (gen_random_uuid()::text, 'process:read',     'process',     'read',   'Просмотр ТП'),
  (gen_random_uuid()::text, 'process:design',   'process',     'design', 'Проектирование ТП'),
  (gen_random_uuid()::text, 'norm:read',        'norm',        'read',   'Просмотр норм'),
  (gen_random_uuid()::text, 'norm:write',       'norm',        'write',  'Изменение норм'),
  (gen_random_uuid()::text, 'report:read',      'report',      'read',   'Просмотр отчётов'),
  (gen_random_uuid()::text, 'equipment:read',   'equipment',   'read',   'Просмотр оборудования'),
  (gen_random_uuid()::text, 'equipment:write',  'equipment',   'write',  'Изменение оборудования'),
  (gen_random_uuid()::text, 'admin:all',        'admin',       'all',    'Полный административный доступ'),
  (gen_random_uuid()::text, 'integration:call', 'integration', 'call',   'Вызов интеграционных API')
ON CONFLICT (code) DO NOTHING;

-- 3. ADMIN -> все permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 4. MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN ('project:read','project:write','user:read','report:read','equipment:read','process:read')
ON CONFLICT DO NOTHING;

-- 5. ENGINEER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ENGINEER'
  AND p.code IN ('process:read','process:design','project:read','report:read')
ON CONFLICT DO NOTHING;

-- 6. NORMER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'NORMER'
  AND p.code IN ('norm:read','norm:write','process:read','report:read')
ON CONFLICT DO NOTHING;

-- 7. OPERATOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'OPERATOR'
  AND p.code IN ('process:read','equipment:read')
ON CONFLICT DO NOTHING;

-- 8. VIEWER -> все *:read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'VIEWER' AND p.action = 'read'
ON CONFLICT DO NOTHING;

-- 9. INTEGRATION -> все
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'INTEGRATION'
ON CONFLICT DO NOTHING;