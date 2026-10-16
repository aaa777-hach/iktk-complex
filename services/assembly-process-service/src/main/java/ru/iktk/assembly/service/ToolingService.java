package ru.iktk.assembly.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.assembly.config.AssemblyProperties;
import ru.iktk.assembly.dto.AssemblyOperationDto;
import ru.iktk.assembly.dto.AssemblyToolingDto;
import ru.iktk.assembly.exception.AssemblyException;
import ru.iktk.assembly.mapper.AssemblyMapper;
import ru.iktk.assembly.model.AssemblyOperation;
import ru.iktk.assembly.model.AssemblyProcess;
import ru.iktk.assembly.model.AssemblyTooling;
import ru.iktk.assembly.repository.AssemblyOperationRepository;
import ru.iktk.assembly.repository.AssemblyProcessRepository;
import ru.iktk.assembly.repository.AssemblyToolingRepository;
import ru.iktk.shared.storage.StorageClient;
import ru.iktk.shared.versioning.VersioningService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис проектирования сборочной оснастки ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>анализ операций ТП сборки и определение потребности
 *       в оснастке и приспособлениях;</li>
 *   <li>подбор оснастки из справочника предприятия;</li>
 *   <li>оценку стоимости изготовления оснастки;</li>
 *   <li>создание и версионирование записей оснастки;</li>
 *   <li>утверждение оснастки;</li>
 *   <li>предоставление данных для карты сборки и документации.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Таблица {@code assembly_tooling}
 * (миграция V5) с привязкой к процессу и, опционально, к операции.
 *
 * <p><b>Типы оснастки:</b>
 * <ul>
 *   <li>{@code FIXTURE} — приспособление для базирования и закрепления;</li>
 *   <li>{@code CLAMP} — зажим;</li>
 *   <li>{@code SUPPORT} — опора;</li>
 *   <li>{@code LOCATOR} — фиксатор;</li>
 *   <li>{@code LIFTING} — грузозахватное приспособление;</li>
 *   <li>{@code MEASURING} — измерительное приспособление;</li>
 *   <li>{@code TOOL} — сборочный инструмент;</li>
 *   <li>{@code OTHER} — прочее.</li>
 * </ul>
 *
 * <p><b>Использование:</b>
 * <ul>
 *   <li>{@code AssemblyProcessService.designProcess} — вызывает
 *       {@link #designForProcess(String)} при включённой опции
 *       {@code iktk.assembly.tooling.enabled};</li>
 *   <li>{@code AssemblyProcessController} — REST API для UI;</li>
 *   <li>{@code documentation} — потребитель карты сборки.</li>
 * </ul>
 */
@Service
public class ToolingService {

    private static final Logger log = LoggerFactory.getLogger(ToolingService.class);

    private static final BigDecimal DEFAULT_TOOLING_COST = BigDecimal.valueOf(15000);

    private final AssemblyToolingRepository toolingRepository;
    private final AssemblyProcessRepository processRepository;
    private final AssemblyOperationRepository operationRepository;
    private final AssemblyMapper mapper;
    private final StorageClient storageClient;
    private final VersioningService versioningService;
    private final AssemblyProperties properties;

    public ToolingService(AssemblyToolingRepository toolingRepository,
                          AssemblyProcessRepository processRepository,
                          AssemblyOperationRepository operationRepository,
                          AssemblyMapper mapper,
                          StorageClient storageClient,
                          VersioningService versioningService,
                          AssemblyProperties properties) {
        this.toolingRepository = toolingRepository;
        this.processRepository = processRepository;
        this.operationRepository = operationRepository;
        this.mapper = mapper;
        this.storageClient = storageClient;
        this.versioningService = versioningService;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Проектирование оснастки для ТП
    // ==================================================================

    /**
     * Спроектировать оснастку для всех операций ТП сборки.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Загрузка ТП и его операций.</li>
     *   <li>Удаление старых записей оснастки.</li>
     *   <li>Для каждой операции — определение потребности
     *       в оснастке и подбор из справочника.</li>
     *   <li>Сохранение и версионирование.</li>
     * </ol>
     */
    @Transactional
    public List<AssemblyToolingDto> designForProcess(String processId) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyOperation> operations =
                operationRepository.findByProcessIdOrderByOrder(processId);

        log.info("designForProcess: processId={}, operations={}",
                processId, operations.size());

        if (operations.isEmpty()) {
            log.warn("designForProcess: нет операций для ТП {}", processId);
            return List.of();
        }

        // Удаляем старые записи оснастки
        toolingRepository.deleteByProcessId(processId);

        List<AssemblyTooling> created = new ArrayList<>();

        for (AssemblyOperation op : operations) {
            List<AssemblyTooling> items = designForOperation(process, op);
            created.addAll(items);
        }

        if (created.isEmpty()) {
            log.info("designForProcess: оснастка не требуется для processId={}", processId);
            return List.of();
        }

        List<AssemblyTooling> saved = toolingRepository.saveAll(created);

        // Версионность
        versioningService.createVersion(
                "ASSEMBLY_TOOLING",
                UUID.fromString(processId),
                UUID.fromString(process.getProjectId()),
                mapper.toJsonNode(process, List.of(), saved),
                VersioningService.ChangeType.CREATE,
                "Спроектирована оснастка (" + saved.size() + " ед.)",
                "system");

        log.info("designForProcess done: processId={}, tooling={}",
                processId, saved.size());

        return saved.stream().map(mapper::toToolingDto).toList();
    }

    /**
     * Спроектировать оснастку для одной операции.
     */
    private List<AssemblyTooling> designForOperation(AssemblyProcess process,
                                                       AssemblyOperation op) {
        List<AssemblyTooling> result = new ArrayList<>();

        // 1. Основное приспособление (базирование и закрепление)
        if (needsFixture(op)) {
            result.add(buildTooling(process, op, "FIXTURE",
                    buildFixtureName(op), DEFAULT_TOOLING_COST));
        }

        // 2. Зажим (если требуется крепление)
        if (needsClamp(op)) {
            result.add(buildTooling(process, op, "CLAMP",
                    "Зажим для " + designation(op), BigDecimal.valueOf(5000)));
        }

        // 3. Измерительное приспособление (если операция контроля)
        if (needsMeasuring(op)) {
            result.add(buildTooling(process, op, "MEASURING",
                    "Приспособление измерительное для " + designation(op),
                    BigDecimal.valueOf(8000)));
        }

        // 4. Грузозахватное приспособление (для тяжёлых подсборок)
        if (needsLifting(op)) {
            result.add(buildTooling(process, op, "LIFTING",
                    "Захват для " + designation(op), BigDecimal.valueOf(12000)));
        }

        return result;
    }

    /**
     * Создать запись оснастки.
     */
    private AssemblyTooling buildTooling(AssemblyProcess process,
                                          AssemblyOperation op,
                                          String type,
                                          String name,
                                          BigDecimal estimatedCost) {
        return AssemblyTooling.builder()
                .id(UUID.randomUUID().toString())
                .processId(process.getId())
                .operationId(op.getId())
                .designation(buildToolingDesignation(process, op, type))
                .name(name)
                .toolingType(type)
                .material(resolveToolingMaterial(type))
                .estimatedCost(estimatedCost)
                .status("DRAFT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    // 2. Чтение
    // ==================================================================

    /**
     * Получить всю оснастку по ТП.
     */
    @Transactional(readOnly = true)
    public List<AssemblyToolingDto> getByProcess(String processId) {
        if (processId == null || processId.isBlank()) {
            throw AssemblyException.validation("Не указан processId");
        }

        return toolingRepository.findByProcessIdOrderByCreatedAt(processId)
                .stream()
                .map(mapper::toToolingDto)
                .toList();
    }

    /**
     * Получить оснастку по операции.
     */
    @Transactional(readOnly = true)
    public List<AssemblyToolingDto> getByOperation(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            throw AssemblyException.validation("Не указан operationId");
        }

        return toolingRepository.findByOperationIdOrderByCreatedAt(operationId)
                .stream()
                .map(mapper::toToolingDto)
                .toList();
    }

    /**
     * Получить оснастку по ID.
     */
    @Transactional(readOnly = true)
    public AssemblyToolingDto getById(String toolingId) {
        AssemblyTooling tooling = findTooling(toolingId);
        return mapper.toToolingDto(tooling);
    }

    /**
     * Получить оснастку по типу в рамках ТП.
     */
    @Transactional(readOnly = true)
    public List<AssemblyToolingDto> getByProcessAndType(String processId, String type) {
        if (processId == null || processId.isBlank()) {
            throw AssemblyException.validation("Не указан processId");
        }
        if (type == null || type.isBlank()) {
            throw AssemblyException.validation("Не указан тип оснастки");
        }

        return toolingRepository
                .findByProcessIdAndToolingTypeOrderByCreatedAt(processId, type)
                .stream()
                .map(mapper::toToolingDto)
                .toList();
    }

    // ==================================================================
    // 3. Ручное управление
    // ==================================================================

    /**
     * Добавить запись оснастки вручную.
     */
    @Transactional
    public AssemblyToolingDto add(String processId, AssemblyToolingDto dto) {
        AssemblyProcess process = findProcess(processId);

        if (dto == null) {
            throw AssemblyException.validation("Пустая запись оснастки");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw AssemblyException.validation("Не указано наименование оснастки");
        }

        AssemblyTooling tooling = mapper.toToolingEntity(dto);
        tooling.setId(UUID.randomUUID().toString());
        tooling.setProcessId(processId);
        tooling.setStatus(dto.getStatus() == null ? "DRAFT" : dto.getStatus());
        tooling.setCreatedAt(Instant.now());
        tooling.setUpdatedAt(Instant.now());

        if (tooling.getEstimatedCost() == null) {
            tooling.setEstimatedCost(DEFAULT_TOOLING_COST);
        }

        AssemblyTooling saved = toolingRepository.save(tooling);

        log.info("add: processId={}, toolingId={}, type={}",
                processId, saved.getId(), saved.getToolingType());

        return mapper.toToolingDto(saved);
    }

    /**
     * Обновить запись оснастки.
     */
    @Transactional
    public AssemblyToolingDto update(String toolingId, AssemblyToolingDto dto) {
        AssemblyTooling tooling = findTooling(toolingId);

        if (dto == null) {
            throw AssemblyException.validation("Пустая запись оснастки");
        }

        mapper.updateTooling(tooling, dto);
        tooling.setUpdatedAt(Instant.now());

        AssemblyTooling saved = toolingRepository.save(tooling);

        log.info("update: toolingId={}", toolingId);
        return mapper.toToolingDto(saved);
    }

    /**
     * Удалить запись оснастки.
     */
    @Transactional
    public void delete(String toolingId) {
        AssemblyTooling tooling = findTooling(toolingId);
        toolingRepository.delete(tooling);
        log.warn("delete: toolingId={}", toolingId);
    }

    /**
     * Утвердить запись оснастки.
     */
    @Transactional
    public AssemblyToolingDto approve(String toolingId, String comment) {
        AssemblyTooling tooling = findTooling(toolingId);
        tooling.setStatus("APPROVED");
        tooling.setUpdatedAt(Instant.now());
        AssemblyTooling saved = toolingRepository.save(tooling);

        versioningService.createVersion(
                "ASSEMBLY_TOOLING",
                UUID.fromString(toolingId),
                null,
                mapper.toJsonNode(saved),
                VersioningService.ChangeType.APPROVE,
                comment == null ? "Оснастка утверждена" : comment,
                "system");

        log.info("approve: toolingId={}", toolingId);
        return mapper.toToolingDto(saved);
    }

    // ==================================================================
    // 4. Сводки и агрегаты
    // ==================================================================

    /**
     * Сводка по оснастке ТП.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String processId) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyTooling> items = toolingRepository.findByProcessId(processId);

        Map<String, Integer> byType = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        int approved = 0;

        for (AssemblyTooling t : items) {
            byType.merge(t.getToolingType(), 1, Integer::sum);
            if (t.getEstimatedCost() != null) {
                totalCost = totalCost.add(t.getEstimatedCost());
            }
            if ("APPROVED".equalsIgnoreCase(t.getStatus())) {
                approved++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("processId", processId);
        summary.put("projectId", process.getProjectId());
        summary.put("designation", process.getDesignation());
        summary.put("totalTooling", items.size());
        summary.put("approved", approved);
        summary.put("totalCost", totalCost);
        summary.put("byType", byType);
        return summary;
    }

    /**
     * Проверить, есть ли оснастка для ТП.
     */
    @Transactional(readOnly = true)
    public boolean hasTooling(String processId) {
        return toolingRepository.countByProcessId(processId) > 0;
    }

    /**
     * Получить суммарную стоимость оснастки ТП.
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalCost(String processId) {
        return toolingRepository.sumEstimatedCostByProcess(processId);
    }

    /**
     * Получить список типов оснастки, используемых в ТП.
     */
    @Transactional(readOnly = true)
    public List<String> getToolingTypes(String processId) {
        return toolingRepository.findDistinctToolingTypes(processId);
    }

    // ==================================================================
    // 5. Экспорт
    // ==================================================================

    /**
     * Экспортировать оснастку ТП в CSV.
     */
    @Transactional(readOnly = true)
    public String export(String processId, String format) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyTooling> items = toolingRepository.findByProcessId(processId);

        log.info("export: processId={}, format={}, items={}",
                processId, format, items.size());

        String content = exportCsv(items);
        String filename = "assembly-tooling-" + processId + ".csv";

        if ("EXCEL".equalsIgnoreCase(format)) {
            filename = "assembly-tooling-" + processId + ".xlsx";
        } else if ("PDF".equalsIgnoreCase(format)) {
            filename = "assembly-tooling-" + processId + ".pdf";
        }

        return storageClient.uploadText(
                content, process.getProjectId(), "tooling", filename);
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    private AssemblyProcess findProcess(String processId) {
        if (processId == null || processId.isBlank()) {
            throw AssemblyException.validation("Не указан processId");
        }
        return processRepository.findById(processId)
                .orElseThrow(() -> AssemblyException.notFound(
                        "ТП сборки не найден: " + processId));
    }

    private AssemblyTooling findTooling(String toolingId) {
        if (toolingId == null || toolingId.isBlank()) {
            throw AssemblyException.validation("Не указан toolingId");
        }
        return toolingRepository.findById(toolingId)
                .orElseThrow(() -> AssemblyException.notFound(
                        "Оснастка не найдена: " + toolingId));
    }

    /**
     * Требуется ли приспособление для операции.
     */
    private boolean needsFixture(AssemblyOperation op) {
        if (op == null) return false;
        String type = op.getOperationType();
        return type == null || !type.equals("MATERIAL");
    }

    /**
     * Требуется ли зажим.
     */
    private boolean needsClamp(AssemblyOperation op) {
        if (op == null) return false;
        String type = op.getOperationType();
        return "INSTALL".equals(type) || "FASTEN".equals(type) || "SUBASSEMBLY".equals(type);
    }

    /**
     * Требуется ли измерительное приспособление.
     */
    private boolean needsMeasuring(AssemblyOperation op) {
        if (op == null) return false;
        return "CONTROL".equalsIgnoreCase(op.getOperationType());
    }

    /**
     * Требуется ли грузозахватное приспособление.
     */
    private boolean needsLifting(AssemblyOperation op) {
        if (op == null) return false;
        return "SUBASSEMBLY".equals(op.getOperationType());
    }

    private String designation(AssemblyOperation op) {
        if (op == null) return "—";
        return op.getDesignation() == null ? op.getName() : op.getDesignation();
    }

    private String buildFixtureName(AssemblyOperation op) {
        return "Приспособление сборочное для " + designation(op);
    }

    private String buildToolingDesignation(AssemblyProcess process,
                                             AssemblyOperation op,
                                             String type) {
        return (process.getDesignation() == null ? "СБ" : process.getDesignation())
                + "-" + type + "-" + op.getOrder();
    }

    private String resolveToolingMaterial(String type) {
        return switch (type) {
            case "FIXTURE", "CLAMP", "SUPPORT", "LOCATOR" -> "Сталь 45";
            case "MEASURING" -> "Сталь 20Х";
            case "LIFTING" -> "Сталь 20";
            default -> "Сталь 45";
        };
    }

    private String exportCsv(List<AssemblyTooling> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("Обозначение;Наименование;Тип;Материал;Стоимость;Статус\n");
        for (AssemblyTooling t : items) {
            sb.append(nvl(t.getDesignation())).append(';')
                    .append(nvl(t.getName())).append(';')
                    .append(nvl(t.getToolingType())).append(';')
                    .append(nvl(t.getMaterial())).append(';')
                    .append(t.getEstimatedCost() == null ? "" : t.getEstimatedCost()).append(';')
                    .append(nvl(t.getStatus())).append('\n');
        }
        return sb.toString();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}