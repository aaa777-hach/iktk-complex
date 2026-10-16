import React, { useMemo } from 'react';
import {
  Card,
  Descriptions,
  Divider,
  Empty,
  Space,
  Statistic,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  FieldTimeOutlined,
  ThunderboltOutlined,
  ToolOutlined,
} from '@ant-design/icons';

import type {
  Equipment,
  EquipmentCapability,
  EquipmentStatus,
} from '../api/types';

const { Text, Paragraph } = Typography;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

/**
 * Конфигурация статусов оборудования.
 */
const STATUS_CONFIG: Record<
  string,
  { label: string; color: string; icon: React.ReactNode }
> = {
  AVAILABLE: {
    label: 'Доступно',
    color: 'green',
    icon: <CheckCircleOutlined />,
  },
  BUSY: {
    label: 'Занято',
    color: 'orange',
    icon: <ClockCircleOutlined />,
  },
  MAINTENANCE: {
    label: 'Обслуживание',
    color: 'blue',
    icon: <ToolOutlined />,
  },
  DECOMMISSIONED: {
    label: 'Списано',
    color: 'default',
    icon: <CloseCircleOutlined />,
  },
};

/**
 * Конфигурация типов оборудования.
 */
const TYPE_LABELS: Record<string, string> = {
  TURNING: 'Токарное',
  MILLING: 'Фрезерное',
  DRILLING: 'Сверлильное',
  GRINDING: 'Шлифовальное',
  PRESS: 'Прессовое',
  CASTING: 'Литейное',
  WELDING: 'Сварочное',
  EDM: 'Электроэрозионное',
  LASER: 'Лазерное',
  OTHER: 'Прочее',
};

// ---------------------------------------------------------------------------
// Типы
// ---------------------------------------------------------------------------

export interface EquipmentCardProps {
  /** Оборудование. */
  equipment: Equipment;

  /** Возможности (опционально, для подробного отображения). */
  capabilities?: EquipmentCapability[];

  /** Состояние и загрузка (опционально). */
  status?: EquipmentStatus;

  /** Компактный режим (без секции возможностей). */
  compact?: boolean;

  /** Показывать ли секцию «Возможности». */
  showCapabilities?: boolean;

  /** Показывать ли секцию «Состояние». */
  showStatus?: boolean;

  /** Клик по карточке. */
  onClick?: (equipment: Equipment) => void;
}

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Карточка единицы оборудования ИКТК.
 *
 * <p>Отображает паспорт станка:
 * <ul>
 *   <li>наименование, тип, модель, инвентарный и серийный номера;</li>
 *   <li>расположение: предприятие, подразделение, цех/участок;</li>
 *   <li>статус: AVAILABLE, BUSY, MAINTENANCE, DECOMMISSIONED;</li>
 *   <li>текущую загрузку и состояние (опционально);</li>
 *   <li>возможности: операции, точность, габариты, ЧПУ,
 *       материалы, мощность, усилие (опционально);</li>
 *   <li>ответственного и даты эксплуатации.</li>
 * </ul>
 *
 * <p>Используется в:
 * <ul>
 *   <li>реестре оборудования ({@code EquipmentRegistryPage});</li>
 *   <li>карточке проекта (при выборе оборудования);</li>
 *   <li>рекомендациях (при отображении альтернативы).</li>
 * </ul>
 */
const EquipmentCard: React.FC<EquipmentCardProps> = ({
  equipment,
  capabilities,
  status,
  compact = false,
  showCapabilities,
  showStatus,
  onClick,
}) => {
  const statusConf = STATUS_CONFIG[equipment.status] || STATUS_CONFIG.DECOMMISSIONED;
  const typeLabel = TYPE_LABELS[equipment.type] || equipment.type || '—';

  // По умолчанию в полном режиме показываем всё
  const displayCapabilities = showCapabilities ?? !compact;
  const displayStatus = showStatus ?? !compact;

  const capabilitySummary = useMemo(() => {
    if (!capabilities || capabilities.length === 0) {
      return { count: 0, cnc: 0, maxPower: 0 };
    }

    const cnc = capabilities.filter((c) => c.cncCapable).length;
    const maxPower = Math.max(
      ...capabilities.map((c) => Number(c.powerKw) || 0),
      0
    );

    return { count: capabilities.length, cnc, maxPower };
  }, [capabilities]);

  const isClickable = Boolean(onClick);

  return (
    <Card
      size={compact ? 'small' : 'default'}
      hoverable={isClickable}
      onClick={isClickable ? () => onClick!(equipment) : undefined}
      title={
        <Space>
          <ApartmentOutlined />
          <Text strong ellipsis style={{ maxWidth: 280 }}>
            {equipment.name}
          </Text>
        </Space>
      }
      extra={
        <Tooltip title={statusConf.label}>
          <Tag color={statusConf.color} icon={statusConf.icon}>
            {statusConf.label}
          </Tag>
        </Tooltip>
      }
    >
      {/* Секция: паспорт ---------------------------------------------------- */}
      <Descriptions
        size="small"
        column={compact ? 1 : 2}
        bordered
        labelStyle={{ width: compact ? 110 : 130 }}
      >
        <Descriptions.Item label="Тип">{typeLabel}</Descriptions.Item>
        <Descriptions.Item label="Модель">
          {equipment.model || '—'}
        </Descriptions.Item>

        <Descriptions.Item label="Инв. №">
          {equipment.inventoryNo || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Серийный №">
          {equipment.serialNo || '—'}
        </Descriptions.Item>

        {!compact && (
          <>
            <Descriptions.Item label="Производитель">
              {equipment.manufacturer || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Год выпуска">
              {equipment.manufactureYear || '—'}
            </Descriptions.Item>

            <Descriptions.Item label="Предприятие">
              {equipment.enterpriseCode || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Подразделение">
              {equipment.departmentCode || '—'}
            </Descriptions.Item>

            <Descriptions.Item label="Расположение" span={2}>
              {equipment.location || '—'}
            </Descriptions.Item>

            {equipment.responsible && (
              <Descriptions.Item label="Ответственный" span={2}>
                {equipment.responsible}
              </Descriptions.Item>
            )}

            {equipment.commissionedAt && (
              <Descriptions.Item label="Введено в эксплуатацию">
                {new Date(equipment.commissionedAt).toLocaleDateString('ru-RU')}
              </Descriptions.Item>
            )}
            {equipment.decommissionedAt && (
              <Descriptions.Item label="Списано">
                {new Date(equipment.decommissionedAt).toLocaleDateString('ru-RU')}
              </Descriptions.Item>
            )}
          </>
        )}
      </Descriptions>

      {/* Секция: состояние ------------------------------------------------- */}
      {displayStatus && status && (
        <>
          <Divider orientation="left" style={{ marginTop: 12, marginBottom: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Состояние
            </Text>
          </Divider>

          <Space size={24} wrap>
            <Statistic
              title="Загрузка"
              value={status.loadPercent ?? 0}
              suffix="%"
              valueStyle={{
                fontSize: 18,
                color:
                  (status.loadPercent ?? 0) >= 90
                    ? '#cf222e'
                    : (status.loadPercent ?? 0) >= 70
                    ? '#d29922'
                    : '#2ea043',
              }}
            />
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Состояние
              </Text>
              <div>
                <Tag color={statusConf.color}>{status.state || statusConf.label}</Tag>
              </div>
            </div>
            {status.schedule && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  График
                </Text>
                <div>
                  <Tag icon={<FieldTimeOutlined />}>{status.schedule}</Tag>
                </div>
              </div>
            )}
          </Space>
        </>
      )}

      {/* Секция: возможности ---------------------------------------------- */}
      {displayCapabilities && (
        <>
          <Divider orientation="left" style={{ marginTop: 12, marginBottom: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Возможности
            </Text>
          </Divider>

          {!capabilities || capabilities.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="Возможности не заданы"
              style={{ margin: 0 }}
            />
          ) : (
            <>
              <Space size={24} style={{ marginBottom: 8 }} wrap>
                <Statistic
                  title="Операций"
                  value={capabilitySummary.count}
                  valueStyle={{ fontSize: 16 }}
                />
                <Statistic
                  title="С ЧПУ"
                  value={capabilitySummary.cnc}
                  valueStyle={{ fontSize: 16, color: '#1f6feb' }}
                />
                {capabilitySummary.maxPower > 0 && (
                  <Statistic
                    title="Мощность, кВт"
                    value={capabilitySummary.maxPower}
                    valueStyle={{ fontSize: 16, color: '#d29922' }}
                  />
                )}
              </Space>

              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                {capabilities.slice(0, compact ? 3 : 10).map((cap, index) => (
                  <div
                    key={cap.id || index}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '4px 0',
                      borderBottom: '1px solid #30363d',
                    }}
                  >
                    <Tag color="blue" style={{ margin: 0, minWidth: 100 }}>
                      {cap.operationType}
                    </Tag>
                    {cap.tolerance && (
                      <Tooltip title="Точность">
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {cap.tolerance}
                        </Text>
                      </Tooltip>
                    )}
                    {cap.maxDimensions && (
                      <Tooltip title="Габариты">
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {cap.maxDimensions}
                        </Text>
                      </Tooltip>
                    )}
                    {cap.cncCapable && (
                      <Tag color="purple" style={{ margin: 0 }}>
                        ЧПУ
                      </Tag>
                    )}
                    {cap.powerKw != null && (
                      <Tag
                        color="orange"
                        icon={<ThunderboltOutlined />}
                        style={{ margin: 0 }}
                      >
                        {cap.powerKw} кВт
                      </Tag>
                    )}
                  </div>
                ))}

                {capabilities.length > (compact ? 3 : 10) && (
                  <Paragraph
                    type="secondary"
                    style={{ margin: 0, fontSize: 12, textAlign: 'center' }}
                  >
                    … и ещё {capabilities.length - (compact ? 3 : 10)}
                  </Paragraph>
                )}
              </Space>
            </>
          )}
        </>
      )}
    </Card>
  );
};

export default EquipmentCard;