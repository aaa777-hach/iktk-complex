package ru.iktk.modules.norming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.norming.config.NormingProperties;
import ru.iktk.modules.norming.dto.MaterialNormRequest;
import ru.iktk.modules.norming.dto.MaterialNormResponse;
import ru.iktk.modules.norming.model.MaterialConsumption;
import ru.iktk.modules.norming.model.MaterialInfo;
import ru.iktk.modules.norming.model.MaterialNorm;
import ru.iktk.modules.norming.model.ToolConsumption;
import ru.iktk.modules.norming.repository.MaterialNormRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис расчёта норм расхода материалов ИКТК.
 *
 * <p>Отвечает за расчёт:
 * <ul>
 *   <li><b>Нр</b> — норма расхода материала на изделие (кг, м, м²);</li>
 *   <li><b>Мзаг</b> — масса заготовки;</li>
 *   <li><b>Мдет</b> — масса готовой детали;</li>
 *   <li><b>КИМ</b> = Мдет / Мзаг — коэффициент использования материала;</li>
 *   <li><b>Отходы</b> — технологические потери (стружка, облой, литники,
 *       угар, брак);</li>
 *   <li><b>Расход инструмента</b> — по стойкости и объёму обработки;</li>
 *   <li><b>Расход оснастки</b> — по стойкости и числу циклов.</li>
 * </ul>
 *
 * <p><b>Виды материалов:</b>
 * <ul>
 *   <li>металлы — сталь, чугун, алюминий, медь, бронза, латунь, титан;</li>
 *   <li>полимеры — пластмассы, резины, композиты;</li>
 *   <li>вспомогательные — смазки, СОЖ, газы, припои, клеи, краски.</li>
 * </ul>
 *
 * <p><b>Расчёт нормы расхода материала:</b>
 * <pre>
 *   Нр = Мзаг · (1 + Ктех) · (1 + Кбр),
 *   где:
 *     Мзаг  — масса заготовки;
 *     Ктех  — коэффициент технологических потерь (угар, облой, литники,
 *             стружка, некратность);
 *     Кбр   — коэффициент брака (по статистике предприятия).
 * </pre>
 *
 * <p><b>Расчёт по видам ТП:</b>
 * <ul>
 *   <li>литьё — масса отливки + литники + прибыли + угар;</li>
 *   <li>штамповка — масса поковки + облой + угар + некратность;</li>
 *   <li>механообработка — масса заготовки + стружка (по объёму среза);</li>
 *   <li>сборка — расход вспомогательных материалов (крепёж, клей, СОЖ);</li>
 *   <li>термообработка, покрытия — расход сред, газов, реагентов.</li>
 * </ul>
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>получает ТП от модулей {@code casting}, {@code forging},
 *       {@code machining}, {@code assembly-process-service};</li>
 *   <li>использует справочники материалов из {@link NormingProperties};</li>
 *   <li>использует нормы времени из {@link TimeNormService} для расчёта
 *       износа инструмента;</li>
 *   <li>сохраняет результат в {@link MaterialNormRepository}.</li>
 * </ul>
 */
@Service
public class MaterialNormService {

    private static final Logger log = LoggerFactory.getLogger(MaterialNormService.class);

    /** Точность расчёта массы — 4 знака. */
    private static final int MASS_SCALE = 4;

    /** Точность расчёта коэффициентов — 4 знака. */
    private static final int COEFFICIENT_SCALE = 4;

    private final MaterialNormRepository repository;
    private final NormingProperties properties;

    public MaterialNormService(MaterialNormRepository repository,
                               NormingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: расчёт норм расхода материалов по ТП
    // ------------------------------------------------------------------

    /**
     * Рассчитать нормы расхода материалов по технологическому процессу.
     *
     * @param request запрос с ТП и параметрами партии
     * @return результат расчёта
     */
    @Transactional
    public MaterialNormResponse calculate(MaterialNormRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        TechnologyProcess process = request.getProcess();
        BigDecimal batchSize = request.getBatchSize() == null
                ? BigDecimal.ONE
                : request.getBatchSize();

        log.info("MaterialNormService.calculate start: projectId={}, processId={}, " +
                        "operations={}, batchSize={}",
                projectId, process.getId(),
                process.getStages() == null ? 0 : process.getStages().size(),
                batchSize);

        // 1. Определение материала и заготовки
        MaterialInfo material = resolveMaterial(process, request);

        // 2. Расчёт массы заготовки и детали
        BigDecimal blankMass = calculateBlankMass(process, material, request);
        BigDecimal partMass = calculatePartMass(process, material, request);

        // 3. Расчёт технологических потерь по операциям
        List<MaterialConsumption> consumptions = new ArrayList<>();
        BigDecimal totalLosses = BigDecimal.ZERO;

        for (Stage stage : process.getStages()) {
            MaterialConsumption consumption = calculateStageConsumption(
                    stage, process, material, blankMass, partMass);
            consumptions.add(consumption);

            totalLosses = totalLosses.add(consumption.getLossMass());
        }

        // 4. Норма расхода на изделие
        BigDecimal techLossCoefficient = calculateTechLossCoefficient(
                totalLosses, blankMass);
        BigDecimal defectCoefficient = properties.getDefectCoefficient(
                request.getEnterpriseCode());

        BigDecimal normPerPart = blankMass
                .multiply(BigDecimal.ONE.add(techLossCoefficient))
                .multiply(BigDecimal.ONE.add(defectCoefficient))
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);

        // 5. Норма расхода на партию
        BigDecimal normPerBatch = normPerPart
                .multiply(batchSize)
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);

        // 6. Коэффициент использования материала
        BigDecimal kим = normPerPart.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : partMass.divide(normPerPart, COEFFICIENT_SCALE, RoundingMode.HALF_UP);

        // 7. Расход инструмента и оснастки
        List<ToolConsumption> toolConsumptions = calculateToolConsumption(
                process, material, batchSize);

        // 8. Сохранение результата
        MaterialNorm norm = MaterialNorm.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .processId(process.getId())
                .material(material)
                .blankMass(blankMass)
                .partMass(partMass)
                .consumptions(consumptions)
                .toolConsumptions(toolConsumptions)
                .normPerPart(normPerPart)
                .normPerBatch(normPerBatch)
                .batchSize(batchSize)
                .materialUtilization(kим)
                .techLossCoefficient(techLossCoefficient)
                .defectCoefficient(defectCoefficient)
                .createdAt(Instant.now())
                .build();

        repository.save(norm);

        log.info("MaterialNormService.calculate done: projectId={}, processId={}, " +
                        "blankMass={} кг, partMass={} кг, normPerPart={} кг, КИМ={}",
                projectId, process.getId(), blankMass, partMass, normPerPart, kим);

        return MaterialNormResponse.builder()
                .projectId(projectId)
                .processId(process.getId())
                .normId(norm.getId())
                .material(material)
                .blankMass(blankMass)
                .partMass(partMass)
                .consumptions(consumptions)
                .toolConsumptions(toolConsumptions)
                .normPerPart(normPerPart)
                .normPerBatch(normPerBatch)
                .batchSize(batchSize)
                .materialUtilization(kим)
                .techLossCoefficient(techLossCoefficient)
                .defectCoefficient(defectCoefficient)
                .build();
    }

    // ------------------------------------------------------------------
    // Определение материала
    // ------------------------------------------------------------------

    /**
     * Определить материал и его свойства.
     */
    private MaterialInfo resolveMaterial(TechnologyProcess process, MaterialNormRequest request) {
        String materialCode = request.getMaterialCode();
        if (materialCode == null || materialCode.isBlank()) {
            materialCode = properties.getDefaultMaterial(process.getProcessType());
        }

        MaterialInfo info = properties.getMaterialInfo(materialCode);
        if (info == null) {
            throw new OrchestrationException(
                    "Материал не найден в справочнике: " + materialCode);
        }

        log.debug("MaterialNormService.resolveMaterial: code={}, name={}, density={}",
                info.getCode(), info.getName(), info.getDensity());
        return info;
    }

    // ------------------------------------------------------------------
    // Расчёт массы заготовки и детали
    // ------------------------------------------------------------------

    /**
     * Расчёт массы заготовки.
     *
     * <p>Зависит от вида ТП:
     * <ul>
     *   <li>литьё — масса отливки с литниками и прибылями;</li>
     *   <li>штамповка — масса поковки с облоем;</li>
     *   <li>механообработка — масса исходного проката.</li>
     * </ul>
     */
    public BigDecimal calculateBlankMass(TechnologyProcess process,
                                         MaterialInfo material,
                                         MaterialNormRequest request) {
        BigDecimal blankVolume = request.getBlankVolumeMm3();
        if (blankVolume == null) {
            blankVolume = properties.getBlankVolume(process, request);
        }

        if (blankVolume == null || blankVolume.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("MaterialNormService: не задан объём заготовки, " +
                    "используется справочная масса");
            return properties.getReferenceBlankMass(process);
        }

        // Мзаг = V · ρ / 1_000_000  (V в мм³, ρ в кг/м³ → масса в кг)
        return blankVolume.multiply(material.getDensity())
                .divide(BigDecimal.valueOf(1_000_000), MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Расчёт массы готовой детали.
     */
    public BigDecimal calculatePartMass(TechnologyProcess process,
                                        MaterialInfo material,
                                        MaterialNormRequest request) {
        BigDecimal partVolume = request.getPartVolumeMm3();
        if (partVolume == null) {
            partVolume = properties.getPartVolume(process, request);
        }

        if (partVolume == null || partVolume.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("MaterialNormService: не задан объём детали, " +
                    "используется справочная масса");
            return properties.getReferencePartMass(process);
        }

        return partVolume.multiply(material.getDensity())
                .divide(BigDecimal.valueOf(1_000_000), MASS_SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Расчёт технологических потерь по операции
    // ------------------------------------------------------------------

    /**
     * Расчёт расхода материала по одной операции.
     */
    public MaterialConsumption calculateStageConsumption(Stage stage,
                                                         TechnologyProcess process,
                                                         MaterialInfo material,
                                                         BigDecimal blankMass,
                                                         BigDecimal partMass) {
        log.debug("MaterialNormService.calculateStageConsumption: stage={}, opType={}",
                stage.getName(), stage.getOperationType());

        BigDecimal lossMass = switch (stage.getOperationType()) {
            case CASTING -> calculateCastingLoss(stage, process, material, blankMass);
            case HOT_FORGING, COLD_FORGING, SHEET_STAMPING ->
                    calculateForgingLoss(stage, process, material, blankMass);
            case MACHINING, CNC_MACHINING ->
                    calculateMachiningLoss(stage, process, material, blankMass, partMass);
            case BLANK_PREPARATION -> calculateBlankPrepLoss(stage, process, material, blankMass);
            case HEAT_TREATMENT -> calculateHeatTreatmentLoss(stage, process, material, blankMass);
            case COATING -> calculateCoatingLoss(stage, process, material, blankMass);
            case ASSEMBLY -> calculateAssemblyLoss(stage, process, material);
            case TECHNICAL_CONTROL, NORMING, AUXILIARY -> BigDecimal.ZERO;
        };

        BigDecimal consumptionMass = blankMass.add(lossMass)
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);

        return MaterialConsumption.builder()
                .stageId(stage.getId())
                .stageDesignation(stage.getDesignation())
                .stageName(stage.getName())
                .operationType(stage.getOperationType())
                .materialCode(material.getCode())
                .lossMass(lossMass)
                .consumptionMass(consumptionMass)
                .lossPercent(blankMass.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : lossMass.divide(blankMass, COEFFICIENT_SCALE, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    /**
     * Потери при литье: литники, прибыли, угар.
     */
    private BigDecimal calculateCastingLoss(Stage stage, TechnologyProcess process,
                                            MaterialInfo material, BigDecimal blankMass) {
        BigDecimal gatingLoss = properties.getGatingLossCoefficient(stage, material);
        BigDecimal burnLoss = properties.getBurnLossCoefficient(stage, material);

        return blankMass.multiply(gatingLoss.add(burnLoss))
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Потери при штамповке: облой, угар, некратность.
     */
    private BigDecimal calculateForgingLoss(Stage stage, TechnologyProcess process,
                                            MaterialInfo material, BigDecimal blankMass) {
        BigDecimal flashLoss = properties.getFlashLossCoefficient(stage, material);
        BigDecimal burnLoss = properties.getBurnLossCoefficient(stage, material);
        BigDecimal nonMultipleLoss = properties.getNonMultipleLossCoefficient(stage);

        return blankMass.multiply(flashLoss.add(burnLoss).add(nonMultipleLoss))
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Потери при механообработке: стружка (разница масс).
     */
    private BigDecimal calculateMachiningLoss(Stage stage, TechnologyProcess process,
                                              MaterialInfo material,
                                              BigDecimal blankMass, BigDecimal partMass) {
        BigDecimal removedMass = blankMass.subtract(partMass);
        if (removedMass.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal chipRecovery = properties.getChipRecoveryCoefficient(stage);
        return removedMass.multiply(chipRecovery)
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Потери при заготовительной операции: резка, некратность.
     */
    private BigDecimal calculateBlankPrepLoss(Stage stage, TechnologyProcess process,
                                              MaterialInfo material, BigDecimal blankMass) {
        BigDecimal cutLoss = properties.getCutLossCoefficient(stage);
        BigDecimal nonMultipleLoss = properties.getNonMultipleLossCoefficient(stage);

        return blankMass.multiply(cutLoss.add(nonMultipleLoss))
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Потери при термообработке: окалина, угар.
     */
    private BigDecimal calculateHeatTreatmentLoss(Stage stage, TechnologyProcess process,
                                                  MaterialInfo material, BigDecimal blankMass) {
        BigDecimal scaleLoss = properties.getScaleLossCoefficient(stage, material);
        return blankMass.multiply(scaleLoss)
                .setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Потери при нанесении покрытий: расход материала покрытия.
     */
    private BigDecimal calculateCoatingLoss(Stage stage, TechnologyProcess process,
                                            MaterialInfo material, BigDecimal blankMass) {
        BigDecimal coatingConsumption = properties.getCoatingConsumption(stage);
        return coatingConsumption.setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Расход вспомогательных материалов при сборке.
     */
    private BigDecimal calculateAssemblyLoss(Stage stage, TechnologyProcess process,
                                             MaterialInfo material) {
        BigDecimal assemblyConsumption = properties.getAssemblyConsumption(stage);
        return assemblyConsumption.setScale(MASS_SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Коэффициенты
    // ------------------------------------------------------------------

    /**
     * Коэффициент технологических потерь.
     *
     * <p>Ктех = Σ потерь / масса заготовки.
     */
    private BigDecimal calculateTechLossCoefficient(BigDecimal totalLosses,
                                                    BigDecimal blankMass) {
        if (blankMass.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalLosses.divide(blankMass, COEFFICIENT_SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Расчёт расхода инструмента и оснастки
    // ------------------------------------------------------------------

    /**
     * Расчёт расхода режущего инструмента и оснастки.
     *
     * <p>Расход инструмента определяется по стойкости:
     * <pre>
     *   Nинстр = Тмаш / Тстойкости,
     *   где:
     *     Тмаш       — машинное время обработки (мин);
     *     Тстойкости — стойкость инструмента до полного износа (мин).
     * </pre>
     * С учётом числа переточек расход уменьшается.
     */
    public List<ToolConsumption> calculateToolConsumption(TechnologyProcess process,
                                                          MaterialInfo material,
                                                          BigDecimal batchSize) {
        log.debug("MaterialNormService.calculateToolConsumption: stages={}",
                process.getStages().size());

        List<ToolConsumption> result = new ArrayList<>();

        for (Stage stage : process.getStages()) {
            if (!isToolConsuming(stage.getOperationType())) {
                continue;
            }

            BigDecimal machineTime = properties.getMachineTime(stage);
            BigDecimal toolLife = properties.getToolLife(stage, material);
            int regrinds = properties.getRegrindCount(stage);

            if (toolLife == null || toolLife.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("MaterialNormService: не задана стойкость для {}, пропуск",
                        stage.getName());
                continue;
            }

            // Число инструментов на одну деталь
            BigDecimal toolsPerPart = machineTime
                    .divide(toolLife, COEFFICIENT_SCALE, RoundingMode.HALF_UP);

            // С учётом переточек — делим на (1 + regrinds)
            BigDecimal regrindFactor = BigDecimal.ONE.add(BigDecimal.valueOf(regrinds));
            BigDecimal toolsPerPartWithRegrinds = toolsPerPart
                    .divide(regrindFactor, COEFFICIENT_SCALE, RoundingMode.HALF_UP);

            BigDecimal toolsPerBatch = toolsPerPartWithRegrinds
                    .multiply(batchSize)
                    .setScale(COEFFICIENT_SCALE, RoundingMode.HALF_UP);

            ToolConsumption consumption = ToolConsumption.builder()
                    .stageId(stage.getId())
                    .stageName(stage.getName())
                    .toolType(properties.getToolType(stage))
                    .toolMaterial(properties.getToolMaterial(stage, material))
                    .machineTimeMin(machineTime)
                    .toolLifeMin(toolLife)
                    .regrindCount(regrinds)
                    .toolsPerPart(toolsPerPartWithRegrinds)
                    .toolsPerBatch(toolsPerBatch)
                    .build();

            result.add(consumption);
        }

        log.debug("MaterialNormService.calculateToolConsumption done: items={}", result.size());
        return result;
    }

    private boolean isToolConsuming(Stage.OperationType type) {
        return switch (type) {
            case MACHINING, CNC_MACHINING -> true;
            case HOT_FORGING, COLD_FORGING, SHEET_STAMPING -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Дополнительные сценарии
    // ------------------------------------------------------------------

    /**
     * Пересчитать нормы с другой партией.
     */
    @Transactional
    public MaterialNormResponse recalculateWithBatch(String normId, BigDecimal newBatchSize) {
        log.info("MaterialNormService.recalculateWithBatch: normId={}, newBatchSize={}",
                normId, newBatchSize);

        MaterialNorm existing = repository.findById(normId)
                .orElseThrow(() -> new OrchestrationException(
                        "Норма не найдена: " + normId));

        MaterialNormRequest request = MaterialNormRequest.builder()
                .projectId(existing.getProjectId())
                .process(existing.getProcess())
                .batchSize(newBatchSize)
                .materialCode(existing.getMaterial().getCode())
                .build();

        return calculate(request);
    }

    /**
     * Получить норму по идентификатору.
     */
    @Transactional(readOnly = true)
    public MaterialNorm getById(String normId) {
        return repository.findById(normId)
                .orElseThrow(() -> new OrchestrationException(
                        "Норма не найдена: " + normId));
    }

    /**
     * Получить все нормы по процессу.
     */
    @Transactional(readOnly = true)
    public List<MaterialNorm> getByProcess(String processId) {
        return repository.findByProcessId(processId);
    }

    /**
     * Получить все нормы по проекту.
     */
    @Transactional(readOnly = true)
    public List<MaterialNorm> getByProject(String projectId) {
        return repository.findByProjectId(projectId);
    }

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private void validate(MaterialNormRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на расчёт норм расхода материалов пуст");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new OrchestrationException("Не указан projectId");
        }
        if (request.getProcess() == null) {
            throw new OrchestrationException("Не указан технологический процесс");
        }
        if (request.getProcess().getStages() == null
                || request.getProcess().getStages().isEmpty()) {
            throw new OrchestrationException(
                    "Технологический процесс не содержит операций");
        }
    }

    // ------------------------------------------------------------------
}
