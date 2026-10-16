import React, { useCallback, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
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
  Progress,
  Radio,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  BulbOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  ExperimentOutlined,
  ExclamationCircleOutlined,
  ShopOutlined,
  SwapOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  TrophyOutlined,
} from '@ant-design/icons';

import type {
  EquipmentRecommendation,
  OutsourcingOption,
} from '../api/types';

const { Title, Text, Paragraph } = Typography;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

/**
 * Конфигурация типов рекомендаций.
 */
const TYPE_CONFIG: Record<
  string,
  {
    label: string;
    color: string;
    icon: React.ReactNode;
    description: string;
  }
> = {
  ALTERNATIVE: {
    label: 'Альтернатива',
    color: 'green',
    icon: <SwapOutlined />,
    description: 'Использовать имеющееся оборудование предприятия',
  },
  PURCHASE: {
    label: 'Докупка',
    color: 'blue',
    icon: <DollarOutlined />,
    description: 'Приобрести новое оборудование',
  },
  RENT: {
    label: 'Аренда',
    color: 'cyan',
    icon: <ToolOutlined />,
    description: 'Арендовать оборудование у поставщика',
  },
  OUTSOURCE: {
    label: 'Аутсорс',
    color: 'purple',
    icon: <ShopOutlined />,
    description: 'Передать операцию подрядчику',
  },
  ADJUST_PROCESS: {
    label: 'Корректировка ТП',
    color: 'gold',
    icon: <ExperimentOutlined />,
    description: 'Пересмотреть ТП под имеющийся парк',
  },
};

/**
 * Конфигурация статусов рекомендаций.
 */
const STATUS_CONFIG: Record<
  string,
  { label: string; badge: 'success' | 'warning' | 'error' | 'default' }
> = {
  PENDING: { label: 'Ожидает решения', badge: 'warning' },
  ACCEPTED: { label: 'Принята', badge: 'success' },
  REJECTED: { label: 'Отклонена', badge: 'error' },
};

// ---------------------------------------------------------------------------
// Типы
// ---------------------------------------------------------------------------

export interface RecommendationPanelProps {
  /** Дефицит (опционально, для группировки). */
  gapId?: string;

  /** Рекомендации для отображения. */
  recommendations: EquipmentRecommendation[];

  /** Варианты аутсорса (если есть рекомендация типа OUTSOURCE). */
  outsourcingOptions?: OutsourcingOption[];

  /** Выбранный вариант аутсорса (контролируемый режим). */
  selectedOutsourcingOptionId?: string;

  /** Состояние загрузки. */
  loading?: boolean;

  /** Разрешить принятие решений. */
  canApprove?: boolean;

  /** Разрешить докупку/аренду. */
  canPurchase?: boolean;

  /** Разрешить корректировку ТП. */
  canAdjust?: boolean;

  /** Компактный режим (без деталей). */
  compact?: boolean;

  /** Обработчик принятия рекомендации. */
  onAccept?: (rec: EquipmentRecommendation) => Promise<void> | void;

  /** Обработчик отклонения рекомендации. */
  onReject?: (rec: EquipmentRecommendation, reason: string) => Promise<void> | void;

  /** Обработчик выбора докупки/аренды. */
  onPurchase?: (rec: EquipmentRecommendation) => Promise<void> | void;

  /** Обработчик корректировки ТП. */
  onAdjust?: (rec: EquipmentRecommendation) => Promise<void> | void;

  /** Обработчик выбора аутсорса. */
  onSelectOutsourcing?: (
    rec: EquipmentRecommendation,
    option: OutsourcingOption
  ) => Promise<void> | void;

  /** Обработчик выбора варианта аутсорса (для контролируемого режима). */
  onOutsourcingOptionChange?: (optionId: string) => void;
}

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Панель рекомендаций ИКТК.
 *
 * <p>Отображает список рекомендаций по дефициту оборудования:
 * <ul>
 *   <li>тип рекомендации (альтернатива, докупка, аренда, аутсорс,
 *       корректировка ТП) с цветовой кодировкой;</li>
 *   <li>обоснование, скор, стоимость, срок;</li>
 *   <li>статус (ожидает, принята, отклонена);</li>
 *   <li>действия: принять, отклонить, докупка/аренда, аутсорс,
 *       корректировка ТП;</li>
 *   <li>выбор подрядчика при аутсорсе;</li>
 *   <li>сортировку по скору/стоимости/сроку;</li>
 *   <li>лучший вариант (по скору) выделяется отдельно;</li>
 *   <li>сводку по рекомендациям.</li>
 * </ul>
 *
 * <p>Используется в:
 * <ul>
 *   <li>{@code EquipmentGapPage} (позиция 108) — как основная панель;</li>
 *   <li>точке контроля {@code RECOMMENDATION_REVIEW} оркестратора;</li>
 *   <li>карточке проекта — как раздел «Рекомендации».</li>
 * </ul>
 */
const RecommendationPanel: React.FC<RecommendationPanelProps> = ({
  gapId,
  recommendations,
  outsourcingOptions = [],
  selectedOutsourcingOptionId,
  loading = false,
  canApprove = false,
  canPurchase = false,
  canAdjust = false,
  compact = false,
  onAccept,
  onReject,
  onPurchase,
  onAdjust,
  onSelectOutsourcing,
  onOutsourcingOptionChange,
}) => {
  const [sortBy, setSortBy] = useState<'score' | 'cost' | 'leadTime'>('score');
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [outsourceModalOpen, setOutsourceModalOpen] = useState(false);
  const [currentRec, setCurrentRec] = useState<EquipmentRecommendation | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [selectedOptionId, setSelectedOptionId] = useState<string | undefined>(
    selectedOutsourcingOptionId
  );
  const [submitting, setSubmitting] = useState(false);

  // -------------------------------------------------------------------------
  // Производные значения
  // -------------------------------------------------------------------------

  const sorted = useMemo(() => {
    const list = [...recommendations];
    list.sort((a, b) => {
      switch (sortBy) {
        case 'cost':
          return (a.estimatedCost ?? Infinity) - (b.estimatedCost ?? Infinity);
        case 'leadTime':
          return (a.leadTimeDays ?? Infinity) - (b.leadTimeDays ?? Infinity);
        case 'score':
        default:
          return (b.score ?? 0) - (a.score ?? 0);
      }
    });
    return list;
  }, [recommendations, sortBy]);

  const bestRecommendation = useMemo(() => {
    if (sorted.length === 0) return null;
    return sorted.reduce((best, rec) => {
      if (!best) return rec;
      return (rec.score ?? 0) > (best.score ?? 0) ? rec : best;
    }, null as EquipmentRecommendation | null);
  }, [sorted]);

  const summary = useMemo(() => {
    const pending = recommendations.filter((r) => r.status === 'PENDING').length;
    const accepted = recommendations.filter((r) => r.status === 'ACCEPTED').length;
    const rejected = recommendations.filter((r) => r.status === 'REJECTED').length;

    const minCost = recommendations
      .filter((r) => r.estimatedCost != null)
      .reduce(
        (min, r) => Math.min(min, Number(r.estimatedCost)),
        Number.POSITIVE_INFINITY
      );

    const minLeadTime = recommendations
      .filter((r) => r.leadTimeDays != null)
      .reduce(
        (min, r) => Math.min(min, Number(r.leadTimeDays)),
        Number.POSITIVE_INFINITY
      );

    return {
      total: recommendations.length,
      pending,
      accepted,
      rejected,
      minCost: isFinite(minCost) ? minCost : null,
      minLeadTime: isFinite(minLeadTime) ? minLeadTime : null,
    };
  }, [recommendations]);

  // -------------------------------------------------------------------------
  // Обработчики
  // -------------------------------------------------------------------------

  const handleAccept = useCallback(
    async (rec: EquipmentRecommendation) => {
      if (!onAccept) return;
      setSubmitting(true);
      try {
        await onAccept(rec);
      } catch (e: any) {
        message.error(e?.message || 'Ошибка принятия рекомендации');
      } finally {
        setSubmitting(false);
      }
    },
    [onAccept]
  );

  const handleOpenReject = useCallback((rec: EquipmentRecommendation) => {
    setCurrentRec(rec);
    setRejectReason('');
    setRejectModalOpen(true);
  }, []);

  const handleConfirmReject = useCallback(async () => {
    if (!currentRec || !onReject) return;
    setSubmitting(true);
    try {
      await onReject(currentRec, rejectReason);
      message.success('Рекомендация отклонена');
      setRejectModalOpen(false);
      setCurrentRec(null);
    } catch (e: any) {
      message.error(e?.message || 'Ошибка отклонения');
    } finally {
      setSubmitting(false);
    }
  }, [currentRec, rejectReason, onReject]);

  const handlePurchase = useCallback(
    async (rec: EquipmentRecommendation) => {
      if (!onPurchase) return;
      setSubmitting(true);
      try {
        await onPurchase(rec);
      } catch (e: any) {
        message.error(e?.message || 'Ошибка');
      } finally {
        setSubmitting(false);
      }
    },
    [onPurchase]
  );

  const handleAdjust = useCallback(
    async (rec: EquipmentRecommendation) => {
      if (!onAdjust) return;
      setSubmitting(true);
      try {
        await onAdjust(rec);
      } catch (e: any) {
        message.error(e?.message || 'Ошибка');
      } finally {
        setSubmitting(false);
      }
    },
    [onAdjust]
  );

  const handleOpenOutsource = useCallback(
    (rec: EquipmentRecommendation) => {
      setCurrentRec(rec);
      setSelectedOptionId(selectedOutsourcingOptionId);
      setOutsourceModalOpen(true);
    },
    [selectedOutsourcingOptionId]
  );

  const handleConfirmOutsource = useCallback(async () => {
    if (!currentRec || !onSelectOutsourcing) return;

    const option = outsourcingOptions.find((o) => o.id === selectedOptionId);
    if (!option) {
      message.warning('Выберите подрядчика');
      return;
    }

    setSubmitting(true);
    try {
      await onSelectOutsourcing(currentRec, option);
      message.success('Подрядчик выбран');
      setOutsourceModalOpen(false);
      setCurrentRec(null);
    } catch (e: any) {
      message.error(e?.message || 'Ошибка выбора подрядчика');
    } finally {
      setSubmitting(false);
    }
  }, [currentRec, selectedOptionId, outsourcingOptions, onSelectOutsourcing]);

  const handleOptionChange = useCallback(
    (value: string) => {
      setSelectedOptionId(value);
      onOutsourcingOptionChange?.(value);
    },
    [onOutsourcingOptionChange]
  );

  // -------------------------------------------------------------------------
  // Рендер одной рекомендации
  // -------------------------------------------------------------------------

  const renderRecommendation = useCallback(
    (rec: EquipmentRecommendation, isBest: boolean) => {
      const typeConf = TYPE_CONFIG[rec.type] || TYPE_CONFIG.ALTERNATIVE;
      const statusConf = STATUS_CONFIG[rec.status] || STATUS_CONFIG.PENDING;

      const isPending = rec.status === 'PENDING';
      const isAccepted = rec.status === 'ACCEPTED';
      const isRejected = rec.status === 'REJECTED';

      const actions: React.ReactNode[] = [];

      if (isPending) {
        if (rec.type === 'ALTERNATIVE' && canApprove && onAccept) {
          actions.push(
            <Button
              key="accept"
              type="primary"
              size="small"
              icon={<CheckCircleOutlined />}
              onClick={() => handleAccept(rec)}
              loading={submitting}
            >
              Принять
            </Button>
          );
        }

        if ((rec.type === 'PURCHASE' || rec.type === 'RENT') && canPurchase && onPurchase) {
          actions.push(
            <Button
              key="purchase"
              size="small"
              icon={<DollarOutlined />}
              onClick={() => handlePurchase(rec)}
              loading={submitting}
            >
              {rec.type === 'RENT' ? 'Арендовать' : 'Закупить'}
            </Button>
          );
        }

        if (rec.type === 'OUTSOURCE' && canApprove && onSelectOutsourcing) {
          actions.push(
            <Button
              key="outsource"
              size="small"
              icon={<ShopOutlined />}
              onClick={() => handleOpenOutsource(rec)}
            >
              Выбрать подрядчика
            </Button>
          );
        }

        if (rec.type === 'ADJUST_PROCESS' && canAdjust && onAdjust) {
          actions.push(
            <Button
              key="adjust"
              size="small"
              icon={<ExperimentOutlined />}
              onClick={() => handleAdjust(rec)}
              loading={submitting}
            >
              Скорректировать ТП
            </Button>
          );
        }

        if (onReject) {
          actions.push(
            <Button
              key="reject"
              size="small"
              danger
              icon={<CloseCircleOutlined />}
              onClick={() => handleOpenReject(rec)}
            >
              Отклонить
            </Button>
          );
        }
      }

      return (
        <Card
          key={rec.id}
          size="small"
          style={{
            marginBottom: 12,
            borderColor: isBest && isPending ? '#2ea043' : undefined,
            borderWidth: isBest && isPending ? 2 : 1,
          }}
          title={
            <Space>
              <Tag color={typeConf.color} icon={typeConf.icon}>
                {typeConf.label}
              </Tag>
              {isBest && isPending && (
                <Tag color="green" icon={<TrophyOutlined />}>
                  Лучший вариант
                </Tag>
              )}
              <Badge status={statusConf.badge} text={statusConf.label} />
            </Space>
          }
          extra={
            rec.score != null && (
              <Tooltip title="Интегральный скор (0…1)">
                <Space>
                  <ThunderboltOutlined style={{ color: '#d29922' }} />
                  <Text strong>{rec.score.toFixed(3)}</Text>
                </Space>
              </Tooltip>
            )
          }
        >
          <Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 12 }}>
            {typeConf.description}
          </Paragraph>

          {rec.rationale && (
            <Paragraph style={{ marginBottom: 12 }} ellipsis={{ rows: 2, expandable: true }}>
              {rec.rationale}
            </Paragraph>
          )}

          {!compact && (
            <Descriptions size="small" column={{ xs: 1, sm: 2 }} style={{ marginBottom: 12 }}>
              {rec.equipmentId && (
                <Descriptions.Item label="Оборудование">
                  <Text code>{rec.equipmentId}</Text>
                </Descriptions.Item>
              )}
              {rec.vendorId && (
                <Descriptions.Item label="Поставщик">
                  <Text code>{rec.vendorId}</Text>
                </Descriptions.Item>
              )}
              {rec.estimatedCost != null && (
                <Descriptions.Item label="Стоимость">
                  <Text strong>{rec.estimatedCost.toLocaleString('ru-RU')} ₽</Text>
                </Descriptions.Item>
              )}
              {rec.leadTimeDays != null && (
                <Descriptions.Item label="Срок">
                  <Text>{rec.leadTimeDays} дн.</Text>
                </Descriptions.Item>
              )}
              {rec.acceptedBy && (
                <Descriptions.Item label="Принял">
                  {rec.acceptedBy}
                </Descriptions.Item>
              )}
              {rec.acceptedAt && (
                <Descriptions.Item label="Принято">
                  {new Date(rec.acceptedAt).toLocaleString('ru-RU')}
                </Descriptions.Item>
              )}
            </Descriptions>
          )}

          {actions.length > 0 && (
            <>
              <Divider style={{ margin: '8px 0' }} />
              <Space wrap>{actions}</Space>
            </>
          )}
        </Card>
      );
    },
    [
      canApprove,
      canPurchase,
      canAdjust,
      compact,
      submitting,
      handleAccept,
      handlePurchase,
      handleAdjust,
      handleOpenReject,
      handleOpenOutsource,
      onAccept,
      onPurchase,
      onAdjust,
      onReject,
      onSelectOutsourcing,
    ]
  );

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 40 }}>
        <Spin tip="Загрузка рекомендаций…">
          <div style={{ width: 200, height: 60 }} />
        </Spin>
      </div>
    );
  }

  if (recommendations.length === 0) {
    return (
      <Empty
        description="Рекомендации не сформированы"
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    );
  }

  return (
    <div className="recommendation-panel">
      {/* Сводка */}
      {!compact && (
        <Row gutter={12} style={{ marginBottom: 16 }}>
          <Col xs={12} sm={6}>
            <Card size="small">
              <Statistic
                title="Всего"
                value={summary.total}
                valueStyle={{ fontSize: 18 }}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small">
              <Statistic
                title="Ожидают"
                value={summary.pending}
                valueStyle={{ fontSize: 18, color: '#d29922' }}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small">
              <Statistic
                title="Принято"
                value={summary.accepted}
                valueStyle={{ fontSize: 18, color: '#2ea043' }}
              />
            </Card>
          </Col>
          <Col xs={12} sm={6}>
            <Card size="small">
              <Statistic
                title="Отклонено"
                value={summary.rejected}
                valueStyle={{ fontSize: 18, color: '#cf222e' }}
              />
            </Card>
          </Col>
        </Row>
      )}

      {/* Панель сортировки */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 12,
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Space>
          <BulbOutlined />
          <Text strong>Рекомендации ({recommendations.length})</Text>
        </Space>

        <Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Сортировка:
          </Text>
          <Segmented
            size="small"
            value={sortBy}
            onChange={(v) => setSortBy(v as 'score' | 'cost' | 'leadTime')}
            options={[
              { label: 'По скору', value: 'score' },
              { label: 'По стоимости', value: 'cost' },
              { label: 'По сроку', value: 'leadTime' },
            ]}
          />
        </Space>
      </div>

      {/* Список рекомендаций */}
      {sorted.map((rec) =>
        renderRecommendation(rec, bestRecommendation?.id === rec.id)
      )}

      {/* Модальное окно отклонения */}
      <Modal
        title="Отклонить рекомендацию?"
        open={rejectModalOpen}
        onCancel={() => {
          setRejectModalOpen(false);
          setCurrentRec(null);
        }}
        onOk={handleConfirmReject}
        confirmLoading={submitting}
        okText="Отклонить"
        okButtonProps={{ danger: true }}
        cancelText="Отмена"
      >
        <Paragraph type="secondary">
          Укажите причину отклонения (необязательно):
        </Paragraph>
        <Input.TextArea
          rows={3}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="Например: не подходит по срокам"
        />
      </Modal>

      {/* Модальное окно выбора подрядчика */}
      <Modal
        title="Выбор подрядчика для аутсорса"
        open={outsourceModalOpen}
        onCancel={() => {
          setOutsourceModalOpen(false);
          setCurrentRec(null);
        }}
        onOk={handleConfirmOutsource}
        confirmLoading={submitting}
        okText="Выбрать"
        cancelText="Отмена"
        width={720}
      >
        {outsourcingOptions.length === 0 ? (
          <Empty description="Варианты аутсорса не найдены" />
        ) : (
          <Radio.Group
            value={selectedOptionId}
            onChange={(e) => handleOptionChange(e.target.value)}
            style={{ width: '100%' }}
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              {outsourcingOptions.map((opt) => (
                <Card
                  key={opt.id}
                  size="small"
                  style={{
                    borderColor: selectedOptionId === opt.id ? '#1f6feb' : undefined,
                    borderWidth: selectedOptionId === opt.id ? 2 : 1,
                  }}
                >
                  <Radio value={opt.id}>
                    <Space>
                      <Text strong>{opt.contractor || '—'}</Text>
                      <Tag color="blue">{opt.type || 'CONTRACTOR'}</Tag>
                      {opt.cost != null && (
                        <Tag color="green">
                          {opt.cost.toLocaleString('ru-RU')} ₽
                        </Tag>
                      )}
                      {opt.leadTimeDays != null && (
                        <Tag color="orange">{opt.leadTimeDays} дн.</Tag>
                      )}
                      {opt.score != null && (
                        <Tag color="purple">Скор: {opt.score.toFixed(3)}</Tag>
                      )}
                    </Space>
                  </Radio>
                </Card>
              ))}
            </Space>
          </Radio.Group>
        )}
      </Modal>
    </div>
  );
};

export default RecommendationPanel;