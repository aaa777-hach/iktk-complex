package ru.iktk.modules.assembly.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.model.AssemblyComponent;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.model.AssemblyStage;
import ru.iktk.modules.assembly.model.AssemblyTooling;
import ru.iktk.modules.assembly.repository.AssemblyComponentRepository;
import ru.iktk.modules.assembly.repository.AssemblyProcessRepository;
import ru.iktk.modules.assembly.repository.AssemblyStageRepository;
import ru.iktk.modules.assembly.repository.AssemblyToolingRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сервис проектирования технологического процесса сборки ИКТК.
 *
 * <p>Логика:
 * <ol>
 *   <li>Получает {@link AssemblyRequest} с projectId, objectRef, strategy.</li>
 *   <li>Формирует {@link AssemblyProcess} + список {@link AssemblyStage}
 *       (этапы сборки) и {@link AssemblyComponent} (состав из BOM).</li>
 *   <li>Сохраняет всё в БД через репозитории.</li>
 *   <li>Возвращает {@link AssemblyResponse} с processId и метриками.</li>
 * </ol>
 */
@Service
public class AssemblyProcessService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyProcessService.class);

    private final AssemblyProcessRepository processRepository;
    private final AssemblyStageRepository stageRepository;
    private final AssemblyComponentRepository componentRepository;
    private final AssemblyToolingRepository toolingRepository;

    @Value("${iktk.assembly.demo-fallback:true}")
    private boolean demoFallback;

    @Value("${iktk.assembly.default-strategy:SEQUENTIAL}")
    private String defaultStrategy;

    public AssemblyProcessService(AssemblyProcessRepository processRepository,
                                  AssemblyStageRepository stageRepository,
                                  AssemblyComponentRepository componentRepository,
                                  AssemblyToolingRepository toolingRepository) {
        this.processRepository = processRepository;
        this.stageRepository = stageRepository;
        this.componentRepository = componentRepository;
        this.toolingRepository = toolingRepository;
    }

    /**
     * Спроектировать ТП сборки по запросу.
     */
    @Transactional
    public AssemblyResponse designProcess(AssemblyRequest request) {
        if (request == null || request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw new IllegalArgumentException("projectId не задан");
        }

        String strategy = request.getStrategy() == null || request.getStrategy().isBlank()
                ? defaultStrategy
                : request.getStrategy();

        log.info("designProcess: projectId={}, objectRef={}, strategy={}",
                request.getProjectId(), request.getObjectRef(), strategy);

        String processId = UUID.randomUUID().toString();
        AssemblyProcess process = new AssemblyProcess(processId, request.getProjectId());
        process.setObjectRef(request.getObjectRef());
        process.setStrategy(strategy);

        // 1. Компоненты (состав) — из BOM, либо demo fallback
        List<AssemblyComponent> components = buildComponents(processId, request);

        // 2. Этапы сборки
        List<AssemblyStage> stages = buildStages(processId, components.size());

        // 3. Оснастка
        List<AssemblyTooling> toolings = buildToolings(processId);

        // 4. Итоговые метрики
        BigDecimal totalTime = stages.stream()
                .map(AssemblyStage::totalTimeMin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        process.setStageCount(stages.size());
        process.setComponentCount(components.size());
        process.setTotalTimeMin(totalTime);

        // 5. Сохранение
        processRepository.save(process);
        componentRepository.saveAll(components);
        stageRepository.saveAll(stages);
        toolingRepository.saveAll(toolings);

        log.info("designProcess done: processId={}, stages={}, components={}, totalTime={}",
                processId, stages.size(), components.size(), totalTime);

        return AssemblyResponse.builder()
                .processId(processId)
                .projectId(request.getProjectId())
                .objectRef(request.getObjectRef())
                .strategy(strategy)
                .status(process.getStatus())
                .stageCount(stages.size())
                .componentCount(components.size())
                .totalTimeMin(totalTime)
                .message("ТП сборки спроектирован")
                .build();
    }

    /**
     * Получить ТП по id.
     */
    @Transactional(readOnly = true)
    public AssemblyProcess getById(String processId) {
        return processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("ТП не найден: " + processId));
    }

    /**
     * Список ТП по проекту.
     */
    @Transactional(readOnly = true)
    public List<AssemblyProcess> listByProject(String projectId) {
        return processRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    // ------------------------------------------------------------------
    // Внутренние методы: построение структуры
    // ------------------------------------------------------------------

    private List<AssemblyComponent> buildComponents(String processId, AssemblyRequest request) {
        List<AssemblyComponent> result = new ArrayList<>();
        // MVP: demo-состав (3 компонента). В будущем — из bom-service по bomRef.
        if (demoFallback || request.getBomRef() == null || request.getBomRef().isBlank()) {
            result.add(component(processId, "C-001", "Корпус", 1, 1, "PART"));
            result.add(component(processId, "C-002", "Вал",   1, 2, "PART"));
            result.add(component(processId, "C-003", "Крышка", 2, 3, "PART"));
        } else {
            // TODO: получить BOM через bom-service (request.getBomRef())
            result.add(component(processId, "C-BOM-001", "Компонент из BOM", 1, 1, "PART"));
        }
        return result;
    }

    private AssemblyComponent component(String processId, String designation,
                                        String name, int qty, int position, String kind) {
        AssemblyComponent c = new AssemblyComponent(UUID.randomUUID().toString(), processId);
        c.setDesignation(designation);
        c.setName(name);
        c.setQuantity(qty);
        c.setPosition(position);
        c.setPartKind(kind);
        return c;
    }

    private List<AssemblyStage> buildStages(String processId, int componentCount) {
        List<AssemblyStage> stages = new ArrayList<>();
        String[][] ops = {
                {"010", "Подготовка деталей и узлов",     "PREPARATION"},
                {"020", "Базовая сборка",                "BASIC_ASSEMBLY"},
                {"030", "Сборка узлов и подузлов",       "NODE_ASSEMBLY"},
                {"040", "Общая сборка",                  "GENERAL_ASSEMBLY"},
                {"050", "Контроль сборки",               "CONTROL"}
        };
        int order = 1;
        for (String[] op : ops) {
            AssemblyStage s = new AssemblyStage(
                    UUID.randomUUID().toString(), processId, order++);
            s.setDesignation(op[0]);
            s.setName(op[1]);
            s.setOperationType(op[2]);
            s.setTimeNormMin(BigDecimal.valueOf(10L + (order * 5L)));
            s.setSetupTimeMin(BigDecimal.valueOf(3L));
            stages.add(s);
        }
        log.debug("buildStages: processId={}, count={}, components={}",
                processId, stages.size(), componentCount);
        return stages;
    }

    private List<AssemblyTooling> buildToolings(String processId) {
        List<AssemblyTooling> result = new ArrayList<>();
        // MVP: 2 вида оснастки
        result.add(tooling(processId, "TL-ASSEMBLY-JIG",  "FULL",
                "Сборочное приспособление"));
        result.add(tooling(processId, "TL-TORQUE-WRENCH", "PARTIAL",
                "Динамометрический ключ"));
        return result;
    }

    private AssemblyTooling tooling(String processId, String toolingId,
                                    String compatibility, String notes) {
        AssemblyTooling t = new AssemblyTooling(
                UUID.randomUUID().toString(), processId, toolingId);
        t.setCompatibility(compatibility);
        t.setNotes(notes);
        return t;
    }
}