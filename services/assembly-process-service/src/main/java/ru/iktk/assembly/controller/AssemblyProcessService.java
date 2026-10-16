package ru.iktk.assembly.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.assembly.client.BomClient;
import ru.iktk.assembly.client.EquipmentClient;
import ru.iktk.assembly.config.AssemblyProperties;
import ru.iktk.assembly.dto.AssemblyOperationDto;
import ru.iktk.assembly.dto.AssemblyProcessDto;
import ru.iktk.assembly.dto.AssemblyRequest;
import ru.iktk.assembly.dto.AssemblyResponse;
import ru.iktk.assembly.dto.AssemblySequenceDto;
import ru.iktk.assembly.dto.AssemblyToolingDto;
import ru.iktk.assembly.dto.BomTreeDto;
import ru.iktk.assembly.exception.AssemblyException;
import ru.iktk.assembly.mapper.AssemblyMapper;
import ru.iktk.assembly.model.AssemblyOperation;
import ru.iktk.assembly.model.AssemblyProcess;
import ru.iktk.assembly.repository.AssemblyOperationRepository;
import ru.iktk.assembly.repository.AssemblyProcessRepository;
import ru.iktk.shared.storage.StorageClient;
import ru.iktk.shared.versioning.VersioningService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Основной сервис проектирования ТП сборки ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>проектирование ТП сборки на основе BOM и связей;</li>
 *   <li>определение последовательности сборочных операций;</li>
 *   <li>формирование операций сборки (установка, крепление,
 *       контроль);</li>
 *   <li>выбор сборочного оборудования и инструмента;</li>
 *   <li>проектирование сборочной оснастки через {@link ToolingService};</li>
 *   <li>нормирование сборочных операций (предварительное);</li>
 *   <li>формирование карты сборки и экспорт;</li>
 *   <li>утверждение/отклонение ТП сборки;</li>
 *   <li>версионирование через {@link VersioningService}.</li>
 * </ul>
 *
 * <p><b>Алгоритм проектирования.</b>
 * <ol>
 *   <li>Загрузка BOM и связей из {@link BomClient}.</li>
 *   <li>Построение графа сборки по связям.</li>
 *   <li>Топологическая сортировка (или иная стратегия).</li>
 *   <li>Формирование операций сборки.</li>
 *   <li>Подбор оборудования и оснастки.</li>
 *   <li>Предварительное нормирование.</li>
 *   <li>Сохранение ТП и фиксация версии.</li>
 * </ol>
 */
@Service
public class AssemblyProcessService {

    private static final Logger log = LoggerFactory.getLogger(AssemblyProcessService.class);

    private static final int MAX_OPERATIONS = 5000;
    private static final BigDecimal DEFAULT_OPERATION_TIME_MIN = BigDecimal.valueOf(2.0);

    private final AssemblyProcessRepository processRepository;
    private final AssemblyOperationRepository operationRepository;
    private final AssemblyMapper mapper;
    private final BomClient bomClient;
    private final EquipmentClient equipmentClient;
    private final ToolingService toolingService;
    private final StorageClient storageClient;
    private final VersioningService versioningService;
    private final AssemblyProperties properties;

    public AssemblyProcessService(AssemblyProcessRepository processRepository,
                                  AssemblyOperationRepository operationRepository,
                                  AssemblyMapper mapper,
                                  BomClient bomClient,
                                  EquipmentClient equipmentClient,
                                  ToolingService toolingService,
                                  StorageClient storageClient,
                                  VersioningService versioningService,
                                  AssemblyProperties properties) {
        this.processRepository = processRepository;
        this.operationRepository = operationRepository;
        this.mapper = mapper;
        this.bomClient = bomClient;
        this.equipmentClient = equipmentClient;
        this.toolingService = toolingService;
        this.storageClient = storageClient;
        this.versioningService = versioningService;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Проектирование ТП сборки
    // ==================================================================

    /**
     * Спроектировать ТП сборки по проекту.
     */
    @Transactional
    public AssemblyResponse designProcess(AssemblyRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        String bomId = request.getBomId();

        log.info("designProcess: projectId={}, bomId={}, strategy={}, tooling={}",
                projectId, bomId, request.getSequenceStrategy(), request.isToolingEnabled());

        // 1. Загрузка BOM и связей
        BomTreeDto bom = loadBom(projectId, bomId);
        if (bom == null || bom.getItems() == null || bom.getItems().isEmpty()) {
            throw AssemblyException.emptyBom(projectId);
        }

        // 2. Определение стратегии
        String strategy = resolveStrategy(request);

        // 3. Построение последовательности
        List<BomTreeDto.Item> sequence = buildSequence(bom, strategy);

        // 4. Формирование операций
        List<AssemblyOperation> operations = buildOperations(projectId, sequence, bom);

        if (operations.size() > MAX_OPERATIONS) {
            throw AssemblyException.validation(
                    "Слишком много операций сборки: " + operations.size());
        }

        // 5. Создание ТП
        String processId = UUID.randomUUID().toString();
        AssemblyProcess process = AssemblyProcess.builder()
                .id(processId)
                .projectId(projectId)
                .bomId(bom.getId())
                .designation(buildProcessDesignation(bom))
                .name("ТП сборки " + bom.getName())
                .version(1)
                .status("DRAFT")
                .strategy(strategy)
                .operationsCount(operations.size())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        processRepository.save(process);

        // 6. Сохранение операций
        for (AssemblyOperation op : operations) {
            op.setProcessId(processId);
        }
        operationRepository.saveAll(operations);

        // 7. Проектирование оснастки (опционально)
        List<AssemblyToolingDto> tooling = List.of();
        if (request.isToolingEnabled()) {
            try {
                tooling = toolingService.designForProcess(processId);
                process.setToolingCount(tooling.size());
                processRepository.save(process);
            } catch (Exception e) {
                log.warn("designProcess: оснастка не спроектирована: {}", e.getMessage());
            }
        }

        // 8. Версионность
        versioningService.createVersion(
                "ASSEMBLY_PROCESS",
                UUID.fromString(processId),
                UUID.fromString(projectId),
                mapper.toJsonNode(process, operations),
                VersioningService.ChangeType.CREATE,
                "Создан ТП сборки",
                "system");

        log.info("designProcess done: projectId={}, processId={}, operations={}, tooling={}",
                projectId, processId, operations.size(), tooling.size());

        return AssemblyResponse.builder()
                .projectId(projectId)
                .processId(processId)
                .status("COMPLETED")
                .operationsCount(operations.size())
                .toolingCount(tooling.size())
                .message("ТП сборки спроектирован")
                .build();
    }

    // ==================================================================
    // 2. Чтение
    // ==================================================================

    /**
     * Получить ТП сборки по проекту.
     */
    @Transactional(readOnly = true)
    public AssemblyProcessDto getByProject(String projectId) {
        AssemblyProcess process = processRepository
                .findTopByProjectIdOrderByCreatedAtDesc(projectId)
                .orElseThrow(() -> AssemblyException.notFound(
                        "ТП сборки для проекта не найден: " + projectId));

        return enrich(mapper.toDto(process));
    }

    /**
     * Получить ТП сборки по ID.
     */
    @Transactional(readOnly = true)
    public AssemblyProcessDto getById(String processId) {
        AssemblyProcess process = findProcess(processId);
        return enrich(mapper.toDto(process));
    }

    /**
     * Получить статус проектирования.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatus(String processId) {
        AssemblyProcess process = findProcess(processId);

        Map<String, Object> status = new HashMap<>();
        status.put("processId", processId);
        status.put("status", process.getStatus());
        status.put("operationsCount", process.getOperationsCount());
        status.put("toolingCount", process.getToolingCount());
        status.put("createdAt", process.getCreatedAt());
        status.put("updatedAt", process.getUpdatedAt());
        return status;
    }

    // ==================================================================
    // 3. Операции
    // ==================================================================

    /**
     * Получить операции ТП.
     */
    @Transactional(readOnly = true)
    public List<AssemblyOperationDto> getOperations(String processId, String type) {
        findProcess(processId);

        List<AssemblyOperation> operations = (type == null || type.isBlank())
                ? operationRepository.findByProcessIdOrderByOrder(processId)
                : operationRepository.findByProcessIdAndOperationTypeOrderByOrder(processId, type);

        return operations.stream().map(mapper::toOperationDto).toList();
    }

    /**
     * Получить операцию по ID.
     */
    @Transactional(readOnly = true)
    public AssemblyOperationDto getOperation(String operationId) {
        AssemblyOperation op = operationRepository.findById(operationId)
                .orElseThrow(() -> AssemblyException.notFound(
                        "Операция не найдена: " + operationId));
        return mapper.toOperationDto(op);
    }

    /**
     * Обновить операцию.
     */
    @Transactional
    public AssemblyOperationDto updateOperation(String operationId, AssemblyOperationDto dto) {
        AssemblyOperation op = operationRepository.findById(operationId)
                .orElseThrow(() -> AssemblyException.notFound(
                        "Операция не найдена: " + operationId));

        mapper.updateOperation(op, dto);
        op.setUpdatedAt(Instant.now());
        AssemblyOperation saved = operationRepository.save(op);

        log.info("updateOperation: operationId={}", operationId);
        return mapper.toOperationDto(saved);
    }

    // ==================================================================
    // 4. Последовательность сборки
    // ==================================================================

    /**
     * Получить последовательность установки деталей.
     */
    @Transactional(readOnly = true)
    public AssemblySequenceDto getSequence(String processId) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyOperation> operations =
                operationRepository.findByProcessIdOrderByOrder(processId);

        return mapper.toSequenceDto(process, operations);
    }

    /**
     * Пересчитать последовательность с указанной стратегией.
     */
    @Transactional
    public AssemblySequenceDto recalculateSequence(String processId, String strategy) {
        AssemblyProcess process = findProcess(processId);

        BomTreeDto bom = loadBom(process.getProjectId(), process.getBomId());
        if (bom == null) {
            throw AssemblyException.emptyBom(process.getProjectId());
        }

        // Удаляем старые операции
        operationRepository.deleteByProcessId(processId);

        // Строим новую последовательность
        List<BomTreeDto.Item> sequence = buildSequence(bom, strategy);
        List<AssemblyOperation> operations = buildOperations(
                process.getProjectId(), sequence, bom);

        for (AssemblyOperation op : operations) {
            op.setProcessId(processId);
        }
        operationRepository.saveAll(operations);

        process.setStrategy(strategy);
        process.setOperationsCount(operations.size());
        process.setUpdatedAt(Instant.now());
        process.setVersion(process.getVersion() + 1);
        processRepository.save(process);

        versioningService.createVersion(
                "ASSEMBLY_PROCESS",
                UUID.fromString(processId),
                UUID.fromString(process.getProjectId()),
                mapper.toJsonNode(process, operations),
                VersioningService.ChangeType.UPDATE,
                "Пересчёт последовательности: " + strategy,
                "system");

        log.info("recalculateSequence: processId={}, strategy={}, operations={}",
                processId, strategy, operations.size());

        return mapper.toSequenceDto(process, operations);
    }

    // ==================================================================
    // 5. Карта сборки и экспорт
    // ==================================================================

    /**
     * Получить карту сборки.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssemblyCard(String processId) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyOperation> operations =
                operationRepository.findByProcessIdOrderByOrder(processId);
        List<AssemblyToolingDto> tooling = toolingService.getByProcess(processId);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (AssemblyOperation op : operations) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("order", op.getOrder());
            step.put("designation", op.getDesignation());
            step.put("name", op.getName());
            step.put("operationType", op.getOperationType());
            step.put("equipmentId", op.getEquipmentId());
            step.put("toolingId", op.getToolingId());
            step.put("timeNormMin", op.getTimeNormMin());
            step.put("setupTimeMin", op.getSetupTimeMin());
            steps.add(step);
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("processId", processId);
        card.put("designation", process.getDesignation());
        card.put("name", process.getName());
        card.put("strategy", process.getStrategy());
        card.put("version", process.getVersion());
        card.put("steps", steps);
        card.put("tooling", tooling);
        card.put("totalOperations", operations.size());
        card.put("totalTooling", tooling.size());
        return card;
    }

    /**
     * Экспортировать ТП сборки.
     */
    @Transactional(readOnly = true)
    public String export(String processId, String format) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyOperation> operations =
                operationRepository.findByProcessIdOrderByOrder(processId);

        log.info("export: processId={}, format={}", processId, format);

        String content;
        String filename;

        switch (format == null ? "EXCEL" : format.toUpperCase()) {
            case "CSV" -> {
                content = exportCsv(operations);
                filename = "assembly-" + processId + ".csv";
            }
            case "PDF" -> {
                content = "ТП сборки " + process.getDesignation()
                        + ": " + operations.size() + " операций";
                filename = "assembly-" + processId + ".pdf";
            }
            case "EXCEL" -> {
                content = exportCsv(operations); // заглушка
                filename = "assembly-" + processId + ".xlsx";
            }
            default -> throw AssemblyException.validation(
                    "Неподдерживаемый формат: " + format);
        }

        return storageClient.uploadText(content, process.getProjectId(), "docs", filename);
    }

    // ==================================================================
    // 6. Утверждение и версии
    // ==================================================================

    /**
     * Утвердить ТП сборки.
     */
    @Transactional
    public AssemblyProcessDto approve(String processId, String comment) {
        AssemblyProcess process = findProcess(processId);
        process.setStatus("APPROVED");
        process.setUpdatedAt(Instant.now());
        AssemblyProcess saved = processRepository.save(process);

        versioningService.createVersion(
                "ASSEMBLY_PROCESS",
                UUID.fromString(processId),
                UUID.fromString(process.getProjectId()),
                mapper.toJsonNode(saved, List.of()),
                VersioningService.ChangeType.APPROVE,
                comment == null ? "Утверждён ТП сборки" : comment,
                "system");

        log.info("approve: processId={}", processId);
        return mapper.toDto(saved);
    }

    /**
     * Отклонить ТП сборки.
     */
    @Transactional
    public AssemblyProcessDto reject(String processId, String reason) {
        AssemblyProcess process = findProcess(processId);
        process.setStatus("REJECTED");
        process.setUpdatedAt(Instant.now());
        AssemblyProcess saved = processRepository.save(process);

        versioningService.createVersion(
                "ASSEMBLY_PROCESS",
                UUID.fromString(processId),
                UUID.fromString(process.getProjectId()),
                mapper.toJsonNode(saved, List.of()),
                VersioningService.ChangeType.REJECT,
                reason == null ? "Отклонён ТП сборки" : reason,
                "system");

        log.warn("reject: processId={}, reason={}", processId, reason);
        return mapper.toDto(saved);
    }

    /**
     * История версий ТП сборки.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVersions(String processId) {
        AssemblyProcess process = findProcess(processId);
        return versioningService.getHistory(
                "ASSEMBLY_PROCESS",
                UUID.fromString(processId),
                100
        ).stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("version", v.version());
                    m.put("changeType", v.changeType() == null
                            ? null : v.changeType().name());
                    m.put("reason", v.changeReason());
                    m.put("createdBy", v.createdBy());
                    m.put("createdAt", v.createdAt());
                    m.put("isCurrent", v.isCurrent());
                    return m;
                })
                .toList();
    }

    // ==================================================================
    // 7. Сводка
    // ==================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String processId) {
        AssemblyProcess process = findProcess(processId);
        List<AssemblyOperation> operations =
                operationRepository.findByProcessIdOrderByOrder(processId);

        BigDecimal totalTime = operations.stream()
                .map(AssemblyOperation::totalTimeNorm)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("processId", processId);
        summary.put("projectId", process.getProjectId());
        summary.put("designation", process.getDesignation());
        summary.put("status", process.getStatus());
        summary.put("strategy", process.getStrategy());
        summary.put("version", process.getVersion());
        summary.put("operationsCount", operations.size());
        summary.put("toolingCount", process.getToolingCount());
        summary.put("totalTimeMin", totalTime);
        summary.put("createdAt", process.getCreatedAt());
        summary.put("updatedAt", process.getUpdatedAt());
        return summary;
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

    /**
     * Загрузить BOM через {@link BomClient}.
     */
    private BomTreeDto loadBom(String projectId, String bomId) {
        try {
            if (bomId != null && !bomId.isBlank()) {
                return bomClient.getBomTree(bomId);
            }
            return bomClient.getByProject(projectId);
        } catch (Exception e) {
            log.error("loadBom: ошибка загрузки BOM, projectId={}, bomId={}",
                    projectId, bomId, e);
            throw AssemblyException.validation(
                    "Не удалось загрузить BOM: " + e.getMessage());
        }
    }

    /**
     * Определить стратегию.
     */
    private String resolveStrategy(AssemblyRequest request) {
        if (request.getSequenceStrategy() != null) {
            return request.getSequenceStrategy().toUpperCase();
        }
        return properties.getSequenceStrategy() == null
                ? "TOPOLOGICAL"
                : properties.getSequenceStrategy().toUpperCase();
    }

    /**
     * Построить последовательность установки деталей.
     *
     * <p>Реализация: топологическая сортировка по графу связей.
     * Альтернативно — GREEDY (жадный) или MANUAL (по порядку BOM).
     */
    private List<BomTreeDto.Item> buildSequence(BomTreeDto bom, String strategy) {
        List<BomTreeDto.Item> flat = flatten(bom);

        return switch (strategy == null ? "TOPOLOGICAL" : strategy.toUpperCase()) {
            case "TOPOLOGICAL" -> topologicalSort(flat, bom.getConnections());
            case "GREEDY" -> greedySort(flat);
            case "MANUAL" -> flat;
            default -> topologicalSort(flat, bom.getConnections());
        };
    }

    /**
     * Уплощение дерева BOM.
     */
    private List<BomTreeDto.Item> flatten(BomTreeDto bom) {
        List<BomTreeDto.Item> result = new ArrayList<>();
        if (bom.getItems() != null) {
            for (BomTreeDto.Item root : bom.getItems()) {
                flattenRecursive(root, result);
            }
        }
        return result;
    }

    private void flattenRecursive(BomTreeDto.Item item, List<BomTreeDto.Item> result) {
        if (item == null) {
            return;
        }
        result.add(item);
        if (item.getChildren() != null) {
            for (BomTreeDto.Item child : item.getChildren()) {
                flattenRecursive(child, result);
            }
        }
    }

    /**
     * Топологическая сортировка по графу связей.
     */
    private List<BomTreeDto.Item> topologicalSort(List<BomTreeDto.Item> items,
                                                    List<BomTreeDto.Connection> connections) {
        Map<String, BomTreeDto.Item> byId = new LinkedHashMap<>();
        for (BomTreeDto.Item item : items) {
            byId.put(item.getId(), item);
        }

        Map<String, Set<String>> incoming = new HashMap<>();
        for (BomTreeDto.Item item : items) {
            incoming.put(item.getId(), new HashSet<>());
        }
        if (connections != null) {
            for (BomTreeDto.Connection c : connections) {
                if (c.getPart2Id() != null && incoming.containsKey(c.getPart2Id())) {
                    incoming.get(c.getPart2Id()).add(c.getPart1Id());
                }
            }
        }

        List<BomTreeDto.Item> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        boolean progress = true;
        while (progress && result.size() < items.size()) {
            progress = false;
            for (BomTreeDto.Item item : items) {
                if (visited.contains(item.getId())) {
                    continue;
                }
                Set<String> inc = incoming.get(item.getId());
                if (inc == null || inc.stream().allMatch(visited::contains)) {
                    result.add(item);
                    visited.add(item.getId());
                    progress = true;
                }
            }
        }

        for (BomTreeDto.Item item : items) {
            if (!visited.contains(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Жадная сортировка: сначала крупные подсборки, затем детали.
     */
    private List<BomTreeDto.Item> greedySort(List<BomTreeDto.Item> items) {
        List<BomTreeDto.Item> result = new ArrayList<>(items);
        result.sort((a, b) -> {
            int cmp = Boolean.compare(
                    "ASSEMBLY".equals(b.getType()),
                    "ASSEMBLY".equals(a.getType()));
            if (cmp != 0) return cmp;
            return Integer.compare(a.getOrder(), b.getOrder());
        });
        return result;
    }

    /**
     * Построить операции сборки на основе последовательности.
     */
    private List<AssemblyOperation> buildOperations(String projectId,
                                                     List<BomTreeDto.Item> sequence,
                                                     BomTreeDto bom) {
        List<AssemblyOperation> operations = new ArrayList<>();
        int order = 1;

        for (BomTreeDto.Item item : sequence) {
            String opType = resolveOperationType(item);

            AssemblyOperation op = AssemblyOperation.builder()
                    .id(UUID.randomUUID().toString())
                    .processId(null) // проставится позже
                    .order(order)
                    .designation(String.format("%03d", order * 5))
                    .name(buildOperationName(item, opType))
                    .operationType(opType)
                    .bomItemId(item.getId())
                    .bomItemDesignation(item.getDesignation())
                    .timeNormMin(DEFAULT_OPERATION_TIME_MIN)
                    .setupTimeMin(BigDecimal.ZERO)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            // Подбор оборудования
            try {
                String equipmentId = equipmentClient.findAvailable(
                        projectId, opType, false, Map.of());
                if (equipmentId != null) {
                    op.setEquipmentId(equipmentId);
                }
            } catch (Exception e) {
                log.debug("buildOperations: оборудование не подобрано для {}", opType);
            }

            operations.add(op);
            order++;
        }

        return operations;
    }

    /**
     * Определить тип операции по позиции BOM.
     */
    private String resolveOperationType(BomTreeDto.Item item) {
        if (item == null) return "INSTALL";
        if ("ASSEMBLY".equals(item.getType())) return "SUBASSEMBLY";
        if ("FASTENER".equals(item.getType())) return "FASTEN";
        if ("MATERIAL".equals(item.getType())) return "MATERIAL";
        return "INSTALL";
    }

    private String buildOperationName(BomTreeDto.Item item, String opType) {
        String designation = item.getDesignation() == null
                ? item.getName() : item.getDesignation();
        return switch (opType) {
            case "SUBASSEMBLY" -> "Установить подсборку " + designation;
            case "FASTEN" -> "Закрепить " + designation;
            case "MATERIAL" -> "Нанести " + designation;
            default -> "Установить " + designation;
        };
    }

    private String buildProcessDesignation(BomTreeDto bom) {
        String base = bom.getDesignation() == null ? "СБ" : bom.getDesignation();
        return base + "-ТП";
    }

    private AssemblyProcessDto enrich(AssemblyProcessDto dto) {
        if (dto == null) return null;
        try {
            List<AssemblyOperation> operations =
                    operationRepository.findByProcessIdOrderByOrder(dto.getId());
            dto.setOperations(operations.stream().map(mapper::toOperationDto).toList());
        } catch (Exception e) {
            log.warn("enrich: не удалось загрузить операции для {}", dto.getId());
        }
        return dto;
    }

    private String exportCsv(List<AssemblyOperation> operations) {
        StringBuilder sb = new StringBuilder();
        sb.append("№;Обозначение;Наименование;Тип;Оборудование;Оснастка;Тшт;Тпз\n");
        for (AssemblyOperation op : operations) {
            sb.append(op.getOrder()).append(';')
                    .append(nvl(op.getDesignation())).append(';')
                    .append(nvl(op.getName())).append(';')
                    .append(nvl(op.getOperationType())).append(';')
                    .append(nvl(op.getEquipmentId())).append(';')
                    .append(nvl(op.getToolingId())).append(';')
                    .append(op.getTimeNormMin() == null ? "" : op.getTimeNormMin()).append(';')
                    .append(op.getSetupTimeMin() == null ? "" : op.getSetupTimeMin()).append('\n');
        }
        return sb.toString();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private void validate(AssemblyRequest request) {
        if (request == null) {
            throw AssemblyException.validation("Пустой запрос");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw AssemblyException.validation("Не указан projectId");
        }
    }
}