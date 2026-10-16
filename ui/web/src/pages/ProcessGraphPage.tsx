import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import ReactFlow, {
  addEdge,
  applyEdgeChanges,
  applyNodeChanges,
  Background,
  BackgroundVariant,
  Connection,
  Controls,
  Edge,
  EdgeChange,
  MiniMap,
  Node,
  NodeChange,
  Panel,
  ReactFlowProvider,
  useReactFlow,
} from 'reactflow';
import 'reactflow/dist/style.css';

import { useProjectStore } from '../store/projectStore';
import { useAuthStore } from '../store/authStore';
import orchestratorApi from '../api/orchestratorApi';
import type {
  OrchestrationResponse,
  ProjectState,
  StageGraph,
  StageGraphEdge,
  StageGraphNode,
} from '../api/types';
import { ROLES, STATE_CATEGORIES, STATE_LABELS } from '../api/constants';

import StageNode from '../components/graph/StageNode';
import PointOfControlNode from '../components/graph/PointOfControlNode';
import GraphLegend from '../components/graph/GraphLegend';
import GraphToolbar from '../components/graph/GraphToolbar';

const { Title, Text, Paragraph } = Typography;

// ---------------------------------------------------------------------------
// Регистрация кастомных типов узлов
// ---------------------------------------------------------------------------

const nodeTypes = {
  stage: StageNode,
  pointOfControl: PointOfControlNode,
};

// ---------------------------------------------------------------------------
// Утилиты преобразования
// ---------------------------------------------------------------------------

/**
 * Преобразовать доменные узлы графа в узлы ReactFlow.
 */
function toFlowNodes(
  graphNodes: StageGraphNode[],
  isEditable: boolean,
  selectedId?: string
): Node[] {
  return graphNodes.map((n, index) => ({
    id: n.id,
    type: n.pointOfControl ? 'pointOfControl' : 'stage',
    position: n.position ?? { x: 200 * (index % 4), y: 120 * Math.floor(index / 4) },
    data: {
      ...n,
      isEditable,
      isSelected: n.id === selectedId,
    },
    selected: n.id === selectedId,
  }));
}

/**
 * Преобразовать доменные рёбра графа в рёбра ReactFlow.
 */
function toFlowEdges(graphEdges: StageGraphEdge[]): Edge[] {
  return graphEdges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    type: 'smoothstep',
    animated: e.active === true,
    style: e.active
      ? { stroke: '#1f6feb', strokeWidth: 2 }
      : { stroke: '#8b949e', strokeWidth: 1.5 },
    labelStyle: { fontSize: 11, fill: '#8b949e' },
    markerEnd: { type: 'arrowclosed' as any, color: e.active ? '#1f6feb' : '#8b949e' },
    data: { ...e },
  }));
}

// ---------------------------------------------------------------------------
// Внутренний компонент (внутри ReactFlowProvider)
// ---------------------------------------------------------------------------

const ProcessGraphInner: React.FC = () => {
  const { id: projectId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const { projectToFitView } = useReactFlow();

  const { hasRole } = useAuthStore();
  const { currentProject } = useProjectStore();

  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [originalNodes, setOriginalNodes] = useState<Node[]>([]);
  const [originalEdges, setOriginalEdges] = useState<Edge[]>([]);
  const [orchestration, setOrchestration] = useState<OrchestrationResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [editMode, setEditMode] = useState<boolean>(false);
  const [dirty, setDirty] = useState<boolean>(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | undefined>();
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [form] = Form.useForm();

  const canEdit = hasRole(ROLES.ADMIN) || hasRole(ROLES.ENGINEER);

  // -------------------------------------------------------------------------
  // Загрузка графа
  // -------------------------------------------------------------------------

  const loadGraph = useCallback(
    async (showSpinner = true) => {
      if (!projectId) return;
      if (showSpinner) setLoading(true);

      try {
        const [graph, state] = await Promise.all([
          orchestratorApi.getStageGraph(projectId),
          orchestratorApi.getState(projectId),
        ]);

        const flowNodes = toFlowNodes(graph.nodes, editMode, selectedNodeId);
        const flowEdges = toFlowEdges(graph.edges);

        setNodes(flowNodes);
        setEdges(flowEdges);
        setOriginalNodes(flowNodes);
        setOriginalEdges(flowEdges);
        setOrchestration(state);
        setDirty(false);

        setTimeout(() => projectToFitView({ duration: 400, padding: 0.2 }), 100);
      } catch (e: any) {
        const msg = e?.response?.data?.message || e?.message || 'Ошибка загрузки графа ТП';
        message.error(msg);
      } finally {
        setLoading(false);
      }
    },
    [projectId, editMode, selectedNodeId, projectToFitView]
  );

  useEffect(() => {
    loadGraph(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // -------------------------------------------------------------------------
  // Управление изменениями ReactFlow
  // -------------------------------------------------------------------------

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => {
      setNodes((nds) => applyNodeChanges(changes, nds));
      if (editMode) setDirty(true);
    },
    [editMode]
  );

  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => {
      setEdges((eds) => applyEdgeChanges(changes, eds));
      if (editMode) setDirty(true);
    },
    [editMode]
  );

  const onConnect = useCallback(
    (connection: Connection) => {
      if (!editMode) {
        message.info('Включите режим редактирования, чтобы изменить граф');
        return;
      }
      setEdges((eds) => addEdge({ ...connection, type: 'smoothstep' }, eds));
      setDirty(true);
    },
    [editMode]
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      setSelectedNodeId(node.id);
      if (!editMode) {
        setDrawerOpen(true);
        const stage = node.data as StageGraphNode;
        form.setFieldsValue({
          name: stage.name,
          description: stage.description,
          operationType: stage.operationType,
          equipmentId: stage.equipmentId,
          toolingId: stage.toolingId,
          timeNormMin: stage.timeNormMin,
          setupTimeMin: stage.setupTimeMin,
        });
      }
    },
    [editMode, form]
  );

  // -------------------------------------------------------------------------
  // Режим редактирования
  // -------------------------------------------------------------------------

  const toggleEditMode = (value: boolean) => {
    setEditMode(value);
    if (!value) {
      // Выход из режима — восстановить оригинал при наличии изменений
      if (dirty) {
        Modal.confirm({
          title: 'Отменить изменения?',
          content: 'В режиме редактирования были изменения. Отменить их?',
          okText: 'Отменить',
          cancelText: 'Продолжить редактирование',
          onOk: () => {
            setNodes(originalNodes);
            setEdges(originalEdges);
            setDirty(false);
          },
        });
      }
    }
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: { ...n.data, isEditable: value },
      }))
    );
  };

  // -------------------------------------------------------------------------
  // Сохранение
  // -------------------------------------------------------------------------

  const handleSave = async () => {
    if (!projectId || !dirty) {
      message.info('Нет изменений для сохранения');
      return;
    }

    setSaving(true);
    try {
      const payload: StageGraph = {
        nodes: nodes.map((n) => ({
          id: n.id,
          name: n.data.name,
          description: n.data.description,
          operationType: n.data.operationType,
          stageKind: n.data.stageKind,
          equipmentId: n.data.equipmentId,
          toolingId: n.data.toolingId,
          cncProgramId: n.data.cncProgramId,
          timeNormMin: n.data.timeNormMin,
          setupTimeMin: n.data.setupTimeMin,
          pointOfControl: n.data.pointOfControl,
          position: n.position,
        })),
        edges: edges.map((e) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          label: typeof e.label === 'string' ? e.label : undefined,
          active: e.animated,
        })),
      };

      await orchestratorApi.updateStageGraph(projectId, payload);

      setOriginalNodes(nodes);
      setOriginalEdges(edges);
      setDirty(false);
      message.success('Граф ТП сохранён');
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || 'Ошибка сохранения графа';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    Modal.confirm({
      title: 'Сбросить изменения?',
      content: 'Все несохранённые изменения будут потеряны.',
      okText: 'Сбросить',
      okButtonProps: { danger: true },
      cancelText: 'Отмена',
      onOk: () => {
        setNodes(originalNodes);
        setEdges(originalEdges);
        setDirty(false);
      },
    });
  };

  // -------------------------------------------------------------------------
  // Обновление узла из формы
  // -------------------------------------------------------------------------

  const handleNodeFormSave = async () => {
    if (!selectedNodeId) return;
    try {
      const values = await form.validateFields();
      setNodes((nds) =>
        nds.map((n) =>
          n.id === selectedNodeId
            ? { ...n, data: { ...n.data, ...values } }
            : n
        )
      );
      setDirty(true);
      setDrawerOpen(false);
      message.success('Изменения узла применены');
    } catch {
      // валидация не пройдена
    }
  };

  // -------------------------------------------------------------------------
  // Удаление выбранного узла
  // -------------------------------------------------------------------------

  const handleDeleteNode = () => {
    if (!selectedNodeId) return;
    Modal.confirm({
      title: 'Удалить операцию?',
      content: 'Узел и связанные рёбра будут удалены из графа.',
      okText: 'Удалить',
      okButtonProps: { danger: true },
      cancelText: 'Отмена',
      onOk: () => {
        setNodes((nds) => nds.filter((n) => n.id !== selectedNodeId));
        setEdges((eds) =>
          eds.filter((e) => e.source !== selectedNodeId && e.target !== selectedNodeId)
        );
        setSelectedNodeId(undefined);
        setDrawerOpen(false);
        setDirty(true);
      },
    });
  };

  // -------------------------------------------------------------------------
  // Добавление новой операции
  // -------------------------------------------------------------------------

  const handleAddNode = () => {
    if (!editMode) {
      message.info('Включите режим редактирования');
      return;
    }
    const id = `new-${Date.now()}`;
    const newNode: Node = {
      id,
      type: 'stage',
      position: { x: 300, y: 300 },
      data: {
        id,
        name: 'Новая операция',
        operationType: 'MACHINING',
        stageKind: 'MAIN',
        isEditable: true,
        isNew: true,
      },
    };
    setNodes((nds) => [...nds, newNode]);
    setSelectedNodeId(id);
    setDirty(true);
    setDrawerOpen(true);
    form.resetFields();
  };

  // -------------------------------------------------------------------------
  // Производные значения
  // -------------------------------------------------------------------------

  const currentState: ProjectState | undefined = orchestration?.state;
  const isPointOfControl = currentState
    ? STATE_CATEGORIES[currentState] === 'POINT_OF_CONTROL'
    : false;

  const activeEdges = useMemo(() => edges.filter((e) => e.animated), [edges]);

  // -------------------------------------------------------------------------
  // Рендер
  // -------------------------------------------------------------------------

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin size="large" tip="Загрузка графа ТП…">
          <div style={{ width: 200, height: 80 }} />
        </Spin>
      </div>
    );
  }

  return (
    <div className="process-graph-page" style={{ height: 'calc(100vh - 120px)' }}>
      <Breadcrumb style={{ marginBottom: 12 }}>
        <Breadcrumb.Item>
          <a onClick={() => navigate('/projects')}>Проекты</a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>
          <a onClick={() => navigate(`/projects/${projectId}`)}>
            {currentProject?.designation || currentProject?.projectName || projectId}
          </a>
        </Breadcrumb.Item>
        <Breadcrumb.Item>Граф ТП</Breadcrumb.Item>
      </Breadcrumb>

      {isPointOfControl && (
        <Alert
          style={{ marginBottom: 12 }}
          type="warning"
          showIcon
          icon={<ExclamationCircleOutlined />}
          message={`Точка контроля: ${STATE_LABELS[currentState!] || currentState}`}
          description="Измените граф при необходимости и подтвердите этап на странице проекта."
          action={
            <Button size="small" onClick={() => navigate(`/projects/${projectId}`)}>
              К проекту
            </Button>
          }
        />
      )}

      <Card
        bodyStyle={{ padding: 0, height: '100%' }}
        style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
        title={
          <Space>
            <ApartmentOutlined />
            <Text strong>Интерактивный граф технологического процесса</Text>
            {dirty && <Tag color="orange">Не сохранено</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Tooltip title="Режим редактирования">
              <Switch
                checked={editMode}
                onChange={toggleEditMode}
                checkedChildren="Ред."
                unCheckedChildren="Просмотр"
                disabled={!canEdit}
              />
            </Tooltip>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddNode}
              disabled={!editMode}
            >
              Операция
            </Button>
            <Button
              icon={<SaveOutlined />}
              type="primary"
              onClick={handleSave}
              loading={saving}
              disabled={!editMode || !dirty}
            >
              Сохранить
            </Button>
            <Button
              icon={<UndoOutlined />}
              onClick={handleReset}
              disabled={!dirty}
            >
              Сбросить
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => loadGraph(false)}
            >
              Обновить
            </Button>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(`/projects/${projectId}`)}>
              К проекту
            </Button>
          </Space>
        }
      >
        <div ref={reactFlowWrapper} style={{ flex: 1, minHeight: 500 }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            nodesDraggable={editMode}
            nodesConnectable={editMode}
            elementsSelectable
            fitView
            fitViewOptions={{ padding: 0.2 }}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#30363d" />
            <Controls showInteractive={editMode} />
            <MiniMap
              pannable
              zoomable
              nodeColor={(n) =>
                n.type === 'pointOfControl'
                  ? '#d29922'
                  : n.data?.status === 'COMPLETED'
                  ? '#2ea043'
                  : '#1f6feb'
              }
            />
            <Panel position="top-left">
              <GraphToolbar
                editMode={editMode}
                dirty={dirty}
                activeEdges={activeEdges.length}
                totalNodes={nodes.length}
                totalEdges={edges.length}
              />
            </Panel>
            <Panel position="bottom-left">
              <GraphLegend />
            </Panel>
          </ReactFlow>
        </div>
      </Card>

      {/* --------------------------------------------------------------- */}
      {/* Drawer для редактирования узла                                   */}
      {/* --------------------------------------------------------------- */}
      <Drawer
        title={editMode ? 'Редактирование операции' : 'Операция ТП'}
        placement="right"
        width={460}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          editMode && (
            <Space>
              <Button danger icon={<CloseCircleOutlined />} onClick={handleDeleteNode}>
                Удалить
              </Button>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleNodeFormSave}
              >
                Применить
              </Button>
            </Space>
          )
        }
      >
        <Form
          form={form}
          layout="vertical"
          disabled={!editMode}
          initialValues={{ stageKind: 'MAIN', operationType: 'MACHINING' }}
        >
          <Form.Item
            name="name"
            label="Наименование операции"
            rules={[{ required: true, message: 'Укажите наименование' }]}
          >
            <Input placeholder="Например, Токарная 010" />
          </Form.Item>

          <Form.Item name="description" label="Описание">
            <Input.TextArea rows={3} placeholder="Описание операции" />
          </Form.Item>

          <Form.Item name="operationType" label="Тип операции">
            <Select
              options={[
                { value: 'BLANK_PREPARATION', label: 'Заготовительная' },
                { value: 'CASTING', label: 'Литьё' },
                { value: 'HOT_FORGING', label: 'Горячая штамповка' },
                { value: 'COLD_FORGING', label: 'Холодная штамповка' },
                { value: 'SHEET_STAMPING', label: 'Листовая штамповка' },
                { value: 'MACHINING', label: 'Механообработка' },
                { value: 'CNC_MACHINING', label: 'Механообработка ЧПУ' },
                { value: 'HEAT_TREATMENT', label: 'Термообработка' },
                { value: 'COATING', label: 'Покрытие' },
                { value: 'ASSEMBLY', label: 'Сборка' },
                { value: 'TECHNICAL_CONTROL', label: 'Контроль' },
                { value: 'NORMING', label: 'Нормирование' },
                { value: 'AUXILIARY', label: 'Вспомогательная' },
              ]}
            />
          </Form.Item>

          <Form.Item name="stageKind" label="Вид этапа">
            <Select
              options={[
                { value: 'MAIN', label: 'Основной' },
                { value: 'AUXILIARY', label: 'Вспомогательный' },
                { value: 'CONTROL', label: 'Контрольный' },
                { value: 'TRANSPORT', label: 'Транспортный' },
              ]}
            />
          </Form.Item>

          <Form.Item name="equipmentId" label="Оборудование">
            <Input placeholder="Идентификатор оборудования" />
          </Form.Item>

          <Form.Item name="toolingId" label="Оснастка">
            <Input placeholder="Идентификатор оснастки" />
          </Form.Item>

          <Form.Item name="cncProgramId" label="Программа ЧПУ">
            <Input placeholder="Идентификатор УП" />
          </Form.Item>

          <Form.Item name="timeNormMin" label="Штучное время, мин">
            <Input type="number" step="0.001" />
          </Form.Item>

          <Form.Item name="setupTimeMin" label="Подготовительно-заключительное, мин">
            <Input type="number" step="0.001" />
          </Form.Item>
        </Form>

        {!editMode && (
          <Paragraph type="secondary" style={{ marginTop: 16 }}>
            Для редактирования включите режим «Ред.» в верхней панели.
          </Paragraph>
        )}
      </Drawer>
    </div>
  );
};

// ---------------------------------------------------------------------------
// Обёртка с ReactFlowProvider
// ---------------------------------------------------------------------------

/**
 * Страница интерактивного графа технологического процесса ИКТК.
 *
 * <p>Возможности:
 * <ul>
 *   <li>отображение графа ТП: узлы-операции и рёбра-переходы;</li>
 *   <li>подсветка активных переходов (анимированные рёбра);</li>
 *   <li>выделение точек контроля особым типом узла;</li>
 *   <li>режим просмотра и режим редактирования (переключение);</li>
 *   <li>добавление, удаление, перемещение узлов; создание и удаление связей;</li>
 *   <li>редактирование свойств операции в боковой панели (Drawer);</li>
 *   <li>сохранение графа через API оркестратора;</li>
 *   <li>сброс несохранённых изменений;</li>
 *   <li>мини-карта, легенда, панель инструментов;</li>
 *   <li>автоподгонка масштаба (fitView).</li>
 * </ul>
 *
 * <p>Права: редактирование доступно ролям {@code ADMIN} и {@code ENGINEER}.
 * Роли {@code MANAGER}, {@code NORMER}, {@code OPERATOR}, {@code VIEWER}
 * работают в режиме просмотра.
 */
const ProcessGraphPage: React.FC = () => (
  <ReactFlowProvider>
    <ProcessGraphInner />
  </ReactFlowProvider>
);

export default ProcessGraphPage;