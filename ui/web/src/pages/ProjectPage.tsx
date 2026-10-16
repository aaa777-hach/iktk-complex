import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Empty,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Steps,
  Tabs,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  ReloadOutlined,
  StopOutlined,
  ToolOutlined,
  WarningOutlined,
} from '@ant-design/icons';

import { useProjectStore } from '../store/projectStore';
import { useAuthStore } from '../store/authStore';
import orchestratorApi from '../api/orchestratorApi';
import type {
  OrchestrationResponse,
  ProjectHistoryEntry,
  ProjectSummary,
  ProjectState,
} from '../api/types';
import { STATE_LABELS, STATE_CATEGORIES, ROLES } from '../api/constants';

import ProjectHeader from '../components/ProjectHeader';
import StageStatus from '../components/StageStatus';
import DecisionLogPanel from '../components/DecisionLogPanel';
import PointOfControlPanel from '../components/PointOfControlPanel';
import ProjectActionsMenu from '../components/ProjectActionsMenu';

const { Title, Text, Paragraph } = Typography;
const { TabPane } = Tabs;

// ---------------------------------------------------------------------------
// Описание состояний конечного автомата (синхронизировано с ProjectState)
// ---------------------------------------------------------------------------

const STATE_ORDER: ProjectState[] = [
  'PROJECT_INIT',
  'GEOMETRY_INPUT',
  'GEOMETRY_ANALYSIS',
  'ASSEMBLY_ANALYSIS',
  'PART_PROCESS_PLANNING',
  'TOOLING_DESIGN',
  'EQUIPMENT_MATCHING',
  'EQUIPMENT_GAP_RESOLUTION',
  'RECOMMENDATION_REVIEW',
  'ASSEMBLY_PROCESS_PLANNING',
  'NORMING',
  'DOCUMENTATION',
  'TECHNICAL_CONTROL',
  'COMPLETED',
];

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Страница проекта ИКТК.
 *
 * <p>Отображает:
 * <ul>
 *   <li>заголовок проекта: обозначение, наименование, статус, тип объекта;</li>
 *   <li>прогресс прохождения этапов ТПП (Steps);</li>
 *   <li>сводную информацию (Descriptions): предприятие, ответственные, даты;</li>
 *   <li>точки контроля — панель действий (подтверждение, корректировка,
 *       аутсорс, откат);</li>
 *   <li>вкладки: граф ТП, геометрия, BOM, оборудование, нормы, документация,
 *       решения оркестратора, история;</li>
 *   <li>журнал решений оркестратора (DecisionLogPanel);</li>
 *   <li>предупреждения и ошибки текущего этапа.</li>
 * </ul>
 *
 * <p>Данные загружаются через {@code orchestratorApi}: состояние проекта,
 * сводка, история решений. Обновление — по кнопке «Обновить» и автоматически
 * каждые 15 секунд, если проект не завершён.
 */
const ProjectPage: React.FC = () => {
  const { id: projectId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { user, hasRole } = useAuthStore();
  const {
    currentProject,
    setCurrentProject,
    clearCurrentProject,
  } = useProjectStore();

  const [summary, setSummary] = useState<ProjectSummary | null>(null);
  const [orchestration, setOrchestration] = useState<OrchestrationResponse | null>(null);
  const [history, setHistory] = useState<ProjectHistoryEntry[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('overview');

  // -------------------------------------------------------------------------
  // Загрузка данных
  // -------------------------------------------------------------------------

  const loadAll = async (showSpinner = true) => {
    if (!projectId) return;

    if (showSpinner) setLoading(true);
    setRefreshing(true);
    setError(null);

    try {
      const [summaryData, stateData, historyData] = await Promise.all([
        orchestratorApi.getSummary(projectId),
        orchestratorApi.getState(projectId),
        orchestratorApi.getHistory(projectId, 100),
      ]);

      setSummary(summaryData);
      setOrchestration(stateData);
      setHistory(historyData);

      // Синхронизируем текущий проект с глобальным стором
      if (summaryData) {
        setCurrentProject(summaryData);
      }
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Ошибка загрузки проекта';
      setError(msg);
      message.error(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  // Первичная загрузка и очистка при уходе со страницы
  useEffect(() => {
    loadAll(true);
    return () => {
      clearCurrentProject();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // Автообновление, если проект не завершён
  useEffect(() => {
    if (!orchestration || orchestration.state === 'COMPLETED') return;

    const interval = setInterval(() => {
      loadAll(false);
    }, 15_000);

    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orchestration?.state]);

  // -------------------------------------------------------------------------
  // Производные значения
  // -------------------------------------------------------------------------

  const currentState: ProjectState | undefined = orchestration?.state;
  const stateCategory = currentState ? STATE_CATEGORIES[currentState] : undefined;
  const isPointOfControl = stateCategory === 'POINT_OF_CONTROL';
  const isCompleted = currentState === 'COMPLETED';
  const isAwaitingUser = orchestration?.awaitingUserAction === true || isPointOfControl;

  const progressPercent = useMemo(() => {
    if (!currentState) return 0;
    const idx = STATE_ORDER.indexOf(currentState);
    if (idx < 0) return 0;
    return Math.round((idx / (STATE_ORDER.length - 1)) * 100);
  }, [currentState]);

  const canEdit = hasRole(ROLES.ADMIN) || hasRole(ROLES.ENGINEER) || hasRole(ROLES.MANAGER);

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" tip="Загрузка проекта…">
          <div style={{ width: 200, height: 80 }} />
        </Spin>
      </div>
    );
  }

  if (error && !summary) {
    return (
      <Card>
        <Empty description={error}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>
              К списку проектов
            </Button>
            <Button type="primary" icon={<ReloadOutlined />} onClick={() => loadAll(true)}>
              Повторить
            </Button>
          </Space>
        </Empty>
      </Card>
    );
  }

  if (!summary || !orchestration) {
    return (
      <Card>
        <Empty description="Проект не найден">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>
            К списку проектов
          </Button>
        </Empty>
      </Card>
    );
  }

  return (
    <div className="project-page">
      {/* --------------------------------------------------------------- */}
      {/* Хлебные крошки                                                   */}
      {/* --------------------------------------------------------------- */}
      <Breadcrumb style={{ marginBottom: 16 }}>
        <Breadcrumb.Item>
          <a onClick={() => navigate('/dashboard')}>Главная</a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>
          <a onClick={() => navigate('/projects')}>Проекты</a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>{summary.designation || summary.projectName}</Breadcrumb.Item>
      </Breadcrumb>

      {/* --------------------------------------------------------------- */}
      {/* Заголовок проекта                                                */}
      {/* --------------------------------------------------------------- */}
      <ProjectHeader
        project={summary}
        state={currentState}
        onRefresh={() => loadAll(false)}
        refreshing={refreshing}
        actions={
          <ProjectActionsMenu
            projectId={summary.projectId}
            state={currentState}
            canEdit={canEdit}
            onActionComplete={() => loadAll(false)}
          />
        }
      />

      {/* --------------------------------------------------------------- */}
      {/* Предупреждение о точке контроля                                  */}
      {/* --------------------------------------------------------------- */}
      {isAwaitingUser && !isCompleted && (
        <Alert
          style={{ marginTop: 16 }}
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          message="Требуется решение пользователя"
          description={
            orchestration.message ||
            `Проект остановлен на точке контроля: ${STATE_LABELS[currentState!] || currentState}`
          }
          action={
            <PointOfControlPanel
              projectId={summary.projectId}
              state={currentState}
              availableEvents={orchestration.availableEvents || []}
              onComplete={() => loadAll(false)}
            />
          }
        />
      )}

      {/* --------------------------------------------------------------- */}
      {/* Ошибки текущего этапа                                            */}
      {/* --------------------------------------------------------------- */}
      {summary.warnings && summary.warnings.length > 0 && (
        <Alert
          style={{ marginTop: 16 }}
          type="info"
          showIcon
          icon={<WarningOutlined />}
          message={`Предупреждения (${summary.warnings.length})`}
          description={
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {summary.warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          }
        />
      )}

      {/* --------------------------------------------------------------- */}
      {/* Прогресс                                                         */}
      {/* --------------------------------------------------------------- */}
      <Card style={{ marginTop: 16 }}>
        <Row gutter={24} align="middle">
          <Col xs={24} md={16}>
            <Steps
              size="small"
              current={STATE_ORDER.indexOf(currentState!)}
              status={isCompleted ? 'finish' : isAwaitingUser ? 'error' : 'process'}
              items={STATE_ORDER.map((state) => ({
                title: STATE_LABELS[state] || state,
                status:
                  STATE_ORDER.indexOf(state) < STATE_ORDER.indexOf(currentState!)
                    ? 'finish'
                    : state === currentState
                    ? isCompleted
                      ? 'finish'
                      : isAwaitingUser
                      ? 'error'
                      : 'process'
                    : 'wait',
              }))}
            />
          </Col>
          <Col xs={24} md={8}>
            <Statistic
              title="Прогресс ТПП"
              value={progressPercent}
              suffix="%"
              valueStyle={{ color: isCompleted ? '#2ea043' : '#1f6feb' }}
            />
            <Progress
              percent={progressPercent}
              status={isCompleted ? 'success' : 'active'}
              showInfo={false}
              style={{ marginTop: 8 }}
            />
          </Col>
        </Row>
      </Card>

      {/* --------------------------------------------------------------- */}
      {/* Вкладки                                                          */}
      {/* --------------------------------------------------------------- */}
      <Card style={{ marginTop: 16 }}>
        <Tabs activeKey={activeTab} onChange={setActiveTab} destroyInactiveTabPane>
          {/* Обзор ---------------------------------------------------- */}
          <TabPane
            tab={
              <span>
                <DatabaseOutlined /> Обзор
              </span>
            }
            key="overview"
          >
            <Row gutter={16}>
              <Col xs={24} lg={14}>
                <Descriptions
                  title="Основные сведения"
                  bordered
                  column={{ xs: 1, sm: 2 }}
                  size="middle"
                >
                  <Descriptions.Item label="Обозначение">
                    {summary.designation || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Наименование">
                    {summary.projectName}
                  </Descriptions.Item>
                  <Descriptions.Item label="Тип объекта">
                    <Tag color={summary.objectType === 'ASSEMBLY' ? 'purple' : 'blue'}>
                      {summary.objectType === 'ASSEMBLY' ? 'Сборочная единица' : 'Деталь'}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Статус">
                    <Tag color={isCompleted ? 'green' : isAwaitingUser ? 'orange' : 'blue'}>
                      {STATE_LABELS[currentState!] || currentState}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Инициатор">
                    {summary.requestedBy || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Ответственный">
                    {summary.responsibleEngineer || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Создан">
                    {formatDate(summary.createdAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Обновлён">
                    {formatDate(summary.updatedAt)}
                  </Descriptions.Item>
                  {summary.technologyProcessId && (
                    <Descriptions.Item label="ТП" span={2}>
                      <Text code>{summary.technologyProcessId}</Text>
                    </Descriptions.Item>
                  )}
                  {summary.recommendationId && (
                    <Descriptions.Item label="Активная рекомендация" span={2}>
                      <Text code>{summary.recommendationId}</Text>
                    </Descriptions.Item>
                  )}
                </Descriptions>

                <Divider />

                <Space wrap>
                  <Button
                    icon={<ApartmentOutlined />}
                    onClick={() => navigate(`/projects/${summary.projectId}/graph`)}
                  >
                    Граф ТП
                  </Button>
                  <Button
                    icon={<ToolOutlined />}
                    onClick={() => navigate(`/projects/${summary.projectId}/equipment`)}
                  >
                    Оборудование
                  </Button>
                  <Button
                    icon={<FileTextOutlined />}
                    onClick={() => navigate(`/projects/${summary.projectId}/docs`)}
                  >
                    Документация
                  </Button>
                  <Button
                    icon={<ReloadOutlined />}
                    loading={refreshing}
                    onClick={() => loadAll(false)}
                  >
                    Обновить
                  </Button>
                </Space>
              </Col>

              <Col xs={24} lg={10}>
                <Card size="small" title="Текущий этап" style={{ marginBottom: 16 }}>
                  <StageStatus
                    state={currentState}
                    availableEvents={orchestration.availableEvents}
                  />
                </Card>

                <Card size="small" title="Последние решения оркестратора">
                  <Timeline
                    items={
                      history.slice(0, 8).map((entry) => ({
                        color: entry.eventType.includes('FAIL') ? 'red' : 'blue',
                        dot: entry.eventType.includes('APPROVED') ? (
                          <CheckCircleOutlined />
                        ) : entry.eventType.includes('FAIL') ? (
                          <StopOutlined />
                        ) : (
                          <ClockCircleOutlined />
                        ),
                        children: (
                          <>
                            <Text strong>{entry.eventType}</Text>
                            <br />
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatDate(entry.timestamp)} — {entry.actor}
                            </Text>
                          </>
                        ),
                      })) || []
                    }
                  />
                  {history.length === 0 && <Empty description="Решений пока нет" />}
                </Card>
              </Col>
            </Row>
          </TabPane>

          {/* Граф ТП -------------------------------------------------- */}
          <TabPane
            tab={
              <span>
                <ApartmentOutlined /> Граф ТП
              </span>
            }
            key="graph"
          >
            <Paragraph type="secondary">
              Интерактивный граф технологического процесса проекта. Возможен переход
              к отдельной странице для полноценного редактирования.
            </Paragraph>
            <Button
              type="primary"
              icon={<ApartmentOutlined />}
              onClick={() => navigate(`/projects/${summary.projectId}/graph`)}
            >
              Открыть граф
            </Button>
          </TabPane>

          {/* Решения оркестратора ------------------------------------- */}
          <TabPane
            tab={
              <span>
                <ClockCircleOutlined /> Решения
              </span>
            }
            key="decisions"
          >
            <DecisionLogPanel projectId={summary.projectId} history={history} />
          </TabPane>

          {/* История версий ------------------------------------------- */}
          <TabPane
            tab={
              <span>
                <ReloadOutlined /> История версий
              </span>
            }
            key="versions"
          >
            <Paragraph type="secondary">
              История версий проекта и точки отката доступны на отдельной странице
              (функция в разработке).
            </Paragraph>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => navigate(`/projects/${summary.projectId}/graph`)}
            >
              Перейти к графу
            </Button>
          </TabPane>
        </Tabs>
      </Card>
    </div>
  );
};

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

function formatDate(value?: string | null): string {
  if (!value) return '—';
  try {
    const d = new Date(value);
    return d.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return value;
  }
}

export default ProjectPage;