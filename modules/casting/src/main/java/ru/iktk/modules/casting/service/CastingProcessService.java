package ru.iktk.modules.casting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.domain.Project;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.casting.client.EquipmentClient;
import ru.iktk.modules.casting.client.GeometryClient;
import ru.iktk.modules.casting.config.CastingProperties;
import ru.iktk.modules.casting.dto.CastingRequest;
import ru.iktk.modules.casting.dto.CastingResponse;
import ru.iktk.modules.casting.model.CastingMethod;
import ru.iktk.modules.casting.model.CastingProcess;
import ru.iktk.modules.casting.model.GatingSystem;
import ru.iktk.modules.casting.model.ShrinkageCalculation;
import ru.iktk.modules.casting.repository.CastingProcessRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Основной сервис проектирования литейного технологического процесса ИКТК.
 *
 * <p>Отвечает за полный цикл проектирования литейного ТП:
 * <ol>
 *   <li><b>Анализ технологичности</b> — проверка геометрии на литейные
 *       ограничения: уклоны, радиусы, толщины стенок, теплоузлы,
 *       поднутрения.</li>
 *   <li><b>Выбор способа литья</b> — по материалу, серийности, точности,
 *       габаритам, сложности; с учётом доступного оборудования.</li>
 *   <li><b>Расчёт усадки и припусков</b> — по материалу и способу литья.</li>
 *   <li><b>Расчёт литниково-питающей системы (ЛПС)</b> — стояк, шлакоуловитель,
 *       питатели, прибыли, выпоры.</li>
 *   <li><b>Проектирование оснастки</b> — модели, стержни, формы,
 *       подмодельные плиты.</li>
 *   <li><b>Формирование этапов ТП</b> — операции литейного передела.</li>
 *   <li><b>Нормирование</b> — предварительные нормы времени по операциям.</li>
 * </ol>
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>получает команду от оркестратора через {@code ModuleCommandConsumer};</li>
 *   <li>запрашивает геометрию через {@link GeometryClient};</li>
 *   <li>проверяет оборудование через {@link EquipmentClient};</li>
 *   <li>сохраняет результат в {@link CastingProcessRepository};</li>
 *   <li>публикует событие {@code PART_PROCESS_READY} в оркестратор.</li>
 * </ul>
 */
@Service
public class CastingProcessService {

    private static final Logger log = LoggerFactory.getLogger(CastingProcessService.class);

    private final CastingProcessRepository repository;
    private final GeometryClient geometryClient;
    private final EquipmentClient equipmentClient;
    private final CastingProperties properties;

    public CastingProcessService(CastingProcessRepository repository,
                                 GeometryClient geometryClient,
                                 EquipmentClient equipmentClient,
                                 CastingProperties properties) {
        this.repository = repository;
        this.geometryClient = geometryClient;
        this.equipmentClient = equipmentClient;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: проектирование литейного ТП
    // ------------------------------------------------------------------

    /**
     * Спроектировать литейный ТП для объекта изготовления.
     *
     * @param request запрос от оркестратора
     * @return результат проектирования
     */
    @Transactional
    public CastingResponse designProcess(CastingRequest request) {
        validate(request);
        String projectId = request.getProjectId();
        String objectRef = request.getObjectRef();

        log.info("CastingProcessService.designProcess start: projectId={}, objectRef={}",
                projectId, objectRef);

        // 1. Получить геометрию
        GeometryData geometry = geometryClient.fetchGeometry(projectId, objectRef);
        if (geometry == null) {
            throw new OrchestrationException(
                    "Геометрия не найдена: projectId=" + projectId + ", objectRef=" + objectRef);
        }

        return designProcessWithGeometry(request, geometry);
    }

    public CastingResponse designProcessWithGeometry(CastingRequest request, GeometryData geometry) {
        validate(request);
        String projectId = request.getProjectId();
        String objectRef = request.getObjectRef();

        // 2. Анализ технологичности
        ManufacturabilityReport report = analyzeManufacturability(geometry);
        if (!report.isPassed()) {
            log.warn("CastingProcessService: технологичность не пройдена, projectId={}, issues={}",
                    projectId, report.getIssues().size());
        }

        // 3. Выбор способа литья
        CastingMethod method = selectCastingMethod(geometry, request);

        // 4. Расчёт усадки и припусков
        ShrinkageCalculation shrinkage = calculateShrinkage(geometry, method);

        // 5. Расчёт ЛПС
        GatingSystem gating = calculateGatingSystem(geometry, method, shrinkage);

        // 6. Проектирование оснастки
        ToolingSpec tooling = designTooling(geometry, method, gating);

        // 7. Формирование этапов ТП
        List<Stage> stages = buildStages(method, gating, tooling);

        // 8. Сборка и сохранение результата
        CastingProcess process = CastingProcess.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .objectRef(objectRef)
                .method(method)
                .shrinkage(shrinkage)
                .gatingSystem(gating)
                .tooling(tooling)
                .stages(stages)
                .manufacturabilityReport(report)
                .createdAt(Instant.now())
                .build();

        CastingProcess saved = repository.save(process);

        log.info("CastingProcessService.designProcess done: projectId={}, processId={}, method={}, stages={}",
                projectId, saved.getId(), method, stages.size());

        return CastingResponse.builder()
                .projectId(projectId)
                .processId(saved.getId())
                .method(method)
                .stages(stages)
                .shrinkage(shrinkage)
                .gatingSystem(gating)
                .tooling(tooling)
                .warnings(report.getIssues())
                .build();
    }

    // ------------------------------------------------------------------
    // 1. Анализ технологичности
    // ------------------------------------------------------------------

    /**
     * Проверка геометрии на литейные ограничения.
     *
     * <p>Проверки:
     * <ul>
     *   <li>минимальная толщина стенки — {@code properties.getMinWallThickness()};</li>
     *   <li>минимальный радиус скругления;</li>
     *   <li>минимальный литейный уклон;</li>
     *   <li>наличие поднутрений;</li>
     *   <li>теплоузлы (массивные узлы, где возможна усадочная рыхлость).</li>
     * </ul>
     */
    public ManufacturabilityReport analyzeManufacturability(GeometryData geometry) {
        log.debug("CastingProcessService.analyzeManufacturability: objectRef={}",
                geometry.getObjectRef());

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal minWall = properties.getMinWallThickness();
        if (geometry.getMinWallThickness() != null
                && geometry.getMinWallThickness().compareTo(minWall) < 0) {
            issues.add("Толщина стенки " + geometry.getMinWallThickness()
                    + " мм меньше минимальной " + minWall + " мм");
        }

        BigDecimal minRadius = properties.getMinFilletRadius();
        if (geometry.getMinFilletRadius() != null
                && geometry.getMinFilletRadius().compareTo(minRadius) < 0) {
            issues.add("Радиус скругления " + geometry.getMinFilletRadius()
                    + " мм меньше минимального " + minRadius + " мм");
        }

        BigDecimal minDraft = properties.getMinDraftAngle();
        if (geometry.getMinDraftAngle() != null
                && geometry.getMinDraftAngle().compareTo(minDraft) < 0) {
            warnings.add("Литейный уклон " + geometry.getMinDraftAngle()
                    + "° меньше рекомендуемого " + minDraft + "°");
        }

        if (geometry.hasUndercuts()) {
            warnings.add("Обнаружены поднутрения — потребуются стержни или разъёмные формы");
        }

        if (geometry.hasHotSpots()) {
            warnings.add("Обнаружены теплоузлы — возможна усадочная рыхлость, нужны прибыли");
        }

        boolean passed = issues.isEmpty();
        log.debug("CastingProcessService.analyzeManufacturability done: passed={}, issues={}, warnings={}",
                passed, issues.size(), warnings.size());

        return new ManufacturabilityReport(passed, issues, warnings);
    }

    // ------------------------------------------------------------------
    // 2. Выбор способа литья
    // ------------------------------------------------------------------

    /**
     * Выбор способа литья по атрибутам детали и доступному оборудованию.
     *
     * <p>Критерии:
     * <ul>
     *   <li>материал (сталь, чугун, алюминий, бронза, ...);</li>
     *   <li>серийность (единичное, серийное, массовое);</li>
     *   <li>точность и шероховатость;</li>
     *   <li>габариты и масса;</li>
     *   <li>сложность геометрии;</li>
     *   <li>доступность оборудования на предприятии.</li>
     * </ul>
     */
    public CastingMethod selectCastingMethod(GeometryData geometry, CastingRequest request) {
        log.debug("CastingProcessService.selectCastingMethod: material={}, production={}",
                geometry.getMaterial(), request.getProductionType());

        // Приоритет — способ, указанный в запросе
        if (request.getPreferredMethod() != null) {
            boolean available = isMethodAvailableSafe(request.getProjectId(), request.getPreferredMethod());
            if (available) {
                log.debug("CastingProcessService: выбран предпочтительный способ {}", request.getPreferredMethod());
                return request.getPreferredMethod();
            }
            log.warn("CastingProcessService: предпочтительный способ {} недоступен, выполняется подбор",
                    request.getPreferredMethod());
        }

        // Автоматический подбор по эвристикам
        CastingMethod method = pickMethodByAttributes(geometry, request);

        boolean available = isMethodAvailableSafe(request.getProjectId(), method);
        if (!available) {
            log.warn("CastingProcessService: способ {} недоступен на предприятии, потребуется gap-анализ", method);
        }

        log.info("CastingProcessService.selectCastingMethod done: method={}, available={}", method, available);
        return method;
    }

    /**
     * Безопасная проверка доступности оборудования. Если equipment-service недоступен,
     * считаем оборудование доступным (деградация без падения).
     */
    private boolean isMethodAvailableSafe(String projectId, CastingMethod method) {
        try {
            return equipmentClient.isMethodAvailable(projectId, method);
        } catch (Exception e) {
            log.warn("CastingProcessService: equipment-service недоступен ({}), считаем оборудование доступным для {}",
                    e.getMessage(), method);
            return true;
        }
    }

    private CastingMethod pickMethodByAttributes(GeometryData geometry, CastingRequest request) {
        // Упрощённая эвристика. Реальная логика — на основе справочника
        // технологических решений и правил.
        if (request.getProductionType() == TechnologyProcess.ProductionType.MASS) {
            return CastingMethod.DIE_CASTING;
        }
        if (geometry.getMaterial() != null
                && geometry.getMaterial().toLowerCase().contains("алюмин")) {
            return CastingMethod.DIE_CASTING;
        }
        if (request.getProductionType() == TechnologyProcess.ProductionType.SINGLE) {
            return CastingMethod.SAND_CASTING;
        }
        return CastingMethod.INVESTMENT_CASTING;
    }

    // ------------------------------------------------------------------
    // 3. Расчёт усадки и припусков
    // ------------------------------------------------------------------

    /**
     * Расчёт усадки и припусков на механическую обработку.
     *
     * <p>Коэффициенты усадки зависят от материала и способа литья
     * (справочные данные в {@link CastingProperties}).
     */
    public ShrinkageCalculation calculateShrinkage(GeometryData geometry, CastingMethod method) {
        log.debug("CastingProcessService.calculateShrinkage: method={}, material={}",
                method, geometry.getMaterial());

        BigDecimal shrinkagePercent = properties.getShrinkageCoefficient(
                geometry.getMaterial(), method);

        BigDecimal linearAllowance = properties.getMachiningAllowance(
                method, geometry.getNominalDimensions());

        ShrinkageCalculation result = ShrinkageCalculation.builder()
                .shrinkagePercent(shrinkagePercent)
                .linearAllowanceMm(linearAllowance)
                .material(geometry.getMaterial())
                .method(method)
                .build();

        log.debug("CastingProcessService.calculateShrinkage done: shrinkage={}%, allowance={} мм",
                shrinkagePercent, linearAllowance);
        return result;
    }

    // ------------------------------------------------------------------
    // 4. Расчёт ЛПС
    // ------------------------------------------------------------------

    /**
     * Расчёт литниково-питающей системы (ЛПС).
     *
     * <p>Определяются:
     * <ul>
     *   <li>диаметр стояка;</li>
     *   <li>сечение шлакоуловителя;</li>
     *   <li>сечения питателей;</li>
     *   <li>размеры прибылей;</li>
     *   <li>количество и расположение выпоров;</li>
     *   <li>объём заливаемого металла и время заливки.</li>
     * </ul>
     */
    public GatingSystem calculateGatingSystem(GeometryData geometry,
                                              CastingMethod method,
                                              ShrinkageCalculation shrinkage) {
        log.debug("CastingProcessService.calculateGatingSystem: method={}, volume={}",
                method, geometry.getVolume());

        // Упрощённый расчёт. Реальная логика — по методикам литейной
        // технологии (например, по модулю заливки, площади питателей,
        // гидравлическому расчёту).
        BigDecimal sprueDiameter = geometry.getVolume() == null
                ? BigDecimal.valueOf(20)
                : BigDecimal.valueOf(Math.sqrt(geometry.getVolume().doubleValue() / 1000.0))
                        .multiply(BigDecimal.valueOf(10))
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal runnerArea = sprueDiameter.multiply(BigDecimal.valueOf(1.5))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal gateArea = sprueDiameter.multiply(BigDecimal.valueOf(0.8))
                .setScale(2, RoundingMode.HALF_UP);

        int gatesCount = geometry.getComplexityScore() > 5 ? 4 : 2;

        GatingSystem gating = GatingSystem.builder()
                .sprueDiameterMm(sprueDiameter)
                .runnerAreaMm2(runnerArea)
                .gateAreaMm2(gateArea)
                .gatesCount(gatesCount)
                .risersCount(calculateRisers(geometry))
                .pouringTimeSec(calculatePouringTime(geometry))
                .metalVolumeMm3(geometry.getVolume())
                .build();

        log.debug("CastingProcessService.calculateGatingSystem done: sprue={} мм, gates={}",
                sprueDiameter, gatesCount);
        return gating;
    }

    private int calculateRisers(GeometryData geometry) {
        return geometry.hasHotSpots() ? 2 : 0;
    }

    private BigDecimal calculatePouringTime(GeometryData geometry) {
        if (geometry.getMass() == null) {
            return BigDecimal.valueOf(15);
        }
        return BigDecimal.valueOf(geometry.getMass().doubleValue() * 1.2)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 5. Проектирование оснастки
    // ------------------------------------------------------------------

    /**
     * Проектирование литейной оснастки.
     *
     * <p>Состав оснастки зависит от способа литья:
     * <ul>
     *   <li>песчаные формы — модели, стержневые ящики, опоки;</li>
     *   <li>кокиль — металлические формы, стержни;</li>
     *   <li>литьё под давлением — пресс-формы;</li>
     *   <li>точное литьё — восковые модели, керамические формы.</li>
     * </ul>
     */
    public ToolingSpec designTooling(GeometryData geometry,
                                     CastingMethod method,
                                     GatingSystem gating) {
        log.debug("CastingProcessService.designTooling: method={}", method);

        List<String> toolingItems = new ArrayList<>();
        toolingItems.add("Модель литейная");
        toolingItems.add("Стержневой ящик");

        if (method == CastingMethod.DIE_CASTING) {
            toolingItems.add("Пресс-форма");
        } else if (method == CastingMethod.INVESTMENT_CASTING) {
            toolingItems.add("Пресс-форма для восковой модели");
            toolingItems.add("Керамическая форма");
        } else {
            toolingItems.add("Опока");
            toolingItems.add("Подмодельная плита");
        }

        if (gating.getRisersCount() > 0) {
            toolingItems.add("Прибыль (питатель)");
        }

        ToolingSpec spec = ToolingSpec.builder()
                .items(toolingItems)
                .material(properties.getToolingMaterial(method))
                .estimatedCost(properties.estimateToolingCost(method, geometry))
                .build();

        log.debug("CastingProcessService.designTooling done: items={}", toolingItems.size());
        return spec;
    }

    // ------------------------------------------------------------------
    // 6. Формирование этапов ТП
    // ------------------------------------------------------------------

    /**
     * Формирование этапов литейного ТП.
     *
     * <p>Стандартные этапы:
     * <ol>
     *   <li>Подготовка шихты и расплава.</li>
     *   <li>Изготовление формы / сборка формы.</li>
     *   <li>Заливка металла.</li>
     *   <li>Кристаллизация и охлаждение.</li>
     *   <li>Выбивка и очистка отливки.</li>
     *   <li>Обрубка литников и прибылей.</li>
     *   <li>Термообработка (при необходимости).</li>
     *   <li>Контроль отливки.</li>
     * </ol>
     */
    public List<Stage> buildStages(CastingMethod method,
                                   GatingSystem gating,
                                   ToolingSpec tooling) {
        log.debug("CastingProcessService.buildStages: method={}", method);

        List<Stage> stages = new ArrayList<>();
        int order = 1;

        stages.add(buildStage(String.valueOf(order++ * 5), "Подготовка шихты",
                Stage.OperationType.BLANK_PREPARATION, null));

        stages.add(buildStage(String.valueOf(order++ * 5), "Изготовление формы",
                Stage.OperationType.CASTING, tooling.getItems().get(0)));

        stages.add(buildStage(String.valueOf(order++ * 5), "Заливка металла",
                Stage.OperationType.CASTING, null));

        stages.add(buildStage(String.valueOf(order++ * 5), "Выбивка и очистка",
                Stage.OperationType.CASTING, null));

        stages.add(buildStage(String.valueOf(order++ * 5), "Обрубка литников",
                Stage.OperationType.CASTING, null));

        stages.add(buildStage(String.valueOf(order++ * 5), "Контроль отливки",
                Stage.OperationType.TECHNICAL_CONTROL, null));

        log.debug("CastingProcessService.buildStages done: stages={}", stages.size());
        return stages;
    }

    private Stage buildStage(String designation,
                             String name,
                             Stage.OperationType operationType,
                             String toolingId) {
        Stage stage = new Stage(
                UUID.randomUUID().toString(),
                designation,
                name,
                operationType,
                Integer.parseInt(designation) / 5
        );
        if (toolingId != null) {
            stage.setComment("Оснастка: " + toolingId);
        }
        return stage;
    }

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private void validate(CastingRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на проектирование литейного ТП пуст");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new OrchestrationException("Не указан projectId");
        }
        if (request.getObjectRef() == null || request.getObjectRef().isBlank()) {
            throw new OrchestrationException("Не указан objectRef");
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные типы (при необходимости вынести в отдельные файлы)
    // ------------------------------------------------------------------

    /** Данные геометрии, полученные из integration. */
    public static class GeometryData {
        private final String objectRef;
        private final String material;
        private final BigDecimal volume;
        private final BigDecimal mass;
        private final BigDecimal minWallThickness;
        private final BigDecimal minFilletRadius;
        private final BigDecimal minDraftAngle;
        private final BigDecimal nominalDimensions;
        private final int complexityScore;
        private final boolean undercuts;
        private final boolean hotSpots;

        public GeometryData(String objectRef, String material, BigDecimal volume,
                            BigDecimal mass, BigDecimal minWallThickness,
                            BigDecimal minFilletRadius, BigDecimal minDraftAngle,
                            BigDecimal nominalDimensions, int complexityScore,
                            boolean undercuts, boolean hotSpots) {
            this.objectRef = objectRef;
            this.material = material;
            this.volume = volume;
            this.mass = mass;
            this.minWallThickness = minWallThickness;
            this.minFilletRadius = minFilletRadius;
            this.minDraftAngle = minDraftAngle;
            this.nominalDimensions = nominalDimensions;
            this.complexityScore = complexityScore;
            this.undercuts = undercuts;
            this.hotSpots = hotSpots;
        }

        public String getObjectRef() { return objectRef; }
        public String getMaterial() { return material; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getMass() { return mass; }
        public BigDecimal getMinWallThickness() { return minWallThickness; }
        public BigDecimal getMinFilletRadius() { return minFilletRadius; }
        public BigDecimal getMinDraftAngle() { return minDraftAngle; }
        public BigDecimal getNominalDimensions() { return nominalDimensions; }
        public int getComplexityScore() { return complexityScore; }
        public boolean hasUndercuts() { return undercuts; }
        public boolean hasHotSpots() { return hotSpots; }
    }

    /** Отчёт о технологичности. */
    public static class ManufacturabilityReport {
        private final boolean passed;
        private final List<String> issues;
        private final List<String> warnings;

        public ManufacturabilityReport(boolean passed,
                                       List<String> issues,
                                       List<String> warnings) {
            this.passed = passed;
            this.issues = issues == null ? List.of() : issues;
            this.warnings = warnings == null ? List.of() : warnings;
        }

        public boolean isPassed() { return passed; }
        public List<String> getIssues() { return issues; }
        public List<String> getWarnings() { return warnings; }
    }

    /** Спецификация оснастки. */
    public static class ToolingSpec {
        private final List<String> items;
        private final String material;
        private final BigDecimal estimatedCost;

        private ToolingSpec(List<String> items, String material, BigDecimal estimatedCost) {
            this.items = items == null ? List.of() : items;
            this.material = material;
            this.estimatedCost = estimatedCost;
        }

        public List<String> getItems() { return items; }
        public String getMaterial() { return material; }
        public BigDecimal getEstimatedCost() { return estimatedCost; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private List<String> items;
            private String material;
            private BigDecimal estimatedCost;

            public Builder items(List<String> items) { this.items = items; return this; }
            public Builder material(String material) { this.material = material; return this; }
            public Builder estimatedCost(BigDecimal cost) { this.estimatedCost = cost; return this; }

            public ToolingSpec build() {
                return new ToolingSpec(items, material, estimatedCost);
            }
        }
    }
}