package ru.iktk.modules.machining.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.machining.client.EquipmentClient;
import ru.iktk.modules.machining.client.StorageClient;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.CncProgramRequest;
import ru.iktk.modules.machining.dto.CncProgramResponse;
import ru.iktk.modules.machining.model.CncProgram;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningOperation;
import ru.iktk.modules.machining.postprocessor.Postprocessor;
import ru.iktk.modules.machining.postprocessor.PostprocessorRegistry;
import ru.iktk.modules.machining.repository.CncProgramRepository;
import ru.iktk.modules.machining.toolpath.ToolpathGenerator;
import ru.iktk.modules.machining.validator.GCodeValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис формирования управляющих программ (УП) для станков с ЧПУ ИКТК.
 *
 * <p>Отвечает за полный цикл подготовки УП:
 * <ol>
 *   <li><b>Получение операций</b> из ТП механообработки
 *       ({@link MachiningProcessService}, позиция 37).</li>
 *   <li><b>Генерация траектории инструмента</b> (toolpath) для каждой
 *       операции на основе геометрии и стратегии обработки.</li>
 *   <li><b>Расчёт опорных точек</b> с учётом коррекции на радиус
 *       инструмента, врезания/выхода, переходов между проходами.</li>
 *   <li><b>Постпроцессирование</b> — преобразование траектории в машинный
 *       код конкретной СЧПУ (Fanuc, Siemens, Heidenhain, Haas,
 *       Mitsubishi, Fagor и др.) с учётом кинематики станка.</li>
 *   <li><b>Валидация G-кода</b> — проверка синтаксиса, границ перемещений,
 *       столкновений (быстрая проверка без полной симуляции).</li>
 *   <li><b>Сохранение УП</b> в хранилище (MinIO/S3) и метаданных в БД.</li>
 *   <li><b>Формирование карты наладки</b> — список инструментов, режимов,
 *       нулевых точек, коррекций.</li>
 * </ol>
 *
 * <p><b>Поддерживаемые форматы УП:</b>
 * <ul>
 *   <li>ISO 6983 (G-код) — базовый формат;</li>
 *   <li>STEP-NC (ISO 14649) — при поддержке СЧПУ;</li>
 *   <li>фирменные диалекты: Fanuc, Siemens 840D, Heidenhain TNC,
 *       Haas, Mitsubishi, Fagor, Okuma.</li>
 * </ul>
 *
 * <p><b>Постпроцессор</b> выбирается по модели станка через
 * {@link PostprocessorRegistry}. Если модель не найдена — используется
 * постпроцессор по умолчанию из {@link MachiningProperties}
 * ({@code iktk.machining.cnc.default-postprocessor}).
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>вызывается из {@link MachiningProcessService} для операций с ЧПУ;</li>
 *   <li>использует {@link EquipmentClient} для получения характеристик
 *       станка;</li>
 *   <li>использует {@link StorageClient} для сохранения УП в MinIO;</li>
 *   <li>публикует событие {@code CNC_PROGRAM_READY} в оркестратор.</li>
 * </ul>
 */
@Service
public class CncProgramService {

    private static final Logger log = LoggerFactory.getLogger(CncProgramService.class);

    /** Ключ категории для сохранения УП в хранилище. */
    private static final String STORAGE_CATEGORY = "cnc";

    private final CncProgramRepository repository;
    private final EquipmentClient equipmentClient;
    private final StorageClient storageClient;
    private final PostprocessorRegistry postprocessorRegistry;
    private final ToolpathGenerator toolpathGenerator;
    private final GCodeValidator validator;
    private final MachiningProperties properties;

    public CncProgramService(CncProgramRepository repository,
                             EquipmentClient equipmentClient,
                             StorageClient storageClient,
                             PostprocessorRegistry postprocessorRegistry,
                             ToolpathGenerator toolpathGenerator,
                             GCodeValidator validator,
                             MachiningProperties properties) {
        this.repository = repository;
        this.equipmentClient = equipmentClient;
        this.storageClient = storageClient;
        this.postprocessorRegistry = postprocessorRegistry;
        this.toolpathGenerator = toolpathGenerator;
        this.validator = validator;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: генерация УП
    // ------------------------------------------------------------------

    /**
     * Сформировать управляющую программу для набора операций ЧПУ.
     *
     * @param request запрос с операциями и параметрами станка
     * @return результат генерации УП
     */
    @Transactional
    public CncProgramResponse generate(CncProgramRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        String processId = request.getProcessId();
        List<MachiningOperation> operations = request.getOperations();

        log.info("CncProgramService.generate start: projectId={}, processId={}, operations={}",
                projectId, processId, operations == null ? 0 : operations.size());

        // 1. Отбор операций ЧПУ
        List<MachiningOperation> cncOperations = operations == null
                ? List.of()
                : operations.stream().filter(MachiningOperation::isCnc).toList();

        if (cncOperations.isEmpty()) {
            throw new OrchestrationException(
                    "Нет операций ЧПУ для генерации УП: processId=" + processId);
        }

        // 2. Определение станка и постпроцессора
        String equipmentId = request.getEquipmentId();
        if (equipmentId == null || equipmentId.isBlank()) {
            equipmentId = cncOperations.get(0).getEquipmentId();
        }
        EquipmentInfo equipment = equipmentClient.getEquipment(equipmentId);
        if (equipment == null) {
            throw new OrchestrationException("Станок не найден: " + equipmentId);
        }

        Postprocessor postprocessor = resolvePostprocessor(equipment);

        log.info("CncProgramService: станок={}, постпроцессор={}",
                equipment.getModel(), postprocessor.getName());

        // 3. Генерация траектории и G-кода по каждой операции
        List<OperationProgram> operationPrograms = new ArrayList<>();
        for (MachiningOperation op : cncOperations) {
            OperationProgram program = generateForOperation(
                    op, equipment, postprocessor);
            operationPrograms.add(program);
        }

        // 4. Сборка полной УП
        String fullProgram = assembleProgram(equipment, postprocessor, operationPrograms);

        // 5. Валидация G-кода
        ValidationResult validation = validator.validate(fullProgram, equipment);
        if (!validation.isValid()) {
            log.warn("CncProgramService: валидация УП не пройдена, projectId={}, errors={}",
                    projectId, validation.getErrors().size());
        }

        // 6. Сохранение УП в хранилище
        String programName = buildProgramName(processId, equipment);
        String programUri = storageClient.uploadText(
                fullProgram, projectId, STORAGE_CATEGORY, programName + ".nc");

        // 7. Формирование карты наладки
        SetupSheet setupSheet = buildSetupSheet(equipment, postprocessor, operationPrograms);

        // 8. Сохранение метаданных УП
        CncProgram program = CncProgram.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .processId(processId)
                .equipmentId(equipmentId)
                .equipmentModel(equipment.getModel())
                .postprocessor(postprocessor.getName())
                .programUri(programUri)
                .programName(programName)
                .lineCount(countLines(fullProgram))
                .byteSize(fullProgram.getBytes().length)
                .operations(operationPrograms)
                .setupSheet(setupSheet)
                .validation(validation)
                .createdAt(Instant.now())
                .build();

        repository.save(program);

        log.info("CncProgramService.generate done: projectId={}, programId={}, uri={}, " +
                        "operations={}, lines={}, valid={}",
                projectId, program.getId(), programUri,
                operationPrograms.size(), program.getLineCount(), validation.isValid());

        return CncProgramResponse.builder()
                .projectId(projectId)
                .programId(program.getId())
                .equipmentId(equipmentId)
                .equipmentModel(equipment.getModel())
                .postprocessor(postprocessor.getName())
                .programUri(programUri)
                .programName(programName)
                .operations(operationPrograms)
                .setupSheet(setupSheet)
                .validation(validation)
                .build();
    }

    // ------------------------------------------------------------------
    // Генерация по одной операции
    // ------------------------------------------------------------------

    /**
     * Сгенерировать фрагмент УП для одной операции.
     */
    private OperationProgram generateForOperation(MachiningOperation op,
                                                  EquipmentInfo equipment,
                                                  Postprocessor postprocessor) {
        log.debug("CncProgramService: генерация операции {}, метод={}, инструмент={}",
                op.getName(), op.getMethod(),
                op.getTool() == null ? "—" : op.getTool().getType());

        // 1. Траектория инструмента
        Toolpath toolpath = toolpathGenerator.generate(op, equipment);

        if (toolpath == null || toolpath.getPoints().isEmpty()) {
            log.warn("CncProgramService: пустая траектория для операции {}", op.getName());
            return new OperationProgram(
                    op.getOrder(),
                    op.getName(),
                    op.getMethod(),
                    op.getTool(),
                    op.getCuttingMode(),
                    ";" + op.getName() + " — траектория не сгенерирована\n",
                    0
            );
        }

        // 2. Постпроцессирование
        String gcode = postprocessor.process(toolpath, op.getTool(), op.getCuttingMode());

        // 3. Проверка фрагмента
        ValidationResult fragmentValidation = validator.validateFragment(gcode, equipment);
        if (!fragmentValidation.isValid()) {
            log.warn("CncProgramService: ошибки в фрагменте {}: {}",
                    op.getName(), fragmentValidation.getErrors().size());
        }

        int lines = countLines(gcode);

        log.debug("CncProgramService: операция {} готова, точек={}, строк={}",
                op.getName(), toolpath.getPoints().size(), lines);

        return new OperationProgram(
                op.getOrder(),
                op.getName(),
                op.getMethod(),
                op.getTool(),
                op.getCuttingMode(),
                gcode,
                lines
        );
    }

    // ------------------------------------------------------------------
    // Сборка полной программы
    // ------------------------------------------------------------------

    /**
     * Собрать полную УП: заголовок + фрагменты операций + конец программы.
     */
    private String assembleProgram(EquipmentInfo equipment,
                                   Postprocessor postprocessor,
                                   List<OperationProgram> operationPrograms) {
        StringBuilder sb = new StringBuilder();

        // Заголовок УП
        sb.append(postprocessor.header(equipment));

        // Фрагменты операций
        for (OperationProgram op : operationPrograms) {
            sb.append("\n; ===== ").append(op.getName()).append(" =====\n");
            sb.append(op.getGcode());
        }

        // Конец программы
        sb.append(postprocessor.footer(equipment));

        log.debug("CncProgramService: полная УП собрана, строк={}",
                countLines(sb.toString()));

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Карта наладки
    // ------------------------------------------------------------------

    /**
     * Построить карту наладки: инструменты, режимы, нулевые точки, коррекции.
     */
    private SetupSheet buildSetupSheet(EquipmentInfo equipment,
                                       Postprocessor postprocessor,
                                       List<OperationProgram> operationPrograms) {
        List<ToolSpec> tools = new ArrayList<>();
        int toolNumber = 1;

        for (OperationProgram op : operationPrograms) {
            if (op.getTool() == null) {
                continue;
            }
            CuttingTool tool = op.getTool();
            CuttingMode mode = op.getCuttingMode();

            tools.add(ToolSpec.builder()
                    .toolNumber(toolNumber++)
                    .operationName(op.getName())
                    .type(tool.getType())
                    .diameter(tool.getDiameter())
                    .material(tool.getMaterial())
                    .coating(tool.getCoating())
                    .cuttingSpeed(mode == null ? null : mode.getCuttingSpeedMPerMin())
                    .feed(mode == null ? null : mode.getFeedMmPerRev())
                    .depthOfCut(mode == null ? null : mode.getDepthOfCutMm())
                    .spindleSpeed(mode == null ? null : mode.getSpindleSpeedRpm())
                    .build());
        }

        return SetupSheet.builder()
                .equipmentModel(equipment.getModel())
                .postprocessor(postprocessor.getName())
                .zeroPoints(Map.of(
                        "G54", "деталь",
                        "G55", "заготовка",
                        "G56", "стол"
                ))
                .tools(tools)
                .notes("Проверить привязку нуля и коррекции перед запуском")
                .build();
    }

    // ------------------------------------------------------------------
    // Определение постпроцессора
    // ------------------------------------------------------------------

    /**
     * Определить постпроцессор по модели станка.
     * Если не найден — использовать постпроцессор по умолчанию.
     */
    private Postprocessor resolvePostprocessor(EquipmentInfo equipment) {
        Postprocessor pp = postprocessorRegistry.find(equipment.getModel());
        if (pp != null) {
            return pp;
        }

        String defaultName = properties.getCncDefaultPostprocessor();
        Postprocessor defaultPp = postprocessorRegistry.findByName(defaultName);
        if (defaultPp == null) {
            throw new OrchestrationException(
                    "Постпроцессор не найден: model=" + equipment.getModel()
                            + ", default=" + defaultName);
        }
        log.warn("CncProgramService: постпроцессор для модели {} не найден, " +
                "используется по умолчанию: {}", equipment.getModel(), defaultName);
        return defaultPp;
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private String buildProgramName(String processId, EquipmentInfo equipment) {
        String safeModel = equipment.getModel() == null
                ? "CNC"
                : equipment.getModel().replaceAll("[^a-zA-Z0-9_.-]", "_");
        return "O_" + processId + "_" + safeModel + "_" + System.currentTimeMillis();
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void validate(CncProgramRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на генерацию УП пуст");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new OrchestrationException("Не указан projectId");
        }
        if (request.getProcessId() == null || request.getProcessId().isBlank()) {
            throw new OrchestrationException("Не указан processId");
        }
    }

    // ------------------------------------------------------------------
    // Дополнительные сценарии
    // ------------------------------------------------------------------

    /**
     * Перегенерировать УП для существующего процесса.
     */
    @Transactional
    public CncProgramResponse regenerate(String programId) {
        log.info("CncProgramService.regenerate: programId={}", programId);

        CncProgram existing = repository.findById(programId)
                .orElseThrow(() -> new OrchestrationException(
                        "УП не найдена: " + programId));

        CncProgramRequest request = CncProgramRequest.builder()
                .projectId(existing.getProjectId())
                .processId(existing.getProcessId())
                .equipmentId(existing.getEquipmentId())
                .operations(existing.getOperations().stream()
                        .map(OperationProgram::toOperation)
                        .toList())
                .build();

        return generate(request);
    }

    /**
     * Получить УП по идентификатору.
     */
    @Transactional(readOnly = true)
    public CncProgram getById(String programId) {
        return repository.findById(programId)
                .orElseThrow(() -> new OrchestrationException(
                        "УП не найдена: " + programId));
    }

    /**
     * Получить все УП по процессу.
     */
    @Transactional(readOnly = true)
    public List<CncProgram> getByProcess(String processId) {
        return repository.findByProcessId(processId);
    }

    /**
     * Удалить УП (метаданные + файл из хранилища).
     */
    @Transactional
    public void delete(String programId) {
        CncProgram program = getById(programId);

        try {
            storageClient.delete(program.getProgramUri());
        } catch (Exception e) {
            log.warn("CncProgramService.delete: не удалось удалить файл {}: {}",
                    program.getProgramUri(), e.getMessage());
        }

        repository.delete(program);
        log.info("CncProgramService.delete: programId={} удалена", programId);
    }

    // ------------------------------------------------------------------
    // Вспомогательные типы
    // ------------------------------------------------------------------

    /** Информация о станке. */
    public static class EquipmentInfo {
        private final String id;
        private final String model;
        private final String cncSystem;
        private final int axes;
        private final Map<String, Object> parameters;

        public EquipmentInfo(String id, String model, String cncSystem,
                             int axes, Map<String, Object> parameters) {
            this.id = id;
            this.model = model;
            this.cncSystem = cncSystem;
            this.axes = axes;
            this.parameters = parameters == null ? Map.of() : parameters;
        }

        public String getId() { return id; }
        public String getModel() { return model; }
        public String getCncSystem() { return cncSystem; }
        public int getAxes() { return axes; }
        public Map<String, Object> getParameters() { return parameters; }
    }

    /** Траектория инструмента. */
    public static class Toolpath {
        private final List<ToolpathPoint> points;

        public Toolpath(List<ToolpathPoint> points) {
            this.points = points == null ? List.of() : points;
        }

        public List<ToolpathPoint> getPoints() { return points; }
    }

    /** Точка траектории. */
    public static class ToolpathPoint {
        private final BigDecimal x;
        private final BigDecimal y;
        private final BigDecimal z;
        private final String type;
        private final BigDecimal feed;

        public ToolpathPoint(BigDecimal x, BigDecimal y, BigDecimal z,
                             String type, BigDecimal feed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.feed = feed;
        }

        public BigDecimal getX() { return x; }
        public BigDecimal getY() { return y; }
        public BigDecimal getZ() { return z; }
        public String getType() { return type; }
        public BigDecimal getFeed() { return feed; }
    }

    /** Программа по одной операции. */
    public static class OperationProgram {
        private final int order;
        private final String name;
        private final MachiningMethod method;
        private final CuttingTool tool;
        private final CuttingMode mode;
        private final String gcode;
        private final int lineCount;

        public OperationProgram(int order, String name, MachiningMethod method,
                                CuttingTool tool, CuttingMode mode,
                                String gcode, int lineCount) {
            this.order = order;
            this.name = name;
            this.method = method;
            this.tool = tool;
            this.mode = mode;
            this.gcode = gcode;
            this.lineCount = lineCount;
        }

        public int getOrder() { return order; }
        public String getName() { return name; }
        public MachiningMethod getMethod() { return method; }
        public CuttingTool getTool() { return tool; }
        public CuttingMode getMode() { return mode; }
        public String getGcode() { return gcode; }
        public int getLineCount() { return lineCount; }

        /** Восстановить операцию для перегенерации. */
        public MachiningOperation toOperation() {
            return MachiningOperation.builder()
                    .order(order)
                    .name(name)
                    .method(method)
                    .cnc(true)
                    .tool(tool)
                    .cuttingMode(mode)
                    .build();
        }
    }

    /** Результат валидации G-кода. */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid,
                                List<String> errors,
                                List<String> warnings) {
            this.valid = valid;
            this.errors = errors == null ? List.of() : errors;
            this.warnings = warnings == null ? List.of() : warnings;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }
    }

    /** Карта наладки. */
    public static class SetupSheet {
        private final String equipmentModel;
        private final String postprocessor;
        private final Map<String, String> zeroPoints;
        private final List<ToolSpec> tools;
        private final String notes;

        private SetupSheet(String equipmentModel, String postprocessor,
                           Map<String, String> zeroPoints, List<ToolSpec> tools,
                           String notes) {
            this.equipmentModel = equipmentModel;
            this.postprocessor = postprocessor;
            this.zeroPoints = zeroPoints == null ? Map.of() : zeroPoints;
            this.tools = tools == null ? List.of() : tools;
            this.notes = notes;
        }

        public String getEquipmentModel() { return equipmentModel; }
        public String getPostprocessor() { return postprocessor; }
        public Map<String, String> getZeroPoints() { return zeroPoints; }
        public List<ToolSpec> getTools() { return tools; }
        public String getNotes() { return notes; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String equipmentModel;
            private String postprocessor;
            private Map<String, String> zeroPoints;
            private List<ToolSpec> tools;
            private String notes;

            public Builder equipmentModel(String v) { this.equipmentModel = v; return this; }
            public Builder postprocessor(String v) { this.postprocessor = v; return this; }
            public Builder zeroPoints(Map<String, String> v) { this.zeroPoints = v; return this; }
            public Builder tools(List<ToolSpec> v) { this.tools = v; return this; }
            public Builder notes(String v) { this.notes = v; return this; }

            public SetupSheet build() {
                return new SetupSheet(equipmentModel, postprocessor,
                        zeroPoints, tools, notes);
            }
        }
    }

    /** Спецификация инструмента в карте наладки. */
    public static class ToolSpec {
        private final int toolNumber;
        private final String operationName;
        private final String type;
        private final BigDecimal diameter;
        private final String material;
        private final String coating;
        private final BigDecimal cuttingSpeed;
        private final BigDecimal feed;
        private final BigDecimal depthOfCut;
        private final BigDecimal spindleSpeed;

        private ToolSpec(int toolNumber, String operationName, String type,
                         BigDecimal diameter, String material, String coating,
                         BigDecimal cuttingSpeed, BigDecimal feed,
                         BigDecimal depthOfCut, BigDecimal spindleSpeed) {
            this.toolNumber = toolNumber;
            this.operationName = operationName;
            this.type = type;
            this.diameter = diameter;
            this.material = material;
            this.coating = coating;
            this.cuttingSpeed = cuttingSpeed;
            this.feed = feed;
            this.depthOfCut = depthOfCut;
            this.spindleSpeed = spindleSpeed;
        }

        public int getToolNumber() { return toolNumber; }
        public String getOperationName() { return operationName; }
        public String getType() { return type; }
        public BigDecimal getDiameter() { return diameter; }
        public String getMaterial() { return material; }
        public String getCoating() { return coating; }
        public BigDecimal getCuttingSpeed() { return cuttingSpeed; }
        public BigDecimal getFeed() { return feed; }
        public BigDecimal getDepthOfCut() { return depthOfCut; }
        public BigDecimal getSpindleSpeed() { return spindleSpeed; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int toolNumber;
            private String operationName;
            private String type;
            private BigDecimal diameter;
            private String material;
            private String coating;
            private BigDecimal cuttingSpeed;
            private BigDecimal feed;
            private BigDecimal depthOfCut;
            private BigDecimal spindleSpeed;

            public Builder toolNumber(int v) { this.toolNumber = v; return this; }
            public Builder operationName(String v) { this.operationName = v; return this; }
            public Builder type(String v) { this.type = v; return this; }
            public Builder diameter(BigDecimal v) { this.diameter = v; return this; }
            public Builder material(String v) { this.material = v; return this; }
            public Builder coating(String v) { this.coating = v; return this; }
            public Builder cuttingSpeed(BigDecimal v) { this.cuttingSpeed = v; return this; }
            public Builder feed(BigDecimal v) { this.feed = v; return this; }
            public Builder depthOfCut(BigDecimal v) { this.depthOfCut = v; return this; }
            public Builder spindleSpeed(BigDecimal v) { this.spindleSpeed = v; return this; }

            public ToolSpec build() {
                return new ToolSpec(toolNumber, operationName, type, diameter,
                        material, coating, cuttingSpeed, feed, depthOfCut, spindleSpeed);
            }
        }
    }
}