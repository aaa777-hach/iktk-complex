import axios, { AxiosError, AxiosInstance, AxiosRequestConfig } from 'axios';

import type {
  OrchestrationRequest,
  OrchestrationResponse,
  ProjectHistoryEntry,
  ProjectState,
  ProjectSummary,
  RecommendationResponse,
  RollbackResponse,
  StageGraph,
} from './types';

// ---------------------------------------------------------------------------
// Конфигурация
// ---------------------------------------------------------------------------

/**
 * Базовый URL API Gateway.
 * Берётся из переменной окружения Vite или по умолчанию — localhost.
 */
const API_BASE_URL: string =
  (import.meta as any)?.env?.VITE_API_BASE_URL ?? 'http://localhost:8080';

/** Таймаут запросов по умолчанию (мс). */
const DEFAULT_TIMEOUT = 30_000;

/** Таймаут длительных операций (анализ BOM, gap-анализ). */
const LONG_TIMEOUT = 120_000;

/** Ключ для хранения access-токена. */
const ACCESS_TOKEN_KEY = 'iktk.accessToken';

/** Ключ для хранения refresh-токена. */
const REFRESH_TOKEN_KEY = 'iktk.refreshToken';

// ---------------------------------------------------------------------------
// Axios instance
// ---------------------------------------------------------------------------

/**
 * HTTP-клиент для взаимодействия с оркестратором через API Gateway.
 *
 * <p>Автоматически:
 * <ul>
 *   <li>подставляет {@code Authorization: Bearer <token>};</li>
 *   <li>добавляет {@code X-Request-Id} для трассировки;</li>
 *   <li>при 401 пытается обновить токен через refresh;</li>
 *   <li>нормализует ошибки в {@link OrchestratorApiError}.</li>
 * </ul>
 */
const http: AxiosInstance = axios.create({
  baseURL: `${API_BASE_URL}/api/v1/orchestrator`,
  timeout: DEFAULT_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// ---------------------------------------------------------------------------
// Интерсепторы
// ---------------------------------------------------------------------------

/**
 * Request interceptor: добавление токена и Request-Id.
 */
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(ACCESS_TOKEN_KEY);
    if (token) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Трассировка
    config.headers = config.headers ?? {};
    if (!config.headers['X-Request-Id']) {
      config.headers['X-Request-Id'] = generateRequestId();
    }

    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Response interceptor: обработка 401 с попыткой refresh.
 */
let refreshPromise: Promise<string> | null = null;

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & {
      _retry?: boolean;
    };

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken();
        }
        const newToken = await refreshPromise;
        refreshPromise = null;

        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newToken}`;

        return http(originalRequest);
      } catch (refreshError) {
        refreshPromise = null;
        clearTokens();
        redirectToLogin();
        return Promise.reject(normalizeError(error));
      }
    }

    return Promise.reject(normalizeError(error));
  }
);

// ---------------------------------------------------------------------------
// Основное API
// ---------------------------------------------------------------------------

/**
 * API-клиент оркестратора ИКТК.
 *
 * <p>Согласован с {@code OrchestratorController} (позиция 3):
 * <ul>
 *   <li>создание и запуск проекта;</li>
 *   <li>получение состояния, сводки, истории решений;</li>
 *   <li>точки контроля: подтверждение, события, рекомендации, аутсорс;</li>
 *   <li>откат версии;</li>
 *   <li>работа с графом ТП.</li>
 * </ul>
 */
export const orchestratorApi = {
  // =========================================================================
  // 1. Создание и запуск проекта
  // =========================================================================

  /**
   * Создать новый проект и запустить оркестрацию.
   */
  async createProject(request: OrchestrationRequest): Promise<OrchestrationResponse> {
    const { data } = await http.post<OrchestrationResponse>('/projects', request);
    return data;
  },

  /**
   * Запустить или продолжить оркестрацию по проекту.
   */
  async run(projectId: string): Promise<OrchestrationResponse> {
    const { data } = await http.post<OrchestrationResponse>(
      `/projects/${encodeURIComponent(projectId)}/run`
    );
    return data;
  },

  // =========================================================================
  // 2. Состояние и сводка
  // =========================================================================

  /**
   * Текущее состояние проекта (ProjectState).
   */
  async getState(projectId: string): Promise<OrchestrationResponse> {
    const { data } = await http.get<OrchestrationResponse>(
      `/projects/${encodeURIComponent(projectId)}/state`
    );
    return data;
  },

  /**
   * Сводка по проекту: состояние, текущий этап, прогресс, доступные события.
   */
  async getSummary(projectId: string): Promise<ProjectSummary> {
    const { data } = await http.get<ProjectSummary>(
      `/projects/${encodeURIComponent(projectId)}/summary`
    );
    return data;
  },

  /**
   * История решений оркестратора (свежие сверху).
   */
  async getHistory(
    projectId: string,
    limit: number = 100
  ): Promise<ProjectHistoryEntry[]> {
    const { data } = await http.get<ProjectHistoryEntry[]>(
      `/projects/${encodeURIComponent(projectId)}/history`,
      { params: { limit } }
    );
    return data;
  },

  // =========================================================================
  // 3. Точки контроля
  // =========================================================================

  /**
   * Подтвердить текущий этап (точка контроля).
   */
  async approve(
    projectId: string,
    comment?: string
  ): Promise<OrchestrationResponse> {
    const { data } = await http.post<OrchestrationResponse>(
      `/projects/${encodeURIComponent(projectId)}/approve`,
      { comment: comment ?? '' }
    );
    return data;
  },

  /**
   * Отправить событие в конечный автомат проекта.
   */
  async sendEvent(
    projectId: string,
    event: string,
    payload?: Record<string, string>
  ): Promise<OrchestrationResponse> {
    const { data } = await http.post<OrchestrationResponse>(
      `/projects/${encodeURIComponent(projectId)}/events`,
      payload ?? {},
      { params: { event } }
    );
    return data;
  },

  /**
   * Принять рекомендацию при дефиците оборудования.
   */
  async acceptRecommendation(
    projectId: string,
    recommendationId: string,
    payload?: Record<string, string>
  ): Promise<RecommendationResponse> {
    const { data } = await http.post<RecommendationResponse>(
      `/projects/${encodeURIComponent(projectId)}/recommendations/${encodeURIComponent(
        recommendationId
      )}/accept`,
      payload ?? {}
    );
    return data;
  },

  /**
   * Скорректировать ТП под имеющееся оборудование.
   */
  async adjustProcess(
    projectId: string,
    recommendationId: string,
    payload: Record<string, string>
  ): Promise<RecommendationResponse> {
    const { data } = await http.post<RecommendationResponse>(
      `/projects/${encodeURIComponent(projectId)}/recommendations/${encodeURIComponent(
        recommendationId
      )}/adjust`,
      payload
    );
    return data;
  },

  /**
   * Выбрать аутсорс для операции.
   */
  async selectOutsourcing(
    projectId: string,
    recommendationId: string,
    payload: Record<string, string>
  ): Promise<RecommendationResponse> {
    const { data } = await http.post<RecommendationResponse>(
      `/projects/${encodeURIComponent(projectId)}/recommendations/${encodeURIComponent(
        recommendationId
      )}/outsource`,
      payload
    );
    return data;
  },

  // =========================================================================
  // 4. Откат
  // =========================================================================

  /**
   * Откатить проект к указанной версии.
   */
  async rollback(
    projectId: string,
    version: string
  ): Promise<RollbackResponse> {
    const { data } = await http.post<RollbackResponse>(
      `/projects/${encodeURIComponent(projectId)}/rollback`,
      null,
      { params: { version } }
    );
    return data;
  },

  // =========================================================================
  // 5. Граф ТП
  // =========================================================================

  /**
   * Получить граф технологического процесса проекта.
   *
   * <p>Загружается через API процесса (не оркестратора) — endpoint
   * согласован с модулями и API Gateway.
   */
  async getStageGraph(projectId: string): Promise<StageGraph> {
    const { data } = await axios.get<StageGraph>(
      `${API_BASE_URL}/api/v1/processes/projects/${encodeURIComponent(
        projectId
      )}/graph`,
      {
        timeout: DEFAULT_TIMEOUT,
        headers: authHeaders(),
      }
    );
    return data;
  },

  /**
   * Обновить граф технологического процесса проекта.
   */
  async updateStageGraph(
    projectId: string,
    graph: StageGraph
  ): Promise<StageGraph> {
    const { data } = await axios.put<StageGraph>(
      `${API_BASE_URL}/api/v1/processes/projects/${encodeURIComponent(
        projectId
      )}/graph`,
      graph,
      {
        timeout: LONG_TIMEOUT,
        headers: authHeaders(),
      }
    );
    return data;
  },

  // =========================================================================
  // 6. Служебные
  // =========================================================================

  /**
   * Проверка доступности оркестратора.
   */
  async health(): Promise<boolean> {
    try {
      const { status } = await http.get('/health', { timeout: 5_000 });
      return status === 200;
    } catch {
      return false;
    }
  },
};

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Получить заголовки авторизации для прямых axios-запросов.
 */
function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'X-Request-Id': generateRequestId(),
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

/**
 * Обновить access-токен через refresh-токен.
 */
async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) {
    throw new Error('Refresh-токен отсутствует');
  }

  const { data } = await axios.post(
    `${API_BASE_URL}/api/v1/auth/refresh`,
    { refreshToken },
    { timeout: 10_000 }
  );

  const newAccessToken: string = data.accessToken;
  const newRefreshToken: string = data.refreshToken ?? refreshToken;

  localStorage.setItem(ACCESS_TOKEN_KEY, newAccessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken);

  return newAccessToken;
}

/**
 * Очистить токены.
 */
function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

/**
 * Перенаправление на страницу входа.
 */
function redirectToLogin(): void {
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    window.location.href = '/login';
  }
}

/**
 * Сгенерировать Request-Id.
 */
function generateRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Нормализовать ошибку в единый формат.
 */
function normalizeError(error: AxiosError): OrchestratorApiError {
  const status = error.response?.status ?? 0;
  const data: any = error.response?.data ?? {};

  return new OrchestratorApiError(
    data.message ?? error.message ?? 'Ошибка обращения к оркестратору',
    status,
    data.code ?? 'UNKNOWN',
    data.error ?? 'ORCHESTRATION_ERROR',
    data.context ?? {},
    data.requestId
  );
}

// ---------------------------------------------------------------------------
// Ошибка API
// ---------------------------------------------------------------------------

/**
 * Ошибка API оркестратора.
 */
export class OrchestratorApiError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly errorType: string;
  public readonly context: Record<string, unknown>;
  public readonly requestId?: string;

  constructor(
    message: string,
    status: number,
    code: string,
    errorType: string,
    context: Record<string, unknown> = {},
    requestId?: string
  ) {
    super(message);
    this.name = 'OrchestratorApiError';
    this.status = status;
    this.code = code;
    this.errorType = errorType;
    this.context = context;
    this.requestId = requestId;
  }

  /** Ошибка валидации. */
  isValidation(): boolean {
    return this.status === 400 || this.code === 'VALIDATION_FAILED';
  }

  /** Ошибка авторизации. */
  isUnauthorized(): boolean {
    return this.status === 401;
  }

  /** Ошибка доступа. */
  isForbidden(): boolean {
    return this.status === 403;
  }

  /** Проект не найден. */
  isNotFound(): boolean {
    return this.status === 404 || this.code === 'PROJECT_NOT_FOUND';
  }

  /** Конфликт (недопустимый переход, событие не принято). */
  isConflict(): boolean {
    return (
      this.status === 422 ||
      this.code === 'INVALID_TRANSITION' ||
      this.code === 'EVENT_NOT_ACCEPTED'
    );
  }
}

// ---------------------------------------------------------------------------
// Экспорт по умолчанию
// ---------------------------------------------------------------------------

export default orchestratorApi;