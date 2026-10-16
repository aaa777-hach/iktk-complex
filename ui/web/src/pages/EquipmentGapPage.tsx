import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Badge,
  Breadcrumb,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Divider,
  Empty,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowLeftOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  ShopOutlined,
  SwapOutlined,
  ToolOutlined,
  WarningOutlined,
} from '@ant-design/icons';

import { useAuthStore } from '../store/authStore';
import { useProjectStore } from '../store/projectStore';
import { ROLES } from '../api/constants';
import recommendationApi from '../api/recommendationApi';
import type {
  EquipmentRecommendation,
  GapAnalysisResponse,
  GapItem,
  OutsourcingOption,
  VendorCatalog,
} from '../api/types';

const { Title, Text, Paragraph } = Typography;
const { TabPane } = Tabs;
const { Option } = Select;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

const SEVERITY_CONFIG: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: 'Критичный', color: 'red' },
  MAJOR: { label: 'Важный', color: 'orange' },
  MINOR: { label: 'Незначительный', color: 'blue' },
};

const RECOMMENDATION_TYPE_CONFIG: Record<
  string,
  { label: string; color: string; icon: React.ReactNode }
> = {
  ALTERNATIVE: {
    label: 'Альтернатива',
    color: 'green',
    icon: <SwapOutlined />,
  },
  PURCHASE: {
    label: 'Докупка',
    color: 'blue',
    icon: <DollarOutlined />,
  },
  RENT: {
    label: 'Аренда',
    color: 'cyan',
    icon: <ToolOutlined />,
  },
  OUTSOURCE: {
    label: 'Аутсорс',
    color: 'purple',
    icon: <ShopOutlined />,
  },
  ADJUST_PROCESS: {
    label: 'Корректировка ТП',
    color: 'gold',
    icon: <ExperimentOutlined />,
  },
};

const RECOMMENDATION_STATUS_CONFIG: Record<string, { label: string; badge: any }> = {
  PENDING: { label: 'Ожидает', badge: 'warning' },
  ACCEPTED: { label: 'Принята', badge: 'success' },
  REJECTED: { label: 'Отклонена', badge: 'error' },
};

const GAP_STRATEGIES = [
  { value: 'STRICT', label: 'Строгая' },
  { value: 'FLEXIBLE', label: 'Гибкая' },
  { value: 'HYBRID', label: 'Гибридная' },
];

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Страница дефицита оборудования и рекомендаций ИКТК.
 *
 * <p>Возможности:
 * <ul>
 *   <li>запуск gap-анализа по проекту;</li>
 *   <li>просмотр списка дефицитов с критичностью;</li>
 *   <li>просмотр рекомендаций по каждому дефициту;</li>
 *   <li>принятие рекомендации (альтернатива, докупка, аренда);</li>
 *   <li>корректировка ТП под имеющееся оборудование;</li>
 *   <li>выбор аутсорса (с подбором подрядчика);</li>
 *   <li>поиск в каталоге внешнего оборудования;</li>
 *   <li>сводка по рекомендациям проекта.</li>
 * </ul>
 *
 * <p>Доступ по ролям:
 * <ul>
 *   <li>чтение — все авторизованные роли;</li>
 *   <li>запуск анализа, корректировка ТП — ENGINEER, ADMIN;</li>
 *   <li>принятие рекомендаций — ENGINEER, MANAGER, ADMIN;</li>
 *   <li>докупка/аренда — MANAGER, ADMIN.</li>
 * </ul>
 */
const EquipmentGapPage: React.FC = () => {
  const { id: projectId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { hasRole } = useAuthStore();
  const { currentProject } = useProjectStore();

  const canEdit = hasRole(ROLES.ADMIN) || hasRole(ROLES.ENGINEER);
  const canApprove =
    hasRole(ROLES.ADMIN) || hasRole(ROLES.MANAGER) || hasRole(ROLES.ENGINEER);
  const canPurchase = hasRole(ROLES.ADMIN) || hasRole(ROLES.MANAGER);

  // ---- Состояние ------------------------------------------------------
  const [analysis, setAnalysis] = useState<GapAnalysisResponse | null>(null);
  const [recommendations, setRecommendations] = useState<EquipmentRecommendation[]>([]);
  const [outsourcingOptions, setOutsourcingOptions] = useState<OutsourcingOption[]>([]);
  const [vendorCatalog, setVendorCatalog] = useState<VendorCatalog[]>([]);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState('gaps');
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');

  // ---- Модальные окна -------------------------------------------------
  const [outsourceModalOpen, setOutsourceModalOpen] = useState(false);
  const [currentGap, setCurrentGap] = useState<GapItem | null>(null);
  const [selectedOutsourcingOption, setSelectedOutsourcingOption] =
    useState<OutsourcingOption | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // -------------------------------------------------------------------------
  // Загрузка
  // -------------------------------------------------------------------------

  const loadAll = useCallback(
    async (showSpinner = true) => {
      if (!projectId) return;
      if (showSpinner) setLoading(true);
      setRefreshing(true);
      setError(null);

      try {
        const [analysisData, recommendationsData, summaryData] = await Promise.all([
          recommendationApi.getLatestGapAnalysis(projectId).catch(() => null),
          recommendationApi.getByProject(projectId).catch(() => []),
          recommendationApi.getSummary(projectId).catch(() => null),
        ]);

        setAnalysis(analysisData);
        setRecommendations(recommendationsData);
      } catch (e: any) {
        const msg =
          e?.response?.data?.message || e?.message || 'Ошибка загрузки данных';
        setError(msg);
        message.error(msg);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [projectId]
  );

  useEffect(() => {
    loadAll(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const loadOutsourcingOptions = useCallback(
    async (gapId: string) => {
      try {
        const data = await recommendationApi.getOutsourcingOptionsByGap(gapId);
        setOutsourcingOptions(data);
      } catch (e) {
        message.error('Не удалось загрузить варианты аутсорса');
      }
    },
    []
  );

  const loadVendorCatalog = useCallback(
    async (equipmentType?: string) => {
      try {
        const data = await recommendationApi.searchVendorCatalog({
          equipmentType,
        });
        setVendorCatalog(data);
      } catch (e) {
        message.error('Не удалось загрузить каталог');
      }
    },
    []
  );

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'vendors' && vendorCatalog.length === 0) loadVendorCatalog();
  };

  // -------------------------------------------------------------------------
  // Gap-анализ
  // -------------------------------------------------------------------------

  const handleRunAnalysis = async (strategy: string = 'STRICT') => {
    if (!projectId) return;

    Modal.confirm({
      title: 'Запустить gap-анализ?',
      content:
        'Система сопоставит требования спроектированных ТП с парком оборудования ' +
        'и выявит дефицит. При необходимости будут сформированы рекомендации.',
      okText: 'Запустить',
      cancelText: 'Отмена',
      onOk: async () => {
        setAnalyzing(true);
        try {
          const result = await recommendationApi.runGapAnalysis(projectId, {
            strategy,
          });
          message.success(
            `Gap-анализ завершён: ${result.gapsCount ?? 0} дефицитов, ${result.recommendationsCount ?? 0} рекомендаций`
          );
          await loadAll(false);
        } catch (e: any) {
          const msg =
            e?.response?.data?.message || e?.message || 'Ошибка gap-анализа';
          message.error(msg);
        } finally {
          setAnalyzing(false);
        }
      },
    });
  };

  // -------------------------------------------------------------------------
  // Принятие решений
  // -------------------------------------------------------------------------

  const handleAccept = async (rec: EquipmentRecommendation) => {
    try {
      setSubmitting(true);
      await recommendationApi.acceptRecommendation(projectId!, rec.id, {});
      message.success('Рекомендация принята');
      loadAll(false);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка принятия');
    } finally {
      setSubmitting(false);
    }
  };

  const handleAdjustProcess = async (rec: EquipmentRecommendation) => {
    Modal.confirm({
      title: 'Скорректировать ТП?',
      content:
        'Проект вернётся на этап сопоставления с оборудованием. ' +
        'ТП будет пересмотрен под имеющийся парк.',
      okText: 'Скорректировать',
      cancelText: 'Отмена',
      onOk: async () => {
        try {
          setSubmitting(true);
          await recommendationApi.adjustProcess(projectId!, rec.id, {});
          message.success('ТП будет скорректирован');
          loadAll(false);
        } catch (e: any) {
          message.error(e?.response?.data?.message || 'Ошибка');
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  const handlePurchase = async (rec: EquipmentRecommendation) => {
    Modal.confirm({
      title: 'Зафиксировать докупку/аренду?',
      content: (
        <div>
          <p>Оборудование будет отмечено как закупаемое/арендуемое.</p>
          {rec.estimatedCost != null && (
            <p>
              Оценочная стоимость: <Text strong>{rec.estimatedCost} ₽</Text>
            </p>
          )}
          {rec.leadTimeDays != null && (
            <p>
              Срок поставки: <Text strong>{rec.leadTimeDays} дней</Text>
            </p>
          )}
        </div>
      ),
      okText: 'Подтвердить',
      cancelText: 'Отмена',
      onOk: async () => {
        try {
          setSubmitting(true);
          await recommendationApi.purchase(projectId!, rec.id, {
            vendorId: rec.vendorId || '',
          });
          message.success('Решение зафиксировано');
          loadAll(false);
        } catch (e: any) {
          message.error(e?.response?.data?.message || 'Ошибка');
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  const handleOpenOutsourcing = async (
    gap: GapItem,
    rec: EquipmentRecommendation
  ) => {
    setCurrentGap(gap);
    setSelectedOutsourcingOption(null);
    setOutsourceModalOpen(true);
    await loadOutsourcingOptions(gap.id);
  };

  const handleSelectOutsourcing = async () => {
    if (!selectedOutsourcingOption || !currentGap) {
      message.warning('Выберите подрядчика');
      return;
    }

    try {
      setSubmitting(true);

      // Найти рекомендацию типа OUTSOURCE для этого дефицита
      const outsourceRec = recommendations.find(
        (r) => r.gapId === currentGap.id && r.type === 'OUTSOURCE'
      );

      if (!outsourceRec) {
        message.error('Рекомендация аутсорса не найдена');
        return;
      }

      await recommendationApi.selectOutsourcing(projectId!, outsourceRec.id, {
        contractor: selectedOutsourcingOption.contractor || '',
        optionId: selectedOutsourcingOption.id,
      });

      message.success('Подрядчик выбран');
      setOutsourceModalOpen(false);
      loadAll(false);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка выбора подрядчика');
    } finally {
      setSubmitting(false);
    }
  };

  // -------------------------------------------------------------------------
  // Производные значения
  // -------------------------------------------------------------------------

  const gaps: GapItem[] = useMemo(
    () => analysis?.gaps || [],
    [analysis]
  );

  const filteredGaps = useMemo(() => {
    return gaps.filter((g) => {
      if (severityFilter !== 'ALL' && g.severity !== severityFilter) return false;
      return true;
    });
  }, [gaps, severityFilter]);

  const filteredRecommendations = useMemo(() => {
    return recommendations.filter((r) => {
      if (typeFilter !== 'ALL' && r.type !== typeFilter) return false;
      return true;
    });
  }, [recommendations, typeFilter]);

  const summaryStats = useMemo(() => {
    return {
      totalGaps: analysis?.gapsCount ?? 0,
      recommendations: recommendations.length,
      pending: recommendations.filter((r) => r.status === 'PENDING').length,
      accepted: recommendations.filter((r) => r.status === 'ACCEPTED').length,
    };
  }, [analysis, recommendations]);

  const recommendationsByGap = useMemo(() => {
    const map: Record<string, EquipmentRecommendation[]> = {};
    recommendations.forEach((r) => {
      if (r.gapId) {
        if (!map[r.gapId]) map[r.gapId] = [];
        map[r.gapId].push(r);
      }
    });
    return map;
  }, [recommendations]);

  // -------------------------------------------------------------------------
  // Колонки
  // -------------------------------------------------------------------------

  const gapColumns: ColumnsType<GapItem> = useMemo(
    () => [
      {
        title: 'Тип операции',
        dataIndex: 'operationType',
        key: 'operationType',
        width: 180,
        render: (value: string) => <Tag color="blue">{value}</Tag>,
      },
      {
        title: 'Критичность',
        dataIndex: 'severity',
        key: 'severity',
        width: 140,
        render: (value: string) => {
          const conf = SEVERITY_CONFIG[value] || SEVERITY_CONFIG.MINOR;
          return <Tag color={conf.color}>{conf.label}</Tag>;
        },
      },
      {
        title: 'Причина',
        dataIndex: 'gapReason',
        key: 'gapReason',
        ellipsis: true,
      },
      {
        title: 'Рекомендаций',
        key: 'recCount',
        width: 140,
        align: 'center',
        render: (_, record) =>
          recommendationsByGap[record.id]?.length || 0,
      },
      {
        title: 'Действия',
        key: 'actions',
        width: 240,
        fixed: 'right',
        render: (_, record) => {
          const recs = recommendationsByGap[record.id] || [];
          const pendingRec = recs.find((r) => r.status === 'PENDING');
          const altRec = recs.find((r) => r.type === 'ALTERNATIVE' && r.status === 'PENDING');
          const purchaseRec = recs.find(
            (r) => (r.type === 'PURCHASE' || r.type === 'RENT') && r.status === 'PENDING'
          );
          const outsourceRec = recs.find((r) => r.type === 'OUTSOURCE' && r.status === 'PENDING');
          const adjustRec = recs.find(
            (r) => r.type === 'ADJUST_PROCESS' && r.status === 'PENDING'
          );

          return (
            <Space size={4} wrap>
              {altRec && canApprove && (
                <Tooltip title="Принять альтернативу">
                  <Button
                    size="small"
                    type="primary"
                    icon={<CheckCircleOutlined />}
                    onClick={() => handleAccept(altRec)}
                    loading={submitting}
                  >
                    Альтернатива
                  </Button>
                </Tooltip>
              )}
              {purchaseRec && canPurchase && (
                <Tooltip title="Докупка / аренда">
                  <Button
                    size="small"
                    icon={<DollarOutlined />}
                    onClick={() => handlePurchase(purchaseRec)}
                    loading={submitting}
                  >
                    Докупка
                  </Button>
                </Tooltip>
              )}
              {outsourceRec && canApprove && (
                <Tooltip title="Выбрать аутсорс">
                  <Button
                    size="small"
                    icon={<ShopOutlined />}
                    onClick={() => handleOpenOutsourcing(record, outsourceRec)}
                  >
                    Аутсорс
                  </Button>
                </Tooltip>
              )}
              {adjustRec && canEdit && (
                <Tooltip title="Скорректировать ТП">
                  <Button
                    size="small"
                    onClick={() => handleAdjustProcess(adjustRec)}
                  >
                    Скорректировать
                  </Button>
                </Tooltip>
              )}
            </Space>
          );
        },
      },
    ],
    [recommendationsByGap, canApprove, canEdit, canPurchase, submitting]
  );

  const recommendationColumns: ColumnsType<EquipmentRecommendation> = useMemo(
    () => [
      {
        title: 'Тип',
        dataIndex: 'type',
        key: 'type',
        width: 150,
        render: (value: string) => {
          const conf = RECOMMENDATION_TYPE_CONFIG[value];
          return (
            <Tag color={conf?.color || 'default'} icon={conf?.icon}>
              {conf?.label || value}
            </Tag>
          );
        },
      },
      {
        title: 'Обоснование',
        dataIndex: 'rationale',
        key: 'rationale',
        ellipsis: true,
      },
      {
        title: 'Скор',
        dataIndex: 'score',
        key: 'score',
        width: 90,
        align: 'right',
        render: (v: number) => (v != null ? v.toFixed(3) : '—'),
      },
      {
        title: 'Стоимость',
        dataIndex: 'estimatedCost',
        key: 'estimatedCost',
        width: 140,
        align: 'right',
        render: (v: number) =>
          v != null ? `${v.toLocaleString('ru-RU')} ₽` : '—',
      },
      {
        title: 'Срок',
        dataIndex: 'leadTimeDays',
        key: 'leadTimeDays',
        width: 90,
        align: 'right',
        render: (v: number) => (v != null ? `${v} дн.` : '—'),
      },
      {
        title: 'Статус',
        dataIndex: 'status',
        key: 'status',
        width: 130,
        render: (value: string) => {
          const conf = RECOMMENDATION_STATUS_CONFIG[value];
          return <Badge status={conf?.badge || 'default'} text={conf?.label || value} />;
        },
      },
      {
        title: 'Действия',
        key: 'actions',
        width: 180,
        fixed: 'right',
        render: (_, record) => {
          if (record.status !== 'PENDING') return null;

          return (
            <Space size={4}>
              {canApprove && record.type !== 'ADJUST_PROCESS' && record.type !== 'OUTSOURCE' && (
                <Button
                  type="primary"
                  size="small"
                  icon={<CheckCircleOutlined />}
                  onClick={() => handleAccept(record)}
                >
                  Принять
                </Button>
              )}
            </Space>
          );
        },
      },
    ],
    [canApprove]
  );

  const vendorColumns: ColumnsType<VendorCatalog> = useMemo(
    () => [
      {
        title: 'Наименование',
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
      },
      {
        title: 'Производитель',
        dataIndex: 'manufacturer',
        key: 'manufacturer',
        width: 150,
      },
      {
        title: 'Тип',
        dataIndex: 'equipmentType',
        key: 'equipmentType',
        width: 130,
        render: (value: string) => <Tag color="blue">{value}</Tag>,
      },
      {
        title: 'Стоимость',
        dataIndex: 'cost',
        key: 'cost',
        width: 140,
        align: 'right',
        render: (v: number) =>
          v != null ? `${v.toLocaleString('ru-RU')} ₽` : '—',
      },
      {
        title: 'Срок',
        dataIndex: 'leadTimeDays',
        key: 'leadTimeDays',
        width: 90,
        align: 'right',
        render: (v: number) => (v != null ? `${v} дн.` : '—'),
      },
      {
        title: 'Поставщик',
        dataIndex: 'vendorName',
        key: 'vendorName',
        width: 180,
      },
    ],
    []
  );

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" tip="Загрузка данных…">
          <div style={{ width: 200, height: 80 }} />
        </Spin>
      </div>
    );
  }

  return (
    <div className="equipment-gap-page">
      {/* Хлебные крошки */}
      <Breadcrumb style={{ marginBottom: 12 }}>
        <Breadcrumb.Item>
          <a onClick={() => navigate('/projects')}>Проекты</a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>
          <a onClick={() => navigate(`/projects/${projectId}`)}>
            {currentProject?.designation || currentProject?.projectName || projectId}
          </a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>Дефицит и рекомендации</Breadcrumb.Item>
      </Breadcrumb>

      {/* Заголовок */}
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            <ExclamationCircleOutlined /> Дефицит оборудования и рекомендации
          </Title>
          <Text type="secondary">
            Сопоставление требований ТП с парком, подбор решений
          </Text>
        </Col>
        <Col>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadAll(false)}
              loading={refreshing}
            >
              Обновить
            </Button>
            {canEdit && (
              <Button
                type="primary"
                icon={<FileSearchOutlined />}
                onClick={() => handleRunAnalysis('STRICT')}
                loading={analyzing}
              >
                Запустить gap-анализ
              </Button>
            )}
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/projects/${projectId}`)}>
              К проекту
            </Button>
          </Space>
        </Col>
      </Row>

      {error && (
        <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />
      )}

      {/* Сводка */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Дефицитов"
              value={summaryStats.totalGaps}
              valueStyle={{ color: summaryStats.totalGaps > 0 ? '#d29922' : '#2ea043' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="Рекомендаций" value={summaryStats.recommendations} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Ожидают решения"
              value={summaryStats.pending}
              valueStyle={{ color: '#d29922' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Принято"
              value={summaryStats.accepted}
              valueStyle={{ color: '#2ea043' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Пустое состояние */}
      {!analysis && (
        <Card>
          <Empty description="Gap-анализ не запущен">
            {canEdit && (
              <Button
                type="primary"
                icon={<FileSearchFocussed />}
                onClick={() => handleRunAnalysis('STRICT')}
                loading={analyzing}
              >
                Запустить gap-анализ
              </Button>
            )}
          </Empty>
        </Card>
      )}

      {analysis && (
        <>
          {/* Информация о последнем анализе */}
          <Card size="small" style={{ marginBottom: 16 }}>
            <Space split={<Divider type="vertical" />} wrap>
              <Text>
                Стратегия: <Tag>{analysis.strategy || 'STRICT'}</Tag>
              </Text>
              <Text>
                Статус:{' '}
                <Tag color={analysis.status === 'COMPLETED' ? 'green' : 'orange'}>
                  {analysis.status}
                </Tag>
              </Text>
              <Text type="secondary">
                Запущен: {analysis.startedAt ? new Date(analysis.startedAt).toLocaleString('ru-RU') : '—'}
              </Text>
              {analysis.completedAt && (
                <Text type="secondary">
                  Завершён: {new Date(analysis.completedAt).toLocaleString('ru-RU')}
                </Text>
              )}
            </Space>
          </Card>

          {/* Вкладки */}
          <Card size="small">
            <Tabs activeKey={activeTab} onChange={handleTabChange} destroyInactiveTabPane>
              {/* Дефициты */}
              <TabPane
                tab={
                  <span>
                    <WarningOutlined /> Дефициты ({filteredGaps.length})
                  </span>
                }
                key="gaps"
              >
                <Space style={{ marginBottom: 12 }}>
                  <Text>Критичность:</Text>
                  <Segmented
                    value={severityFilter}
                    onChange={(v) => setSeverityFilter(v as string)}
                    options={[
                      { label: 'Все', value: 'ALL' },
                      { label: 'Критичные', value: 'CRITICAL' },
                      { label: 'Важные', value: 'MAJOR' },
                      { label: 'Незначительные', value: 'MINOR' },
                    ]}
                  />
                </Space>

                {filteredGaps.length === 0 ? (
                  <Empty description="Дефицитов не найдено" />
                ) : (
                  <Collapse
                    accordion
                    items={filteredGaps.map((gap, index) => ({
                      key: gap.id || index,
                      label: (
                        <Space>
                          <Tag color={(SEVERITY_CONFIG[gap.severity] || SEVERITY_CONFIG.MINOR).color}>
                            {(SEVERITY_CONFIG[gap.severity] || SEVERITY_CONFIG.MINOR).label}
                          </Tag>
                          <Tag color="blue">{gap.operationType}</Tag>
                          <Text>{gap.gapReason}</Text>
                          {recommendationsByGap[gap.id]?.length > 0 && (
                            <Badge
                              count={recommendationsByGap[gap.id].length}
                              style={{ backgroundColor: '#1f6feb' }}
                              title="Рекомендаций"
                            />
                          )}
                        </Space>
                      ),
                      children: (
                        <>
                          <Descriptions bordered size="small" column={2} style={{ marginBottom: 12 }}>
                            <Descriptions.Item label="Тип операции">
                              {gap.operationType || '—'}
                            </Descriptions.Item>
                            <Descriptions.Item label="Критичность">
                              {gap.severity || '—'}
                            </Descriptions.Item>
                            {gap.requiredAccuracy && (
                              <Descriptions.Item label="Точность">
                                {gap.requiredAccuracy}
                              </Descriptions.Item>
                            )}
                            {gap.maxDimensions && (
                              <Descriptions.Item label="Габариты">
                                {gap.maxDimensions}
                              </Descriptions.Item>
                            )}
                            {gap.material && (
                              <Descriptions.Item label="Материал">
                                {gap.material}
                              </Descriptions.Item>
                            )}
                            {gap.requiredPowerKw && (
                              <Descriptions.Item label="Мощность">
                                {gap.requiredPowerKw} кВт
                              </Descriptions.Item>
                            )}
                          </Descriptions>

                          <Divider orientation="left" style={{ marginTop: 8 }}>
                            Рекомендации ({recommendationsByGap[gap.id]?.length || 0})
                          </Divider>

                          {!recommendationsByGap[gap.id] ||
                          recommendationsByGap[gap.id].length === 0 ? (
                            <Empty
                              description="Рекомендации не сформированы"
                              image={Empty.PRESENTED_IMAGE_SIMPLE}
                            />
                          ) : (
                            <Table<EquipmentRecommendation>
                              rowKey="id"
                              columns={recommendationColumns}
                              dataSource={recommendationsByGap[gap.id]}
                              size="small"
                              pagination={false}
                            />
                          )}
                        </>
                      ),
                    }))}
                  />
                )}
              </TabPane>

              {/* Все рекомендации */}
              <TabPane
                tab={
                  <span>
                    <BulbOutlined /> Все рекомендации ({filteredRecommendations.length})
                  </span>
                }
                key="recommendations"
              >
                <Space style={{ marginBottom: 12 }}>
                  <Text>Тип:</Text>
                  <Select
                    value={typeFilter}
                    onChange={setTypeFilter}
                    style={{ width: 200 }}
                  >
                    <Option value="ALL">Все</Option>
                    {Object.entries(RECOMMENDATION_TYPE_CONFIG).map(([key, conf]) => (
                      <Option key={key} value={key}>
                        {conf.label}
                      </Option>
                    ))}
                  </Select>
                </Space>

                <Table<EquipmentRecommendation>
                  rowKey="id"
                  columns={recommendationColumns}
                  dataSource={filteredRecommendations}
                  size="small"
                  pagination={{ pageSize: 30 }}
                />
              </TabPane>

              {/* Каталог внешнего оборудования */}
              <TabPane
                tab={
                  <span>
                    <ShopOutlined /> Каталог внешнего оборудования
                  </span>
                }
                key="vendors"
              >
                <Table<VendorCatalog>
                  rowKey="id"
                  columns={vendorColumns}
                  dataSource={vendorCatalog}
                  size="small"
                  pagination={{ pageSize: 30 }}
                  loading={vendorCatalog.length === 0}
                />
              </TabPane>
            </Tabs>
          </Card>
        </>
      )}

      {/* Модальное окно выбора аутсорса */}
      <Modal
        title="Выбор подрядчика для аутсорса"
        open={outsourceModalOpen}
        onCancel={() => setOutsourceModalOpen(false)}
        onOk={handleSelectOutsourcing}
        confirmLoading={submitting}
        okText="Выбрать"
        cancelText="Отмена"
        width={720}
      >
        {currentGap && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={`Операция: ${currentGap.operationType}`}
            description={currentGap.gapReason}
          />
        )}

        {outsourcingOptions.length === 0 ? (
          <Empty description="Варианты аутсорса не найдены" />
        ) : (
          <Table<OutsourcingOption>
            rowKey="id"
            columns={[
              {
                title: 'Подрядчик',
                dataIndex: 'contractor',
                key: 'contractor',
              },
              {
                title: 'Тип',
                dataIndex: 'type',
                key: 'type',
                width: 130,
              },
              {
                title: 'Стоимость',
                dataIndex: 'cost',
                key: 'cost',
                width: 130,
                align: 'right',
                render: (v: number) =>
                  v != null ? `${v.toLocaleString('ru-RU')} ₽` : '—',
              },
              {
                title: 'Срок',
                dataIndex: 'leadTimeDays',
                key: 'leadTimeDays',
                width: 90,
                align: 'right',
                render: (v: number) => (v != null ? `${v} дн.` : '—'),
              },
              {
                title: 'Скор',
                dataIndex: 'score',
                key: 'score',
                width: 90,
                align: 'right',
                render: (v: number) => (v != null ? v.toFixed(3) : '—'),
              },
              {
                title: '',
                key: 'select',
                width: 90,
                render: (_, record) => (
                  <Button
                    type={
                      selectedOutsourcingOption?.id === record.id ? 'primary' : 'default'
                    }
                    size="small"
                    onClick={() => setSelectedOutsourcingOption(record)}
                  >
                    {selectedOutsourcingOption?.id === record.id ? 'Выбран' : 'Выбрать'}
                  </Button>
                ),
              },
            ]}
            dataSource={outsourcingOptions}
            size="small"
            pagination={false}
          />
        )}
      </Modal>
    </div>
  );
};

export default EquipmentGapPage;