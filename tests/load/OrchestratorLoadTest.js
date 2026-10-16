/**
 * =============================================================================
 * ИКТК: нагрузочный тест оркестратора (k6)
 * Путь: tests/load/OrchestratorLoadTest.js
 * Назначение: проверка производительности, устойчивости и масштабируемости
 *             сервиса-оркестратора ИКТК под нагрузкой.
 *
 * Сценарии:
 *   1. smoke         — минимальная нагрузка (проверка работоспособности);
 *   2. average_load  — средняя нагрузка (типовой рабочий день);
 *   3. stress        — стресс-тест (поиск предела);
 *   4. spike         — пиковая нагрузка (резкий скачок);
 *   5. soak          — длительная нагрузка (утечки, деградация);
 *   6. parallel_projects — параллельные проекты (проверка очередей и БД).
 *
 * Запуск:
 *   k6 run tests/load/OrchestratorLoadTest.js
 *   k6 run --env SCENARIO=stress tests/load/OrchestratorLoadTest.js
 *   k6 run --out influxdb=http://localhost:8086/k6 tests/load/OrchestratorLoadTest.js
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
const API_PREFIX = '/api/v1/orchestrator';
const AUTH_PREFIX = '/api/v1/auth';

const TEST_USER = {
  username: __ENV.TEST_USER || 'engineer',
  password: __ENV.TEST_PASSWORD || 'engineer_password',
};

const SCENARIO = __ENV.SCENARIO || 'average_load';

/** Таймауты (мс). */
const TIMEOUT_SHORT = 5_000;
const TIMEOUT_NORMAL = 30_000;
const TIMEOUT_LONG = 120_000;

// -----------------------------------------------------------------------------
// Пользовательские метрики
// -----------------------------------------------------------------------------

const projectCreationDuration = new Trend('iktk_project_creation_duration', true);
const stateReadDuration = new Trend('iktk_state_read_duration', true);
const approveDuration = new Trend('iktk_approve_duration', true);
const rollbackDuration = new Trend('iktk_rollback_duration', true);
const graphUpdateDuration = new Trend('iktk_graph_update_duration', true);

const authErrors = new Counter('iktk_auth_errors');
const projectErrors = new Counter('iktk_project_errors');
const transitionErrors = new Counter('iktk_transition_errors');
const rateLimitHits = new Counter('iktk_rate_limit_hits');

const authSuccessRate = new Rate('iktk_auth_success_rate');
const projectSuccessRate = new Rate('iktk_project_success_rate');
const stateReadSuccessRate = new Rate('iktk_state_read_success_rate');

const activeProjects = new Gauge('iktk_active_projects');

// -----------------------------------------------------------------------------
// Опции
// -----------------------------------------------------------------------------

export const options = buildOptions();

/**
 * Построить опции k6 в зависимости от выбранного сценария.
 */
function buildOptions() {
  const commonThresholds = {
    // 95% запросов должны укладываться в 2 секунды
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    // Доля ошибок — не более 1%
    http_req_failed: ['rate<0.01'],
    // Пользовательские метрики
    iktk_auth_success_rate: ['rate>0.99'],
    iktk_project_success_rate: ['rate>0.98'],
    iktk_state_read_success_rate: ['rate>0.99'],
    iktk_project_creation_duration: ['p(95)<3000'],
    iktk_state_read_duration: ['p(95)<1000'],
    iktk_approve_duration: ['p(95)<2000'],
  };

  switch (SCENARIO) {
    case 'smoke':
      return {
        vus: 1,
        duration: '30s',
        thresholds: commonThresholds,
        tags: { scenario: 'smoke' },
      };

    case 'stress':
      return {
        stages: [
          { duration: '1m', target: 20 },   // разогрев
          { duration: '2m', target: 50 },   // средняя
          { duration: '2m', target: 100 },  // высокая
          { duration: '2m', target: 200 },  // стресс
          { duration: '2m', target: 300 },  // предел
          { duration: '1m', target: 0 },    // спад
        ],
        thresholds: commonThresholds,
        tags: { scenario: 'stress' },
      };

    case 'spike':
      return {
        stages: [
          { duration: '10s', target: 5 },    // норма
          { duration: '30s', target: 250 },  // резкий скачок
          { duration: '1m', target: 250 },   // удержание
          { duration: '10s', target: 5 },    // спад
          { duration: '30s', target: 5 },    // стабилизация
        ],
        thresholds: commonThresholds,
        tags: { scenario: 'spike' },
      };

    case 'soak':
      return {
        stages: [
          { duration: '2m', target: 30 },   // разогрев
          { duration: '30m', target: 30 },  // длительная нагрузка
          { duration: '2m', target: 0 },    // спад
        ],
        thresholds: {
          ...commonThresholds,
          // Более мягкие требования для длительного теста
          http_req_duration: ['p(95)<3000'],
        },
        tags: { scenario: 'soak' },
      };

    case 'parallel_projects':
      return {
        scenarios: {
          parallel_projects: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
              { duration: '30s', target: 50 },
              { duration: '5m', target: 50 },
              { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
            exec: 'parallelProjectsScenario',
          },
        },
        thresholds: commonThresholds,
        tags: { scenario: 'parallel_projects' },
      };

    case 'average_load':
    default:
      return {
        stages: [
          { duration: '1m', target: 10 },   // разогрев
          { duration: '3m', target: 30 },   // типовой рабочий день
          { duration: '1m', target: 30 },   // удержание
          { duration: '1m', target: 0 },    // спад
        ],
        thresholds: commonThresholds,
        tags: { scenario: 'average_load' },
      };
  }
}

// -----------------------------------------------------------------------------
// Setup и Teardown
// -----------------------------------------------------------------------------

/**
 * Setup: получение токена, подготовка данных.
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

  const body = JSON.parse(loginRes.body);
  const token = body.accessToken;

  if (!token) {
    fail('Setup: токен не получен');
  }

  console.log(`Setup: аутентификация успешна, пользователь=${TEST_USER.username}`);

  return {
    token,
    baseUrl: BASE_URL,
    startedAt: new Date().toISOString(),
  };
}

/**
 * Teardown: очистка данных.
 */
export function teardown(data) {
  console.log(`Teardown: тест завершён, начат в ${data.startedAt}`);
}

// -----------------------------------------------------------------------------
// Вспомогательные функции
// -----------------------------------------------------------------------------

/**
 * Заголовки авторизации.
 */
function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': `k6-${randomString(16)}`,
  };
}

/**
 * Проверка ответа с учётом rate limit.
 */
function checkResponse(res, name, expectedStatuses = [200, 201, 202]) {
  if (res.status === 429) {
    rateLimitHits.add(1, { name });
  }

  return check(res, {
    [`${name}: статус в ожидаемых`]: (r) => expectedStatuses.includes(r.status),
    [`${name}: ответ получен`]: (r) => r.body !== null && r.body.length > 0,
  });
}

/**
 * Создать проект.
 */
function createProject(token, index = 0) {
  const unique = `${Date.now()}-${index}-${randomString(6)}`;
  const payload = {
    projectName: `LoadTest-Корпус-${unique}`,
    designation: `LT-${unique.slice(-8)}`,
    objectType: index % 3 === 0 ? 'ASSEMBLY' : 'PART',
    requestedBy: TEST_USER.username,
  };

  const res = http.post(
    `${BASE_URL}${API_PREFIX}/projects`,
    JSON.stringify(payload),
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'createProject' },
    }
  );

  projectCreationDuration.add(res.timings.duration);
  projectSuccessRate.add(res.status === 201 || res.status === 200);

  if (!checkResponse(res, 'createProject', [200, 201])) {
    projectErrors.add(1, { reason: `status_${res.status}` });
    return null;
  }

  try {
    return JSON.parse(res.body).projectId;
  } catch (e) {
    projectErrors.add(1, { reason: 'invalid_json' });
    return null;
  }
}

/**
 * Получить состояние проекта.
 */
function getProjectState(token, projectId) {
  const res = http.get(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/state`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_SHORT,
      tags: { name: 'getProjectState' },
    }
  );

  stateReadDuration.add(res.timings.duration);
  stateReadSuccessRate.add(res.status === 200);

  if (!checkResponse(res, 'getProjectState', [200])) {
    return null;
  }

  try {
    return JSON.parse(res.body);
  } catch (e) {
    return null;
  }
}

/**
 * Получить сводку по проекту.
 */
function getProjectSummary(token, projectId) {
  const res = http.get(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/summary`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_SHORT,
      tags: { name: 'getProjectSummary' },
    }
  );

  checkResponse(res, 'getProjectSummary', [200]);
  return res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Получить историю решений проекта.
 */
function getProjectHistory(token, projectId, limit = 50) {
  const res = http.get(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/history?limit=${limit}`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_SHORT,
      tags: { name: 'getProjectHistory' },
    }
  );

  checkResponse(res, 'getProjectHistory', [200]);
  return res.status === 200 ? JSON.parse(res.body) : [];
}

/**
 * Запустить/продолжить оркестрацию.
 */
function runOrchestration(token, projectId) {
  const res = http.post(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/run`,
    null,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'runOrchestration' },
    }
  );

  checkResponse(res, 'runOrchestration', [200, 202]);
  return res.status === 200 || res.status === 202;
}

/**
 * Подтвердить текущий этап (точка контроля).
 */
function approveStage(token, projectId, comment) {
  const res = http.post(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/approve`,
    JSON.stringify({ comment: comment || 'Load test approval' }),
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'approveStage' },
    }
  );

  approveDuration.add(res.timings.duration);

  if (!checkResponse(res, 'approveStage', [200, 422])) {
    transitionErrors.add(1, { operation: 'approve' });
    return false;
  }

  return res.status === 200;
}

/**
 * Отправить событие в конечный автомат.
 */
function sendEvent(token, projectId, event, payload = {}) {
  const res = http.post(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/events?event=${event}`,
    JSON.stringify(payload),
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: `sendEvent_${event}` },
    }
  );

  checkResponse(res, `sendEvent_${event}`, [200, 422]);
  return res.status === 200;
}

/**
 * Откатить проект к версии.
 */
function rollbackProject(token, projectId, version) {
  const res = http.post(
    `${BASE_URL}${API_PREFIX}/projects/${projectId}/rollback?version=${version}`,
    null,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'rollbackProject' },
    }
  );

  rollbackDuration.add(res.timings.duration);
  checkResponse(res, 'rollbackProject', [200, 404, 422]);
  return res.status === 200;
}

/**
 * Получить граф ТП.
 */
function getStageGraph(token, projectId) {
  const res = http.get(
    `${BASE_URL}/api/v1/processes/projects/${projectId}/graph`,
    {
      headers: authHeaders(token),
      timeout: TIMEOUT_NORMAL,
      tags: { name: 'getStageGraph' },
    }
  );

  checkResponse(res, 'getStageGraph', [200]);
  return res.status === 200 ? JSON.parse(res.body) : null;
}

/**
 * Проверка здоровья.
 */
function healthCheck() {
  const res = http.get(`${BASE_URL}/actuator/health`, {
    timeout: TIMEOUT_SHORT,
    tags: { name: 'healthCheck' },
  });
  checkResponse(res, 'healthCheck', [200]);
  return res.status === 200;
}

// -----------------------------------------------------------------------------
// Сценарии (основные VU-функции)
// -----------------------------------------------------------------------------

/**
 * Основной сценарий: полный цикл проекта.
 */
export default function mainScenario(data) {
  const token = data.token;

  group('01. Создание проекта', () => {
    const projectId = createProject(token, __VU);

    if (!projectId) {
      sleep(1);
      return;
    }

    sleep(randomIntBetween(1, 2));

    group('02. Чтение состояния', () => {
      getProjectState(token, projectId);
      getProjectSummary(token, projectId);
      getProjectHistory(token, projectId, 20);
    });

    sleep(randomIntBetween(1, 2));

    group('03. Запуск оркестрации', () => {
      runOrchestration(token, projectId);
    });

    sleep(randomIntBetween(2, 4));

    group('04. Мониторинг состояния', () => {
      for (let i = 0; i < 3; i++) {
        const state = getProjectState(token, projectId);
        if (state && state.awaitingUserAction) {
          // Точка контроля — подтверждаем
          group('05. Подтверждение точки контроля', () => {
            approveStage(token, projectId, 'Auto-approve from load test');
          });
        }
        sleep(randomIntBetween(2, 3));
      }
    });

    group('06. Граф ТП', () => {
      getStageGraph(token, projectId);
    });

    activeProjects.add(1);
    sleep(randomIntBetween(2, 5));
  });
}

/**
 * Сценарий параллельных проектов: создание множества проектов
 * и одновременная работа с ними.
 */
export function parallelProjectsScenario(data) {
  const token = data.token;
  const projects = [];

  group('Создание партии проектов', () => {
    for (let i = 0; i < 5; i++) {
      const projectId = createProject(token, __VU * 100 + i);
      if (projectId) {
        projects.push(projectId);
      }
      sleep(0.5);
    }
  });

  group('Параллельная работа с проектами', () => {
    projects.forEach((projectId) => {
      getProjectState(token, projectId);
      getProjectSummary(token, projectId);
      runOrchestration(token, projectId);
    });
  });

  group('Мониторинг партии', () => {
    for (let i = 0; i < 5; i++) {
      projects.forEach((projectId) => {
        getProjectState(token, projectId);
      });
      sleep(randomIntBetween(3, 5));
    }
  });

  activeProjects.add(projects.length);
  sleep(2);
}

/**
 * Сценарий только чтения (read-only): проверка производительности
 * эндпоинтов чтения без создания проектов.
 */
export function readOnlyScenario(data) {
  const token = data.token;

  group('Read-only: список и состояния', () => {
    // Проверка здоровья
    healthCheck();

    // Создаём один «якорный» проект для чтения
    const projectId = createProject(token, 0);
    if (!projectId) {
      sleep(2);
      return;
    }

    for (let i = 0; i < 10; i++) {
      getProjectState(token, projectId);
      getProjectSummary(token, projectId);
      getProjectHistory(token, projectId, 50);
      sleep(randomIntBetween(1, 2));
    }
  });
}

// -----------------------------------------------------------------------------
// Summary
// -----------------------------------------------------------------------------

/**
 * Обработка результатов: HTML-отчёт и краткая сводка.
 */
export function handleSummary(data) {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

  return {
    [`tests/load/reports/summary-${SCENARIO}-${timestamp}.html`]: htmlReport(data),
    [`tests/load/reports/summary-${SCENARIO}-${timestamp}.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
  };
}