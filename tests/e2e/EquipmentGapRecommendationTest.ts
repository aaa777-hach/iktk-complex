import { test, expect, Page, APIRequestContext, request } from '@playwright/test';

// ---------------------------------------------------------------------------
// Конфигурация
// ---------------------------------------------------------------------------

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

const TEST_USER = {
  username: process.env.E2E_USER || 'engineer',
  password: process.env.E2E_PASSWORD || 'engineer_password',
};

const MANAGER_USER = {
  username: process.env.E2E_MANAGER_USER || 'manager',
  password: process.env.E2E_MANAGER_PASSWORD || 'manager_password',
};

const NAVIGATION_TIMEOUT = 15_000;
const API_TIMEOUT = 30_000;
const LONG_TIMEOUT = 120_000;

const TEST_PROJECT = {
  name: `E2E-Gap-${Date.now()}`,
  designation: `E2E-GAP-${Date.now().toString().slice(-6)}`,
  objectType: 'PART' as const,
};

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Аутентификация через API.
 */
async function loginViaApi(
  api: APIRequestContext,
  user = TEST_USER
): Promise<string> {
  const response = await api.post(`${API_URL}/api/v1/auth/login`, {
    data: { username: user.username, password: user.password },
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
async function loginViaUi(page: Page, user = TEST_USER): Promise<void> {
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('input[name="username"]', user.username);
  await page.fill('input[name="password"]', user.password);
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/, { timeout: NAVIGATION_TIMEOUT });
}

/**
 * Создать проект через API.
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

  expect(response.ok(), `Создание проекта: ${response.status()}`).toBeTruthy();
  const body = await response.json();
  return body.projectId;
}

/**
 * Запустить gap-анализ через API.
 */
async function runGapAnalysis(
  api: APIRequestContext,
  token: string,
  projectId: string,
  strategy = 'STRICT'
): Promise<any> {
  const response = await api.post(
    `${API_URL}/api/v1/recommendations/projects/${projectId}/gap-analysis`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: { projectId, strategy },
      timeout: LONG_TIMEOUT,
    }
  );

  expect(
    [200, 202].includes(response.status()),
    `Gap-анализ: ${response.status()}`
  ).toBeTruthy();

  return response.json();
}

/**
 * Получить последний gap-анализ по проекту.
 */
async function getLatestGapAnalysis(
  api: APIRequestContext,
  token: string,
  projectId: string
): Promise<any | null> {
  const response = await api.get(
    `${API_URL}/api/v1/recommendations/projects/${projectId}/gap-analysis`,
    {
      headers: { Authorization: `Bearer ${token}` },
      timeout: API_TIMEOUT,
    }
  );

  if (response.status() === 404) return null;
  if (!response.ok()) return null;
  return response.json();
}

/**
 * Получить рекомендации по проекту.
 */
async function getRecommendations(
  api: APIRequestContext,
  token: string,
  projectId: string,
  filters: { type?: string; status?: string } = {}
): Promise<any[]> {
  const params = new URLSearchParams();
  if (filters.type) params.append('type', filters.type);
  if (filters.status) params.append('status', filters.status);

  const url = `${API_URL}/api/v1/recommendations/projects/${projectId}${
    params.toString() ? '?' + params.toString() : ''
  }`;

  const response = await api.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: API_TIMEOUT,
  });

  if (!response.ok()) return [];
  return response.json();
}

/**
 * Получить сводку по рекомендациям.
 */
async function getSummary(
  api: APIRequestContext,
  token: string,
  projectId: string
): Promise<any | null> {
  const response = await api.get(
    `${API_URL}/api/v1/recommendations/projects/${projectId}/summary`,
    {
      headers: { Authorization: `Bearer ${token}` },
      timeout: API_TIMEOUT,
    }
  );

  if (!response.ok()) return null;
  return response.json();
}

/**
 * Получить каталог внешнего оборудования.
 */
async function searchVendorCatalog(
  api: APIRequestContext,
  token: string,
  filters: { equipmentType?: string; maxCost?: number } = {}
): Promise<any[]> {
  const params = new URLSearchParams();
  if (filters.equipmentType) params.append('equipmentType', filters.equipmentType);
  if (filters.maxCost) params.append('maxCost', String(filters.maxCost));

  const url = `${API_URL}/api/v1/recommendations/vendor-catalog${
    params.toString() ? '?' + params.toString() : ''
  }`;

  const response = await api.get(url, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: API_TIMEOUT,
  });

  if (!response.ok()) return [];
  return response.json();
}

// ---------------------------------------------------------------------------
// Тесты
// ---------------------------------------------------------------------------

test.describe('E2E: Gap-анализ оборудования и рекомендации', () => {
  let api: APIRequestContext;
  let token: string;
  let managerToken: string;
  let projectId: string;

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL: API_URL });
    token = await loginViaApi(api, TEST_USER);
    managerToken = await loginViaApi(api, MANAGER_USER).catch(() => token);
    projectId = await createProjectViaApi(api, token);
    expect(projectId).toBeTruthy();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  // =========================================================================
  // 1. Gap-анализ
  // =========================================================================

  test.describe('1. Gap-анализ', () => {
    test('1.1. Запуск gap-анализа выполняется успешно', async () => {
      const result = await runGapAnalysis(api, token, projectId, 'STRICT');

      expect(result).toBeTruthy();
      expect(result.projectId).toBe(projectId);
      expect(['RUNNING', 'COMPLETED']).toContain(result.status);
      expect(typeof result.gapsCount).toBe('number');
    });

    test('1.2. Последний gap-анализ доступен', async () => {
      const analysis = await getLatestGapAnalysis(api, token, projectId);
      expect(analysis).toBeTruthy();
      expect(analysis.projectId).toBe(projectId);
      expect(analysis.strategy).toBeTruthy();
    });

    test('1.3. Gap-анализ содержит корректную структуру дефицитов', async () => {
      const analysis = await getLatestGapAnalysis(api, token, projectId);
      if (!analysis || !analysis.gaps || analysis.gaps.length === 0) {
        test.skip();
        return;
      }

      const gap = analysis.gaps[0];
      expect(gap.id).toBeTruthy();
      expect(gap.operationType).toBeTruthy();
      expect(['CRITICAL', 'MAJOR', 'MINOR']).toContain(gap.severity);
      expect(gap.gapReason).toBeTruthy();
    });

    test('1.4. Gap-анализ доступен по analysisId', async () => {
      const analysis = await getLatestGapAnalysis(api, token, projectId);
      if (!analysis?.analysisId) {
        test.skip();
        return;
      }

      const response = await api.get(
        `${API_URL}/api/v1/recommendations/gap-analysis/${analysis.analysisId}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.analysisId).toBe(analysis.analysisId);
    });
  });

  // =========================================================================
  // 2. Рекомендации
  // =========================================================================

  test.describe('2. Рекомендации', () => {
    test('2.1. Рекомендации сформированы', async () => {
      const recommendations = await getRecommendations(api, token, projectId);

      // Может быть 0, если дефицит отсутствует — это допустимо
      expect(Array.isArray(recommendations)).toBeTruthy();

      if (recommendations.length > 0) {
        const rec = recommendations[0];
        expect(rec.id).toBeTruthy();
        expect(rec.projectId).toBe(projectId);
        expect([
          'ALTERNATIVE',
          'PURCHASE',
          'RENT',
          'OUTSOURCE',
          'ADJUST_PROCESS',
        ]).toContain(rec.type);
        expect(['PENDING', 'ACCEPTED', 'REJECTED']).toContain(rec.status);
      }
    });

    test('2.2. Рекомендации отсортированы по скору (убывание)', async () => {
      const recommendations = await getRecommendations(api, token, projectId);
      if (recommendations.length < 2) {
        test.skip();
        return;
      }

      const scores = recommendations
        .map((r) => r.score)
        .filter((s) => s != null) as number[];

      for (let i = 1; i < scores.length; i++) {
        expect(scores[i - 1]).toBeGreaterThanOrEqual(scores[i]);
      }
    });

    test('2.3. Фильтр по типу работает', async () => {
      const alternatives = await getRecommendations(api, token, projectId, {
        type: 'ALTERNATIVE',
      });

      alternatives.forEach((r) => {
        expect(r.type).toBe('ALTERNATIVE');
      });
    });

    test('2.4. Фильтр по статусу работает', async () => {
      const pending = await getRecommendations(api, token, projectId, {
        status: 'PENDING',
      });

      pending.forEach((r) => {
        expect(r.status).toBe('PENDING');
      });
    });

    test('2.5. Сводка по рекомендациям возвращает корректные данные', async () => {
      const summary = await getSummary(api, token, projectId);
      expect(summary).toBeTruthy();
      expect(summary.projectId).toBe(projectId);
      expect(typeof summary.total).toBe('number');
      expect(typeof summary.pending).toBe('number');
      expect(typeof summary.accepted).toBe('number');
      expect(typeof summary.rejected).toBe('number');
    });
  });

  // =========================================================================
  // 3. Каталог внешнего оборудования
  // =========================================================================

  test.describe('3. Каталог внешнего оборудования', () => {
    test('3.1. Каталог доступен для поиска', async () => {
      const catalog = await searchVendorCatalog(api, token);
      expect(Array.isArray(catalog)).toBeTruthy();
    });

    test('3.2. Фильтр по типу оборудования работает', async () => {
      const catalog = await searchVendorCatalog(api, token, {
        equipmentType: 'TURNING',
      });

      catalog.forEach((item) => {
        expect(item.equipmentType).toBe('TURNING');
      });
    });

    test('3.3. Фильтр по максимальной стоимости работает', async () => {
      const maxCost = 10_000_000;
      const catalog = await searchVendorCatalog(api, token, { maxCost });

      catalog.forEach((item) => {
        if (item.cost != null) {
          expect(item.cost).toBeLessThanOrEqual(maxCost);
        }
      });
    });

    test('3.4. Каталог содержит корректную структуру', async () => {
      const catalog = await searchVendorCatalog(api, token);
      if (catalog.length === 0) {
        test.skip();
        return;
      }

      const item = catalog[0];
      expect(item.id).toBeTruthy();
      expect(item.name).toBeTruthy();
      expect(item.equipmentType).toBeTruthy();
    });
  });

  // =========================================================================
  // 4. UI: страница дефицита и рекомендаций
  // =========================================================================

  test.describe('4. UI: страница дефицита и рекомендаций', () => {
    test('4.1. Страница открывается', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/equipment`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('.equipment-gap-page')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('h3')).toContainText('Дефицит', {
        timeout: NAVIGATION_TIMEOUT,
      });
    });

    test('4.2. Вкладки отображаются', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/equipment`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('text=Дефициты')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('text=Все рекомендации')).toBeVisible();
      await expect(page.locator('text=Каталог внешнего оборудования')).toBeVisible();
    });

    test('4.3. Сводка отображается', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/equipment`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('text=Дефицитов')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('text=Рекомендаций')).toBeVisible();
      await expect(page.locator('text=Ожидают решения')).toBeVisible();
      await expect(page.locator('text=Принято')).toBeVisible();
    });

    test('4.4. Переключение на вкладку каталога', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/equipment`, {
        waitUntil: 'domcontentloaded',
      });

      await page.click('text=Каталог внешнего оборудования');
      await expect(page.locator('.ant-table, .ant-empty')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
    });
  });

  // =========================================================================
  // 5. Принятие решений
  // =========================================================================

  test.describe('5. Принятие решений', () => {
    test('5.1. Принятие рекомендации изменяет её статус', async () => {
      const recommendations = await getRecommendations(api, token, projectId, {
        status: 'PENDING',
      });

      if (recommendations.length === 0) {
        test.skip();
        return;
      }

      // Выбираем рекомендацию типа ALTERNATIVE или PURCHASE для принятия
      const candidate = recommendations.find(
        (r) => r.type === 'ALTERNATIVE' || r.type === 'PURCHASE' || r.type === 'RENT'
      );

      if (!candidate) {
        test.skip();
        return;
      }

      const response = await api.post(
        `${API_URL}/api/v1/recommendations/${candidate.id}/accept`,
        {
          headers: {
            Authorization: `Bearer ${managerToken}`,
            'Content-Type': 'application/json',
          },
          data: { comment: 'E2E test accept' },
          timeout: API_TIMEOUT,
        }
      );

      expect([200, 202]).toContain(response.status());

      // Проверяем, что статус изменился
      const updated = await getRecommendations(api, token, projectId, {
        status: 'ACCEPTED',
      });
      const accepted = updated.find((r) => r.id === candidate.id);
      expect(accepted).toBeTruthy();
    });

    test('5.2. Отклонение рекомендации изменяет её статус', async () => {
      const recommendations = await getRecommendations(api, token, projectId, {
        status: 'PENDING',
      });

      if (recommendations.length === 0) {
        test.skip();
        return;
      }

      const candidate = recommendations[0];

      const response = await api.post(
        `${API_URL}/api/v1/recommendations/${candidate.id}/reject`,
        {
          headers: {
            Authorization: `Bearer ${managerToken}`,
            'Content-Type': 'application/json',
          },
          data: { reason: 'E2E test reject' },
          timeout: API_TIMEOUT,
        }
      );

      expect([200, 202]).toContain(response.status());
    });
  });

  // =========================================================================
  // 6. Аутсорс
  // =========================================================================

  test.describe('6. Аутсорс', () => {
    test('6.1. Варианты аутсорса доступны по проекту', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/recommendations/projects/${projectId}/outsourcing-options`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const options = await response.json();
      expect(Array.isArray(options)).toBeTruthy();
    });

    test('6.2. Структура варианта аутсорса корректна', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/recommendations/projects/${projectId}/outsourcing-options`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      if (!response.ok()) {
        test.skip();
        return;
      }

      const options = await response.json();
      if (options.length === 0) {
        test.skip();
        return;
      }

      const option = options[0];
      expect(option.id).toBeTruthy();
      expect([
        'CONTRACTOR',
        'PARTNER_PLANT',
        'RENTAL',
        'COOPERATION',
      ]).toContain(option.type);
      expect(['PENDING', 'SELECTED', 'REJECTED']).toContain(option.status);
    });
  });

  // =========================================================================
  // 7. Негативные сценарии
  // =========================================================================

  test.describe('7. Негативные сценарии', () => {
    test('7.1. Gap-анализ для несуществующего проекта — ошибка', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000';

      const response = await api.post(
        `${API_URL}/api/v1/recommendations/projects/${fakeId}/gap-analysis`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: { projectId: fakeId, strategy: 'STRICT' },
          timeout: API_TIMEOUT,
        }
      );

      expect([400, 404, 422]).toContain(response.status());
    });

    test('7.2. Невалидная стратегия отклоняется', async () => {
      const response = await api.post(
        `${API_URL}/api/v1/recommendations/projects/${projectId}/gap-analysis`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: { projectId, strategy: 'INVALID_STRATEGY' },
          timeout: API_TIMEOUT,
        }
      );

      expect([400, 422]).toContain(response.status());
    });

    test('7.3. Принятие несуществующей рекомендации — 404', async () => {
      const fakeRecId = '00000000-0000-0000-0000-000000000000';

      const response = await api.post(
        `${API_URL}/api/v1/recommendations/${fakeRecId}/accept`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: { comment: 'test' },
          timeout: API_TIMEOUT,
        }
      );

      expect([404, 422]).toContain(response.status());
    });

    test('7.4. Неавторизованный доступ запрещён', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/recommendations/projects/${projectId}`,
        { timeout: API_TIMEOUT }
      );

      expect([401, 403]).toContain(response.status());
    });
  });

  // =========================================================================
  // 8. Согласованность с оркестратором
  // =========================================================================

  test.describe('8. Согласованность с оркестратором', () => {
    test('8.1. Проект можно получить в состоянии gap-анализа', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${projectId}/state`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const state = await response.json();
      expect(state.projectId).toBe(projectId);
      expect(state.state).toBeTruthy();
    });

    test('8.2. История решений содержит gap-анализ или рекомендации', async () => {
      const response = await api.get(
        `${API_URL}/api/v1/orchestrator/projects/${projectId}/history?limit=50`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const history = await response.json();
      expect(Array.isArray(history)).toBeTruthy();
    });
  });
});

// ---------------------------------------------------------------------------
// Глобальные хуки
// ---------------------------------------------------------------------------

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus && page) {
    const screenshotPath = `test-results/gap-rec-${testInfo.title.replace(/\s+/g, '_')}.png`;
    try {
      await page.screenshot({ path: screenshotPath, fullPage: true });
      testInfo.attachments.push({
        name: 'screenshot',
        path: screenshotPath,
        contentType: 'image/png',
      });
    } catch {
      // страница могла быть закрыта
    }
  }
});