package ru.iktk.modules.machining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.machining.client.EquipmentClient;
import ru.iktk.modules.machining.client.GeometryClientRouter;
import ru.iktk.modules.machining.client.ToolingClientRouter;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.dto.ManufacturabilityReport;
import ru.iktk.modules.machining.dto.MachiningRequest;
import ru.iktk.modules.machining.dto.MachiningResponse;
import ru.iktk.modules.machining.dto.Setup;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;
import ru.iktk.modules.machining.model.MachiningProcess;
import ru.iktk.modules.machining.repository.MachiningProcessRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Основной сервис проектирования технологического процесса механической
 * обработки ИКТК.
 *
 * <p>Отвечает за полный цикл проектирования ТП механообработки:
 * <ol>
 *   <li><b>Анализ технологичности</b> — проверка геометрии на ограничения
 *       механообработки: доступность инструмента, жёсткость, базирование,
 *       минимальные радиусы, глубины отверстий, толщины стенок.</li>
 *   <li><b>Выбор метода обработки</b> — точение, фрезерование, сверление,
 *       шлифование, электроэрозия; с учётом требуемой точности
 *       и шероховатости.</li>
 *   <li><b>Определение установов и базирования</b> — выбор схем установки,
 *       числа установов, базовых поверхностей.</li>
 *   <li><b>Планирование операций и переходов</b> — последовательность
 *       черновой, получистовой, чистовой обработки.</li>
 *   <li><b>Выбор оборудования и инструмента</b> — через
 *       {@link EquipmentClient} и {@link ToolingClientRouter}.</li>
 *   <li><b>Расчёт режимов резания</b> — скорость, подача, глубина,
 *       стойкость инструмента.</li>
 *   <li><b>Формирование этапов ТП</b> — операции механообработки.</li>
 *   <li><b>Передача в генерацию УП</b> — для операций на станках с ЧПУ
 *       вызывается {@code CncProgramService} (позиция 38).</li>
 * </ol>
 *
 * <p><b>Поддерживаемые виды обработки:</b>
 * <ul>
 *   <li>{@link MachiningMethod#TURNING} — точение (включая ЧПУ);</li>
 *   <li>{@link MachiningMethod#MILLING} — фрезерование (включая ЧПУ);</li>
 *   <li>{@link MachiningMethod#DRILLING} — сверление;</li>
 *   <li>{@link MachiningMethod#BORING} — растачивание;</li>
 *   <li>{@link MachiningMethod#REAMING} — развёртывание;</li>
 *   <li>{@link MachiningMethod#GRINDING} — шлифование;</li>
 *   <li>{@link MachiningMethod#EDM} — электроэрозионная обработка;</li>
 *   <li>{@link MachiningMethod#LASER} — лазерная обработка.</li>
 * </ul>
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>получает команду от оркестратора;</li>
 *   <li>запрашивает геометрию через {@link GeometryClientRouter};</li>
 *   <li>проверяет оборудование через {@link EquipmentClient};</li>
 *   <li>подбирает инструмент через {@link ToolingClientRouter};</li>
 *   <li>сохраняет результат в {@link MachiningProcessRepository};</li>
 *   <li>публикует событие {@code PART_PROCESS_READY} в оркестратор.</li>
 * </ul>
 */
@Service
public class MachiningProcessService {

    private static final Logger log = LoggerFactory.getLogger(MachiningProcessService.class);

    private final MachiningProcessRepository repository;
    private final GeometryClientRouter geometryClient;
    private final EquipmentClient equipmentClient;
    private final ToolingClientRouter toolingClient;
    private final MachiningProperties properties;

    public MachiningProcessService(MachiningProcessRepository repository,
                                   GeometryClientRouter geometryClient,
                                   EquipmentClient equipmentClient,
                                   ToolingClientRouter toolingClient,
                                   MachiningProperties properties) {
        this.repository = repository;
        this.geometryClient = geometryClient;
        this.equipmentClient = equipmentClient;
        this.toolingClient = toolingClient;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: проектирование ТП механообработки
    // ------------------------------------------------------------------

    /**
     * Спроектировать ТП механической обработки для объекта изготовления.
     *
     * @param request запрос от оркестратора
     * @return результат проектирования
     */
    @Transactional
    public MachiningResponse designProcess(MachiningRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        String objectRef = request.getObjectRef();

        log.info("MachiningProcessService.designProcess start: projectId={}, objectRef={}",
                projectId, objectRef);

        // 1. Получить геометрию
        GeometryData geometry = geometryClient.fetchGeometry(projectId, objectRef);
        if (geometry == null) {
            throw new OrchestrationException(
                    "Геометрия не найдена: projectId=" + projectId + ", objectRef=" + objectRef);
        }

        // 2. Анализ технологичности
        ManufacturabilityReport report = analyzeManufacturability(geometry);
        if (!report.isPassed()) {
            log.warn("MachiningProcessService: технологичность не пройдена, projectId={}, issues={}",
                    projectId, report.getIssues().size());
        }

        // 3. Определение установов и базирования
        List<Setup> setups = planSetups(geometry, request);

        // 4. Планирование операций и переходов
        List<MachiningOperation> operations = planOperations(geometry, request, setups);

        // 5. Выбор оборудования и инструмента + расчёт режимов
        enrichWithEquipmentAndModes(projectId, geometry, operations);

        // 6. Формирование этапов ТП
        List<Stage> stages = buildStages(operations);

        // 7. Сборка и сохранение результата
        MachiningProcess process = MachiningProcess.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .objectRef(objectRef)
                .setups(setups)
                .operations(operations)
                .stages(stages)
                .manufacturabilityReport(report)
                .createdAt(Instant.now())
                .build();

        repository.save(process);

        long cncOperations = operations.stream()
                .filter(MachiningOperation::isCnc)
                .count();

        log.info("MachiningProcessService.designProcess done: projectId={}, processId={}, " +
                        "operations={}, cnc={}, stages={}",
                projectId, process.getId(), operations.size(), cncOperations, stages.size());

        return MachiningResponse.builder()
                .projectId(projectId)
                .processId(process.getId())
                .setups(setups)
                .operations(operations)
                .stages(stages)
                .requiresCncProgram(cncOperations > 0)
                .warnings(report.getIssues())
                .build();
    }

    // ------------------------------------------------------------------
    // 1. Анализ технологичности
    // ------------------------------------------------------------------

    /**
     * Проверка геометрии на ограничения механообработки.
     *
     * <p>Проверки:
     * <ul>
     *   <li>минимальный радиус внутренних углов (доступность фрезы);</li>
     *   <li>минимальная толщина стенки (жёсткость);</li>
     *   <li>глубина отверстий (соотношение L/D);</li>
     *   <li>наличие поднутрений (доступность инструмента);</li>
     *   <li>наличие тонких рёбер и высоких выступов;</li>
     *   <li>требуемая точность и шероховатость (выбор метода);</li>
     *   <li>возможность базирования и установки.</li>
     * </ul>
     */
    public ManufacturabilityReport analyzeManufacturability(GeometryData geometry) {
        log.debug("MachiningProcessService.analyzeManufacturability: objectRef={}",
                geometry.getObjectRef());

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal minRadius = properties.getMinInternalRadius();
        if (geometry.getMinInternalRadius() != null
                && geometry.getMinInternalRadius().compareTo(minRadius) < 0) {
            issues.add("Внутренний радиус " + geometry.getMinInternalRadius()
                    + " мм меньше минимального " + minRadius + " мм (недоступен инструмент)");
        }

        BigDecimal minWall = properties.getMinWallThickness();
        if (geometry.getMinWallThickness() != null
                && geometry.getMinWallThickness().compareTo(minWall) < 0) {
            warnings.add("Толщина стенки " + geometry.getMinWallThickness()
                    + " мм меньше рекомендуемой " + minWall + " мм (риск деформации)");
        }

        BigDecimal maxRatio = properties.getMaxHoleDepthRatio();
        if (geometry.getMaxHoleDepthRatio() != null
                && geometry.getMaxHoleDepthRatio().compareTo(maxRatio) > 0) {
            issues.add("Соотношение глубины отверстия к диаметру "
                    + geometry.getMaxHoleDepthRatio()
                    + " превышает максимальное " + maxRatio);
        }

        if (geometry.hasUndercuts()) {
            warnings.add("Обнаружены поднутрения — требуется специальный инструмент " +
                    "или дополнительный установ");
        }

        if (geometry.hasThinRibs()) {
            warnings.add("Обнаружены тонкие рёбра — возможны вибрации при обработке");
        }

        if (geometry.getRequiredAccuracy() != null
                && geometry.getRequiredAccuracy().compareTo(properties.getMaxAccuracy()) > 0) {
            warnings.add("Требуемая точность " + geometry.getRequiredAccuracy()
                    + " выше достижимой без финишной операции");
        }

        boolean passed = issues.isEmpty();
        log.debug("MachiningProcessService.analyzeManufacturability done: passed={}, " +
                        "issues={}, warnings={}",
                passed, issues.size(), warnings.size());

        return new ManufacturabilityReport(passed, issues, warnings);
    }

    // ------------------------------------------------------------------
    // 2. Определение установов и базирования
    // ------------------------------------------------------------------

    /**
     * Планирование установов и схем базирования.
     *
     * <p>Установ — совокупность операций, выполняемых при неизменном
     * базировании заготовки. Число установов зависит от:
     * <ul>
     *   <li>числа обрабатываемых поверхностей и их ориентации;</li>
     *   <li>требований к точности взаимного расположения поверхностей;</li>
     *   <li>возможности обработки с одной установки;</li>
     *   <li>типа оборудования (универсальное / ЧПУ с несколькими осями).</li>
     * </ul>
     */
    public List<Setup> planSetups(GeometryData geometry, MachiningRequest request) {
        log.debug("MachiningProcessService.planSetups: objectRef={}, complexity={}",
                geometry.getObjectRef(), geometry.getComplexityScore());

        List<Setup> setups = new ArrayList<>();
        int count = estimateSetupCount(geometry);

        for (int i = 1; i <= count; i++) {
            Setup setup = Setup.builder()
                    .order(i)
                    .name("Установ " + toRoman(i))
                    .scheme(properties.getDefaultSetupScheme(i, geometry))
                    .fixtures(properties.getFixturesFor(i, geometry))
                    .surfaces(properties.getSurfacesFor(i, geometry))
                    .build();
            setups.add(setup);
        }

        log.debug("MachiningProcessService.planSetups done: setups={}", setups.size());
        return setups;
    }

    private int estimateSetupCount(GeometryData geometry) {
        // Упрощённая эвристика. Реальная — по анализу ориентации поверхностей.
        if (geometry.getComplexityScore() <= 2) {
            return 1;
        }
        if (geometry.getComplexityScore() <= 5) {
            return 2;
        }
        return 3;
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // ------------------------------------------------------------------
    // 3. Планирование операций и переходов
    // ------------------------------------------------------------------

    /**
     * Планирование последовательности операций и переходов.
     *
     * <p>Типовая последовательность:
     * <ol>
     *   <li>Черновая обработка (снятие основного припуска).</li>
     *   <li>Получистовая обработка.</li>
     *   <li>Чистовая обработка (по поверхностям с высокой точностью).</li>
     *   <li>Отделочная обработка (шлифование, полирование).</li>
     * </ol>
     */
    public List<MachiningOperation> planOperations(GeometryData geometry,
                                                   MachiningRequest request,
                                                   List<Setup> setups) {
        log.debug("MachiningProcessService.planOperations: setups={}, holes={}, surfaces={}",
                setups.size(), geometry.hasHoles(), geometry.getSurfaceCount());

        List<MachiningOperation> operations = new ArrayList<>();
        int order = 1;

        // Черновая: снятие припуска
        operations.add(buildOperation(order++, "Черновая обработка",
                MachiningMethod.MILLING, Stage.StageKind.MAIN, geometry, null));

        // Сверление (если есть отверстия)
        if (geometry.hasHoles()) {
            operations.add(buildOperation(order++, "Сверление",
                    MachiningMethod.DRILLING, Stage.StageKind.MAIN, geometry, null));
        }

        // Растачивание (если есть точные отверстия)
        if (geometry.hasPrecisionHoles()) {
            operations.add(buildOperation(order++, "Растачивание",
                    MachiningMethod.BORING, Stage.StageKind.MAIN, geometry, null));
        }

        // Получистовая
        operations.add(buildOperation(order++, "Получистовая обработка",
                MachiningMethod.MILLING, Stage.StageKind.MAIN, geometry, null));

        // Чистовая
        operations.add(buildOperation(order++, "Чистовая обработка",
                MachiningMethod.MILLING, Stage.StageKind.MAIN, geometry, null));

        // Шлифование (при высокой точности)
        if (geometry.getRequiredAccuracy() != null
                && geometry.getRequiredAccuracy().compareTo(properties.getGrindingThreshold()) >= 0) {
            operations.add(buildOperation(order++, "Шлифование",
                    MachiningMethod.GRINDING, Stage.StageKind.MAIN, geometry, null));
        }

        // Контроль
        operations.add(buildOperation(order, "Контроль размеров",
                MachiningMethod.CONTROL, Stage.StageKind.CONTROL, geometry, null));

        log.debug("MachiningProcessService.planOperations done: operations={}", operations.size());
        return operations;
    }

    private MachiningOperation buildOperation(int order,
                                              String name,
                                              MachiningMethod method,
                                              Stage.StageKind kind,
                                              GeometryData geometry,
                                              String setupId) {
        return MachiningOperation.builder()
                .order(order)
                .designation(String.format("%03d", order * 5))
                .name(name)
                .method(method)
                .kind(kind)
                .setupId(setupId)
                .cnc(properties.isCncCapable(method) && geometry.getComplexityScore() > 2)
                .build();
    }

    // ------------------------------------------------------------------
    // 4. Выбор оборудования, инструмента, режимов
    // ------------------------------------------------------------------

    /**
     * Обогащение операций данными об оборудовании, инструменте и режимах.
     */
    private void enrichWithEquipmentAndModes(String projectId,
                                             GeometryData geometry,
                                             List<MachiningOperation> operations) {
        for (MachiningOperation op : operations) {
            if (op.getMethod() == MachiningMethod.CONTROL) {
                continue;
            }

            // Оборудование
            String equipmentId = equipmentClient.findAvailable(
                    projectId, op.getMethod(), op.isCnc(), geometry);
            op.setEquipmentId(equipmentId);

            // Инструмент
            CuttingTool tool = toolingClient.selectTool(op.getMethod(), geometry);
            op.setTool(tool);

            // Режимы резания
            CuttingMode mode = calculateCuttingMode(geometry, op.getMethod(), tool);
            op.setCuttingMode(mode);

            log.debug("MachiningProcessService: operation={}, equipment={}, tool={}, mode={}",
                    op.getName(), equipmentId,
                    tool == null ? "—" : tool.getType(),
                    mode == null ? "—" : mode.getSummary());
        }
    }

    /**
     * Расчёт режимов резания.
     *
     * <p>Определяются:
     * <ul>
     *   <li>скорость резания V (м/мин);</li>
     *   <li>подача S (мм/об или мм/мин);</li>
     *   <li>глубина резания t (мм);</li>
     *   <li>частота вращения n (об/мин);</li>
     *   <li>стойкость инструмента T (мин).</li>
     * </ul>
     */
    public CuttingMode calculateCuttingMode(GeometryData geometry,
                                            MachiningMethod method,
                                            CuttingTool tool) {
        BigDecimal v = properties.getCuttingSpeed(method, geometry.getMaterial(), tool);
        BigDecimal s = properties.getFeed(method, geometry.getMaterial(), tool);
        BigDecimal t = properties.getDepthOfCut(method, geometry.getMachiningAllowance());

        BigDecimal n = BigDecimal.ZERO;
        if (tool != null && tool.getDiameter() != null
                && tool.getDiameter().compareTo(BigDecimal.ZERO) > 0) {
            // n = 1000 · V / (π · D)
            n = v.multiply(BigDecimal.valueOf(1000))
                    .divide(tool.getDiameter().multiply(BigDecimal.valueOf(Math.PI)),
                            2, RoundingMode.HALF_UP);
        }

        BigDecimal toolLife = properties.getToolLife(method, geometry.getMaterial(), tool);

        return CuttingMode.builder()
                .cuttingSpeedMPerMin(v)
                .feedMmPerRev(s)
                .depthOfCutMm(t)
                .spindleSpeedRpm(n)
                .toolLifeMin(toolLife)
                .method(method)
                .build();
    }

    // ------------------------------------------------------------------
    // 5. Формирование этапов ТП
    // ------------------------------------------------------------------

    /**
     * Формирование этапов ТП на основе операций.
     */
    public List<Stage> buildStages(List<MachiningOperation> operations) {
        log.debug("MachiningProcessService.buildStages: operations={}", operations.size());

        List<Stage> stages = new ArrayList<>();

        for (MachiningOperation op : operations) {
            Stage.OperationType opType = mapToOperationType(op.getMethod());

            Stage stage = new Stage(
                    UUID.randomUUID().toString(),
                    op.getDesignation(),
                    op.getName(),
                    opType,
                    op.getOrder()
            );

            if (op.getEquipmentId() != null) {
                stage.setEquipmentId(op.getEquipmentId());
            }
            if (op.getTool() != null) {
                stage.setComment("Инструмент: " + op.getTool().getType());
            }
            if (op.getCuttingMode() != null) {
                stage.setRequiredAccuracy(op.getCuttingMode().getSummary());
            }

            stages.add(stage);
        }

        log.debug("MachiningProcessService.buildStages done: stages={}", stages.size());
        return stages;
    }

    private Stage.OperationType mapToOperationType(MachiningMethod method) {
        return switch (method) {
            case TURNING, MILLING, BORING -> method.isCnc()
                    ? Stage.OperationType.CNC_MACHINING
                    : Stage.OperationType.MACHINING;
            case DRILLING, REAMING -> Stage.OperationType.MACHINING;
            case GRINDING -> Stage.OperationType.MACHINING;
            case EDM, LASER -> Stage.OperationType.MACHINING;
            case CONTROL -> Stage.OperationType.TECHNICAL_CONTROL;
        };
    }

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private void validate(MachiningRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на проектирование ТП механообработки пуст");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new OrchestrationException("Не указан projectId");
        }
        if (request.getObjectRef() == null || request.getObjectRef().isBlank()) {
            throw new OrchestrationException("Не указан objectRef");
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные типы
    // ------------------------------------------------------------------



}
