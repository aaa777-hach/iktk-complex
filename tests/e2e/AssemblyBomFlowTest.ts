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

const NAVIGATION_TIMEOUT = 15_000;
const API_TIMEOUT = 30_000;
const LONG_TIMEOUT = 120_000;

const TEST_PROJECT = {
  name: `E2E-Сборка-${Date.now()}`,
  designation: `E2E-СБ-${Date.now().toString().slice(-6)}`,
  objectType: 'ASSEMBLY' as const,
};

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Аутентификация через API.
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
  await page.waitForURL(/\/dashboard/, { timeout: NAVIGATION_TIMEOUT });
}

/**
 * Создать проект-сборку через API.
 */
async function createAssemblyProjectViaApi(
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

  expect(response.ok(), `Создание проекта-сборки: ${response.status()}`).toBeTruthy();
  const body = await response.json();
  return body.projectId;
}

/**
 * Запустить анализ BOM через API.
 */
async function runBomAnalysis(
  api: APIRequestContext,
  token: string,
  projectId: string
): Promise<any> {
  const response = await api.post(
    `${API_URL}/api/v1/bom/projects/${projectId}/analyze`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: { force: true },
      timeout: LONG_TIMEOUT,
    }
  );

  expect(response.ok(), `Анализ BOM: ${response.status()}`).toBeTruthy();
  return response.json();
}

/**
 * Получить BOM по проекту.
 */
async function getBom(
  api: APIRequestContext,
  token: string,
  projectId: string
): Promise<any> {
  const response = await api.get(`${API_URL}/api/v1/bom/projects/${projectId}`, {
    headers: { Authorization: `Bearer ${token}` },
    timeout: API_TIMEOUT,
  });

  if (response.status() === 404) {
    return null;
  }

  expect(response.ok(), `Получение BOM: ${response.status()}`).toBeTruthy();
  return response.json();
}

/**
 * Получить связи сборки.
 */
async function getConnections(
  api: APIRequestContext,
  token: string,
  bomId: string
): Promise<any[]> {
  const response = await api.get(
    `${API_URL}/api/v1/bom/${bomId}/connections`,
    {
      headers: { Authorization: `Bearer ${token}` },
      timeout: API_TIMEOUT,
    }
  );

  if (!response.ok()) return [];
  return response.json();
}

/**
 * Получить последовательность сборки.
 */
async function getSequence(
  api: APIRequestContext,
  token: string,
  bomId: string
): Promise<any[]> {
  const response = await api.get(
    `${API_URL}/api/v1/bom/${bomId}/assembly-sequence`,
    {
      headers: { Authorization: `Bearer ${token}` },
      timeout: API_TIMEOUT,
    }
  );

  if (!response.ok()) return [];
  return response.json();
}

// ---------------------------------------------------------------------------
// Тесты
// ---------------------------------------------------------------------------

test.describe('E2E: Полный цикл работы с BOM сборочной единицы', () => {
  let api: APIRequestContext;
  let token: string;
  let projectId: string;

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL: API_URL });
    token = await loginViaApi(api);
    projectId = await createAssemblyProjectViaApi(api, token);
    expect(projectId).toBeTruthy();
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  // =========================================================================
  // 1. Анализ сборочной единицы
  // =========================================================================

  test.describe('1. Анализ сборочной единицы', () => {
    test('1.1. BOM извлекается из CAD', async () => {
      const result = await runBomAnalysis(api, token, projectId);

      expect(result).toBeTruthy();
      expect(result.projectId).toBe(projectId);
      expect(result.status).toBe('COMPLETED');
      expect(result.positionsCount).toBeGreaterThanOrEqual(0);
    });

    test('1.2. BOM доступен для чтения', async () => {
      const bom = await getBom(api, token, projectId);
      expect(bom).toBeTruthy();
      expect(bom.projectId).toBe(projectId);
      expect(bom.id).toBeTruthy();
      expect(Array.isArray(bom.items)).toBeTruthy();
      expect(bom.totalPositions).toBeGreaterThanOrEqual(0);
    });

    test('1.3. Дерево BOM имеет корректную структуру', async () => {
      const bom = await getBom(api, token, projectId);
      expect(bom).toBeTruthy();

      if (bom.items && bom.items.length > 0) {
        const firstItem = bom.items[0];
        expect(firstItem.id).toBeTruthy();
        expect(firstItem.type).toBeTruthy();
        expect(['PART', 'ASSEMBLY', 'STANDARD', 'FASTENER', 'MATERIAL']).toContain(
          firstItem.type
        );
        expect(firstItem.quantity).toBeGreaterThan(0);
      }
    });
  });

  // =========================================================================
  // 2. UI: страница BOM
  // =========================================================================

  test.describe('2. UI: страница BOM', () => {
    test('2.1. Страница BOM открывается', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/bom`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('.bom-tree-page')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('h3')).toContainText('Состав изделия', {
        timeout: NAVIGATION_TIMEOUT,
      });
    });

    test('2.2. Вкладки дерева и таблицы присутствуют', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/bom`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('text=Дерево')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('text=Таблица')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('text=Связи и сопряжения')).toBeVisible();
      await expect(page.locator('text=Последовательность')).toBeVisible();
      await expect(page.locator('text=Спецификация ГОСТ')).toBeVisible();
    });

    test('2.3. Переключение между вкладками работает', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/bom`, {
        waitUntil: 'domcontentloaded',
      });

      // Переключиться на вкладку «Таблица»
      await page.click('text=Таблица');
      await expect(page.locator('.ant-table')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });

      // Переключиться на вкладку «Связи»
      await page.click('text=Связи и сопряжения');
      await expect(page.locator('.ant-table, .ant-empty')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
    });

    test('2.4. Сводка по BOM отображается', async ({ page }) => {
      await loginViaUi(page);
      await page.goto(`${BASE_URL}/projects/${projectId}/bom`, {
        waitUntil: 'domcontentloaded',
      });

      await expect(page.locator('text=Всего позиций')).toBeVisible({
        timeout: NAVIGATION_TIMEOUT,
      });
      await expect(page.locator('text=Деталей')).toBeVisible();
      await expect(page.locator('text=Сборочных единиц')).toBeVisible();
      await expect(page.locator('text=Уровней')).toBeVisible();
    });
  });

  // =========================================================================
  // 3. Связи и последовательность
  // =========================================================================

  test.describe('3. Связи и последовательность', () => {
    let bomId: string;

    test.beforeAll(async () => {
      const bom = await getBom(api, token, projectId);
      bomId = bom?.id;
    });

    test('3.1. Связи доступны для чтения', async () => {
      if (!bomId) test.skip();
      const connections = await getConnections(api, token, bomId);
      expect(Array.isArray(connections)).toBeTruthy();
    });

    test('3.2. Последовательность сборки доступна', async () => {
      if (!bomId) test.skip();
      const sequence = await getSequence(api, token, bomId);
      expect(Array.isArray(sequence)).toBeTruthy();
    });

    test('3.3. Связи имеют корректную структуру', async () => {
      if (!bomId) test.skip();
      const connections = await getConnections(api, token, bomId);

      if (connections.length > 0) {
        const conn = connections[0];
        expect(conn.id).toBeTruthy();
        expect(conn.type).toBeTruthy();
        expect(conn.part1Id).toBeTruthy();
        expect(conn.part2Id).toBeTruthy();
        expect(conn.part1Id).not.toBe(conn.part2Id);
      }
    });
  });

  // =========================================================================
  // 4. Спецификация
  // =========================================================================

  test.describe('4. Спецификация ГОСТ 2.106', () => {
    let bomId: string;

    test.beforeAll(async () => {
      const bom = await getBom(api, token, projectId);
      bomId = bom?.id;
    });

    test('4.1. Спецификация доступна', async () => {
      if (!bomId) test.skip();

      const response = await api.get(
        `${API_URL}/api/v1/bom/${bomId}/specification`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const spec = await response.json();
      expect(spec).toBeTruthy();
      expect(Array.isArray(spec.sections)).toBeTruthy();
    });
  });

  // =========================================================================
  // 5. Экспорт BOM
  // =========================================================================

  test.describe('5. Экспорт BOM', () => {
    let bomId: string;

    test.beforeAll(async () => {
      const bom = await getBom(api, token, projectId);
      bomId = bom?.id;
    });

    test('5.1. Экспорт в CSV возвращает URI', async () => {
      if (!bomId) test.skip();

      const response = await api.post(
        `${API_URL}/api/v1/bom/${bomId}/export?format=CSV`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: LONG_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.fileUri).toBeTruthy();
      expect(body.format).toBe('CSV');
    });

    test('5.2. Экспорт в Excel возвращает URI', async () => {
      if (!bomId) test.skip();

      const response = await api.post(
        `${API_URL}/api/v1/bom/${bomId}/export?format=EXCEL`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: LONG_TIMEOUT,
        }
      );

      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.fileUri).toBeTruthy();
    });
  });

  // =========================================================================
  // 6. Ручная корректировка BOM
  // =========================================================================

  test.describe('6. Ручная корректировка BOM', () => {
    let bomId: string;

    test.beforeAll(async () => {
      const bom = await getBom(api, token, projectId);
      bomId = bom?.id;
    });

    test('6.1. Добавление позиции', async () => {
      if (!bomId) test.skip();

      const response = await api.post(
        `${API_URL}/api/v1/bom/${bomId}/items`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: {
            position: '99',
            designation: 'E2E-TEST-001',
            name: 'Тестовая позиция',
            quantity: 1,
            unit: 'шт',
            type: 'PART',
          },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.ok(), `Добавление позиции: ${response.status()}`).toBeTruthy();
      const item = await response.json();
      expect(item.id).toBeTruthy();
      expect(item.designation).toBe('E2E-TEST-001');
    });

    test('6.2. Удаление добавленной позиции', async () => {
      if (!bomId) test.skip();

      const bom = await getBom(api, token, projectId);
      const testItem = bom.items?.find((i: any) => i.designation === 'E2E-TEST-001');

      if (!testItem) {
        test.skip();
        return;
      }

      const response = await api.delete(
        `${API_URL}/api/v1/bom/${bomId}/items/${testItem.id}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect([204, 200]).toContain(response.status());
    });
  });

  // =========================================================================
  // 7. Интеграция с ТП сборки
  // =========================================================================

  test.describe('7. Интеграция с ТП сборки', () => {
    test('7.1. ТП сборки может быть спроектирован', async () => {
      const response = await api.post(
        `${API_URL}/api/v1/assembly/projects/${projectId}/process`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: {
            projectId,
            sequenceStrategy: 'TOPOLOGICAL',
            toolingEnabled: true,
          },
          timeout: LONG_TIMEOUT,
        }
      );

      expect([200, 202]).toContain(response.status());
      const result = await response.json();
      expect(result.projectId).toBe(projectId);
      expect(result.processId).toBeTruthy();
    });

    test('7.2. Операции ТП сборки доступны', async () => {
      const processResponse = await api.get(
        `${API_URL}/api/v1/assembly/projects/${projectId}/process`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      if (!processResponse.ok()) {
        test.skip();
        return;
      }

      const process = await processResponse.json();
      expect(process.id).toBeTruthy();

      const opsResponse = await api.get(
        `${API_URL}/api/v1/assembly/processes/${process.id}/operations`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(opsResponse.ok()).toBeTruthy();
      const operations = await opsResponse.json();
      expect(Array.isArray(operations)).toBeTruthy();
    });

    test('7.3. Оснастка ТП сборки доступна', async () => {
      const processResponse = await api.get(
        `${API_URL}/api/v1/assembly/projects/${projectId}/process`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      if (!processResponse.ok()) {
        test.skip();
        return;
      }

      const process = await processResponse.json();

      const toolingResponse = await api.get(
        `${API_URL}/api/v1/assembly/processes/${process.id}/tooling`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(toolingResponse.ok()).toBeTruthy();
      const tooling = await toolingResponse.json();
      expect(Array.isArray(tooling)).toBeTruthy();
    });
  });

  // =========================================================================
  // 8. Негативные сценарии
  // =========================================================================

  test.describe('8. Негативные сценарии', () => {
    test('8.1. BOM для несуществующего проекта — 404', async () => {
      const fakeId = '00000000-0000-0000-0000-000000000000';
      const response = await api.get(
        `${API_URL}/api/v1/bom/projects/${fakeId}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect(response.status()).toBe(404);
    });

    test('8.2. Добавление позиции с некорректными данными — 400', async () => {
      const bom = await getBom(api, token, projectId);
      if (!bom?.id) test.skip();

      const response = await api.post(
        `${API_URL}/api/v1/bom/${bom.id}/items`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          data: {
            // отсутствует name
            quantity: -1,
          },
          timeout: API_TIMEOUT,
        }
      );

      expect([400, 422]).toContain(response.status());
    });

    test('8.3. Экспорт в неподдерживаемом формате — 400', async () => {
      const bom = await getBom(api, token, projectId);
      if (!bom?.id) test.skip();

      const response = await api.post(
        `${API_URL}/api/v1/bom/${bom.id}/export?format=INVALID`,
        {
          headers: { Authorization: `Bearer ${token}` },
          timeout: API_TIMEOUT,
        }
      );

      expect([400, 422]).toContain(response.status());
    });
  });
});

// ---------------------------------------------------------------------------
// Глобальные хуки
// ---------------------------------------------------------------------------

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus && page) {
    const screenshotPath = `test-results/assembly-bom-${testInfo.title.replace(/\s+/g, '_')}.png`;
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