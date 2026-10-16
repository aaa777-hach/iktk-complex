import React, { useMemo } from 'react';
import {
  Alert,
  Badge,
  Card,
  Descriptions,
  Progress,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  LoadingOutlined,
  MinusCircleOutlined,
  PauseCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';

import type { ProjectState } from '../api/types';
import {
  STATE_CATEGORIES,
  STATE_DESCRIPTIONS,
  STATE_LABELS,
  STATE_ORDER,
} from '../api/constants';

const { Text, Paragraph } = Typography;

// ---------------------------------------------------------------------------
// Типы
// ---------------------------------------------------------------------------

export interface StageStatusProps {
  /** Текущее состояние проекта (ProjectState). */
  state?: ProjectState;

  /** Список доступных событий для текущего состояния. */
  availableEvents?: string[];

  /** Прогресс выполнения проекта (0–100), если известен. */
  progressPercent?: number;

  /** Признак ожидания решения пользователя. */
  awaitingUserAction?: boolean;

  /** Сообщение от оркестратора (что ожидается или произошло). */
  message?: string;

  /** Компактный режим (без описания и прогресса). */
  compact?: boolean;
}

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Иконка по категории состояния.
 */
function iconForState(state?: ProjectState, awaiting?: boolean): React.ReactNode {
  if (!state) {
    return <MinusCircleOutlined style={{ color: '#8b949e' }} />;
  }
  if (state === 'COMPLETED') {
    return <CheckCircleOutlined style={{ color: '#2ea043' }} />;
  }
  if (awaiting) {
    return <PauseCircleOutlined style={{ color: '#d29922' }} />;
  }
  const category = STATE_CATEGORIES[state];
  switch (category) {
    case 'POINT_OF_CONTROL':
      return <ExclamationCircleOutlined style={{ color: '#d29922' }} />;
    case 'AUTOMATIC':
      return <SyncOutlined spin style={{ color: '#1f6feb' }} />;
    case 'TERMINAL':
      return <CheckCircleOutlined style={{ color: '#2ea043' }} />;
    default:
      return <ClockCircleOutlined style={{ color: '#8b949e' }} />;
  }
}

/**
 * Цвет тега по состоянию.
 */
function colorForState(state?: ProjectState, awaiting?: boolean): string {
  if (!state) return 'default';
  if (state === 'COMPLETED') return 'green';
  if (awaiting) return 'orange';
  const category = STATE_CATEGORIES[state];
  switch (category) {
    case 'POINT_OF_CONTROL':
      return 'orange';
    case 'AUTOMATIC':
      return 'blue';
    case 'TERMINAL':
      return 'green';
    default:
      return 'default';
  }
}

/**
 * Текст категории.
 */
function labelForCategory(state?: ProjectState): string {
  if (!state) return '—';
  const category = STATE_CATEGORIES[state];
  switch (category) {
    case 'AUTOMATIC':
      return 'Автоматический этап';
    case 'POINT_OF_CONTROL':
      return 'Точка контроля';
    case 'TERMINAL':
      return 'Завершение';
    default:
      return '—';
  }
}

/**
 * Позиция состояния в общем порядке (для прогресса).
 */
function orderIndex(state?: ProjectState): number {
  if (!state) return 0;
  const idx = STATE_ORDER.indexOf(state);
  return idx < 0 ? 0 : idx;
}

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Компонент отображения статуса текущего этапа ТПП.
 *
 * <p>Показывает:
 * <ul>
 *   <li>иконку и название состояния ({@code STATE_LABELS});</li>
 *   <li>категорию состояния: автоматический / точка контроля / терминальное;</li>
 *   <li>описание состояния ({@code STATE_DESCRIPTIONS});</li>
 *   <li>прогресс прохождения ТПП (если известен);</li>
 *   <li>список доступных событий (кнопки — на стороне родителя);</li>
 *   <li>предупреждение, если требуется решение пользователя.</li>
 * </ul>
 *
 * <p>В компактном режиме отображается одна строка — иконка, название
 * состояния и тег категории.
 */
const StageStatus: React.FC<StageStatusProps> = ({
  state,
  availableEvents,
  progressPercent,
  awaitingUserAction,
  message,
  compact = false,
}) => {
  const category = state ? STATE_CATEGORIES[state] : undefined;
  const isPointOfControl = category === 'POINT_OF_CONTROL';
  const isCompleted = state === 'COMPLETED';
  const isAwaiting = awaitingUserAction === true || isPointOfControl;

  const computedProgress = useMemo(() => {
    if (typeof progressPercent === 'number') {
      return progressPercent;
    }
    const idx = orderIndex(state);
    const total = STATE_ORDER.length - 1;
    return total > 0 ? Math.round((idx / total) * 100) : 0;
  }, [progressPercent, state]);

  // -------------------------------------------------------------------------
  // Компактный режим
  // -------------------------------------------------------------------------

  if (compact) {
    return (
      <Space size={8}>
        {iconForState(state, isAwaiting)}
        <Text strong>{state ? STATE_LABELS[state] ?? state : '—'}</Text>
        <Tag color={colorForState(state, isAwaiting)}>
          {labelForCategory(state)}
        </Tag>
      </Space>
    );
  }

  // -------------------------------------------------------------------------
  // Полный режим
  // -------------------------------------------------------------------------

  return (
    <Card
      size="small"
      bordered
      title={
        <Space>
          {iconForState(state, isAwaiting)}
          <Text strong>{state ? STATE_LABELS[state] ?? state : 'Состояние не задано'}</Text>
          <Tag color={colorForState(state, isAwaiting)}>
            {labelForCategory(state)}
          </Tag>
        </Space>
      }
      extra={
        isCompleted ? (
          <Badge status="success" text="Завершено" />
        ) : isAwaiting ? (
          <Badge status="warning" text="Ожидает решения" />
        ) : (
          <Badge status="processing" text="Выполняется" />
        )
      }
    >
      {message && (
        <Alert
          type={isAwaiting ? 'warning' : 'info'}
          showIcon
          message={message}
          style={{ marginBottom: 12 }}
        />
      )}

      {state && STATE_DESCRIPTIONS[state] && (
        <Paragraph
          type="secondary"
          style={{ marginBottom: 12, fontSize: 13 }}
        >
          {STATE_DESCRIPTIONS[state]}
        </Paragraph>
      )}

      <Descriptions
        size="small"
        column={1}
        bordered
        labelStyle={{ width: 160 }}
      >
        <Descriptions.Item label="Код состояния">
          <Text code>{state ?? '—'}</Text>
        </Descriptions.Item>

        <Descriptions.Item label="Категория">
          <Tag color={colorForState(state, isAwaiting)}>
            {labelForCategory(state)}
          </Tag>
        </Descriptions.Item>

        <Descriptions.Item label="Прогресс ТПП">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Progress
              percent={computedProgress}
              size="small"
              status={isCompleted ? 'success' : 'active'}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              Этап {orderIndex(state) + 1} из {STATE_ORDER.length}
            </Text>
          </Space>
        </Descriptions.Item>

        {availableEvents && availableEvents.length > 0 && (
          <Descriptions.Item label="Доступные события">
            <Space wrap size={4}>
              {availableEvents.map((ev) => (
                <Tooltip key={ev} title={`Событие конечного автомата: ${ev}`}>
                  <Tag color="blue" style={{ margin: 0 }}>
                    {ev}
                  </Tag>
                </Tooltip>
              ))}
            </Space>
          </Descriptions.Item>
        )}
      </Descriptions>

      {isAwaiting && !isCompleted && (
        <Alert
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          style={{ marginTop: 12 }}
          message="Требуется решение пользователя"
          description="Проект остановлен на точке контроля. Подтвердите этап, скорректируйте ТП или выберите другой вариант."
        />
      )}

      {isCompleted && (
        <Alert
          type="success"
          showIcon
          icon={<CheckCircleOutlined />}
          style={{ marginTop: 12 }}
          message="Проект завершён"
          description="Все этапы ТПП пройдены, документация сформирована."
        />
      )}
    </Card>
  );
};

export default StageStatus;