/**
 * =============================================================================
 * ИКТК: нагрузочный тест BOM-сервиса (k6)
 * Путь: tests/load/BomLoadTest.js
 * Назначение: проверка производительности, устойчивости и масштабируемости
 *             сервиса BOM (состав изделия) под нагрузкой.
 *
 * Сценарии:
 *   1. smoke             — минимальная нагрузка (проверка работоспособности);
 *   2. read_load         — нагрузка на чтение (BOM, дерево, связи);
 *   3. analyze_load      — нагрузка на анализ BOM (извлечение из CAD);
 *   4. heavy_bom         — большие сборки (1000+ позиций);
 *   5. mixed_workload    — смешанная нагрузка (чтение + запись);
 *   6. parallel_projects — параллельные проекты.
 *
 * Запуск:
 *   k6 run tests/load/BomLoadTest.js
 *   k6 run --env SCENARIO=analyze_load tests/load/BomLoadTest.js
 *   k6 run --out influxdb=http://localhost:8086/k6 tests/load/BomLoadTest.js
 * =============================================================================
 */

import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';
import { randomString, randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

// -----------------------------------------------------------------------------
// Конфигурация
// -----------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOM_PREFIX = '/api/v1/bom';
const ORCH_PREFIX = '/api/v1/orchestrator';
const AUTH_PREFIX = '/api/v1/auth';

const TEST_USER = {
  username: __ENV.TEST_USER || 'engineer',
  password: __ENV.TEST_PASSWORD || 'engineer_password',
};

const SCENARIO = __ENV.SCENARIO || 'read_load';

/** Таймауты (мс). */
const TIMEOUT_SHORT = 5_000;
const TIMEOUT_NORMAL = 30_000;
const TIMEOUT_LONG = 180_000;

/** Количество проектов, подготавливаемых в setup. */
const SETUP_PROJECTS = Number(__ENV.SETUP_PROJECTS || 20);

// -----------------------------------------------------------------------------
// Пользовательские метрики
// -----------------------------------------------------------------------------

const bomAnalysisDuration = new Trend('iktk_bom_analysis_duration', true);
const bomReadDuration = new Trend('iktk_bom_read_duration', true);
const bomTreeDuration = new Trend('iktk_bom_tree_duration', true);
const bomConnectionsDuration = new Trend('iktk_bom_connections_duration', true);
const bomSequenceDuration = new Trend('iktk_bom_sequence_duration', true);
const bomItemsAddDuration = new Trend('iktk_bom_items_add_duration', true);
const bomExportDuration = new Trend('iktk_bom_export_duration', true);

const bomErrors = new Counter('iktk_bom_errors');
const bomAnalysisErrors = new Counter('iktk_bom_analysis_errors');
const bomReadErrors = new Counter('iktk_bom_read_errors');
const bomRateLimitHits = new Counter('iktk_bom_rate_limit_hits');

const bomAnalysisSuccessRate = new Rate('iktk_bom_analysis_success_rate');
const bomReadSuccessRate = new Rate('iktk_bom_read_success_rate');
const bomTreeSuccessRate = new Rate('iktk_bom_tree_success_rate');

const activeBoms = new Gauge('iktk_active_boms');
const totalPositions = new Gauge('iktk_bom_total_positions');

// -----------------------------------------------------------------------------
// Опции
// -----------------------------------------------------------------------------

export const options = buildOptions();

/**
 * Построить опции k6 в зависимости от выбранного сценария.
 */
function buildOptions() {
  const commonThresholds = {
    http_req_duration: ['p(95)<3000', 'p(99)<8000'],
    http_req_failed: ['rate<0.02'],
    iktk_bom_analysis_success_rate: ['rate>0.95'],
    iktk_bom_read_success_rate: ['rate>0.99'],
    iktk_bom_tree_success_rate: ['rate>0.98'],
    iktk_bom_analysis_duration: ['p(95)<30000'],
    iktk_bom_read_duration: ['p(95)<1500'],
    iktk_bom_tree_duration: ['p(95)<3000'],
  };

  switch (SCENARIO) {
    case 'smoke':
      return {
        vus: 1,
        duration: '30s',
        thresholds: commonThresholds,
        tags: { scenario: 'smoke' },
      };

    case 'analyze_load':
      return {
        stages: [
          { duration: '1m', target: 5 },
          { duration: '3m', target: 10 },
          { duration: '1m', target: 10 },
          { duration: '1m', target: 0 },
        ],
        thresholds: {
          ...commonThresholds,
          // Анализ BOM — тяжёлая операция, допускаем более длинные ответы
          iktk_bom_analysis_duration: ['p(95)<60000'],
        },
        tags: { scenario: 'analyze_load' },
      };

    case 'heavy_bom':
      return {
        stages: [
          { duration: '1m', target: 3 },
          { duration: '5m', target: 5 },
          { duration: '1m', target: 0 },
        ],
        thresholds: {
          ...commonThresholds,
          iktk_bom_analysis_duration: ['p(95)<120000'],
          iktk_bom_tree_duration: ['p(95)<8000'],
        },
        tags: { scenario: 'heavy_bom' },
      };

    case 'mixed_workload':
      return {
        scenarios: {
          read: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 20,
            maxVUs: 50,
            exec: 'readScenario',
            tags: { workload: 'read' },
          },
          write: {
            executor: 'constant-arrival-rate',
            rate: 2,
            timeUnit: '1s',
            duration: '5m',
            preAllocatedVUs: 5,
            maxVUs: 20,
            exec: 'writeScenario',
            tags: { workload: 'write' },
          },
        },
        thresholds: commonThresholds,
        tags: { scenario: 'mixed_workload' },
      };

    case 'parallel_projects':
      return {
        scenarios: {
          parallel_boms: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
              { duration: '1m', target: 20 },
              { duration: '5m', target: 20 },
              { duration: '1m', target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'parallelProjectsScenario',
          },
        },
        thresholds: commonThresholds,
        tags: { scenario: 'parallel_projects' },
      };

    case 'read_load':
    default:
      return {
        stages: [
          { duration: '1m', target: 20 },
          { duration: '3m', target: 50 },
          { duration: '1m', target: 50 },
          { duration: '1m', target: 0 },
        ],
        thresholds: commonThresholds,
        tags: { scenario: 'read_load' },
      };
  }
}

// -----------------------------------------------------------------------------
// Setup и Teardown
// -----------------------------------------------------------------------------

/**
 * Setup: аутентификация и подготовка проектов с BOM.
 */
export function setup() {
  const loginRes = http.post(
    `${BASE_URL}${AUTH_PREFIX}/login`,
    JSON.stringify({
      username: TEST_USER.username,
      password: TEST_USER.password,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: TIMEOUT_NORMAL,
    }
  );

  if (loginRes.status !== 200) {
    fail(`Setup: не удалось аутентифицироваться: ${loginRes.status} ${loginRes.body}`);
  }

  const token = JSON.parse(loginRes.body).accessToken;
  if (!token) fail('Setup: токен не получен');

  console.log(`Setup: аутентификация успешна, пользователь=${TEST_USER.username}`);

  // Подготовка проектов с BOM
  const projects = [];
  const count = SETUP_PROJECTS;

  for (let i = 0; i < count; i++) {
    const projectId = createProject(token, i);
    if (!projectId) {
      console.warn(`Setup: не удалось создать проект #${i}`);
      continue;
    }

    // Запуск анализа BOM (для read_load/read-only сценариев)
    if (SCENARIO !== 'analyze_load' && SCENARIO !== 'heavy_bom') {
      try {
        runBomAnalysis(token, projectId);
      } catch (e) {
        console.warn(`Setup: не удалось выполнить анализ BOM для ${projectId}: ${e}`);
      }
    }

    const bom = getBom(token, projectId);
    projects.push({
      projectId,
      bomId: bom?.id || null,
    });

    sleep(0.3);
  }

  console.log(`Setup: подготовлено ${projects.length} проектов с BOM`);

  return {
    token,
    projects,
    baseUrl: BASE_URL,
    startedAt: new Date().toISOString(),
  };
}

/**
 * Teardown.
 */
export function teardown(data) {
  console.log(`Teardown: тест завершён, начат в ${data.startedAt}, проектов: ${data.projects.length}`);
}

// -----------------------------------------------------------------------------
// Вспомогательные функции
// -----------------------------------------------------------------------------

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': `k6-bom-${randomString(16)}`,
  };
}

function checkResponse(res, name, expectedStatuses = [200]) {
  if (res.status === 429) {
    bomRateLimitHits.add(1, { name });
  }
  return check(res, {
    [`${name}: статус`]: (r) => expectedStatuses.includes(r.status),
    [`${name}: тело`]: (r) => r.body !== null,
  });
}

/**
 * Создать проект через API.
 */
function createProject(token, index = 0) {
  const unique = `${Date.now()}-${index}-${randomString(6)}`;
  const payload = {
    projectName: `BomLoad-${unique}`,
    designation: `BL-${unique.slice(-8)}`,
    objectType: 'ASSEMBLY',
    requestedBy: TEST_USER.username,
  };

  const res = http.post(
    `${BASE_URL}${ORCH_PREFIX}/projects`,
    JSON.stringify(payload),
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'createProject' },
    }
  );

  if (!checkResponse(res, 'createProject', [200, 201])) {
    return null;
  }

  try {
    return JSON.parse(res.body).projectId;
  } catch {
    return null;
  }
}

/**
 * Запустить анализ BOM.
 */
function runBomAnalysis(token, projectId) {
  const res = http.post(
    `${BASE_URL}${BOM_PREFIX}/projects/${projectId}/analyze`,
    JSON.stringify({ force: true }),
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_LONG,
      tags: { name: 'runBomAnalysis' },
    }
  );

  bomAnalysisDuration.add(res.timings.duration);
  bomAnalysisSuccessRate.add(res.status === 200 || res.status === 202);

  if (!checkResponse(res, 'runBomAnalysis', [200, 202])) {
    bomAnalysisErrors.add(1, { reason: `status_${res.status}` });
    return null;
  }

  try {
    return JSON.parse(res.body);
  } catch {
    return null;
  }
}

/**
 * Получить BOM по проекту.
 */
function getBom(token, projectId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/projects/${projectId}`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getBom' },
    }
  );

  bomReadDuration.add(res.timings.duration);
  bomReadSuccessRate.add(res.status === 200);

  if (!checkResponse(res, 'getBom', [200, 404])) {
    bomReadErrors.add(1);
    return null;
  }

  if (res.status === 404) return null;

  try {
    return JSON.parse(res.body);
  } catch {
    return null;
  }
}

/**
 * Получить дерево BOM по ID.
 */
function getBomTree(token, bomId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/tree`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getBomTree' },
    }
  );

  bomTreeDuration.add(res.timings.duration);
  bomTreeSuccessRate.add(res.status === 200);

  if (!checkResponse(res, 'getBomTree', [200, 404])) {
    bomReadErrors.add(1);
    return null;
  }

  return res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Получить уплощённый список позиций.
 */
function getBomItems(token, bomId, type) {
  const url = `${BASE_URL}${BOM_PREFIX}/${bomId}/items${
    type ? '?type=' + type : ''
  }`;

  const res = http.get(url, {
    headers: authHeaders(token),
    timeout: TIMEOUT_NORMAL,
    tags: { name: 'getBomItems' },
  });

  checkResponse(res, 'getBomItems', [200]);
  return res.status === 200 ? JSON.parse(res.body) : [];
}

/**
 * Получить связи сборки.
 */
function getConnections(token, bomId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/connections`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getConnections' },
    }
  );

  bomConnectionsDuration.add(res.timings.duration);
  checkResponse(res, 'getConnections', [200]);
  return res.status === 200 ? JSON.parse(res.body) : [];
}

/**
 * Получить последовательность сборки.
 */
function getSequence(token, bomId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/assembly-sequence`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getSequence' },
    }
  );

  bomSequenceDuration.add(res.timings.duration);
  checkResponse(res, 'getSequence', [200]);
  return res.status === 200 ? JSON.parse(res.body) : [];
}

/**
 * Получить сводку по BOM.
 */
function getBomSummary(token, bomId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/summary`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getBomSummary' },
    }
  );

  checkResponse(res, 'getBomSummary', [200]);
  return res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Получить спецификацию.
 */
function getSpecification(token, bomId) {
  const res = http.get(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/specification`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getSpecification' },
    }
  );

  checkResponse(res, 'getSpecification', [200]);
  return res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Добавить позицию.
 */
function addItem(token, bomId, parentId) {
  const unique = `${Date.now()}-${randomString(6)}`;
  const payload = {
    position: String(randomIntBetween(1, 999)),
    designation: `LOAD-${unique}`,
    name: `Load item ${unique}`,
    quantity: randomIntBetween(1, 10),
    unit: 'шт',
    type: 'PART',
    material: 'Сталь 45',
  };

  const url = `${BASE_URL}${BOM_PREFIX}/${bomId}/items${
    parentId ? '?parentId=' + parentId : ''
  }`;

  const res = http.post(url, JSON.stringify(payload), {
    headers: authHeaders(token),
    timeout: TIMEOUT_NORMAL,
    tags: { name: 'addItem' },
  });

  bomItemsAddDuration.add(res.timings.duration);
  checkResponse(res, 'addItem', [200, 201]);

  return res.status === 201 || res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Экспорт BOM.
 */
function exportBom(token, bomId, format = 'CSV') {
  const res = http.post(
    `${BASE_URL}${BOM_PREFIX}/${bomId}/export?format=${format}`,
    null,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_LONG,
      tags: { name: `exportBom_${format}` },
    }
  );

  bomExportDuration.add(res.timings.duration);
  checkResponse(res, `exportBom_${format}`, [200]);
  return res.status === 200 ? JSON.parse(res.body) : null;
}

// -----------------------------------------------------------------------------
// Сценарии
// -----------------------------------------------------------------------------

/**
 * Основной сценарий: чтение BOM.
 */
export default function mainScenario(data) {
  const { token, projects } = data;
  if (!projects || projects.length === 0) {
    sleep(1);
    return;
  }

  const project = projects[Math.floor(Math.random() * projects.length)];

  group('01. Чтение BOM по проекту', () => {
    const bom = getBom(token, project.projectId);
    if (!bom || !bom.id) return;

    activeBoms.add(1);
    if (bom.totalPositions) totalPositions.add(bom.totalPositions);

    sleep(randomIntBetween(0.5, 1.5));

    group('02. Дерево BOM', () => {
      getBomTree(token, bom.id);
    });

    sleep(randomIntBetween(0.5, 1.5));

    group('03. Уплощённый список позиций', () => {
      getBomItems(token, bom.id);
      getBomItems(token, bom.id, 'PART');
      getBomItems(token, bom.id, 'ASSEMBLY');
    });

    sleep(randomIntBetween(0.5, 1.5));

    group('04. Связи и последовательность', () => {
      getConnections(token, bom.id);
      getSequence(token, bom.id);
    });

    sleep(randomIntBetween(0.5, 1.5));

    group('05. Сводка и спецификация', () => {
      getBomSummary(token, bom.id);
      getSpecification(token, bom.id);
    });
  });

  sleep(randomIntBetween(1, 3));
}

/**
 * Сценарий только чтения (для mixed_workload).
 */
export function readScenario(data) {
  mainScenario(data);
}

/**
 * Сценарий записи (для mixed_workload).
 */
export function writeScenario(data) {
  const { token, projects } = data;
  if (!projects || projects.length === 0) return;

  const project = projects[Math.floor(Math.random() * projects.length)];
  const bom = getBom(token, project.projectId);

  if (!bom || !bom.id) return;

  group('Добавление позиции', () => {
    addItem(token, bom.id);
  });

  sleep(randomIntBetween(1, 2));
}

/**
 * Сценарий анализа BOM.
 */
export function analyzeScenario(data) {
  const { token } = data;

  // Создаём новый проект для анализа
  const projectId = createProject(token, __VU);
  if (!projectId) {
    sleep(2);
    return;
  }

  group('Анализ BOM', () => {
    runBomAnalysis(token, projectId);
  });

  sleep(randomIntBetween(2, 5));
}

/**
 * Сценарий больших сборок.
 */
export function heavyBomScenario(data) {
  const { token } = data;

  // Предполагается, что в setup подготовлены проекты с большими BOM
  const project = data.projects[Math.floor(Math.random() * data.projects.length)];
  if (!project) return;

  group('Тяжёлое чтение BOM', () => {
    const bom = getBom(token, project.projectId);
    if (!bom || !bom.id) return;

    if (bom.totalPositions) totalPositions.add(bom.totalPositions);

    getBomTree(token, bom.id);
    getBomItems(token, bom.id);
    getSequence(token, bom.id);
  });

  sleep(randomIntBetween(3, 6));
}

/**
 * Сценарий параллельных проектов.
 */
export function parallelProjectsScenario(data) {
  const { token } = data;

  const projects = [];
  for (let i = 0; i < 3; i++) {
    const projectId = createProject(token, __VU * 100 + i);
    if (projectId) projects.push(projectId);
    sleep(0.5);
  }

  group('Параллельные анализы BOM', () => {
    projects.forEach((projectId) => {
      runBomAnalysis(token, projectId);
    });
  });

  group('Мониторинг BOM', () => {
    for (let i = 0; i < 3; i++) {
      projects.forEach((projectId) => {
        getBom(token, projectId);
      });
      sleep(randomIntBetween(3, 5));
    }
  });

  sleep(2);
}

// -----------------------------------------------------------------------------
// Summary
// -----------------------------------------------------------------------------

export function handleSummary(data) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

  return {
    [`tests/load/reports/bom-summary-${SCENARIO}-${timestamp}.html`]: htmlReport(data),
    [`tests/load/reports/bom-summary-${SCENARIO}-${timestamp}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}