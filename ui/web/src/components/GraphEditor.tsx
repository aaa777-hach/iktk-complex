import React, {
  forwardRef,
  useCallback,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Button,
  Empty,
  Form,
  Input,
  message,
  Modal,
  Select,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApartmentOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  RedoOutlined,
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
  ReactFlowInstance,
  useReactFlow,
} from 'reactflow';
import 'reactflow/dist/style.css';

import StageNode from './graph/StageNode';
import PointOfControlNode from './graph/PointOfControlNode';
import type {
  StageGraph,
  StageGraphEdge,
  StageGraphNode,
} from '../api/types';

const { Text } = Typography;

// ---------------------------------------------------------------------------
// Типы
// ---------------------------------------------------------------------------

export interface GraphEditorProps {
  /** Начальный граф (узлы и рёбра). */
  initialGraph: StageGraph;
  /** Признак доступности редактирования (по ролям). */
  editable: boolean;
  /** Вызывается при сохранении; должен вернуть Promise. */
  onSave?: (graph: StageGraph) => Promise<void>;
  /** Вызывается при изменении состояния «грязный»/«чистый». */
  onDirtyChange?: (dirty: boolean) => void;
  /** Высота контейнера (по умолчанию 600). */
  height?: number | string;
  /** Показывать ли мини-карту. */
  showMiniMap?: boolean;
  /** Показывать ли легенду. */
  showLegend?: boolean;
}

/**
 * Публичное API компонента — доступно через ref.
 */
export interface GraphEditorHandle {
  /** Получить текущий граф в доменном формате. */
  getGraph: () => StageGraph;
  /** Проверить, есть ли несохранённые изменения. */
  isDirty: () => boolean;
  /** Сохранить граф (вызов onSave). */
  save: () => Promise<void>;
  /** Сбросить изменения к последнему сохранённому состоянию. */
  reset: () => void;
  /** Уместить граф в видимой области. */
  fitView: () => void;
}

// ---------------------------------------------------------------------------
// Константы
// ---------------------------------------------------------------------------

const nodeTypes = {
  stage: StageNode,
  pointOfControl: PointOfControlNode,
};

const OPERATION_TYPE_OPTIONS = [
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
];

const STAGE_KIND_OPTIONS = [
  { value: 'MAIN', label: 'Основной' },
  { value: 'AUXILIARY', label: 'Вспомогательный' },
  { value: 'CONTROL', label: 'Контрольный' },
  { value: 'TRANSPORT', label: 'Транспортный' },
];

// ---------------------------------------------------------------------------
// Вспомогательные функции
// ---------------------------------------------------------------------------

function toFlowNodes(
  nodes: StageGraphNode[],
  editable: boolean
): Node[] {
  return nodes.map((n, index) => ({
    id: n.id,
    type: n.pointOfControl ? 'pointOfControl' : 'stage',
    position: n.position ?? {
      x: 220 * (index % 4),
      y: 140 * Math.floor(index / 4),
    },
    data: {
      ...n,
      isEditable: editable,
    },
  }));
}

function toFlowEdges(edges: StageGraphEdge[]): Edge[] {
  return edges.map((e) => ({
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
    markerEnd: {
      type: 'arrowclosed' as any,
      color: e.active ? '#1f6feb' : '#8b949e',
    },
  }));
}

function toDomainGraph(nodes: Node[], edges: Edge[]): StageGraph {
  return {
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
}

// ---------------------------------------------------------------------------
// Внутренний компонент
// ---------------------------------------------------------------------------

const GraphEditorInner = forwardRef<GraphEditorHandle, GraphEditorProps>(
  (
    {
      initialGraph,
      editable,
      onSave,
      onDirtyChange,
      height = 600,
      showMiniMap = true,
      showLegend = true,
    },
    ref
  ) => {
    const reactFlowWrapper = useRef<HTMLDivElement>(null);
    const [reactFlowInstance, setReactFlowInstance] =
      useState<ReactFlowInstance | null>(null);
    const { fitView } = useReactFlow();

    const [nodes, setNodes] = useState<Node[]>(() =>
      toFlowNodes(initialGraph.nodes ?? [], editable)
    );
    const [edges, setEdges] = useState<Edge[]>(() =>
      toFlowEdges(initialGraph.edges ?? [])
    );
    const [originalGraph, setOriginalGraph] = useState<StageGraph>(initialGraph);
    const [dirty, setDirty] = useState(false);
    const [saving, setSaving] = useState(false);
    const [selectedNodeId, setSelectedNodeId] = useState<string | undefined>();
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm();

    // -------------------------------------------------------------------------
    // Отслеживание «грязного» состояния
    // -------------------------------------------------------------------------

    const markDirty = useCallback(
      (value: boolean) => {
        setDirty((prev) => {
          if (prev !== value) {
            onDirtyChange?.(value);
          }
          return value;
        });
      },
      [onDirtyChange]
    );

    // -------------------------------------------------------------------------
    // Императивный API через ref
    // -------------------------------------------------------------------------

    useImperativeHandle(
      ref,
      () => ({
        getGraph: () => toDomainGraph(nodes, edges),
        isDirty: () => dirty,
        save: async () => {
          if (!dirty) {
            message.info('Нет изменений для сохранения');
            return;
          }
          if (!onSave) {
            message.warning('Обработчик сохранения не задан');
            return;
          }
          setSaving(true);
          try {
            const graph = toDomainGraph(nodes, edges);
            await onSave(graph);
            setOriginalGraph(graph);
            markDirty(false);
            message.success('Граф сохранён');
          } catch (e: any) {
            const msg =
              e?.response?.data?.message || e?.message || 'Ошибка сохранения графа';
            message.error(msg);
            throw e;
          } finally {
            setSaving(false);
          }
        },
        reset: () => {
          setNodes(toFlowNodes(originalGraph.nodes ?? [], editable));
          setEdges(toFlowEdges(originalGraph.edges ?? []));
          setSelectedNodeId(undefined);
          setEditing(false);
          markDirty(false);
          message.info('Изменения сброшены');
        },
        fitView: () => fitView({ duration: 400, padding: 0.2 }),
      }),
      [nodes, edges, dirty, onSave, originalGraph, editable, fitView, markDirty]
    );

    // -------------------------------------------------------------------------
    // Обработчики ReactFlow
    // -------------------------------------------------------------------------

    const onNodesChange = useCallback(
      (changes: NodeChange[]) => {
        setNodes((nds) => applyNodeChanges(changes, nds));
        if (editable) markDirty(true);
      },
      [editable, markDirty]
    );

    const onEdgesChange = useCallback(
      (changes: EdgeChange[]) => {
        setEdges((eds) => applyEdgeChanges(changes, eds));
        if (editable) markDirty(true);
      },
      [editable, markDirty]
    );

    const onConnect = useCallback(
      (connection: Connection) => {
        if (!editable) {
          message.info('Редактирование недоступно');
          return;
        }
        setEdges((eds) =>
          addEdge({ ...connection, type: 'smoothstep' }, eds)
        );
        markDirty(true);
      },
      [editable, markDirty]
    );

    const onNodeClick = useCallback(
      (_: React.MouseEvent, node: Node) => {
        setSelectedNodeId(node.id);
        form.setFieldsValue({
          name: node.data.name,
          description: node.data.description,
          operationType: node.data.operationType,
          stageKind: node.data.stageKind,
          equipmentId: node.data.equipmentId,
          toolingId: node.data.toolingId,
          cncProgramId: node.data.cncProgramId,
          timeNormMin: node.data.timeNormMin,
          setupTimeMin: node.data.setupTimeMin,
        });
        setEditing(true);
      },
      [form]
    );

    const onPaneClick = useCallback(() => {
      setEditing(false);
      setSelectedNodeId(undefined);
    }, []);

    // -------------------------------------------------------------------------
    // Операции над узлами
    // -------------------------------------------------------------------------

    const handleAddNode = useCallback(() => {
      if (!editable) {
        message.info('Редактирование недоступно');
        return;
      }
      const id = `new-${Date.now()}`;
      const newNode: Node = {
        id,
        type: 'stage',
        position: { x: 250, y: 250 },
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
      setEditing(true);
      markDirty(true);
      form.resetFields();
      form.setFieldsValue({
        name: 'Новая операция',
        operationType: 'MACHINING',
        stageKind: 'MAIN',
      });
    }, [editable, form, markDirty]);

    const handleDeleteNode = useCallback(() => {
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
            eds.filter(
              (e) => e.source !== selectedNodeId && e.target !== selectedNodeId
            )
          );
          setSelectedNodeId(undefined);
          setEditing(false);
          markDirty(true);
        },
      });
    }, [selectedNodeId, markDirty]);

    const handleApplyNode = useCallback(async () => {
      if (!selectedNodeId) return;
      try {
        const values = await form.validateFields();
        setNodes((nds) =>
          nds.map((n) =>
            n.id === selectedNodeId ? { ...n, data: { ...n.data, ...values } } : n
          )
        );
        markDirty(true);
        message.success('Изменения применены');
      } catch {
        // валидация не пройдена
      }
    }, [selectedNodeId, form, markDirty]);

    // -------------------------------------------------------------------------
    // Сохранение / сброс через верхнюю панель
    // -------------------------------------------------------------------------

    const handleSaveClick = useCallback(async () => {
      if (!onSave) return;
      if (!dirty) {
        message.info('Нет изменений');
        return;
      }
      setSaving(true);
      try {
        const graph = toDomainGraph(nodes, edges);
        await onSave(graph);
        setOriginalGraph(graph);
        markDirty(false);
        message.success('Граф сохранён');
      } catch (e: any) {
        const msg =
          e?.response?.data?.message || e?.message || 'Ошибка сохранения';
        message.error(msg);
      } finally {
        setSaving(false);
      }
    }, [nodes, edges, dirty, onSave, markDirty]);

    const handleResetClick = useCallback(() => {
      Modal.confirm({
        title: 'Сбросить изменения?',
        content: 'Все несохранённые изменения будут потеряны.',
        okText: 'Сбросить',
        okButtonProps: { danger: true },
        cancelText: 'Отмена',
        onOk: () => {
          setNodes(toFlowNodes(originalGraph.nodes ?? [], editable));
          setEdges(toFlowEdges(originalGraph.edges ?? []));
          setSelectedNodeId(undefined);
          setEditing(false);
          markDirty(false);
        },
      });
    }, [originalGraph, editable, markDirty]);

    // -------------------------------------------------------------------------
    // Производные значения
    // -------------------------------------------------------------------------

    const isEmpty = useMemo(
      () => nodes.length === 0 && edges.length === 0,
      [nodes, edges]
    );

    const selectedNode = useMemo(
      () => nodes.find((n) => n.id === selectedNodeId),
      [nodes, selectedNodeId]
    );

    // -------------------------------------------------------------------------
    // Рендер
    // -------------------------------------------------------------------------

    return (
      <div
        className="graph-editor"
        style={{
          display: 'flex',
          flexDirection: 'column',
          height,
          border: '1px solid #30363d',
          borderRadius: 8,
          overflow: 'hidden',
        }}
      >
        {/* Панель инструментов */}
        <div
          className="graph-editor__toolbar"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '8px 12px',
            background: '#161b22',
            borderBottom: '1px solid #30363d',
          }}
        >
          <Space>
            <ApartmentOutlined />
            <Text strong style={{ color: '#c9d1d9' }}>
              Граф ТП
            </Text>
            {dirty && <Tag color="orange">Не сохранено</Tag>}
            {!editable && <Tag color="default">Только просмотр</Tag>}
          </Space>

          <Space>
            <Tooltip title="Добавить операцию">
              <Button
                size="small"
                icon={<PlusOutlined />}
                onClick={handleAddNode}
                disabled={!editable}
              >
                Операция
              </Button>
            </Tooltip>
            <Tooltip title="Удалить выбранную операцию">
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={handleDeleteNode}
                disabled={!editable || !selectedNodeId}
              >
                Удалить
              </Button>
            </Tooltip>
            <Tooltip title="Сохранить">
              <Button
                size="small"
                type="primary"
                icon={<SaveOutlined />}
                onClick={handleSaveClick}
                loading={saving}
                disabled={!editable || !dirty}
              >
                Сохранить
              </Button>
            </Tooltip>
            <Tooltip title="Сбросить изменения">
              <Button
                size="small"
                icon={<UndoOutlined />}
                onClick={handleResetClick}
                disabled={!dirty}
              >
                Сбросить
              </Button>
            </Tooltip>
            <Tooltip title="Уместить граф">
              <Button
                size="small"
                icon={<RedoOutlined />}
                onClick={() => fitView({ duration: 400, padding: 0.2 })}
              >
                Fit
              </Button>
            </Tooltip>
          </Space>
        </div>

        {/* Рабочая область: граф + форма редактирования */}
        <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
          <div ref={reactFlowWrapper} style={{ flex: 1, minWidth: 0 }}>
            {isEmpty ? (
              <Empty
                style={{ marginTop: 80 }}
                description="Граф пуст"
              >
                {editable && (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={handleAddNode}
                  >
                    Добавить первую операцию
                  </Button>
                )}
              </Empty>
            ) : (
              <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={nodeTypes}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={onNodeClick}
                onPaneClick={onPaneClick}
                onInit={setReactFlowInstance}
                nodesDraggable={editable}
                nodesConnectable={editable}
                elementsSelectable
                fitView
                fitViewOptions={{ padding: 0.2 }}
                proOptions={{ hideAttribution: true }}
                minZoom={0.2}
                maxZoom={2}
              >
                <Background
                  variant={BackgroundVariant.Dots}
                  gap={20}
                  size={1}
                  color="#30363d"
                />
                <Controls showInteractive={editable} />
                {showMiniMap && (
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
                )}
                {showLegend && (
                  <div
                    style={{
                      position: 'absolute',
                      bottom: 12,
                      left: 12,
                      background: 'rgba(13,17,23,0.85)',
                      border: '1px solid #30363d',
                      borderRadius: 6,
                      padding: '6px 10px',
                      fontSize: 12,
                      color: '#8b949e',
                    }}
                  >
                    <Space size={12}>
                      <span>
                        <span
                          style={{
                            display: 'inline-block',
                            width: 10,
                            height: 10,
                            background: '#1f6feb',
                            borderRadius: 2,
                            marginRight: 4,
                          }}
                        />
                        Операция
                      </span>
                      <span>
                        <span
                          style={{
                            display: 'inline-block',
                            width: 10,
                            height: 10,
                            background: '#d29922',
                            borderRadius: 2,
                            marginRight: 4,
                          }}
                        />
                        Точка контроля
                      </span>
                      <span>
                        <span
                          style={{
                            display: 'inline-block',
                            width: 10,
                            height: 10,
                            background: '#2ea043',
                            borderRadius: 2,
                            marginRight: 4,
                          }}
                        />
                        Завершено
                      </span>
                    </Space>
                  </div>
                )}
              </ReactFlow>
            )}
          </div>

          {/* Боковая панель редактирования узла */}
          {editing && selectedNode && (
            <div
              className="graph-editor__panel"
              style={{
                width: 340,
                borderLeft: '1px solid #30363d',
                background: '#0d1117',
                padding: 16,
                overflowY: 'auto',
              }}
            >
              <Space
                style={{
                  width: '100%',
                  justifyContent: 'space-between',
                  marginBottom: 12,
                }}
              >
                <Text strong style={{ color: '#c9d1d9' }}>
                  {editable ? 'Редактирование' : 'Операция'}
                </Text>
                <Space>
                  {editable && (
                    <>
                      <Tooltip title="Применить изменения">
                        <Button
                          size="small"
                          type="primary"
                          icon={<CheckCircleOutlined />}
                          onClick={handleApplyNode}
                        />
                      </Tooltip>
                      <Tooltip title="Удалить">
                        <Button
                          size="small"
                          danger
                          icon={<CloseCircleOutlined />}
                          onClick={handleDeleteNode}
                        />
                      </Tooltip>
                    </>
                  )}
                  <Tooltip title="Закрыть">
                    <Button
                      size="small"
                      onClick={() => {
                        setEditing(false);
                        setSelectedNodeId(undefined);
                      }}
                    >
                      ×
                    </Button>
                  </Tooltip>
                </Space>
              </Space>

              <Form
                form={form}
                layout="vertical"
                disabled={!editable}
                size="small"
              >
                <Form.Item
                  name="name"
                  label="Наименование"
                  rules={[
                    { required: true, message: 'Укажите наименование' },
                  ]}
                >
                  <Input placeholder="Токарная 010" />
                </Form.Item>

                <Form.Item name="description" label="Описание">
                  <Input.TextArea rows={2} />
                </Form.Item>

                <Form.Item name="operationType" label="Тип операции">
                  <Select options={OPERATION_TYPE_OPTIONS} />
                </Form.Item>

                <Form.Item name="stageKind" label="Вид этапа">
                  <Select options={STAGE_KIND_OPTIONS} />
                </Form.Item>

                <Form.Item name="equipmentId" label="Оборудование">
                  <Input placeholder="ID оборудования" />
                </Form.Item>

                <Form.Item name="toolingId" label="Оснастка">
                  <Input placeholder="ID оснастки" />
                </Form.Item>

                <Form.Item name="cncProgramId" label="Программа ЧПУ">
                  <Input placeholder="ID УП" />
                </Form.Item>

                <Form.Item name="timeNormMin" label="Штучное время, мин">
                  <Input type="number" step="0.001" />
                </Form.Item>

                <Form.Item
                  name="setupTimeMin"
                  label="Подготовительно-заключительное, мин"
                >
                  <Input type="number" step="0.001" />
                </Form.Item>
              </Form>
            </div>
          )}
        </div>
      </div>
    );
  }
);

GraphEditorInner.displayName = 'GraphEditorInner';

// ---------------------------------------------------------------------------
// Публичный компонент
// ---------------------------------------------------------------------------

/**
 * Редактор графа технологического процесса ИКТК.
 *
 * <p>Универсальный компонент, предназначенный для:
 * <ul>
 *   <li>встраивания в страницы проекта и оркестратора;</li>
 *   <li>отображения графа ТП (узлы-операции и рёбра-переходы);</li>
 *   <li>редактирования графа: добавление/удаление узлов, создание связей,
 *       изменение свойств операции;</li>
 *   <li>сохранения через переданный обработчик {@code onSave};</li>
 *   <li>сброса несохранённых изменений;</li>
 *   <li>управления через императивный API ({@link GraphEditorHandle})
 *       при использовании {@code ref}.</li>
 * </ul>
 *
 * <p><b>Отличие от {@code ProcessGraphPage}.</b> Страница — самостоятельный
 * маршрут с загрузкой данных через API. Компонент {@code GraphEditor} —
 * переиспользуемая часть, получающая граф через пропсы и возвращающая
 * изменения через {@code onSave}. Это позволяет использовать его в разных
 * контекстах: в карточке проекта, в мастере создания ТП, в модальном окне.
 *
 * <p><b>Использование:</b>
 * <pre>
 *   const editorRef = useRef&lt;GraphEditorHandle&gt;(null);
 *
 *   &lt;GraphEditor
 *     ref={editorRef}
 *     initialGraph={graph}
 *     editable={hasRole(ROLES.ENGINEER)}
 *     onSave={async (g) =&gt; { await orchestratorApi.updateStageGraph(id, g); }}
 *     onDirtyChange={(d) =&gt; setDirty(d)}
 *     height={700}
 *   /&gt;
 *
 *   // Позже:
 *   await editorRef.current?.save();
 *   editorRef.current?.reset();
 * </pre>
 */
const GraphEditor = forwardRef<GraphEditorHandle, GraphEditorProps>(
  (props, ref) => (
    <ReactFlowProviderWrapper>
      <GraphEditorInner {...props} ref={ref} />
    </ReactFlowProviderWrapper>
  )
);

GraphEditor.displayName = 'GraphEditor';

// ---------------------------------------------------------------------------
// Обёртка с ReactFlowProvider (нужна для useReactFlow внутри)
// ---------------------------------------------------------------------------

import { ReactFlowProvider } from 'reactflow';

const ReactFlowProviderWrapper: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => <ReactFlowProvider>{children}</ReactFlowProvider>;

export default GraphEditor;