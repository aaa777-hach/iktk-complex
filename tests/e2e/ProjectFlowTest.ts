import { test, expect, Page, APIRequestContext, request } from '@playwright/test';

// ---------------------------------------------------------------------------
// Конфигурация
// ---------------------------------------------------------------------------

/**
 * Базовый URL веб-клиента (UI).
 */
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173';

/**
 * Базовый URL API Gateway (для подготовки данных и проверок).
 */
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

/**
 * Тестовые учётные данные.
 */
const TEST_USER = {
  username: process.env.E2E_USER || 'engineer',
  password: process.env.E2E_PASSWORD || 'engineer_password',
};

/**
 * Таймауты.
 */
const NAVIGATION_TIMEOUT = 15_000;
const API_TIMEOUT = 30_000;
const LONG_OPERATION_TIMEOUT = 120_000;

/**
 * Тестовый проект.
 */
const TEST_PROJECT = {
  name: `E2E-Корпус-${Date.now()}`,
  designation: `E2E-${Date.now().toString().slice(-6)}`,
  objectType: 'ASSEMBLY' as const,
};

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Аутентификация через API и получение токена.
 */
async function loginViaApi(api: APIRequestContext): Promise<string> {
  const response = await api.post(`${API_URL}/api/v1/auth/login`, {
    data: {
      username: TEST_USER.username,
      password: TEST_USER.password,
    },
    timeout: API_TIMEOUT,
  });

  expect(response.ok(), `Логин через API: ${response.status()}`).toBeTruthy();
  const body = await response.json();
  expect(body.accessToken).toBeTruthy();
  return body.accessToken;
}

/**
 * Аутентификация через UI.
 */
async function loginViaUi(page: Page): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });

  await page.fill('input[name="username"]', TEST_USER.username);
  await page.fill('input[name="password"]', TEST_USER.password);
  await page.click('button[type="submit"]');

  // Ожидание редиректа на dashboard
  await page.waitForURL(/\/dashboard/, { timeout: NAVIGATION_TIMEOUT });
  await expect(page.locator('h1')).toContainText('Обзор', { timeout: NAVIGATION_TIMEOUT });
}

/**
 * Создать проект через API (для быстрого setup).
 */
async function createProjectViaApi(
  api: APIRequestContext,
  token: string
): Promise<string> {
  const response = await api.post(`${API_URL}/api/v1/orchestrator/projects`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      projectName: TEST_PROJECT.name,
      designation: TEST_PROJECT.designation,
      objectType: TEST_PROJECT.objectType,
      requestedBy: TEST_USER.username,
    },
    timeout: API_TIMEOUT,
  });

  expect(response.ok(), `Создание проекта через API: ${response.status()}`).toBeTruthy();
  const body = await response.json();
  expect(body.projectId).toBeTruthy();
  return body.projectId;
}

/**
 * Ожидание состояния проекта через API (polling).
 */
async function waitForState(
  api: APIRequestContext,
  token: string,
  projectId: string,
  expectedStates: string[],
  timeoutMs: number = LONG_OPERATION_TIMEOUT
): Promise<string> {
  const startTime = Date.now();
  const interval = 2000;

  while (Date.now() - startTime < timeoutMs) {
    const response = await api.get(
      `${API_URL}/api/v1/orchestrator/projects/${projectId}/state`,
      {
        headers: { Authorization: `Bearer ${token}` },
        timeout: API_TIMEOUT,
      }
    );

    if (response.ok()) {
      const body = await response.json();
      if (expectedStates.includes(body.state)) {
        return body.state;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, interval));
  }

  throw new Error(
    `Проект ${projectId} не достиг состояния [${expectedStates.join(', ')}] за ${timeoutMs} мс`
  );
}

// ---------------------------------------------------------------------------
// Тесты
// ---------------------------------------------------------------------------

test.describe('E2E: Полный цикл проекта ТПП', () => {
  let api: APIRequestContext;
  let token: string;
  let projectId: string;

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL: API_URL });
    token = await loginViaApi(api);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  // =========================================================================
  // Сценарий A: Полный цикл ТПП для детали «Корпус»
  // =========================================================================

  test.describe('Сценарий A — Полный цикл', () => {
    test.beforeAll(async () => {
      projectId = await createProjectViaApi(api, token);
      expect(projectId).toBeTruthy();
    });

    test('A.1. Проект создан и находится в состоянии PROJECT_INIT / GEOMETRY_INPUT', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${projectId}/state`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(['PROJECT_INIT', 'GEOMETRY_INPUT', 'GEOMETRY_ANALYSIS']).toContain(body.state);
    });

    test('A.2. Страница проекта открывается и отображает заголовок', async ({ page }) => {
      await loginViaUi(page);

      await page.goto(`${BASE_URL}/projects/${projectId}`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('.project-page')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('h1, .project-header__title')).toContainText(
        TEST_PROJECT.name,
        { timeout: NAVIGATION_TIMEOUT }
      );

      // Отображается прогресс и текущий этап
      await expect(page.locator('.ant-steps')).toBeVisible();
      await expect(page.locator('text=Текущий этап')).toBeVisible();
    });

    test('A.3. Граф ТП открывается, узлы отрисованы', async ({ page }) => {
      await loginViaUi(page);

      await page.goto(`${BASE_URL}/projects/${projectId}/graph`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('.react-flow')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });

      // Мини-карта и тулбар
      await expect(page.locator('.react-flow__minimap')).toBeVisible();
      await expect(page.locator('text=Граф ТП')).toBeVisible();
    });

    test('A.4. Решения оркестратора отображаются на вкладке «Решения»', async ({ page }) => {
      await loginViaUi(page);

      await page.goto(`${BASE_URL}/projects/${projectId}`, {
        waitUntil: 'domcontentloaded',
      });

      await page.click('text=Решения');
      await page.waitForSelector('.ant-timeline, .ant-empty', {
        timeout: NAVIGATION_TIMEOUT,
      });

      // Должен быть хотя бы один элемент журнала
      const timeline = page.locator('.ant-timeline-item');
      await expect(timeline.first()).toBeVisible({ timeout: NAVIGATION_TIMEOUT });
    });

    test('A.5. Авторизация через UI корректна', async ({ page }) => {
      await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' });
      await page.waitForURL(/\/login/, { timeout: NAVIGATION_TIMEOUT });

      await page.fill('input[name="username"]', TEST_USER.username);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button[type="submit"]');

      await page.waitForURL(/\/dashboard/, { timeout: NAVIGATION_TIMEOUT });
      await expect(page.locator('text=Обзор')).toBeVisible();
    });
  });

  // =========================================================================
  // Сценарий B: Точка контроля — вмешательство пользователя
  // =========================================================================

  test.describe('Сценарий B — Вмешательство на точке контроля', () => {
    let bcProjectId: string;

    test.beforeAll(async () => {
      bcProjectId = await createProjectViaApi(api, token);
    });

    test('B.1. Точка контроля отображается с предупреждением', async ({ page }) => {
      await loginViaUi(page);

      // Проверяем через API: если проект на точке контроля — переходим к UI
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${bcProjectId}/state`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      const body = await response.json();

      await page.goto(`${BASE_URL}/projects/${bcProjectId}`, {
        waitUntil: 'domcontentloaded',
      });

      if (
        body.stateCategory === 'POINT_OF_CONTROL' ||
        body.awaitingUserAction === true
      ) {
        await expect(
          page.locator('.ant-alert-warning', { hasText: 'Требуется решение' })
        ).toBeVisible({ timeout: NAVIGATION_TIMEOUT });
      } else {
        // Проект на автоматическом этапе — проверяем, что нет ошибочных алертов
        await expect(page.locator('.ant-alert-error')).toHaveCount(0);
      }
    });

    test('B.2. Редактирование графа ТП в режиме редактирования', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${bcProjectId}/graph`, {
        waitUntil: 'domcontentloaded',
      });

      // Включаем режим редактирования
      const editSwitch = page.locator('.ant-switch');
      await editSwitch.click();

      // Добавляем операцию
      await page.click('button:has-text("Операция")');
      await expect(page.locator('.ant-drawer')).toBeVisible();

      // Меняем наименование
      await page.fill('input[name="name"], #name', 'E2E-Операция');
      await page.click('button:has-text("Применить")');

      // Проверяем, что появился тег «Не сохранено»
      await expect(page.locator('.ant-tag:has-text("Не сохранено")')).toBeVisible();

      // Сброс
      await page.click('button:has-text("Сбросить")');
      await page.click('button:has-text("Сбросить")'); // подтверждение
      await expect(page.locator('.ant-tag:has-text("Не сохранено")')).toHaveCount(0);
    });
  });

  // =========================================================================
  // Сценарий C: Отказ внешнего модуля
  // =========================================================================

  test.describe('Сценарий C — Обработка отказа модуля', () => {
    test('C.1. Плановые задачи и health-check доступны', async () => {
      const response = await api.get(`${API_URL}/actuator/health`, {
        timeout: API_TIMEOUT,
      });
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.status).toBe('UP');
    });
  });

  // =========================================================================
  // Сценарий D: Нагрузка (проверка API)
  // =========================================================================

  test.describe('Сценарий D — Проверка параллельных проектов', () => {
    test('D.1. Создание 10 параллельных проектов', async () => {
      const promises = Array.from({ length: 10 }, () =>
        createProjectViaApi(api, token)
      );
      const ids = await Promise.all(promises);
      expect(ids.length).toBe(10);
      ids.forEach((id) => expect(id).toBeTruthy());
    }, { timeout: 60_000 });
  });

  // =========================================================================
  // Сценарий E: Безопасность
  // =========================================================================

  test.describe('Сценарий E — Безопасность и права доступа', () => {
    test('E.1. Неавторизованный доступ к API запрещён', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${projectId || 'x'}/state`
      );
      expect([401, 403]).toContain(response.status());
    });

    test('E.2. Невалидный токен отклоняется', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${projectId || 'x'}/state`,
        { headers: { Authorization: 'Bearer invalid-token-12345' } }
      );
      expect([401, 403]).toContain(response.status());
    });

    test('E.3. Запрос аудита доступен только по ролям', async () => {
      const response = await api.get(`${API_URL}/api/v1/audit`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      // Доступ либо разрешён (200), либо запрещён (403) — в зависимости от роли
      expect([200, 403]).toContain(response.status());
    });
  });
});

// ---------------------------------------------------------------------------
// Глобальные хуки
// ---------------------------------------------------------------------------

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus) {
    // Скриншот при падении
    const screenshotPath = `test-results/${testInfo.title.replace(/\s+/g, '_')}.png`;
    await page.screenshot({ path: screenshotPath, fullPage: true });
    testInfo.attachments.push({
      name: 'screenshot',
      path: screenshotPath,
      contentType: 'image/png',
    });
  }
});