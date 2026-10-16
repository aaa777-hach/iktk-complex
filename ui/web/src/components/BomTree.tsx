import React, { useCallback, useMemo, useState } from 'react';
import {
  Button,
  Dropdown,
  Empty,
  Input,
  Space,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd';
import type { DataNode, EventDataNode } from 'antd/es/tree';
import type { MenuProps } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  MoreOutlined,
  NodeExpandOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';

import type { BomItem } from '../api/types';

const { Text } = Typography;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

/**
 * Конфигурация типов позиций BOM.
 */
export const BOM_ITEM_TYPES: Record<
  string,
  { label: string; color: string; short: string }
> = {
  PART: { label: 'Деталь', color: 'blue', short: 'Д' },
  ASSEMBLY: { label: 'Сборочная единица', color: 'purple', short: 'СБ' },
  STANDARD: { label: 'Стандартное изделие', color: 'cyan', short: 'СТ' },
  FASTENER: { label: 'Крепёж', color: 'orange', short: 'КР' },
  MATERIAL: { label: 'Материал', color: 'green', short: 'М' },
};

// ---------------------------------------------------------------------------
// Типы
// ---------------------------------------------------------------------------

export interface BomTreeProps {
  /** Корневые позиции BOM. */
  items: BomItem[];

  /** Разрешить редактирование (контекстное меню). */
  editable?: boolean;

  /** Разрешить множественный выбор. */
  multiple?: boolean;

  /** Выбранные ключи (контролируемый режим). */
  selectedKeys?: string[];

  /** Раскрытые ключи (контролируемый режим). */
  expandedKeys?: string[];

  /** Показывать панель поиска. */
  searchable?: boolean;

  /** Показывать легенду типов. */
  showLegend?: boolean;

  /** Компактный режим (без описаний и метаданных). */
  compact?: boolean;

  /** Клик по позиции. */
  onSelect?: (item: BomItem | null, selectedKeys: string[]) => void;

  /** Редактирование позиции. */
  onEdit?: (item: BomItem) => void;

  /** Добавление дочерней позиции. */
  onAddChild?: (parent: BomItem) => void;

  /** Удаление позиции. */
  onDelete?: (item: BomItem) => void;

  /** Двойной клик (например, для открытия карточки). */
  onDoubleClick?: (item: BomItem) => void;
}

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Найти элемент по ключу (рекурсивно).
 */
function findById(items: BomItem[], id: string): BomItem | null {
  for (const item of items) {
    if (item.id === id) return item;
    if (item.children && item.children.length > 0) {
      const found = findById(item.children, id);
      if (found) return found;
    }
  }
  return null;
}

/**
 * Собрать все ключи (для defaultExpandAll).
 */
function collectKeys(items: BomItem[]): string[] {
  const keys: string[] = [];
  const walk = (arr: BomItem[]) => {
    for (const item of arr) {
      if (item.children && item.children.length > 0) {
        keys.push(item.id);
        walk(item.children);
      }
    }
  };
  walk(items);
  return keys;
}

/**
 * Фильтрация дерева по поисковой строке.
 * Возвращает поддерево, содержащее совпадения, или null.
 */
function filterTree(items: BomItem[], query: string): BomItem[] {
  if (!query) return items;
  const q = query.toLowerCase();

  const matches = (item: BomItem): boolean => {
    const designation = (item.designation || '').toLowerCase();
    const name = (item.name || '').toLowerCase();
    const material = (item.material || '').toLowerCase();
    return (
      designation.includes(q) || name.includes(q) || material.includes(q)
    );
  };

  const filterRecursive = (arr: BomItem[]): BomItem[] => {
    const result: BomItem[] = [];
    for (const item of arr) {
      const children = item.children ? filterRecursive(item.children) : [];
      if (matches(item) || children.length > 0) {
        result.push({ ...item, children });
      }
    }
    return result;
  };

  return filterRecursive(items);
}

/**
 * Подсветка найденного текста.
 */
function highlight(text: string | undefined, query: string): React.ReactNode {
  if (!text || !query) return text || '';
  const q = query.toLowerCase();
  const lower = text.toLowerCase();
  const idx = lower.indexOf(q);
  if (idx < 0) return text;

  return (
    <>
      {text.slice(0, idx)}
      <Text mark>{text.slice(idx, idx + query.length)}</Text>
      {text.slice(idx + query.length)}
    </>
  );
}

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Переиспользуемый компонент дерева BOM (состав изделия).
 *
 * <p>Возможности:
 * <ul>
 *   <li>отображение иерархического дерева состава изделия;</li>
 *   <li>типы позиций с цветовой кодировкой и короткими тегами;</li>
 *   <li>поиск с подсветкой совпадений;</li>
 *   <li>выбор одной или нескольких позиций;</li>
 *   <li>контекстное меню: редактировать, добавить дочернюю, удалить;</li>
 *   <li>двойной клик — открытие карточки (например, детали);</li>
 *   <li>легенда типов позиций;</li>
 *   <li>компактный режим.</li>
 * </ul>
 *
 * <p>Используется в:
 * <ul>
 *   <li>{@code BomTreePage} (позиция 107) — как основная вкладка;</li>
 *   <li>карточке проекта — как сводка состава изделия;</li>
 *   <li>модальных окнах выбора детали.</li>
 * </ul>
 *
 * <p>Компонент управляемый: если заданы {@code selectedKeys} или
 * {@code expandedKeys}, использует их как контролируемые значения.
 */
const BomTree: React.FC<BomTreeProps> = ({
  items,
  editable = false,
  multiple = false,
  selectedKeys: controlledSelectedKeys,
  expandedKeys: controlledExpandedKeys,
  searchable = true,
  showLegend = true,
  compact = false,
  onSelect,
  onEdit,
  onAddChild,
  onDelete,
  onDoubleClick,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [internalSelectedKeys, setInternalSelectedKeys] = useState<string[]>([]);
  const [internalExpandedKeys, setInternalExpandedKeys] = useState<string[]>(() =>
    collectKeys(items)
  );

  const selectedKeys = controlledSelectedKeys ?? internalSelectedKeys;
  const expandedKeys = controlledExpandedKeys ?? internalExpandedKeys;

  // -------------------------------------------------------------------------
  // Фильтрация
  // -------------------------------------------------------------------------

  const filteredItems = useMemo(
    () => filterTree(items, searchQuery),
    [items, searchQuery]
  );

  // -------------------------------------------------------------------------
  // Преобразование в DataNode
  // -------------------------------------------------------------------------

  const buildNode = useCallback(
    (item: BomItem): DataNode => {
      const typeConf = BOM_ITEM_TYPES[item.type] || BOM_ITEM_TYPES.PART;

      const title = (
        <Space size={6} wrap>
          {item.position && (
            <Tag color="default" style={{ margin: 0, minWidth: 32, textAlign: 'center' }}>
              {item.position}
            </Tag>
          )}

          <Tag color={typeConf.color} style={{ margin: 0 }}>
            {typeConf.short}
          </Tag>

          <Text strong ellipsis style={{ maxWidth: compact ? 200 : 320 }}>
            {highlight(item.designation || item.name, searchQuery)}
          </Text>

          {item.designation && item.name && (
            <Text
              type="secondary"
              style={{ fontSize: 12 }}
              ellipsis
            >
              {highlight(item.name, searchQuery)}
            </Text>
          )}

          <Tag color="blue" style={{ margin: 0 }}>
            {item.quantity} {item.unit || 'шт'}
          </Tag>

          {item.material && !compact && (
            <Tag color="green" style={{ margin: 0 }}>
              {highlight(item.material, searchQuery)}
            </Tag>
          )}

          {editable && (
            <Dropdown
              trigger={['click']}
              menu={buildContextMenu(item)}
            >
              <Button
                type="text"
                size="small"
                icon={<MoreOutlined />}
                onClick={(e) => e.stopPropagation()}
              />
            </Dropdown>
          )}
        </Space>
      );

      return {
        key: item.id,
        title,
        children: item.children?.map(buildNode),
      };
    },
    [searchQuery, editable, compact, onEdit, onAddChild, onDelete]
  );

  /**
   * Контекстное меню позиции.
   */
  const buildContextMenu = useCallback(
    (item: BomItem): MenuProps => {
      const items: MenuProps['items'] = [];

      if (onEdit) {
        items.push({
          key: 'edit',
          icon: <EditOutlined />,
          label: 'Редактировать',
          onClick: () => onEdit(item),
        });
      }

      if (onAddChild) {
        items.push({
          key: 'add',
          icon: <PlusOutlined />,
          label: 'Добавить дочернюю',
          onClick: () => onAddChild(item),
        });
      }

      if (onDelete) {
        items.push({ type: 'divider' });
        items.push({
          key: 'delete',
          icon: <DeleteOutlined />,
          label: 'Удалить',
          danger: true,
          onClick: () => onDelete(item),
        });
      }

      return { items };
    },
    [onEdit, onAddChild, onDelete]
  );

  const treeData = useMemo(
    () => filteredItems.map(buildNode),
    [filteredItems, buildNode]
  );

  // -------------------------------------------------------------------------
  // Обработчики
  // -------------------------------------------------------------------------

  const handleSelect = useCallback(
    (keys: React.Key[], info: { node: EventDataNode<DataNode> }) => {
      const stringKeys = keys.map(String);

      if (controlledSelectedKeys === undefined) {
        setInternalSelectedKeys(stringKeys);
      }

      const item = stringKeys.length > 0
        ? findById(items, stringKeys[stringKeys.length - 1])
        : null;

      onSelect?.(item, stringKeys);
    },
    [items, controlledSelectedKeys, onSelect]
  );

  const handleExpand = useCallback(
    (keys: React.Key[]) => {
      const stringKeys = keys.map(String);
      if (controlledExpandedKeys === undefined) {
        setInternalExpandedKeys(stringKeys);
      }
    },
    [controlledExpandedKeys]
  );

  const handleDoubleClick = useCallback(
    (_: React.MouseEvent, node: EventDataNode<DataNode>) => {
      const item = findById(items, String(node.key));
      if (item && onDoubleClick) {
        onDoubleClick(item);
      }
    },
    [items, onDoubleClick]
  );

  const handleExpandAll = useCallback(() => {
    const allKeys = collectKeys(filteredItems);
    if (controlledExpandedKeys === undefined) {
      setInternalExpandedKeys(allKeys);
    }
  }, [filteredItems, controlledExpandedKeys]);

  const handleCollapseAll = useCallback(() => {
    if (controlledExpandedKeys === undefined) {
      setInternalExpandedKeys([]);
    }
  }, [controlledExpandedKeys]);

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  if (!items || items.length === 0) {
    return (
      <Empty
        description="Состав изделия пуст"
        style={{ padding: 24 }}
      />
    );
  }

  return (
    <div className="bom-tree-component">
      {/* Панель управления */}
      {(searchable || showLegend) && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 12,
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          {searchable && (
            <Input
              placeholder="Поиск по обозначению, наименованию, материалу"
              prefix={<SearchOutlined />}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              allowClear
              style={{ maxWidth: 360 }}
            />
          )}

          <Space size={4}>
            <Tooltip title="Развернуть все">
              <Button
                size="small"
                icon={<NodeExpandOutlined rotate={90} />}
                onClick={handleExpandAll}
              />
            </Tooltip>
            <Tooltip title="Свернуть все">
              <Button
                size="small"
                icon={<NodeExpandOutlined rotate={-90} />}
                onClick={handleCollapseAll}
              />
            </Tooltip>
          </Space>
        </div>
      )}

      {/* Легенда */}
      {showLegend && (
        <div
          style={{
            marginBottom: 12,
            padding: '6px 10px',
            background: 'rgba(22,27,34,0.5)',
            border: '1px solid #30363d',
            borderRadius: 6,
            display: 'flex',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          {Object.entries(BOM_ITEM_TYPES).map(([key, conf]) => (
            <Space key={key} size={4}>
              <Tag color={conf.color} style={{ margin: 0 }}>
                {conf.short}
              </Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {conf.label}
              </Text>
            </Space>
          ))}
        </div>
      )}

      {/* Дерево */}
      {treeData.length === 0 ? (
        <Empty
          description={
            searchQuery
              ? `Ничего не найдено по запросу «${searchQuery}»`
              : 'Состав изделия пуст'
          }
        />
      ) : (
        <Tree
          showLine={{ showLeafIcon: false }}
          showIcon={false}
          blockNode
          multiple={multiple}
          treeData={treeData}
          selectedKeys={selectedKeys}
          expandedKeys={expandedKeys}
          onSelect={handleSelect}
          onExpand={handleExpand}
          onDoubleClick={handleDoubleClick}
        />
      )}
    </div>
  );
};

export default BomTree;