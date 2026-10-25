package ru.iktk.modules.norming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.norming.client.EquipmentClient;
import ru.iktk.modules.norming.config.NormingProperties;
import ru.iktk.modules.norming.dto.TimeNormRequest;
import ru.iktk.modules.norming.dto.TimeNormResponse;
import ru.iktk.modules.norming.model.StageTimeNorm;
import ru.iktk.modules.norming.model.TimeNorm;
import ru.iktk.modules.norming.model.TimeNormSource;
import ru.iktk.modules.norming.repository.TimeNormRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис расчёта норм времени ИКТК.
 *
 * <p>Отвечает за расчёт:
 * <ul>
 *   <li><b>То</b> — основное (машинное) время;</li>
 *   <li><b>Тв</b> — вспомогательное время (установ, закрепление,
 *       управление станком, измерения);</li>
 *   <li><b>Топ</b> = То + Тв — оперативное время;</li>
 *   <li><b>Тоб</b> — время обслуживания рабочего места
 *       (техническое + организационное);</li>
 *   <li><b>Тотл</b> — время на отдых и личные надобности;</li>
 *   <li><b>Тшт</b> = Топ + Тоб + Тотл — штучное время;</li>
 *   <li><b>Тпз</b> — подготовительно-заключительное время;</li>
 *   <li><b>Тшт-к</b> = Тшт + Тпз/n — штучно-калькуляционное время,
 *       где n — размер партии.</li>
 * </ul>
 *
 * <p><b>Источники норм:</b>
 * <ul>
 *   <li>{@link TimeNormSource#GENERAL_MACHINE_BUILDING} —
 *       общемашиностроительные нормативы;</li>
 *   <li>{@link TimeNormSource#INDUSTRY} — отраслевые нормативы;</li>
 *   <li>{@link TimeNormSource#PLANT} — заводские (уточнённые) нормативы;</li>
 *   <li>{@link TimeNormSource#CALCULATED} — расчётные (по режимам резания);</li>
 *   <li>{@link TimeNormSource#ACTUAL} — фактические (по замерам).</li>
 * </ul>
 *
 * <p><b>Особенности расчёта по видам операций:</b>
 * <ul>
 *   <li>литьё и штамповка — по массе отливки/поковки, сложности,
 *       типу оборудования;</li>
 *   <li>механообработка — по режимам резания (V, S, t), длине
 *       обработки, числу проходов;</li>
 *   <li>ЧПУ — по машинному времени + время на смену инструмента,
 *       позиционирование, контроль;</li>
 *   <li>сборка — по числу сборочных единиц и соединений, типу
 *       соединения (резьбовое, сварное, клеевое);</li>
 *   <li>контроль — по объёму контроля, методу измерения.</li>
 * </ul>
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>получает ТП от модулей {@code casting}, {@code forging},
 *       {@code machining}, {@code assembly-process-service};</li>
 *   <li>использует {@link EquipmentClient} для учёта характеристик
 *       станка (мощность, подачи, обороты);</li>
 *   <li>использует справочники из {@link NormingProperties};</li>
 *   <li>сохраняет результат в {@link TimeNormRepository}.</li>
 * </ul>
 */
@Service
public class TimeNormService {

    private static final Logger log = LoggerFactory.getLogger(TimeNormService.class);

    private final TimeNormRepository repository;
    private final EquipmentClient equipmentClient;
    private final NormingProperties properties;

    public TimeNormService(TimeNormRepository repository,
                           EquipmentClient equipmentClient,
                           NormingProperties properties) {
        this.repository = repository;
        this.equipmentClient = equipmentClient;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: расчёт норм времени по ТП
    // ------------------------------------------------------------------

    /**
     * Рассчитать нормы времени по технологическому процессу.
     *
     * @param request запрос с ТП и параметрами партии
     * @return результат расчёта
     */
    @Transactional
    public TimeNormResponse calculate(TimeNormRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        TechnologyProcess process = request.getProcess();
        BigDecimal batchSize = request.getBatchSize() == null
                ? BigDecimal.ONE
                : request.getBatchSize();

        log.info("TimeNormService.calculate start: projectId={}, processId={}, " +
                        "operations={}, batchSize={}",
                projectId, process.getId(),
                process.getStages() == null ? 0 : process.getStages().size(),
                batchSize);

        // 1. Определение источника норм
        TimeNormSource source = resolveSource(request);

        // 2. Расчёт норм по каждой операции
        List<StageTimeNorm> stageNorms = new ArrayList<>();
        BigDecimal totalTpz = BigDecimal.ZERO;
        BigDecimal totalTshT = BigDecimal.ZERO;

        for (Stage stage : process.getStages()) {
            StageTimeNorm stageNorm = calculateStage(stage, process, source, batchSize);
            stageNorms.add(stageNorm);

            totalTpz = totalTpz.add(stageNorm.getTpz());
            totalTshT = totalTshT.add(stageNorm.getTshT());
        }

        // 3. Сводные показатели
        BigDecimal totalTsh = stageNorms.stream()
                .map(StageTimeNorm::getTsh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTshK = stageNorms.stream()
                .map(StageTimeNorm::getTshK)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Сохранение результата
        TimeNorm norm = TimeNorm.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .processId(process.getId())
                .source(source == null ? null : source.name())
                .batchSize(batchSize)
                .stageNorms(stageNorms)
                .totalTpz(totalTpz)
                .totalTsh(totalTsh)
                .totalTshK(totalTshK)
                .createdAt(Instant.now())
                .build();

        repository.save(norm);

        log.info("TimeNormService.calculate done: projectId={}, processId={}, " +
                        "stages={}, totalTsh={} мин, totalTshK={} мин",
                projectId, process.getId(), stageNorms.size(), totalTsh, totalTshK);

        return TimeNormResponse.builder()
                .projectId(projectId)
                .processId(process.getId())
                .normId(norm.getId())
                .source(source)
                .stageNorms(stageNorms)
                .totalTpz(totalTpz)
                .totalTsh(totalTsh)
                .totalTshK(totalTshK)
                .batchSize(batchSize)
                .build();
    }

    // ------------------------------------------------------------------
    // Расчёт по одной операции
    // ------------------------------------------------------------------

    /**
     * Расчёт норм времени по одной операции (этапу ТП).
     */
    public StageTimeNorm calculateStage(Stage stage,
                                        TechnologyProcess process,
                                        TimeNormSource source,
                                        BigDecimal batchSize) {
        log.debug("TimeNormService.calculateStage: stage={}, operationType={}, source={}",
                stage.getName(), stage.getOperationType(), source);

        // 1. Основное (машинное) время
        BigDecimal to = calculateMainTime(stage, process);

        // 2. Вспомогательное время
        BigDecimal tv = calculateAuxiliaryTime(stage, process);

        // 3. Оперативное время
        BigDecimal top = to.add(tv);

        // 4. Время обслуживания рабочего места
        BigDecimal tob = calculateServiceTime(top, stage);

        // 5. Время на отдых и личные надобности
        BigDecimal totl = calculateRestTime(top, stage);

        // 6. Штучное время
        BigDecimal tsh = top.add(tob).add(totl);

        // 7. Подготовительно-заключительное время
        BigDecimal tpz = calculateSetupTime(stage, process);

        // 8. Штучно-калькуляционное время
        BigDecimal tshK = tsh.add(
                tpz.divide(batchSize, 4, RoundingMode.HALF_UP)
        ).setScale(3, RoundingMode.HALF_UP);

        // Тшт.т - технически обоснованное штучно-калькуляционное (Тшт + Тпз/n),
        //         без поправочного коэффициента источника норм.
        // Тшт.к - нормативное штучно-калькуляционное = Тшт.т * коэффициент.
        //
        // Вариант (б) - альтернативная трактовка, НЕ используется:
        //   Тшт.т = Тшт (технически обоснованное штучное, без Тпз),
        //   Тшт.к = Тшт + Тпз/n. При необходимости - пересмотреть формулу.
        BigDecimal tshT = tshK;

        // 9. Поправочный коэффициент для источника норм
        BigDecimal coefficient = properties.getCoefficient(source, stage);
        tshK = tshK.multiply(coefficient).setScale(3, RoundingMode.HALF_UP);

        log.debug("TimeNormService.calculateStage done: stage={}, To={}, Tv={}, " +
                        "Top={}, Tsh={}, Tpz={}, TshK={}",
                stage.getName(), to, tv, top, tsh, tpz, tshK);

        return StageTimeNorm.builder()
                .stageId(stage.getId())
                .stageDesignation(stage.getDesignation())
                .stageName(stage.getName())
                .operationType(stage.getOperationType())
                .to(to)
                .tv(tv)
                .top(top)
                .tob(tob)
                .totl(totl)
                .tsh(tsh)
                .tshT(tshT)
                .tpz(tpz)
                .tshK(tshK)
                .source(source)
                .build();
    }

    // ------------------------------------------------------------------
    // 1. Основное (машинное) время
    // ------------------------------------------------------------------

    /**
     * Расчёт основного времени по виду операции.
     *
     * <p>Формулы:
     * <ul>
     *   <li>механообработка: То = L · i / (S · n), где L — длина
     *       обработки, i — число проходов, S — подача, n — обороты;</li>
     *   <li>литьё/штамповка: по массе отливки/поковки и сложности;</li>
     *   <li>сборка: по числу соединений и норме на соединение;</li>
     *   <li>контроль: по объёму контроля и норме на измерение.</li>
     * </ul>
     */
    public BigDecimal calculateMainTime(Stage stage, TechnologyProcess process) {
        return switch (stage.getOperationType()) {
            case MACHINING, CNC_MACHINING -> calculateMachiningTime(stage, process);
            case CASTING -> calculateCastingTime(stage, process);
            case HOT_FORGING, COLD_FORGING, SHEET_STAMPING -> calculateForgingTime(stage, process);
            case ASSEMBLY -> calculateAssemblyTime(stage, process);
            case TECHNICAL_CONTROL -> calculateControlTime(stage, process);
            case BLANK_PREPARATION -> properties.getBlankPrepTime(stage);
            case HEAT_TREATMENT -> properties.getHeatTreatmentTime(stage);
            case COATING -> properties.getCoatingTime(stage);
            case NORMING, AUXILIARY -> BigDecimal.valueOf(0.5);
        };
    }

    /**
     * Основное время для механообработки.
     *
     * <p>То = (L · i) / (S · n), где:
     * <ul>
     *   <li>L — расчётная длина обработки (мм);</li>
     *   <li>i — число проходов;</li>
     *   <li>S — подача (мм/об);</li>
     *   <li>n — частота вращения (об/мин).</li>
     * </ul>
     */
    private BigDecimal calculateMachiningTime(Stage stage, TechnologyProcess process) {
        BigDecimal length = properties.getMachiningLength(stage);
        int passes = properties.getPasses(stage);
        BigDecimal feed = stage.getTimeNormMin() != null
                ? properties.getFeed(stage)
                : BigDecimal.valueOf(0.2);
        BigDecimal rpm = properties.getSpindleSpeed(stage);

        if (feed == null || feed.compareTo(BigDecimal.ZERO) == 0
                || rpm == null || rpm.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("TimeNormService: не заданы режимы для операции {}, " +
                    "используется справочная норма", stage.getName());
            return properties.getReferenceMachiningTime(stage);
        }

        // То = L · i / (S · n), в минутах
        BigDecimal numerator = length.multiply(BigDecimal.valueOf(passes));
        BigDecimal denominator = feed.multiply(rpm);

        return numerator.divide(denominator, 3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCastingTime(Stage stage, TechnologyProcess process) {
        BigDecimal mass = properties.getCastingMass(stage);
        BigDecimal complexityFactor = properties.getComplexityFactor(stage);
        BigDecimal baseTime = properties.getCastingBaseTime(stage);

        return baseTime.add(
                mass.multiply(complexityFactor)
        ).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateForgingTime(Stage stage, TechnologyProcess process) {
        BigDecimal mass = properties.getForgingMass(stage);
        BigDecimal baseTime = properties.getForgingBaseTime(stage);

        return baseTime.add(
                mass.multiply(properties.getForgingMassFactor(stage))
        ).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAssemblyTime(Stage stage, TechnologyProcess process) {
        int connections = properties.getAssemblyConnections(stage);
        BigDecimal timePerConnection = properties.getAssemblyTimePerConnection(stage);

        return BigDecimal.valueOf(connections)
                .multiply(timePerConnection)
                .setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateControlTime(Stage stage, TechnologyProcess process) {
        int measurementPoints = properties.getControlPoints(stage);
        BigDecimal timePerPoint = properties.getControlTimePerPoint(stage);

        return BigDecimal.valueOf(measurementPoints)
                .multiply(timePerPoint)
                .setScale(3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 2. Вспомогательное время
    // ------------------------------------------------------------------

    /**
     * Расчёт вспомогательного времени.
     *
     * <p>Составляющие:
     * <ul>
     *   <li>время на установ и снятие заготовки;</li>
     *   <li>время на закрепление и открепление;</li>
     *   <li>время на управление станком (пуск, останов, переключения);</li>
     *   <li>время на измерение (если не выделено в отдельную операцию);</li>
     *   <li>время на перемещение инструмента (для ЧПУ).</li>
     * </ul>
     */
    public BigDecimal calculateAuxiliaryTime(Stage stage, TechnologyProcess process) {
        BigDecimal setupTime = properties.getSetupTime(stage);
        BigDecimal controlTime = properties.getControlTime(stage);
        BigDecimal handlingTime = properties.getHandlingTime(stage);

        return setupTime.add(controlTime).add(handlingTime)
                .setScale(3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 3. Время обслуживания рабочего места
    // ------------------------------------------------------------------

    /**
     * Время обслуживания рабочего места.
     *
     * <p>Тоб = Топ · (а_обс / 100), где а_обс — процент от оперативного
     * времени на обслуживание (смазка, уборка стружки, смена инструмента).
     */
    private BigDecimal calculateServiceTime(BigDecimal top, Stage stage) {
        BigDecimal percent = properties.getServicePercent(stage);
        return top.multiply(percent)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 4. Время на отдых и личные надобности
    // ------------------------------------------------------------------

    /**
     * Время на отдых и личные надобности.
     *
     * <p>Тотл = Топ · (а_отл / 100), где а_отл — процент от оперативного
     * времени. Учитывается категория тяжести работы.
     */
    private BigDecimal calculateRestTime(BigDecimal top, Stage stage) {
        BigDecimal percent = properties.getRestPercent(stage);
        return top.multiply(percent)
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 5. Подготовительно-заключительное время
    // ------------------------------------------------------------------

    /**
     * Подготовительно-заключительное время (Тпз).
     *
     * <p>Включает: получение наряда, ознакомление с чертежом, наладка
     * станка, установка инструмента, пробная обработка, сдача работы.
     */
    private BigDecimal calculateSetupTime(Stage stage, TechnologyProcess process) {
        BigDecimal baseTpz = properties.getSetupTimeTpz(stage);
        BigDecimal toolingTpz = stage.hasTooling()
                ? properties.getToolingSetupTime(stage)
                : BigDecimal.ZERO;
        BigDecimal equipmentTpz = stage.hasEquipment()
                ? properties.getEquipmentSetupTime(stage)
                : BigDecimal.ZERO;

        return baseTpz.add(toolingTpz).add(equipmentTpz)
                .setScale(3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Определение источника норм
    // ------------------------------------------------------------------

    /**
     * Определить источник норм времени.
     *
     * <p>Приоритет:
     * <ol>
     *   <li>явно указан в запросе;</li>
     *   <li>заводские нормы (если есть для данного предприятия);</li>
     *   <li>отраслевые нормы;</li>
     *   <li>общемашиностроительные нормы (по умолчанию).</li>
     * </ol>
     */
    private TimeNormSource resolveSource(TimeNormRequest request) {
        if (request.getSource() != null) {
            return request.getSource();
        }
        if (properties.hasPlantNorms(request.getEnterpriseCode())) {
            return TimeNormSource.PLANT;
        }
        if (properties.hasIndustryNorms(request.getIndustryCode())) {
            return TimeNormSource.INDUSTRY;
        }
        return TimeNormSource.GENERAL_MACHINE_BUILDING;
    }

    // ------------------------------------------------------------------
    // Дополнительные сценарии
    // ------------------------------------------------------------------

    /**
     * Пересчитать нормы с другим источником (например, применить
     * заводские вместо общемашиностроительных).
     */
    @Transactional
    public TimeNormResponse recalculateWithSource(String normId, TimeNormSource newSource) {
        log.info("TimeNormService.recalculateWithSource: normId={}, newSource={}",
                normId, newSource);

        TimeNorm existing = repository.findById(normId)
                .orElseThrow(() -> new OrchestrationException(
                        "Норма не найдена: " + normId));

        TimeNormRequest request = TimeNormRequest.builder()
                .projectId(existing.getProjectId())
                .process(existing.getProcess())
                .batchSize(existing.getBatchSize())
                .source(newSource)
                .build();

        return calculate(request);
    }

    /**
     * Получить норму по идентификатору.
     */
    @Transactional(readOnly = true)
    public TimeNorm getById(String normId) {
        return repository.findById(normId)
                .orElseThrow(() -> new OrchestrationException(
                        "Норма не найдена: " + normId));
    }

    /**
     * Получить все нормы по процессу.
     */
    @Transactional(readOnly = true)
    public List<TimeNorm> getByProcess(String processId) {
        return repository.findByProcessId(processId);
    }

    /**
     * Получить все нормы по проекту.
     */
    @Transactional(readOnly = true)
    public List<TimeNorm> getByProject(String projectId) {
        return repository.findByProjectId(projectId);
    }

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private void validate(TimeNormRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на расчёт норм времени пуст");
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
}
