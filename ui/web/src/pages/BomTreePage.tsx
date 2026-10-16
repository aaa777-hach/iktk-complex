import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FileTextOutlined,
  NodeExpandOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  TableOutlined,
  WarningOutlined,
} from '@ant-design/icons';

import { useAuthStore } from '../store/authStore';
import { useProjectStore } from '../store/projectStore';
import { ROLES } from '../api/constants';
import bomApi from '../api/bomApi';
import type {
  AssemblyConnection,
  BomItem,
  BomTree,
  Specification,
} from '../api/types';

const { Title, Text, Paragraph } = Typography;
const { TabPane } = Tabs;
const { Option } = Select;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

const ITEM_TYPES = [
  { value: 'PART', label: 'Деталь', color: 'blue' },
  { value: 'ASSEMBLY', label: 'Сборочная единица', color: 'purple' },
  { value: 'STANDARD', label: 'Стандартное изделие', color: 'cyan' },
  { value: 'FASTENER', label: 'Крепёж', color: 'orange' },
  { value: 'MATERIAL', label: 'Материал', color: 'green' },
];

const CONNECTION_TYPES = [
  { value: 'FIXED', label: 'Фиксированное' },
  { value: 'ROTATIONAL', label: 'Вращательное' },
  { value: 'SLIDING', label: 'Поступательное' },
  { value: 'WELDED', label: 'Сварное' },
  { value: 'THREADED', label: 'Резьбовое' },
  { value: 'GLUED', label: 'Клеевое' },
  { value: 'PRESSED', label: 'Прессовое' },
  { value: 'RIVETED', label: 'Заклёпочное' },
  { value: 'PINNED', label: 'Штифтовое' },
  { value: 'KEYED', label: 'Шпоночное' },
  { value: 'SPLINED', label: 'Шлицевое' },
  { value: 'OTHER', label: 'Прочее' },
];

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

/**
 * Преобразовать BomItem в DataNode для Ant Design Tree.
 */
function toTreeNode(item: BomItem): DataNode {
  const typeInfo = ITEM_TYPES.find((t) => t.value === item.type);
  const title = (
    <Space size={6}>
      <Tag color={typeInfo?.color || 'default'} style={{ margin: 0 }}>
        {item.position || '—'}
      </Tag>
      <Text strong>{item.designation || item.name}</Text>
      {item.designation && item.name && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {item.name}
        </Text>
      )}
      <Tag>{item.quantity} {item.unit || 'шт'}</Tag>
      {item.material && <Tag color="green">{item.material}</Tag>}
    </Space>
  );

  return {
    key: item.id,
    title,
    children: item.children?.map(toTreeNode),
  };
}

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Страница BOM (Bill of Materials) — состав изделия.
 *
 * <p>Возможности:
 * <ul>
 *   <li>запуск анализа сборочной единицы (извлечение BOM из CAD);</li>
 *   <li>просмотр дерева состава изделия;</li>
 *   <li>просмотр уплощённой таблицы позиций с фильтрами;</li>
 *   <li>ручная корректировка позиций (редактирование, добавление,
 *       удаление);</li>
 *   <li>просмотр связей и сопряжений сборки;</li>
 *   <li>просмотр рекомендуемой последовательности установки деталей;</li>
 *   <li>просмотр спецификации по ГОСТ 2.106;</li>
 *   <li>экспорт BOM в CSV/Excel/PDF;</li>
 *   <li>сводка по BOM (позиции, уровни, типы).</li>
 * </ul>
 *
 * <p>Доступ по ролям:
 * <ul>
 *   <li>чтение — все авторизованные роли;</li>
 *   <li>изменение BOM, запуск анализа — ENGINEER, ADMIN.</li>
 * </ul>
 */
const BomTreePage: React.FC = () => {
  const { id: projectId } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { hasRole } = useAuthStore();
  const { currentProject } = useProjectStore();

  const canEdit = hasRole(ROLES.ADMIN) || hasRole(ROLES.ENGINEER);

  // ---- Состояние ------------------------------------------------------
  const [bomTree, setBomTree] = useState<BomTree | null>(null);
  const [flatItems, setFlatItems] = useState<BomItem[]>([]);
  const [connections, setConnections] = useState<AssemblyConnection[]>([]);
  const [sequence, setSequence] = useState<BomItem[]>([]);
  const [specification, setSpecification] = useState<Specification | null>(null);
  const [summary, setSummary] = useState<Record<string, any> | null>(null);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState('tree');

  // ---- Форма редактирования позиции -----------------------------------
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BomItem | null>(null);
  const [parentId, setParentId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<BomItem>();

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
        const tree = await bomApi.getByProject(projectId);
        setBomTree(tree);

        if (tree?.id) {
          const [items, summaryData] = await Promise.all([
            bomApi.getItems(tree.id).catch(() => []),
            bomApi.getSummary(tree.id).catch(() => null),
          ]);
          setFlatItems(items);
          setSummary(summaryData);
        }
      } catch (e: any) {
        if (e?.response?.status === 404) {
          setBomTree(null);
        } else {
          const msg = e?.response?.data?.message || e?.message || 'Ошибка загрузки BOM';
          setError(msg);
          message.error(msg);
        }
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

  // Ленивая загрузка вкладок
  const loadConnections = useCallback(async () => {
    if (!bomTree?.id) return;
    try {
      const data = await bomApi.getConnections(bomTree.id);
      setConnections(data);
    } catch (e) {
      message.error('Не удалось загрузить связи');
    }
  }, [bomTree?.id]);

  const loadSequence = useCallback(async () => {
    if (!bomTree?.id) return;
    try {
      const data = await bomApi.getAssemblySequence(bomTree.id);
      setSequence(data);
    } catch (e) {
      message.error('Не удалось загрузить последовательность');
    }
  }, [bomTree?.id]);

  const loadSpecification = useCallback(async () => {
    if (!bomTree?.id) return;
    try {
      const data = await bomApi.getSpecification(bomTree.id);
      setSpecification(data);
    } catch (e) {
      message.error('Не удалось загрузить спецификацию');
    }
  }, [bomTree?.id]);

  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'connections' && connections.length === 0) loadConnections();
    if (key === 'sequence' && sequence.length === 0) loadSequence();
    if (key === 'specification' && !specification) loadSpecification();
  };

  // -------------------------------------------------------------------------
  // Анализ BOM
  // -------------------------------------------------------------------------

  const handleAnalyze = async () => {
    if (!projectId) return;
    Modal.confirm({
      title: 'Запустить анализ сборочной единицы?',
      content:
        'Система извлечёт структуру изделия (BOM) из CAD-модели проекта. ' +
        'Если BOM уже существует, он будет перезаписан.',
      okText: 'Запустить',
      cancelText: 'Отмена',
      onOk: async () => {
        setAnalyzing(true);
        try {
          const result = await bomApi.analyze(projectId, { force: true });
          message.success(
            `BOM извлечён: ${result.positionsCount} позиций, ${result.connectionsCount ?? 0} связей`
          );
          await loadAll(false);
        } catch (e: any) {
          const msg = e?.response?.data?.message || e?.message || 'Ошибка анализа';
          message.error(msg);
        } finally {
          setAnalyzing(false);
        }
      },
    });
  };

  // -------------------------------------------------------------------------
  // Ручная корректировка
  // -------------------------------------------------------------------------

  const handleCreate = (parentBomId?: string) => {
    setEditing(null);
    setParentId(parentBomId || null);
    form.resetFields();
    form.setFieldsValue({
      type: 'PART',
      quantity: 1,
      unit: 'шт',
    } as any);
    setFormOpen(true);
  };

  const handleEdit = (item: BomItem) => {
    setEditing(item);
    setParentId(item.parentId || null);
    form.setFieldsValue(item as any);
    setFormOpen(true);
  };

  const handleSave = async () => {
    if (!bomTree?.id) return;

    try {
      const values = await form.validateFields();
      setSaving(true);

      if (editing?.id) {
        await bomApi.updateItems(bomTree.id, {
          items: flatItems.map((i) =>
            i.id === editing.id ? { ...i, ...values } : i
          ),
        });
        message.success('Позиция обновлена');
      } else {
        await bomApi.addItem(bomTree.id, parentId, values);
        message.success('Позиция добавлена');
      }

      setFormOpen(false);
      setEditing(null);
      setParentId(null);
      loadAll(false);
    } catch (e: any) {
      if (e?.errorFields) return;
      const msg = e?.response?.data?.message || e?.message || 'Ошибка сохранения';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (item: BomItem) => {
    if (!bomTree?.id) return;
    try {
      await bomApi.deleteItem(bomTree.id, item.id);
      message.success('Позиция удалена');
      loadAll(false);
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Ошибка удаления';
      message.error(msg);
    }
  };

  // -------------------------------------------------------------------------
  // Экспорт
  // -------------------------------------------------------------------------

  const handleExport = async (format: 'CSV' | 'EXCEL' | 'PDF') => {
    if (!bomTree?.id) return;
    try {
      const response = await bomApi.export(bomTree.id, format);
      if (response.fileUri) {
        window.open(response.fileUri, '_blank');
      } else {
        message.success('Экспорт запущен');
      }
    } catch (e: any) {
      message.error('Ошибка экспорта');
    }
  };

  // -------------------------------------------------------------------------
  // Производные значения
  // -------------------------------------------------------------------------

  const treeData: DataNode[] = useMemo(() => {
    if (!bomTree?.items) return [];
    return bomTree.items.map(toTreeNode);
  }, [bomTree]);

  const flatColumns: ColumnsType<BomItem> = useMemo(
    () => [
      {
        title: 'Позиция',
        dataIndex: 'position',
        key: 'position',
        width: 90,
      },
      {
        title: 'Обозначение',
        dataIndex: 'designation',
        key: 'designation',
        width: 160,
      },
      {
        title: 'Наименование',
        dataIndex: 'name',
        key: 'name',
        ellipsis: true,
      },
      {
        title: 'Тип',
        dataIndex: 'type',
        key: 'type',
        width: 130,
        render: (value: string) => {
          const info = ITEM_TYPES.find((t) => t.value === value);
          return <Tag color={info?.color || 'default'}>{info?.label || value}</Tag>;
        },
      },
      {
        title: 'Кол-во',
        dataIndex: 'quantity',
        key: 'quantity',
        width: 90,
        align: 'right',
      },
      {
        title: 'Ед.',
        dataIndex: 'unit',
        key: 'unit',
        width: 70,
      },
      {
        title: 'Материал',
        dataIndex: 'material',
        key: 'material',
        width: 140,
      },
      {
        title: 'Уровень',
        dataIndex: 'level',
        key: 'level',
        width: 90,
        align: 'right',
      },
      {
        title: 'Действия',
        key: 'actions',
        width: 130,
        fixed: 'right',
        render: (_, record) => (
          <Space size={4}>
            {canEdit && (
              <>
                <Tooltip title="Редактировать">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => handleEdit(record)}
                  />
                </Tooltip>
                <Tooltip title="Добавить дочернюю">
                  <Button
                    type="text"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={() => handleCreate(record.id)}
                  />
                </Tooltip>
                <Popconfirm
                  title="Удалить позицию?"
                  description="Позиция и её потомки будут удалены."
                  onConfirm={() => handleDelete(record)}
                  okText="Удалить"
                  okButtonProps={{ danger: true }}
                  cancelText="Отмена"
                >
                  <Tooltip title="Удалить">
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                    />
                  </Tooltip>
                </Popconfirm>
              </>
            )}
          </Space>
        ),
      },
    ],
    [canEdit, flatItems]
  );

  const connectionColumns: ColumnsType<AssemblyConnection> = useMemo(
    () => [
      { title: 'Тип', dataIndex: 'type', key: 'type', width: 140 },
      { title: 'Деталь 1', dataIndex: 'part1Designation', key: 'part1' },
      { title: 'Деталь 2', dataIndex: 'part2Designation', key: 'part2' },
      { title: 'Порядок', dataIndex: 'order', key: 'order', width: 100, align: 'right' },
      {
        title: 'Инструмент',
        dataIndex: 'needsTool',
        key: 'needsTool',
        width: 120,
        render: (v: boolean) => (v ? 'Да' : '—'),
      },
      {
        title: 'Приспособление',
        dataIndex: 'needsFixture',
        key: 'needsFixture',
        width: 140,
        render: (v: boolean) => (v ? 'Да' : '—'),
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
        <Spin size="large" tip="Загрузка BOM…">
          <div style={{ width: 200, height: 80 }} />
        </Spin>
      </div>
    );
  }

  return (
    <div className="bom-tree-page">
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
        <Breadcrumb.Item>BOM</Breadcrumb.Item>
      </Breadcrumb>

      {/* Заголовок */}
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            <ApartmentOutlined /> Состав изделия (BOM)
          </Title>
          {bomTree && (
            <Text type="secondary">
              {bomTree.designation} — {bomTree.name}
            </Text>
          )}
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
                icon={<NodeExpandOutlined />}
                onClick={handleAnalyze}
                loading={analyzing}
              >
                {bomTree ? 'Переанализировать' : 'Анализировать сборку'}
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

      {/* Пустое состояние */}
      {!bomTree && !loading && (
        <Card>
          <Empty description="BOM не сформирован">
            {canEdit && (
              <Button
                type="primary"
                icon={<NodeExpandOutlined />}
                onClick={handleAnalyze}
                loading={analyzing}
              >
                Запустить анализ сборочной единицы
              </Button>
            )}
          </Empty>
        </Card>
      )}

      {/* Основной контент */}
      {bomTree && (
        <>
          {/* Сводка */}
          {summary && (
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col xs={12} sm={6}>
                <Card size="small">
                  <Statistic title="Всего позиций" value={summary.totalPositions ?? 0} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card size="small">
                  <Statistic title="Деталей" value={summary.parts ?? 0} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card size="small">
                  <Statistic title="Сборочных единиц" value={summary.assemblies ?? 0} />
                </Card>
              </Col>
              <Col xs={12} sm={6}>
                <Card size="small">
                  <Statistic title="Уровней" value={summary.maxDepth ?? 0} />
                </Card>
              </Col>
            </Row>
          )}

          {/* Вкладки */}
          <Card size="small">
            <Tabs activeKey={activeTab} onChange={handleTabChange} destroyInactiveTabPane>
              {/* Дерево */}
              <TabPane
                tab={
                  <span>
                    <ApartmentOutlined /> Дерево
                  </span>
                }
                key="tree"
              >
                <Space style={{ marginBottom: 12 }}>
                  {canEdit && (
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={() => handleCreate()}
                    >
                      Добавить корневую позицию
                    </Button>
                  )}
                  <Button
                    icon={<DownloadOutlined />}
                    onClick={() => handleExport('EXCEL')}
                  >
                    Экспорт Excel
                  </Button>
                  <Button
                    icon={<DownloadOutlined />}
                    onClick={() => handleExport('CSV')}
                  >
                    CSV
                  </Button>
                </Space>

                {treeData.length === 0 ? (
                  <Empty description="Позиции отсутствуют" />
                ) : (
                  <Tree
                    showLine
                    showIcon={false}
                    defaultExpandAll
                    treeData={treeData}
                    blockNode
                  />
                )}
              </TabPane>

              {/* Таблица */}
              <TabPane
                tab={
                  <span>
                    <TableOutlined /> Таблица
                  </span>
                }
                key="table"
              >
                <Table<BomItem>
                  rowKey="id"
                  columns={flatColumns}
                  dataSource={flatItems}
                  size="small"
                  scroll={{ x: 1100 }}
                  pagination={{ pageSize: 50, showSizeChanger: true }}
                />
              </TabPane>

              {/* Связи */}
              <TabPane
                tab={
                  <span>
                    <WarningOutlined /> Связи и сопряжения
                  </span>
                }
                key="connections"
              >
                {connections.length === 0 ? (
                  <Empty description="Связи отсутствуют" />
                ) : (
                  <Table<AssemblyConnection>
                    rowKey="id"
                    columns={connectionColumns}
                    dataSource={connections}
                    size="small"
                    pagination={{ pageSize: 50 }}
                  />
                )}
              </TabPane>

              {/* Последовательность */}
              <TabPane
                tab={
                  <span>
                    <NodeExpandOutlined /> Последовательность
                  </span>
                }
                key="sequence"
              >
                {sequence.length === 0 ? (
                  <Empty description="Последовательность не сформирована" />
                ) : (
                  <Table<BomItem>
                    rowKey="id"
                    columns={[
                      {
                        title: '№',
                        key: 'seq',
                        width: 60,
                        render: (_, __, index) => index + 1,
                      },
                      { title: 'Обозначение', dataIndex: 'designation', key: 'designation' },
                      { title: 'Наименование', dataIndex: 'name', key: 'name' },
                      {
                        title: 'Тип',
                        dataIndex: 'type',
                        key: 'type',
                        render: (value: string) => {
                          const info = ITEM_TYPES.find((t) => t.value === value);
                          return <Tag color={info?.color || 'default'}>{info?.label || value}</Tag>;
                        },
                      },
                    ]}
                    dataSource={sequence}
                    size="small"
                    pagination={{ pageSize: 100 }}
                  />
                )}
              </TabPane>

              {/* Спецификация */}
              <TabPane
                tab={
                  <span>
                    <FileTextOutlined /> Спецификация ГОСТ
                  </span>
                }
                key="specification"
              >
                {!specification ? (
                  <Spin />
                ) : (
                  <div>
                    {specification.sections?.map((section, idx) => (
                      <Card
                        key={idx}
                        size="small"
                        title={section.name}
                        style={{ marginBottom: 12 }}
                      >
                        <Table
                          rowKey={(r: any) => r.id || Math.random().toString()}
                          columns={[
                            { title: 'Позиция', dataIndex: 'position', width: 80 },
                            { title: 'Обозначение', dataIndex: 'designation', width: 160 },
                            { title: 'Наименование', dataIndex: 'name' },
                            { title: 'Кол-во', dataIndex: 'quantity', width: 90, align: 'right' },
                            { title: 'Ед.', dataIndex: 'unit', width: 70 },
                          ]}
                          dataSource={section.items || []}
                          size="small"
                          pagination={false}
                        />
                      </Card>
                    ))}
                  </div>
                )}
              </TabPane>
            </Tabs>
          </Card>
        </>
      )}

      {/* Модальное окно добавления/редактирования позиции */}
      <Modal
        title={editing ? 'Редактирование позиции' : 'Новая позиция BOM'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
          setParentId(null);
        }}
        onOk={handleSave}
        confirmLoading={saving}
        okText={editing ? 'Сохранить' : 'Добавить'}
        cancelText="Отмена"
        width={560}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="position" label="Позиция">
                <Input placeholder="1" />
              </Form.Item>
            </Col>
            <Col span={16}>
              <Form.Item name="designation" label="Обозначение">
                <Input placeholder="КР-001.01" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="name"
            label="Наименование"
            rules={[{ required: true, message: 'Укажите наименование' }]}
          >
            <Input placeholder="Вал" />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="type"
                label="Тип"
                rules={[{ required: true, message: 'Укажите тип' }]}
              >
                <Select>
                  {ITEM_TYPES.map((t) => (
                    <Option key={t.value} value={t.value}>
                      {t.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="material" label="Материал">
                <Input placeholder="Сталь 45" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="quantity"
                label="Количество"
                rules={[{ required: true, message: 'Укажите количество' }]}
              >
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="unit" label="Единица">
                <Select>
                  <Option value="шт">шт</Option>
                  <Option value="кг">кг</Option>
                  <Option value="м">м</Option>
                  <Option value="л">л</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="note" label="Примечание">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default BomTreePage;