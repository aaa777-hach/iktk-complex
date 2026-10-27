-- Seed: 6 users с не-ADMIN ролями (dev-only, пароль у всех admin123)
-- Идемпотентно: ON CONFLICT DO NOTHING. Не подключён к Flyway (auth без Flyway).
-- Пароль admin123, BCrypt cost=10 (взят из admin.password_hash — ровно тот же хэш).

INSERT INTO users
  (id, username, password_hash, display_name, email, enterprise_code, department,
   enabled, locked, failed_attempts, created_at, updated_at)
VALUES
  ('20000000-0000-0000-0000-000000000001', 'manager',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Менеджер проекта', 'manager@iktk.local', 'IKTK', 'Управление',
   TRUE, FALSE, 0, now(), now()),
  ('20000000-0000-0000-0000-000000000002', 'engineer',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Инженер-технолог', 'engineer@iktk.local', 'IKTK', 'Технология',
   TRUE, FALSE, 0, now(), now()),
  ('20000000-0000-0000-0000-000000000003', 'normer',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Нормировщик', 'normer@iktk.local', 'IKTK', 'Нормирование',
   TRUE, FALSE, 0, now(), now()),
  ('20000000-0000-0000-0000-000000000004', 'operator',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Оператор', 'operator@iktk.local', 'IKTK', 'Производство',
   TRUE, FALSE, 0, now(), now()),
  ('20000000-0000-0000-0000-000000000005', 'viewer',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Наблюдатель', 'viewer@iktk.local', 'IKTK', 'Общий',
   TRUE, FALSE, 0, now(), now()),
  ('20000000-0000-0000-0000-000000000006', 'integration',
   '$2a$10$MpBpenp5eF4BNhyaJuFpcO71Is1/0tFPKddq2xkRnAN4aoGG3YNvC',
   'Интегратор', 'integration@iktk.local', 'IKTK', 'Интеграция',
   TRUE, FALSE, 0, now(), now())
ON CONFLICT (username) DO NOTHING;

-- 1 роль каждому (id взяты из таблицы roles)
INSERT INTO user_roles (user_id, role_id) VALUES
  ('20000000-0000-0000-0000-000000000001', '4595dcf5-5884-4627-9b96-dc3438abf121'), -- MANAGER
  ('20000000-0000-0000-0000-000000000002', 'a0e56eac-2fba-4d77-8340-1c67563ac623'), -- ENGINEER
  ('20000000-0000-0000-0000-000000000003', 'aecb1a83-a6e7-45de-9d70-ad83551fb927'), -- NORMER
  ('20000000-0000-0000-0000-000000000004', '09b17120-75b2-49e7-b639-eea8e72e9758'), -- OPERATOR
  ('20000000-0000-0000-0000-000000000005', '6d42f57d-363d-49ee-b595-86cf06060f60'), -- VIEWER
  ('20000000-0000-0000-0000-000000000006', 'c182fca3-3215-4300-b36e-2e383c5aaca3')  -- INTEGRATION
ON CONFLICT (user_id, role_id) DO NOTHING;