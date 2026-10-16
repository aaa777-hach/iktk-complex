import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';

import orchestratorApi, { OrchestratorApiError } from '../api/orchestratorApi';
import type {
  ObjectType,
  OrchestrationResponse,
  ProjectHistoryEntry,
  ProjectState,
  ProjectSummary,
} from '../api/types';

// ---------------------------------------------------------------------------
// Вспомогательные типы
// ---------------------------------------------------------------------------

/**
 * Фильтры списка проектов.
 */
export interface ProjectFilters {
  /** Поиск по обозначению или наименованию. */
  search: string;
  /** Фильтр по статусу. */
  status?: ProjectState | 'ALL';
  /** Фильтр по типу объекта. */
  objectType?: ObjectType | 'ALL';
  /** Фильтр по предприятию. */
  enterpriseCode?: string;
  /** Фильтр по ответственному. */
  responsibleEngineer?: string;
}

/**
 * Сортировка списка проектов.
 */
export type ProjectSortField =
  | 'designation'
  | 'name'
  | 'status'
  | 'createdAt'
  | 'updatedAt';

export interface ProjectSort {
  field: ProjectSortField;
  direction: 'asc' | 'desc';
}

/**
 * Пагинация списка проектов.
 */
export interface ProjectPagination {
  page: number;
  pageSize: number;
  total: number;
}

/**
 * Состояние store.
 */
export interface ProjectState_Store {
  // ---- Активный проект ------------------------------------------------
  currentProject: ProjectSummary | null;
  currentHistory: ProjectHistoryEntry[];
  currentLoading: boolean;
  currentError: string | null;

  // ---- Список проектов ------------------------------------------------
  projects: ProjectSummary[];
  projectsLoading: boolean;
  projectsError: string | null;
  filters: ProjectFilters;
  sort: ProjectSort;
  pagination: ProjectPagination;

  // ---- Действия: активный проект -------------------------------------
  setCurrentProject: (project: ProjectSummary | null) => void;
  clearCurrentProject: () => void;
  loadCurrentProject: (projectId: string) => Promise<ProjectSummary | null>;
  refreshCurrentProject: () => Promise<void>;
  loadCurrentHistory: (projectId: string, limit?: number) => Promise<void>;
  updateCurrentState: (response: OrchestrationResponse) => void;

  // ---- Действия: список проектов -------------------------------------
  loadProjects: () => Promise<void>;
  refreshProjects: () => Promise<void>;
  setFilters: (filters: Partial<ProjectFilters>) => void;
  resetFilters: () => void;
  setSort: (sort: ProjectSort) => void;
  setPage: (page: number) => void;
  setPageSize: (pageSize: number) => void;

  // ---- Служебные -----------------------------------------------------
  clearErrors: () => void;
  reset: () => void;
}

// ---------------------------------------------------------------------------
// Начальные значения
// ---------------------------------------------------------------------------

const INITIAL_FILTERS: ProjectFilters = {
  search: '',
  status: 'ALL',
  objectType: 'ALL',
  enterpriseCode: undefined,
  responsibleEngineer: undefined,
};

const INITIAL_SORT: ProjectSort = {
  field: 'updatedAt',
  direction: 'desc',
};

const INITIAL_PAGINATION: ProjectPagination = {
  page: 1,
  pageSize: 20,
  total: 0,
};

// ---------------------------------------------------------------------------
// Утилиты
// ---------------------------------------------------------------------------

/**
 * Фильтрация проектов на клиенте (для UX).
 * Серверная фильтрация добавляется при переходе на постраничный API.
 */
function applyFilters(
  projects: ProjectSummary[],
  filters: ProjectFilters
): ProjectSummary[] {
  return projects.filter((p) => {
    if (filters.search) {
      const q = filters.search.toLowerCase();
      const inDesignation = (p.designation ?? '').toLowerCase().includes(q);
      const inName = (p.projectName ?? '').toLowerCase().includes(q);
      if (!inDesignation && !inName) return false;
    }
    if (filters.status && filters.status !== 'ALL') {
      if (p.state !== filters.status) return false;
    }
    if (filters.objectType && filters.objectType !== 'ALL') {
      if (p.objectType !== filters.objectType) return false;
    }
    if (filters.enterpriseCode) {
      if (p.enterpriseCode !== filters.enterpriseCode) return false;
    }
    if (filters.responsibleEngineer) {
      if (p.responsibleEngineer !== filters.responsibleEngineer) return false;
    }
    return true;
  });
}

/**
 * Сортировка проектов на клиенте.
 */
function applySort(
  projects: ProjectSummary[],
  sort: ProjectSort
): ProjectSummary[] {
  const { field, direction } = sort;
  const mult = direction === 'asc' ? 1 : -1;

  return [...projects].sort((a, b) => {
    let av: any;
    let bv: any;
    switch (field) {
      case 'designation':
        av = a.designation ?? '';
        bv = b.designation ?? '';
        break;
      case 'name':
        av = a.projectName ?? '';
        bv = b.projectName ?? '';
        break;
      case 'status':
        av = a.state ?? '';
        bv = b.state ?? '';
        break;
      case 'createdAt':
        av = a.createdAt ?? '';
        bv = b.createdAt ?? '';
        break;
      case 'updatedAt':
      default:
        av = a.updatedAt ?? '';
        bv = b.updatedAt ?? '';
        break;
    }
    if (av < bv) return -1 * mult;
    if (av > bv) return 1 * mult;
    return 0;
  });
}

/**
 * Извлечь сообщение об ошибке.
 */
function extractError(e: unknown): string {
  if (e instanceof OrchestratorApiError) {
    return e.message;
  }
  if (e instanceof Error) {
    return e.message;
  }
  return 'Неизвестная ошибка';
}

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

/**
 * Глобальный store проектов ИКТК.
 *
 * <p>Используется компонентами:
 * <ul>
 *   <li>{@code ProjectPage} — активный проект, история решений;</li>
 *   <li>{@code ProjectListPage} — список проектов, фильтры, сортировка;</li>
 *   <li>{@code AppLayout} — счётчики, индикаторы;</li>
 *   <li>{@code DashboardPage} — агрегаты.</li>
 * </ul>
 *
 * <p>Технологии: Zustand + immer (иммутабельные обновления) + persist
 * (сохранение фильтров и сортировки в localStorage) + devtools
 * (интеграция с Redux DevTools).
 *
 * <p>Store разделён на две зоны:
 * <ol>
 *   <li><b>Активный проект:</b> currentProject, currentHistory,
 *       currentLoading, currentError.</li>
 *   <li><b>Список проектов:</b> projects, filters, sort, pagination.</li>
 * </ol>
 */
export const useProjectStore = create<ProjectState_Store>()(
  devtools(
    persist(
      immer((set, get) => ({
        // ===================================================================
        // Начальное состояние
        // ===================================================================

        currentProject: null,
        currentHistory: [],
        currentLoading: false,
        currentError: null,

        projects: [],
        projectsLoading: false,
        projectsError: null,
        filters: INITIAL_FILTERS,
        sort: INITIAL_SORT,
        pagination: INITIAL_PAGINATION,

        // ===================================================================
        // Активный проект
        // ===================================================================

        setCurrentProject: (project) =>
          set((state) => {
            state.currentProject = project;
            state.currentError = null;
          }),

        clearCurrentProject: () =>
          set((state) => {
            state.currentProject = null;
            state.currentHistory = [];
            state.currentError = null;
            state.currentLoading = false;
          }),

        loadCurrentProject: async (projectId) => {
          set((state) => {
            state.currentLoading = true;
            state.currentError = null;
          });

          try {
            const summary = await orchestratorApi.getSummary(projectId);
            set((state) => {
              state.currentProject = summary;
              state.currentLoading = false;
            });
            return summary;
          } catch (e) {
            const msg = extractError(e);
            set((state) => {
              state.currentError = msg;
              state.currentLoading = false;
            });
            return null;
          }
        },

        refreshCurrentProject: async () => {
          const { currentProject } = get();
          if (!currentProject) return;

          try {
            const summary = await orchestratorApi.getSummary(
              currentProject.projectId
            );
            set((state) => {
              state.currentProject = summary;
              state.currentError = null;
            });
          } catch (e) {
            const msg = extractError(e);
            set((state) => {
              state.currentError = msg;
            });
          }
        },

        loadCurrentHistory: async (projectId, limit = 100) => {
          try {
            const history = await orchestratorApi.getHistory(projectId, limit);
            set((state) => {
              state.currentHistory = history;
            });
          } catch (e) {
            const msg = extractError(e);
            set((state) => {
              state.currentError = msg;
            });
          }
        },

        updateCurrentState: (response) =>
          set((state) => {
            if (!state.currentProject) return;
            state.currentProject = {
              ...state.currentProject,
              state: response.state,
              stateDisplayName: response.stateDisplayName,
              stateCategory: response.stateCategory,
              pointOfControl: response.pointOfControl,
              availableEvents: response.availableEvents,
              awaitingUserAction: response.awaitingUserAction,
              message: response.message,
              updatedAt: response.updatedAt,
            };
          }),

        // ===================================================================
        // Список проектов
        // ===================================================================

        loadProjects: async () => {
          set((state) => {
            state.projectsLoading = true;
            state.projectsError = null;
          });

          try {
            // Примечание: в MVP список проектов формируется на клиенте
            // из общего ответа. При добавлении постраничного API
            // сюда подключается серверный запрос с фильтрами.
            const projects = await fetchProjectsList();
            set((state) => {
              state.projects = projects;
              state.pagination.total = projects.length;
              state.projectsLoading = false;
            });
          } catch (e) {
            const msg = extractError(e);
            set((state) => {
              state.projectsError = msg;
              state.projectsLoading = false;
            });
          }
        },

        refreshProjects: async () => {
          await get().loadProjects();
        },

        setFilters: (patch) =>
          set((state) => {
            state.filters = { ...state.filters, ...patch };
            state.pagination.page = 1; // сброс страницы при смене фильтров
          }),

        resetFilters: () =>
          set((state) => {
            state.filters = { ...INITIAL_FILTERS };
            state.pagination.page = 1;
          }),

        setSort: (sort) =>
          set((state) => {
            state.sort = sort;
          }),

        setPage: (page) =>
          set((state) => {
            state.pagination.page = Math.max(1, page);
          }),

        setPageSize: (pageSize) =>
          set((state) => {
            state.pagination.pageSize = Math.max(1, pageSize);
            state.pagination.page = 1;
          }),

        // ===================================================================
        // Служебные
        // ===================================================================

        clearErrors: () =>
          set((state) => {
            state.currentError = null;
            state.projectsError = null;
          }),

        reset: () =>
          set((state) => {
            state.currentProject = null;
            state.currentHistory = [];
            state.currentLoading = false;
            state.currentError = null;
            state.projects = [];
            state.projectsLoading = false;
            state.projectsError = null;
            state.filters = { ...INITIAL_FILTERS };
            state.sort = { ...INITIAL_SORT };
            state.pagination = { ...INITIAL_PAGINATION };
          }),
      })),
      {
        // persist: сохраняем только фильтры, сортировку и размер страницы
        name: 'iktk.projectStore',
        partialize: (state) => ({
          filters: state.filters,
          sort: state.sort,
          pagination: {
            page: state.pagination.page,
            pageSize: state.pagination.pageSize,
            total: 0, // total не сохраняем — он пересчитывается
          },
        }),
      }
    ),
    { name: 'ProjectStore' }
  )
);

// ---------------------------------------------------------------------------
// Селекторы (мемоизированные производные значения)
// ---------------------------------------------------------------------------

/**
 * Селектор: отфильтрованный и отсортированный список проектов.
 */
export function selectVisibleProjects(
  state: ProjectState_Store
): ProjectSummary[] {
  const filtered = applyFilters(state.projects, state.filters);
  const sorted = applySort(filtered, state.sort);
  return sorted;
}

/**
 * Селектор: проекты текущей страницы.
 */
export function selectPaginatedProjects(
  state: ProjectState_Store
): ProjectSummary[] {
  const visible = selectVisibleProjects(state);
  const { page, pageSize } = state.pagination;
  const start = (page - 1) * pageSize;
  return visible.slice(start, start + pageSize);
}

/**
 * Селектор: количество проектов по состояниям (для дашборда).
 */
export function selectCountByState(
  state: ProjectState_Store
): Record<ProjectState, number> {
  const result = {} as Record<ProjectState, number>;
  for (const p of state.projects) {
    result[p.state] = (result[p.state] ?? 0) + 1;
  }
  return result;
}

/**
 * Селектор: активен ли проект в режиме точки контроля.
 */
export function selectIsAwaitingDecision(
  state: ProjectState_Store
): boolean {
  const p = state.currentProject;
  if (!p) return false;
  return (
    p.awaitingUserAction === true ||
    p.stateCategory === 'POINT_OF_CONTROL'
  );
}

/**
 * Селектор: краткая информация об активном проекте (для хедера).
 */
export function selectCurrentProjectHeader(
  state: ProjectState_Store
): {
  projectId: string;
  designation: string;
  name: string;
  state: ProjectState;
  pointOfControl: boolean;
} | null {
  const p = state.currentProject;
  if (!p) return null;
  return {
    projectId: p.projectId,
    designation: p.designation ?? '',
    name: p.projectName,
    state: p.state,
    pointOfControl: p.pointOfControl === true,
  };
}

// ---------------------------------------------------------------------------
// Вспомогательные функции работы с API
// ---------------------------------------------------------------------------

/**
 * Загрузить список проектов.
 *
 * <p>В текущей редакции список собирается через API процессов
 * (согласовано с API Gateway). При расширении до постраничного
 * серверного API этот метод будет использовать параметры фильтров.
 */
async function fetchProjectsList(): Promise<ProjectSummary[]> {
  // Заготовка: подключение к API Gateway
  // const { data } = await axios.get(`${API_BASE_URL}/api/v1/projects`);
  // return data;

  // Заглушка для MVP — пустой список.
  // Реальная реализация выполняется при появлении соответствующего API.
  return [];
}

// ---------------------------------------------------------------------------
// Экспорт по умолчанию
// ---------------------------------------------------------------------------

export default useProjectStore;