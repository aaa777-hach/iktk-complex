package ru.iktk.modules.forging.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.forging.client.EquipmentClient;
import ru.iktk.modules.forging.client.GeometryClient;
import ru.iktk.modules.forging.config.ForgingProperties;
import ru.iktk.modules.forging.dto.ForgingRequest;
import ru.iktk.modules.forging.dto.ForgingResponse;
import ru.iktk.modules.forging.model.BilletCalculation;
import ru.iktk.modules.forging.model.ForgingMethod;
import ru.iktk.modules.forging.model.ForgingProcess;
import ru.iktk.modules.forging.model.ForgingTooling;
import ru.iktk.modules.forging.model.ForgingTransition;
import ru.iktk.modules.forging.repository.ForgingProcessRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Основной сервис проектирования технологического процесса объёмной
 * штамповки ИКТК.
 *
 * <p>Отвечает за полный цикл проектирования штамповочного ТП:
 * <ol>
 *   <li><b>Анализ технологичности</b> — проверка геометрии на ограничения
 *       штамповки: минимальные радиусы, толщины, уклоны, наличие
 *       поднутрений, перемычек, рёбер.</li>
 *   <li><b>Выбор способа штамповки</b> — горячая объёмная, холодная
 *       объёмная, листовая, высадка, выдавливание.</li>
 *   <li><b>Расчёт заготовки</b> — объём и размеры исходной заготовки,
 *       масса, коэффициент использования металла (КИМ).</li>
 *   <li><b>Определение переходов</b> — количество и последовательность
 *       переходов (осадка, протяжка, прошивка, гибка, обрезка).</li>
 *   <li><b>Расчёт усилий</b> — усилие деформации на каждом переходе,
 *       выбор прессового оборудования.</li>
 *   <li><b>Проектирование оснастки</b> — ручьи, матрицы, пуансоны,
 *       выталкиватели, обрезные ножи.</li>
 *   <li><b>Формирование этапов ТП</b> — операции штамповочного передела.</li>
 *   <li><b>Нормирование</b> — предварительные нормы времени по операциям.</li>
 * </ol>
 *
 * <p><b>Поддерживаемые виды штамповки:</b>
 * <ul>
 *   <li>{@link ForgingMethod#HOT_DIE_FORGING} — горячая объёмная;</li>
 *   <li>{@link ForgingMethod#COLD_DIE_FORGING} — холодная объёмная;</li>
 *   <li>{@link ForgingMethod#UPSETTING} — высадка;</li>
 *   <li>{@link ForgingMethod#EXTRUSION} — выдавливание;</li>
 *   <li>{@link ForgingMethod#SHEET_STAMPING} — листовая (Релиз 1).</li>
 * </ul>
 *
 * <p><b>Взаимодействие:</b>
 * <ul>
 *   <li>получает команду от оркестратора через {@code ModuleCommandConsumer};</li>
 *   <li>запрашивает геометрию через {@link GeometryClient};</li>
 *   <li>проверяет доступность прессового оборудования через
 *       {@link EquipmentClient};</li>
 *   <li>сохраняет результат в {@link ForgingProcessRepository};</li>
 *   <li>публикует событие {@code PART_PROCESS_READY} в оркестратор.</li>
 * </ul>
 */
@Service
public class ForgingProcessService {

    private static final Logger log = LoggerFactory.getLogger(ForgingProcessService.class);

    private final ForgingProcessRepository repository;
    private final GeometryClient geometryClient;
    private final EquipmentClient equipmentClient;
    private final ForgingProperties properties;

    public ForgingProcessService(ForgingProcessRepository repository,
                                 GeometryClient geometryClient,
                                 EquipmentClient equipmentClient,
                                 ForgingProperties properties) {
        this.repository = repository;
        this.geometryClient = geometryClient;
        this.equipmentClient = equipmentClient;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Основной сценарий: проектирование штамповочного ТП
    // ------------------------------------------------------------------

    /**
     * Спроектировать штамповочный ТП для объекта изготовления.
     *
     * @param request запрос от оркестратора
     * @return результат проектирования
     */
    @Transactional
    public ForgingResponse designProcess(ForgingRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        String objectRef = request.getObjectRef();

        log.info("ForgingProcessService.designProcess start: projectId={}, objectRef={}",
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
            log.warn("ForgingProcessService: технологичность не пройдена, projectId={}, issues={}",
                    projectId, report.getIssues().size());
        }

        // 3. Выбор способа штамповки
        ForgingMethod method = selectForgingMethod(geometry, request);

        // 4. Расчёт заготовки
        BilletCalculation billet = calculateBillet(geometry, method);

        // 5. Определение переходов
        List<ForgingTransition> transitions = planTransitions(geometry, method, billet);

        // 6. Расчёт усилий
        List<ForceCalculation> forces = calculateForces(geometry, method, transitions);

        // 7. Проектирование оснастки
        ForgingTooling tooling = designTooling(geometry, method, transitions);

        // 8. Формирование этапов ТП
        List<Stage> stages = buildStages(method, transitions, tooling);

        // 9. Сборка и сохранение результата
        ForgingProcess process = ForgingProcess.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .objectRef(objectRef)
                .method(method)
                .billet(billet)
                .transitions(transitions)
                .forces(forces)
                .tooling(tooling)
                .stages(stages)
                .manufacturabilityReport(report)
                .createdAt(Instant.now())
                .build();

        repository.save(process);

        log.info("ForgingProcessService.designProcess done: projectId={}, processId={}, " +
                        "method={}, transitions={}, stages={}",
                projectId, process.getId(), method, transitions.size(), stages.size());

        return ForgingResponse.builder()
                .projectId(projectId)
                .processId(process.getId())
                .method(method)
                .stages(stages)
                .billet(billet)
                .transitions(transitions)
                .forces(forces)
                .tooling(tooling)
                .warnings(report.getIssues())
                .build();
    }

    // ------------------------------------------------------------------
    // 1. Анализ технологичности
    // ------------------------------------------------------------------

    /**
     * Проверка геометрии на ограничения штамповки.
     *
     * <p>Проверки:
     * <ul>
     *   <li>минимальный радиус скругления;</li>
     *   <li>минимальная толщина стенки / перемычки;</li>
     *   <li>минимальный штамповочный уклон;</li>
     *   <li>наличие поднутрений (требуют разъёмных матриц);</li>
     *   <li>наличие тонких рёбер и высоких выступов;</li>
     *   <li>соотношение высоты к диаметру (для высадки).</li>
     * </ul>
     */
    public ManufacturabilityReport analyzeManufacturability(GeometryData geometry) {
        log.debug("ForgingProcessService.analyzeManufacturability: objectRef={}",
                geometry.getObjectRef());

        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal minRadius = properties.getMinFilletRadius();
        if (geometry.getMinFilletRadius() != null
                && geometry.getMinFilletRadius().compareTo(minRadius) < 0) {
            issues.add("Радиус скругления " + geometry.getMinFilletRadius()
                    + " мм меньше минимального " + minRadius + " мм");
        }

        BigDecimal minWall = properties.getMinWallThickness();
        if (geometry.getMinWallThickness() != null
                && geometry.getMinWallThickness().compareTo(minWall) < 0) {
            issues.add("Толщина стенки " + geometry.getMinWallThickness()
                    + " мм меньше минимальной " + minWall + " мм");
        }

        BigDecimal minDraft = properties.getMinDraftAngle();
        if (geometry.getMinDraftAngle() != null
                && geometry.getMinDraftAngle().compareTo(minDraft) < 0) {
            warnings.add("Штамповочный уклон " + geometry.getMinDraftAngle()
                    + "° меньше рекомендуемого " + minDraft + "°");
        }

        if (geometry.hasUndercuts()) {
            warnings.add("Обнаружены поднутрения — потребуется разъёмная матрица " +
                    "или дополнительный переход");
        }

        if (geometry.hasThinRibs()) {
            warnings.add("Обнаружены тонкие рёбра — возможны дефекты заполнения");
        }

        if (geometry.getHeightToDiameterRatio() != null
                && geometry.getHeightToDiameterRatio()
                        .compareTo(properties.getMaxHeightToDiameterRatio()) > 0) {
            warnings.add("Соотношение высоты к диаметру " + geometry.getHeightToDiameterRatio()
                    + " превышает рекомендуемое " + properties.getMaxHeightToDiameterRatio());
        }

        boolean passed = issues.isEmpty();
        log.debug("ForgingProcessService.analyzeManufacturability done: passed={}, " +
                        "issues={}, warnings={}",
                passed, issues.size(), warnings.size());

        return new ManufacturabilityReport(passed, issues, warnings);
    }

    // ------------------------------------------------------------------
    // 2. Выбор способа штамповки
    // ------------------------------------------------------------------

    /**
     * Выбор способа штамповки по атрибутам детали и доступному оборудованию.
     *
     * <p>Критерии:
     * <ul>
     *   <li>материал (сталь, алюминий, медь, титан);</li>
     *   <li>серийность (единичное, серийное, массовое);</li>
     *   <li>габариты и масса;</li>
     *   <li>толщина стенок и рёбер;</li>
     *   <li>сложность геометрии;</li>
     *   <li>доступность прессового оборудования на предприятии.</li>
     * </ul>
     */
    public ForgingMethod selectForgingMethod(GeometryData geometry, ForgingRequest request) {
        log.debug("ForgingProcessService.selectForgingMethod: material={}, production={}",
                geometry.getMaterial(), request.getProductionType());

        // Приоритет — способ, указанный в запросе
        if (request.getPreferredMethod() != null) {
            boolean available = equipmentClient.isMethodAvailable(
                    request.getProjectId(), request.getPreferredMethod());
            if (available) {
                log.debug("ForgingProcessService: выбран предпочтительный способ {}",
                        request.getPreferredMethod());
                return request.getPreferredMethod();
            }
            log.warn("ForgingProcessService: предпочтительный способ {} недоступен, " +
                    "выполняется подбор", request.getPreferredMethod());
        }

        ForgingMethod method = pickMethodByAttributes(geometry, request);

        boolean available = equipmentClient.isMethodAvailable(
                request.getProjectId(), method);
        if (!available) {
            log.warn("ForgingProcessService: способ {} недоступен на предприятии, " +
                    "потребуется gap-анализ", method);
        }

        log.info("ForgingProcessService.selectForgingMethod done: method={}, available={}",
                method, available);
        return method;
    }

    private ForgingMethod pickMethodByAttributes(GeometryData geometry, ForgingRequest request) {
        // Упрощённая эвристика. Реальная логика — на основе справочника
        // технологических решений и правил.
        if (request.getProductionType() == TechnologyProcess.ProductionType.MASS) {
            return ForgingMethod.HOT_DIE_FORGING;
        }
        if (geometry.getThickness() != null
                && geometry.getThickness().compareTo(properties.getColdForgingMaxThickness()) < 0) {
            return ForgingMethod.COLD_DIE_FORGING;
        }
        if (geometry.getHeightToDiameterRatio() != null
                && geometry.getHeightToDiameterRatio()
                        .compareTo(BigDecimal.valueOf(2.5)) > 0) {
            return ForgingMethod.UPSETTING;
        }
        return ForgingMethod.HOT_DIE_FORGING;
    }

    // ------------------------------------------------------------------
    // 3. Расчёт заготовки
    // ------------------------------------------------------------------

    /**
     * Расчёт исходной заготовки.
     *
     * <p>Определяются:
     * <ul>
     *   <li>объём заготовки с учётом облоя и угара;</li>
     *   <li>диаметр и длина (для сортового проката);</li>
     *   <li>масса заготовки;</li>
     *   <li>коэффициент использования металла (КИМ).</li>
     * </ul>
     */
    public BilletCalculation calculateBillet(GeometryData geometry, ForgingMethod method) {
        log.debug("ForgingProcessService.calculateBillet: method={}, volume={}",
                method, geometry.getVolume());

        BigDecimal partVolume = geometry.getVolume() == null
                ? BigDecimal.ZERO
                : geometry.getVolume();

        BigDecimal flashCoefficient = properties.getFlashCoefficient(method);
        BigDecimal scaleLossCoefficient = properties.getScaleLossCoefficient(method);

        BigDecimal billetVolume = partVolume
                .multiply(BigDecimal.ONE.add(flashCoefficient))
                .multiply(BigDecimal.ONE.add(scaleLossCoefficient))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal billetMass = geometry.getDensity() == null
                ? null
                : billetVolume.multiply(geometry.getDensity())
                        .divide(BigDecimal.valueOf(1_000_000), 3, RoundingMode.HALF_UP);

        BigDecimal kим = billetVolume.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : partVolume.divide(billetVolume, 3, RoundingMode.HALF_UP);

        BigDecimal diameter = calculateBilletDiameter(geometry, billetVolume, method);
        BigDecimal length = calculateBilletLength(billetVolume, diameter);

        BilletCalculation result = BilletCalculation.builder()
                .partVolumeMm3(partVolume)
                .billetVolumeMm3(billetVolume)
                .billetMassKg(billetMass)
                .diameterMm(diameter)
                .lengthMm(length)
                .materialUtilization(kим)
                .method(method)
                .build();

        log.debug("ForgingProcessService.calculateBillet done: volume={}, diameter={}, length={}, КИМ={}",
                billetVolume, diameter, length, kим);
        return result;
    }

    private BigDecimal calculateBilletDiameter(GeometryData geometry,
                                               BigDecimal billetVolume,
                                               ForgingMethod method) {
        if (method == ForgingMethod.SHEET_STAMPING) {
            return geometry.getThickness();
        }
        // Диаметр заготовки: d = (4V / (π · k))^(1/3), k ≈ 1.5 для горячей штамповки
        BigDecimal k = BigDecimal.valueOf(method == ForgingMethod.COLD_DIE_FORGING ? 2.0 : 1.5);
        double v = billetVolume.doubleValue();
        double d = Math.cbrt((4 * v) / (Math.PI * k.doubleValue()));
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateBilletLength(BigDecimal billetVolume, BigDecimal diameter) {
        if (diameter == null || diameter.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        double v = billetVolume.doubleValue();
        double d = diameter.doubleValue();
        double length = (4 * v) / (Math.PI * d * d);
        return BigDecimal.valueOf(length).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 4. Определение переходов
    // ------------------------------------------------------------------

    /**
     * Определение последовательности переходов штамповки.
     *
     * <p>Типовые переходы:
     * <ul>
     *   <li>осадка — уменьшение высоты, увеличение поперечного сечения;</li>
     *   <li>протяжка — увеличение длины за счёт уменьшения сечения;</li>
     *   <li>прошивка — образование полости;</li>
     *   <li>гибка — изменение формы без существенного изменения сечения;</li>
     *   <li>обрезка облоя;</li>
     *   <li>правка / калибровка.</li>
     * </ul>
     */
    public List<ForgingTransition> planTransitions(GeometryData geometry,
                                                   ForgingMethod method,
                                                   BilletCalculation billet) {
        log.debug("ForgingProcessService.planTransitions: method={}, complexity={}",
                method, geometry.getComplexityScore());

        List<ForgingTransition> transitions = new ArrayList<>();
        int order = 1;

        transitions.add(buildTransition(order++, "Осадка",
                ForgingTransition.TransitionType.UPSETTING, geometry, billet));

        if (geometry.getComplexityScore() > 3) {
            transitions.add(buildTransition(order++, "Протяжка",
                    ForgingTransition.TransitionType.DRAWING, geometry, billet));
        }

        if (geometry.hasHoles()) {
            transitions.add(buildTransition(order++, "Прошивка",
                    ForgingTransition.TransitionType.PIERCING, geometry, billet));
        }

        transitions.add(buildTransition(order++, "Штамповка в черновом ручье",
                ForgingTransition.TransitionType.BLOCKING, geometry, billet));

        transitions.add(buildTransition(order++, "Штамповка в чистовом ручье",
                ForgingTransition.TransitionType.FINISHING, geometry, billet));

        transitions.add(buildTransition(order, "Обрезка облоя",
                ForgingTransition.TransitionType.TRIMMING, geometry, billet));

        log.debug("ForgingProcessService.planTransitions done: transitions={}", transitions.size());
        return transitions;
    }

    private ForgingTransition buildTransition(int order,
                                              String name,
                                              ForgingTransition.TransitionType type,
                                              GeometryData geometry,
                                              BilletCalculation billet) {
        return ForgingTransition.builder()
                .order(order)
                .name(name)
                .type(type)
                .inputVolumeMm3(order == 1 ? billet.getBilletVolumeMm3() : null)
                .temperatureC(properties.getTemperatureFor(type, geometry.getMaterial()))
                .deformationDegree(properties.getDeformationDegreeFor(type))
                .build();
    }

    // ------------------------------------------------------------------
    // 5. Расчёт усилий
    // ------------------------------------------------------------------

    /**
     * Расчёт усилий деформации на каждом переходе.
     *
     * <p>Усилие оценивается по формуле: P = σт · F · k, где
     * σт — предел текучести материала при температуре,
     * F — площадь контакта, k — коэффициент, учитывающий схему
     * напряжённого состояния.
     */
    public List<ForceCalculation> calculateForces(GeometryData geometry,
                                                  ForgingMethod method,
                                                  List<ForgingTransition> transitions) {
        log.debug("ForgingProcessService.calculateForces: transitions={}", transitions.size());

        List<ForceCalculation> forces = new ArrayList<>();

        BigDecimal yieldStrength = properties.getYieldStrength(
                geometry.getMaterial(), geometry.getTemperature());

        for (ForgingTransition transition : transitions) {
            BigDecimal area = estimateContactArea(geometry, transition);
            BigDecimal coefficient = properties.getForceCoefficient(transition.getType());

            BigDecimal forceKn = yieldStrength
                    .multiply(area)
                    .multiply(coefficient)
                    .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);

            forces.add(ForceCalculation.builder()
                    .transitionOrder(transition.getOrder())
                    .transitionName(transition.getName())
                    .contactAreaMm2(area)
                    .yieldStrengthMpa(yieldStrength)
                    .forceKn(forceKn)
                    .build());
        }

        BigDecimal maxForce = forces.stream()
                .map(ForceCalculation::getForceKn)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        log.debug("ForgingProcessService.calculateForces done: maxForce={} кН", maxForce);
        return forces;
    }

    private BigDecimal estimateContactArea(GeometryData geometry, ForgingTransition transition) {
        // Упрощённая оценка. Реальная — по геометрии ручья.
        if (geometry.getProjectedArea() != null) {
            return geometry.getProjectedArea();
        }
        if (geometry.getVolume() != null) {
            double v = geometry.getVolume().doubleValue();
            return BigDecimal.valueOf(Math.pow(v, 2.0 / 3.0))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(1000);
    }

    // ------------------------------------------------------------------
    // 6. Проектирование оснастки
    // ------------------------------------------------------------------

    /**
     * Проектирование штамповой оснастки.
     *
     * <p>Состав зависит от способа штамповки:
     * <ul>
     *   <li>горячая объёмная — молотовый или прессовый штамп с ручьями,
     *       обрезной штамп, выталкиватели;</li>
     *   <li>холодная объёмная — матрица, пуансон, съёмник;</li>
     *   <li>высадка — матрица, пуансон, упор;</li>
     *   <li>выдавливание — контейнер, матрица, пуансон;</li>
     *   <li>листовая — вырубной, гибочный, формовочный штампы.</li>
     * </ul>
     */
    public ForgingTooling designTooling(GeometryData geometry,
                                        ForgingMethod method,
                                        List<ForgingTransition> transitions) {
        log.debug("ForgingProcessService.designTooling: method={}, transitions={}",
                method, transitions.size());

        List<String> items = new ArrayList<>();
        List<String> dieCavities = new ArrayList<>();

        for (ForgingTransition t : transitions) {
            switch (t.getType()) {
                case BLOCKING -> dieCavities.add("Черновой ручей");
                case FINISHING -> dieCavities.add("Чистовой ручей");
                case TRIMMING -> items.add("Обрезной штамп");
                case PIERCING -> items.add("Прошивень");
                case UPSETTING -> items.add("Осадочная плита");
                case DRAWING -> items.add("Протяжной ручей");
                default -> { /* прочие — без оснастки */ }
            }
        }

        if (method == ForgingMethod.COLD_DIE_FORGING) {
            items.add("Матрица холодной штамповки");
            items.add("Пуансон");
            items.add("Съёмник");
        } else if (method == ForgingMethod.HOT_DIE_FORGING) {
            items.add("Молотовый штамп");
            items.add("Выталкиватель");
            items.add("Клещи для переноса");
        } else if (method == ForgingMethod.EXTRUSION) {
            items.add("Контейнер");
            items.add("Матрица выдавливания");
            items.add("Пуансон");
        } else if (method == ForgingMethod.SHEET_STAMPING) {
            items.add("Вырубной штамп");
            items.add("Гибочный штамп");
        }

        ForgingTooling tooling = ForgingTooling.builder()
                .items(items)
                .dieCavities(dieCavities)
                .material(properties.getToolingMaterial(method))
                .estimatedCost(properties.estimateToolingCost(method, geometry))
                .build();

        log.debug("ForgingProcessService.designTooling done: items={}, cavities={}",
                items.size(), dieCavities.size());
        return tooling;
    }

    // ------------------------------------------------------------------
    // 7. Формирование этапов ТП
    // ------------------------------------------------------------------

    /**
     * Формирование этапов штамповочного ТП.
     *
     * <p>Стандартные этапы:
     * <ol>
     *   <li>Резка заготовки.</li>
     *   <li>Нагрев (для горячей штамповки).</li>
     *   <li>Осадка / протяжка.</li>
     *   <li>Штамповка в черновом ручье.</li>
     *   <li>Штамповка в чистовом ручье.</li>
     *   <li>Обрезка облоя.</li>
     *   <li>Правка / калибровка.</li>
     *   <li>Контроль.</li>
     * </ol>
     */
    public List<Stage> buildStages(ForgingMethod method,
                                   List<ForgingTransition> transitions,
                                   ForgingTooling tooling) {
        log.debug("ForgingProcessService.buildStages: method={}", method);

        List<Stage> stages = new ArrayList<>();
        int order = 1;

        stages.add(buildStage(order++, "Резка заготовки",
                Stage.OperationType.BLANK_PREPARATION, null));

        if (method == ForgingMethod.HOT_DIE_FORGING) {
            stages.add(buildStage(order++, "Нагрев заготовки",
                    Stage.OperationType.HEAT_TREATMENT, null));
        }

        for (ForgingTransition t : transitions) {
            Stage.OperationType opType = switch (t.getType()) {
                case TRIMMING, PIERCING -> Stage.OperationType.MACHINING;
                default -> Stage.OperationType.HOT_FORGING;
            };
            stages.add(buildStage(order++, t.getName(), opType, null));
        }

        stages.add(buildStage(order++, "Правка и калибровка",
                Stage.OperationType.COLD_FORGING, null));

        stages.add(buildStage(order, "Контроль штамповки",
                Stage.OperationType.TECHNICAL_CONTROL, null));

        log.debug("ForgingProcessService.buildStages done: stages={}", stages.size());
        return stages;
    }

    private Stage buildStage(int order,
                             String name,
                             Stage.OperationType operationType,
                             String toolingId) {
        Stage stage = new Stage(
                UUID.randomUUID().toString(),
                String.format("%03d", order * 5),
                name,
                operationType,
                order
        );
        if (toolingId != null) {
            stage.setComment("Оснастка: " + toolingId);
        }
        return stage;
    }

    // ------------------------------------------------------------------
    // Валидация
    // ------------------------------------------------------------------

    private void validate(ForgingRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на проектирование штамповочного ТП пуст");
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

    /** Данные геометрии, полученные из integration. */
    public static class GeometryData {
        private final String objectRef;
        private final String material;
        private final BigDecimal volume;
        private final BigDecimal mass;
        private final BigDecimal density;
        private final BigDecimal thickness;
        private final BigDecimal minWallThickness;
        private final BigDecimal minFilletRadius;
        private final BigDecimal minDraftAngle;
        private final BigDecimal projectedArea;
        private final BigDecimal heightToDiameterRatio;
        private final BigDecimal temperature;
        private final int complexityScore;
        private final boolean undercuts;
        private final boolean holes;
        private final boolean thinRibs;

        public GeometryData(String objectRef, String material, BigDecimal volume,
                            BigDecimal mass, BigDecimal density, BigDecimal thickness,
                            BigDecimal minWallThickness, BigDecimal minFilletRadius,
                            BigDecimal minDraftAngle, BigDecimal projectedArea,
                            BigDecimal heightToDiameterRatio, BigDecimal temperature,
                            int complexityScore, boolean undercuts,
                            boolean holes, boolean thinRibs) {
            this.objectRef = objectRef;
            this.material = material;
            this.volume = volume;
            this.mass = mass;
            this.density = density;
            this.thickness = thickness;
            this.minWallThickness = minWallThickness;
            this.minFilletRadius = minFilletRadius;
            this.minDraftAngle = minDraftAngle;
            this.projectedArea = projectedArea;
            this.heightToDiameterRatio = heightToDiameterRatio;
            this.temperature = temperature;
            this.complexityScore = complexityScore;
            this.undercuts = undercuts;
            this.holes = holes;
            this.thinRibs = thinRibs;
        }

        public String getObjectRef() { return objectRef; }
        public String getMaterial() { return material; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getMass() { return mass; }
        public BigDecimal getDensity() { return density; }
        public BigDecimal getThickness() { return thickness; }
        public BigDecimal getMinWallThickness() { return minWallThickness; }
        public BigDecimal getMinFilletRadius() { return minFilletRadius; }
        public BigDecimal getMinDraftAngle() { return minDraftAngle; }
        public BigDecimal getProjectedArea() { return projectedArea; }
        public BigDecimal getHeightToDiameterRatio() { return heightToDiameterRatio; }
        public BigDecimal getTemperature() { return temperature; }
        public int getComplexityScore() { return complexityScore; }
        public boolean hasUndercuts() { return undercuts; }
        public boolean hasHoles() { return holes; }
        public boolean hasThinRibs() { return thinRibs; }
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

    /** Результат расчёта усилия. */
    public static class ForceCalculation {
        private final int transitionOrder;
        private final String transitionName;
        private final BigDecimal contactAreaMm2;
        private final BigDecimal yieldStrengthMpa;
        private final BigDecimal forceKn;

        private ForceCalculation(int transitionOrder, String transitionName,
                                 BigDecimal contactAreaMm2, BigDecimal yieldStrengthMpa,
                                 BigDecimal forceKn) {
            this.transitionOrder = transitionOrder;
            this.transitionName = transitionName;
            this.contactAreaMm2 = contactAreaMm2;
            this.yieldStrengthMpa = yieldStrengthMpa;
            this.forceKn = forceKn;
        }

        public int getTransitionOrder() { return transitionOrder; }
        public String getTransitionName() { return transitionName; }
        public BigDecimal getContactAreaMm2() { return contactAreaMm2; }
        public BigDecimal getYieldStrengthMpa() { return yieldStrengthMpa; }
        public BigDecimal getForceKn() { return forceKn; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private int transitionOrder;
            private String transitionName;
            private BigDecimal contactAreaMm2;
            private BigDecimal yieldStrengthMpa;
            private BigDecimal forceKn;

            public Builder transitionOrder(int v) { this.transitionOrder = v; return this; }
            public Builder transitionName(String v) { this.transitionName = v; return this; }
            public Builder contactAreaMm2(BigDecimal v) { this.contactAreaMm2 = v; return this; }
            public Builder yieldStrengthMpa(BigDecimal v) { this.yieldStrengthMpa = v; return this; }
            public Builder forceKn(BigDecimal v) { this.forceKn = v; return this; }

            public ForceCalculation build() {
                return new ForceCalculation(transitionOrder, transitionName,
                        contactAreaMm2, yieldStrengthMpa, forceKn);
            }
        }
    }
}