import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
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
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  FilterOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons';

import { useAuthStore } from '../store/authStore';
import { ROLES } from '../api/constants';
import equipmentApi from '../api/equipmentApi';
import type {
  Equipment,
  EquipmentCapability,
  EquipmentStatus,
  EquipmentImportResult,
} from '../api/types';

const { Title, Text } = Typography;
const { Option } = Select;

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

const EQUIPMENT_TYPES = [
  { value: 'TURNING', label: 'Токарное' },
  { value: 'MILLING', label: 'Фрезерное' },
  { value: 'DRILLING', label: 'Сверлильное' },
  { value: 'GRINDING', label: 'Шлифовальное' },
  { value: 'PRESS', label: 'Прессовое' },
  { value: 'CASTING', label: 'Литейное' },
  { value: 'WELDING', label: 'Сварочное' },
  { value: 'EDM', label: 'Электроэрозионное' },
  { value: 'LASER', label: 'Лазерное' },
  { value: 'OTHER', label: 'Прочее' },
];

const EQUIPMENT_STATUSES = [
  { value: 'AVAILABLE', label: 'Доступно', color: 'green' },
  { value: 'BUSY', label: 'Занято', color: 'orange' },
  { value: 'MAINTENANCE', label: 'Обслуживание', color: 'blue' },
  { value: 'DECOMMISSIONED', label: 'Списано', color: 'default' },
];

// ---------------------------------------------------------------------------
// Компонент
// ---------------------------------------------------------------------------

/**
 * Страница реестра оборудования ИКТК.
 *
 * <p>Возможности:
 * <ul>
 *   <li>постраничный список оборудования с фильтрами (тип, статус,
 *       предприятие, поиск);</li>
 *   <li>создание, редактирование, удаление (архивация) оборудования;</li>
 *   <li>массовый импорт из CSV/XLSX;</li>
 *   <li>экспорт списка;</li>
 *   <li>просмотр карточки оборудования с возможностями, состоянием
 *       и совместимой оснасткой;</li>
 *   <li>управление возможностями (в карточке);</li>
 *   <li>сводка по парку: всего, доступно, занято, обслуживание.</li>
 * </ul>
 *
 * <p>Доступ по ролям:
 * <ul>
 *   <li>чтение — все авторизованные роли;</li>
 *   <li>создание/редактирование — ENGINEER, ADMIN;</li>
 *   <li>удаление, импорт — ADMIN.</li>
 * </ul>
 */
const EquipmentRegistryPage: React.FC = () => {
  const navigate = useNavigate();
  const { hasRole } = useAuthStore();

  const canEdit = hasRole(ROLES.ADMIN) || hasRole(ROLES.ENGINEER);
  const canDelete = hasRole(ROLES.ADMIN);
  const canImport = hasRole(ROLES.ADMIN);

  // ---- Состояние ------------------------------------------------------
  const [items, setItems] = useState<Equipment[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 20,
    total: 0,
  });

  const [filters, setFilters] = useState({
    type: undefined as string | undefined,
    status: undefined as string | undefined,
    enterpriseCode: undefined as string | undefined,
    search: '',
  });

  const [summary, setSummary] = useState({
    total: 0,
    available: 0,
    busy: 0,
    maintenance: 0,
  });

  // ---- Модальное окно создания/редактирования -------------------------
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Equipment | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<Equipment>();

  // ---- Drawer карточки ------------------------------------------------
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selected, setSelected] = useState<Equipment | null>(null);
  const [selectedCapabilities, setSelectedCapabilities] = useState<EquipmentCapability[]>([]);
  const [selectedStatus, setSelectedStatus] = useState<EquipmentStatus | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  // ---- Импорт ---------------------------------------------------------
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);

  // -------------------------------------------------------------------------
  // Загрузка данных
  // -------------------------------------------------------------------------

  const loadList = useCallback(
    async (showSpinner = true) => {
      if (showSpinner) setLoading(true);
      setRefreshing(true);
      setError(null);

      try {
        const response = await equipmentApi.list({
          type: filters.type,
          status: filters.status,
          enterpriseCode: filters.enterpriseCode,
          search: filters.search || undefined,
          page: pagination.current - 1,
          size: pagination.pageSize,
          sortBy: 'name',
          sortDir: 'asc',
        });

        setItems(response.content);
        setPagination((prev) => ({
          ...prev,
          total: response.totalElements,
        }));
      } catch (e: any) {
        const msg = e?.response?.data?.message || e?.message || 'Ошибка загрузки оборудования';
        setError(msg);
        message.error(msg);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [filters, pagination.current, pagination.pageSize]
  );

  const loadSummary = useCallback(async () => {
    try {
      const data = await equipmentApi.getSummary();
      setSummary({
        total: data.total ?? 0,
        available: data.available ?? 0,
        busy: data.busy ?? 0,
        maintenance: data.maintenance ?? 0,
      });
    } catch (e) {
      // Тихо игнорируем — сводка не критична
      // eslint-disable-next-line no-console
      console.warn('Не удалось загрузить сводку по оборудованию', e);
    }
  }, []);

  useEffect(() => {
    loadList(true);
    loadSummary();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters, pagination.current, pagination.pageSize]);

  // -------------------------------------------------------------------------
  // CRUD
  // -------------------------------------------------------------------------

  const handleCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      status: 'AVAILABLE',
      type: 'TURNING',
    } as any);
    setFormOpen(true);
  };

  const handleEdit = (record: Equipment) => {
    setEditing(record);
    form.setFieldsValue(record as any);
    setFormOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);

      if (editing?.id) {
        await equipmentApi.update(editing.id, values);
        message.success('Оборудование обновлено');
      } else {
        await equipmentApi.create(values);
        message.success('Оборудование добавлено');
      }

      setFormOpen(false);
      setEditing(null);
      loadList(false);
      loadSummary();
    } catch (e: any) {
      if (e?.errorFields) return; // валидация
      const msg = e?.response?.data?.message || e?.message || 'Ошибка сохранения';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: Equipment) => {
    try {
      await equipmentApi.delete(record.id!);
      message.success('Оборудование списано (архивировано)');
      loadList(false);
      loadSummary();
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Ошибка удаления';
      message.error(msg);
    }
  };

  // -------------------------------------------------------------------------
  // Карточка оборудования
  // -------------------------------------------------------------------------

  const handleOpenCard = async (record: Equipment) => {
    setSelected(record);
    setDrawerOpen(true);
    setDrawerLoading(true);

    try {
      const [capabilities, status] = await Promise.all([
        equipmentApi.getCapabilities(record.id!),
        equipmentApi.getStatus(record.id!).catch(() => null),
      ]);
      setSelectedCapabilities(capabilities);
      setSelectedStatus(status);
    } catch (e: any) {
      message.error('Не удалось загрузить карточку оборудования');
    } finally {
      setDrawerLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // Импорт
  // -------------------------------------------------------------------------

  const handleImport = async (file: File, updateExisting: boolean, dryRun: boolean) => {
    setImporting(true);
    try {
      const result: EquipmentImportResult = await equipmentApi.importFile(
        file,
        updateExisting,
        dryRun
      );
      Modal.info({
        title: dryRun ? 'Проверка импорта (dry-run)' : 'Импорт завершён',
        content: (
          <div>
            <p>Всего строк: {result.total}</p>
            <p>Создано: {result.created}</p>
            <p>Обновлено: {result.updated}</p>
            <p>Пропущено: {result.skipped}</p>
            {result.errors && result.errors.length > 0 && (
              <>
                <p>Ошибок: {result.errors.length}</p>
                <ul style={{ maxHeight: 200, overflow: 'auto' }}>
                  {result.errors.slice(0, 20).map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        ),
      });
      setImportOpen(false);
      if (!dryRun) {
        loadList(false);
        loadSummary();
      }
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Ошибка импорта';
      message.error(msg);
    } finally {
      setImporting(false);
    }
  };

  // -------------------------------------------------------------------------
  // Экспорт
  // -------------------------------------------------------------------------

  const handleExport = async (format: 'CSV' | 'EXCEL') => {
    try {
      const blob = await equipmentApi.export(format);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `equipment-${new Date().toISOString().slice(0, 10)}.${format === 'CSV' ? 'csv' : 'xlsx'}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error('Ошибка экспорта');
    }
  };

  // -------------------------------------------------------------------------
  // Фильтры
  // -------------------------------------------------------------------------

  const handleResetFilters = () => {
    setFilters({
      type: undefined,
      status: undefined,
      enterpriseCode: undefined,
      search: '',
    });
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  // -------------------------------------------------------------------------
  // Колонки таблицы
  // -------------------------------------------------------------------------

  const columns: ColumnsType<Equipment> = useMemo(
    () => [
      {
        title: 'Наименование',
        dataIndex: 'name',
        key: 'name',
        sorter: true,
        render: (value: string, record) => (
          <a onClick={() => handleOpenCard(record)}>{value}</a>
        ),
      },
      {
        title: 'Тип',
        dataIndex: 'type',
        key: 'type',
        width: 130,
        render: (value: string) => {
          const t = EQUIPMENT_TYPES.find((x) => x.value === value);
          return <Tag>{t?.label || value}</Tag>;
        },
      },
      {
        title: 'Модель',
        dataIndex: 'model',
        key: 'model',
        width: 150,
      },
      {
        title: 'Инв. №',
        dataIndex: 'inventoryNo',
        key: 'inventoryNo',
        width: 130,
      },
      {
        title: 'Статус',
        dataIndex: 'status',
        key: 'status',
        width: 130,
        render: (value: string) => {
          const s = EQUIPMENT_STATUSES.find((x) => x.value === value);
          return <Tag color={s?.color || 'default'}>{s?.label || value}</Tag>;
        },
      },
      {
        title: 'Расположение',
        dataIndex: 'location',
        key: 'location',
        width: 180,
        ellipsis: true,
      },
      {
        title: 'Загрузка',
        key: 'load',
        width: 100,
        render: (_, record) =>
          record.currentStatus?.loadPercent != null
            ? `${record.currentStatus.loadPercent}%`
            : '—',
      },
      {
        title: 'Действия',
        key: 'actions',
        width: 140,
        fixed: 'right',
        render: (_, record) => (
          <Space size={4}>
            <Tooltip title="Открыть карточку">
              <Button
                type="text"
                size="small"
                icon={<ExportOutlined />}
                onClick={() => handleOpenCard(record)}
              />
            </Tooltip>
            {canEdit && (
              <Tooltip title="Редактировать">
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => handleEdit(record)}
                />
              </Tooltip>
            )}
            {canDelete && (
              <Popconfirm
                title="Списать оборудование?"
                description="Оборудование будет переведено в статус «Списано»"
                onConfirm={() => handleDelete(record)}
                okText="Списать"
                okButtonProps={{ danger: true }}
                cancelText="Отмена"
              >
                <Tooltip title="Списать">
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                  />
                </Tooltip>
              </Popconfirm>
            )}
          </Space>
        ),
      },
    ],
    [canEdit, canDelete]
  );

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  return (
    <div className="equipment-registry-page">
      {/* Заголовок */}
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={3} style={{ margin: 0 }}>
            Реестр оборудования
          </Title>
          <Text type="secondary">
            Оборудование предприятия, доступное для проектирования ТП
          </Text>
        </Col>
        <Col>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                loadList(false);
                loadSummary();
              }}
              loading={refreshing}
            >
              Обновить
            </Button>
            {canEdit && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleCreate}
              >
                Добавить
              </Button>
            )}
          </Space>
        </Col>
      </Row>

      {/* Сводка */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic title="Всего" value={summary.total} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Доступно"
              value={summary.available}
              valueStyle={{ color: '#2ea043' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Занято"
              value={summary.busy}
              valueStyle={{ color: '#d29922' }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Statistic
              title="Обслуживание"
              value={summary.maintenance}
              valueStyle={{ color: '#1f6feb' }}
            />
          </Card>
        </Col>
      </Row>

      {/* Ошибка */}
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={() => loadList(true)}>
              Повторить
            </Button>
          }
        />
      )}

      {/* Фильтры */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={12} align="middle">
          <Col xs={24} sm={12} md={6}>
            <Input
              placeholder="Поиск по наименованию, инв. №"
              prefix={<SearchOutlined />}
              value={filters.search}
              onChange={(e) =>
                setFilters((prev) => ({ ...prev, search: e.target.value }))
              }
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              placeholder="Тип"
              value={filters.type}
              onChange={(value) => setFilters((prev) => ({ ...prev, type: value }))}
              allowClear
              style={{ width: '100%' }}
            >
              {EQUIPMENT_TYPES.map((t) => (
                <Option key={t.value} value={t.value}>
                  {t.label}
                </Option>
              ))}
            </Select>
          </Col>
          <Col xs={24} sm={12} md={5}>
            <Select
              placeholder="Статус"
              value={filters.status}
              onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
              allowClear
              style={{ width: '100%' }}
            >
              {EQUIPMENT_STATUSES.map((s) => (
                <Option key={s.value} value={s.value}>
                  {s.label}
                </Option>
              ))}
            </Select>
          </Col>
          <Col xs={24} sm={12} md={4}>
            <Input
              placeholder="Предприятие"
              value={filters.enterpriseCode}
              onChange={(e) =>
                setFilters((prev) => ({ ...prev, enterpriseCode: e.target.value || undefined }))
              }
              allowClear
            />
          </Col>
          <Col xs={24} md={4}>
            <Space>
              <Button icon={<FilterOutlined />} onClick={() => loadList(true)}>
                Применить
              </Button>
              <Button onClick={handleResetFilters}>Сбросить</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* Таблица */}
      <Card size="small">
        <Table<Equipment>
          rowKey="id"
          columns={columns}
          dataSource={items}
          loading={loading}
          scroll={{ x: 1100 }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            showTotal: (total) => `Всего: ${total}`,
          }}
          onChange={(p) =>
            setPagination((prev) => ({
              ...prev,
              current: p.current || 1,
              pageSize: p.pageSize || 20,
            }))
          }
          locale={{
            emptyText: <Empty description="Оборудование не найдено" />,
          }}
        />
      </Card>

      {/* Панель массовых действий */}
      <Space style={{ marginTop: 12 }}>
        {canImport && (
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Импорт
          </Button>
        )}
        <Button icon={<DownloadOutlined />} onClick={() => handleExport('CSV')}>
          Экспорт CSV
        </Button>
        <Button icon={<DownloadOutlined />} onClick={() => handleExport('EXCEL')}>
          Экспорт Excel
        </Button>
      </Space>

      {/* Модальное окно создания/редактирования */}
      <Modal
        title={editing ? 'Редактирование оборудования' : 'Новое оборудование'}
        open={formOpen}
        onCancel={() => {
          setFormOpen(false);
          setEditing(null);
        }}
        onOk={handleSave}
        confirmLoading={saving}
        okText={editing ? 'Сохранить' : 'Создать'}
        cancelText="Отмена"
        width={640}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="Наименование"
                rules={[{ required: true, message: 'Укажите наименование' }]}
              >
                <Input placeholder="Токарный станок с ЧПУ" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="type"
                label="Тип"
                rules={[{ required: true, message: 'Укажите тип' }]}
              >
                <Select>
                  {EQUIPMENT_TYPES.map((t) => (
                    <Option key={t.value} value={t.value}>
                      {t.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="model" label="Модель">
                <Input placeholder="16К20Ф3" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="manufacturer" label="Производитель">
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="inventoryNo" label="Инвентарный №">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="serialNo" label="Серийный №">
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="status" label="Статус">
                <Select>
                  {EQUIPMENT_STATUSES.map((s) => (
                    <Option key={s.value} value={s.value}>
                      {s.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="location" label="Расположение">
                <Input placeholder="Цех №3" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="enterpriseCode" label="Предприятие">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="departmentCode" label="Подразделение">
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="manufactureYear" label="Год выпуска">
                <InputNumber min={1900} max={2100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="responsible" label="Ответственный">
                <Input />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* Drawer карточки */}
      <Drawer
        title={selected?.name || 'Карточка оборудования'}
        placement="right"
        width={720}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setSelected(null);
        }}
      >
        {drawerLoading ? (
          <Spin />
        ) : selected ? (
          <>
            <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Тип">{selected.type}</Descriptions.Item>
              <Descriptions.Item label="Модель">{selected.model || '—'}</Descriptions.Item>
              <Descriptions.Item label="Инв. №">{selected.inventoryNo || '—'}</Descriptions.Item>
              <Descriptions.Item label="Серийный №">{selected.serialNo || '—'}</Descriptions.Item>
              <Descriptions.Item label="Статус">{selected.status}</Descriptions.Item>
              <Descriptions.Item label="Расположение">{selected.location || '—'}</Descriptions.Item>
              <Descriptions.Item label="Предприятие">
                {selected.enterpriseCode || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Подразделение">
                {selected.departmentCode || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Год выпуска">
                {selected.manufactureYear || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Ответственный">
                {selected.responsible || '—'}
              </Descriptions.Item>
            </Descriptions>

            {selectedStatus && (
              <>
                <Title level={5}>Состояние и загрузка</Title>
                <Descriptions bordered column={2} size="small" style={{ marginBottom: 16 }}>
                  <Descriptions.Item label="Состояние">
                    {selectedStatus.state || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Загрузка">
                    {selectedStatus.loadPercent != null
                      ? `${selectedStatus.loadPercent}%`
                      : '—'}
                  </Descriptions.Item>
                </Descriptions>
              </>
            )}

            <Title level={5}>Возможности</Title>
            {selectedCapabilities.length === 0 ? (
              <Empty description="Возможности не заданы" />
            ) : (
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={selectedCapabilities}
                columns={[
                  { title: 'Операция', dataIndex: 'operationType' },
                  { title: 'Точность', dataIndex: 'tolerance' },
                  { title: 'Габариты', dataIndex: 'maxDimensions' },
                  {
                    title: 'ЧПУ',
                    dataIndex: 'cncCapable',
                    render: (v: boolean) => (v ? 'Да' : 'Нет'),
                  },
                  { title: 'Мощность', dataIndex: 'powerKw' },
                ]}
              />
            )}
          </>
        ) : null}
      </Drawer>

      {/* Модальное окно импорта */}
      <Modal
        title="Импорт оборудования"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        footer={null}
        width={480}
      >
        <p>Загрузите файл CSV или XLSX с перечнем оборудования.</p>
        <Space direction="vertical" style={{ width: '100%' }}>
          <input
            type="file"
            accept=".csv,.xlsx,.xls"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) handleImport(file, false, false);
            }}
          />
          <Button
            onClick={() => {
              const input = document.createElement('input');
              input.type = 'file';
              input.accept = '.csv,.xlsx,.xls';
              input.onchange = (e) => {
                const file = (e.target as HTMLInputElement).files?.[0];
                if (file) handleImport(file, false, true);
              };
              input.click();
            }}
            loading={importing}
          >
            Проверить без сохранения (dry-run)
          </Button>
        </Space>
      </Modal>
    </div>
  );
};

export default EquipmentRegistryPage;