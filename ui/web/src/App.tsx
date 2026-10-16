import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ConfigProvider, Spin, theme } from 'antd';
import ruRU from 'antd/locale/ru_RU';

import AppLayout from './components/AppLayout';
import ErrorBoundary from './components/ErrorBoundary';
import RequireAuth from './components/RequireAuth';
import RequireRole from './components/RequireRole';
import LoadingScreen from './components/LoadingScreen';

import { AuthProvider } from './store/authStore';
import { ProjectProvider } from './store/projectStore';
import { NotificationProvider } from './store/notificationStore';

import { ROLES } from './api/constants';

// ---------------------------------------------------------------------------
// Ленивая загрузка страниц (code-splitting)
// ---------------------------------------------------------------------------

const LoginPage            = lazy(() => import('./pages/LoginPage'));
const DashboardPage        = lazy(() => import('./pages/DashboardPage'));
const ProjectListPage      = lazy(() => import('./pages/ProjectListPage'));
const ProjectPage          = lazy(() => import('./pages/ProjectPage'));
const ProjectCreatePage    = lazy(() => import('./pages/ProjectCreatePage'));
const ProcessGraphPage     = lazy(() => import('./pages/ProcessGraphPage'));
const GeometryPage         = lazy(() => import('./pages/GeometryPage'));
const BomTreePage          = lazy(() => import('./pages/BomTreePage'));
const EquipmentRegistryPage= lazy(() => import('./pages/EquipmentRegistryPage'));
const EquipmentGapPage     = lazy(() => import('./pages/EquipmentGapPage'));
const CncProgramsPage      = lazy(() => import('./pages/CncProgramsPage'));
const NormsPage            = lazy(() => import('./pages/NormsPage'));
const DocumentationPage    = lazy(() => import('./pages/DocumentationPage'));
const AuditPage            = lazy(() => import('./pages/AuditPage'));
const UsersPage            = lazy(() => import('./pages/UsersPage'));
const RolesPage            = lazy(() => import('./pages/RolesPage'));
const SettingsPage         = lazy(() => import('./pages/SettingsPage'));
const NotFoundPage         = lazy(() => import('./pages/NotFoundPage'));

// ---------------------------------------------------------------------------
// Тема оформления
// ---------------------------------------------------------------------------

const themeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#1f6feb',
    colorInfo: '#1f6feb',
    colorSuccess: '#2ea043',
    colorWarning: '#d29922',
    colorError: '#cf222e',
    borderRadius: 6,
    fontSize: 14,
    fontFamily:
      "'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, 'Roboto', sans-serif",
  },
  components: {
    Layout: {
      headerBg: '#0d1117',
      headerHeight: 56,
      siderBg: '#161b22',
    },
    Menu: {
      darkItemBg: '#161b22',
      darkItemSelectedBg: '#1f6feb',
    },
  },
};

// ---------------------------------------------------------------------------
// Корневой компонент
// ---------------------------------------------------------------------------

/**
 * Корневой компонент веб-клиента ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>настройку темы Ant Design (русская локаль, фирменные цвета);</li>
 *   <li>провайдеры контекстов: аутентификация, проект, уведомления;</li>
 *   <li>маршрутизацию (react-router) с ленивой загрузкой страниц;</li>
 *   <li>защиту маршрутов: требуется аутентификация ({@code RequireAuth})
 *       и роль ({@code RequireRole});</li>
 *   <li>обработку ошибок рендеринга ({@code ErrorBoundary});</li>
 *   <li>общий layout приложения ({@code AppLayout}) — хедер, сайдбар, контент.</li>
 * </ul>
 *
 * <p>Структура маршрутов:
 * <pre>
 *   /login                    — вход (публичный)
 *   /                         — редирект на /dashboard
 *   /dashboard                — обзорная панель
 *   /projects                 — список проектов
 *   /projects/new             — создание проекта
 *   /projects/:id             — карточка проекта
 *   /projects/:id/graph       — интерактивный граф ТП
 *   /projects/:id/geometry    — геометрия объекта
 *   /projects/:id/bom         — BOM-дерево
 *   /projects/:id/equipment   — сопоставление с оборудованием
 *   /projects/:id/gap         — дефицит и рекомендации
 *   /projects/:id/cnc         — программы ЧПУ
 *   /projects/:id/norms       — нормы
 *   /projects/:id/docs        — документация
 *   /equipment                — реестр оборудования
 *   /audit                    — аудит (ADMIN, MANAGER)
 *   /users                    — пользователи (ADMIN)
 *   /roles                    — роли (ADMIN)
 *   /settings                 — настройки (ADMIN)
 *   *                         — 404
 * </pre>
 */
const App: React.FC = () => {
  return (
    <ConfigProvider locale={ruRU} theme={themeConfig}>
      <ErrorBoundary>
        <NotificationProvider>
          <AuthProvider>
            <ProjectProvider>
              <BrowserRouter>
                <Suspense fallback={<LoadingScreen />}>
                  <Routes>
                    {/* -------------------------------------------------- */}
                    {/* Публичные маршруты                                 */}
                    {/* -------------------------------------------------- */}
                    <Route path="/login" element={<LoginPage />} />

                    {/* -------------------------------------------------- */}
                    {/* Защищённые маршруты (требуется аутентификация)     */}
                    {/* -------------------------------------------------- */}
                    <Route
                      path="/"
                      element={
                        <RequireAuth>
                          <AppLayout />
                        </RequireAuth>
                      }
                    >
                      <Route index element={<Navigate to="/dashboard" replace />} />

                      <Route path="dashboard" element={<DashboardPage />} />

                      {/* Проекты */}
                      <Route path="projects">
                        <Route index element={<ProjectListPage />} />
                        <Route path="new" element={<ProjectCreatePage />} />
                        <Route path=":id" element={<ProjectPage />} />
                        <Route path=":id/graph" element={<ProcessGraphPage />} />
                        <Route path=":id/geometry" element={<GeometryPage />} />
                        <Route path=":id/bom" element={<BomTreePage />} />
                        <Route path=":id/equipment" element={<EquipmentGapPage />} />
                        <Route path=":id/gap" element={<EquipmentGapPage />} />
                        <Route path=":id/cnc" element={<CncProgramsPage />} />
                        <Route path=":id/norms" element={<NormsPage />} />
                        <Route path=":id/docs" element={<DocumentationPage />} />
                      </Route>

                      {/* Оборудование */}
                      <Route path="equipment">
                        <Route index element={<EquipmentRegistryPage />} />
                      </Route>

                      {/* Нормирование (общий раздел) */}
                      <Route path="norms" element={<NormsPage />} />

                      {/* Документация (общий раздел) */}
                      <Route path="docs" element={<DocumentationPage />} />

                      {/* ---------------------------------------------- */}
                      {/* Административные маршруты (по ролям)           */}
                      {/* ---------------------------------------------- */}

                      <Route
                        path="audit"
                        element={
                          <RequireRole roles={[ROLES.ADMIN, ROLES.MANAGER]}>
                            <AuditPage />
                          </RequireRole>
                        }
                      />

                      <Route
                        path="users"
                        element={
                          <RequireRole roles={[ROLES.ADMIN]}>
                            <UsersPage />
                          </RequireRole>
                        }
                      />

                      <Route
                        path="roles"
                        element={
                          <RequireRole roles={[ROLES.ADMIN]}>
                            <RolesPage />
                          </RequireRole>
                        }
                      />

                      <Route
                        path="settings"
                        element={
                          <RequireRole roles={[ROLES.ADMIN]}>
                            <SettingsPage />
                          </RequireRole>
                        }
                      />

                      {/* 404 внутри layout */}
                      <Route path="*" element={<NotFoundPage />} />
                    </Route>

                    {/* 404 вне layout (на случай прямого входа) */}
                    <Route path="*" element={<NotFoundPage />} />
                  </Routes>
                </Suspense>
              </BrowserRouter>
            </ProjectProvider>
          </AuthProvider>
        </NotificationProvider>
      </ErrorBoundary>
    </ConfigProvider>
  );
};

export default App;