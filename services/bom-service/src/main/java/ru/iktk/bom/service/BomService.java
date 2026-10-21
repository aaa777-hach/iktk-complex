package ru.iktk.bom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.bom.client.IntegrationClient;
import ru.iktk.bom.config.BomProperties;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.dto.BomTree;
import ru.iktk.bom.dto.BomUpdateRequest;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.mapper.BomMapper;
import ru.iktk.bom.model.AssemblyConnection;
import ru.iktk.bom.model.AssemblyUnit;
import ru.iktk.bom.model.BomItem;
import ru.iktk.bom.repository.AssemblyConnectionRepository;
import ru.iktk.bom.repository.AssemblyRepository;
import ru.iktk.bom.repository.BomItemRepository;
import ru.iktk.shared.storage.StorageClient;
import ru.iktk.shared.versioning.VersioningService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Основной сервис BOM (Bill of Materials) ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>анализ сборочной единицы и извлечение структуры изделия
 *       из CAD через {@link IntegrationClient};</li>
 *   <li>построение иерархического дерева BOM
 *       (сборка → подсборки → детали → крепёж → материалы);</li>
 *   <li>сохранение позиций, количеств, обозначений, материалов
 *       в БД с версионностью;</li>
 *   <li>ручную корректировку позиций BOM;</li>
 *   <li>извлечение связей и сопряжений сборки
 *       (для проектирования ТП сборки);</li>
 *   <li>формирование спецификации по ГОСТ 2.106;</li>
 *   <li>экспорт BOM в Excel/CSV/PDF через {@link StorageClient}.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b>
 * <ul>
 *   <li>{@code assembly_units} — сборочные единицы;</li>
 *   <li>{@code bom_items} — позиции состава изделия (иерархия
 *       через {@code parent_id});</li>
 *   <li>{@code assembly_connections} — связи и сопряжения.</li>
 * </ul>
 *
 * <p><b>Версионность.</b> Каждое изменение BOM фиксируется через
 * {@link VersioningService} с типом {@code CREATE} / {@code UPDATE} /
 * {@code ROLLBACK}.
 */
@Service
public class BomService {

    private static final Logger log = LoggerFactory.getLogger(BomService.class);

    private static final int MAX_TREE_DEPTH = 20;

    private final AssemblyRepository assemblyRepository;
    private final BomItemRepository itemRepository;
    private final AssemblyConnectionRepository connectionRepository;
    private final BomMapper mapper;
    private final BomExtractionService extractionService;
    private final IntegrationClient integrationClient;
    private final StorageClient storageClient;
    private final VersioningService versioningService;
    private final BomProperties properties;

    public BomService(AssemblyRepository assemblyRepository,
                      BomItemRepository itemRepository,
                      AssemblyConnectionRepository connectionRepository,
                      BomMapper mapper,
                      BomExtractionService extractionService,
                      IntegrationClient integrationClient,
                      StorageClient storageClient,
                      VersioningService versioningService,
                      BomProperties properties) {
        this.assemblyRepository = assemblyRepository;
        this.itemRepository = itemRepository;
        this.connectionRepository = connectionRepository;
        this.mapper = mapper;
        this.extractionService = extractionService;
        this.integrationClient = integrationClient;
        this.storageClient = storageClient;
        this.versioningService = versioningService;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Анализ сборочной единицы
    // ==================================================================

    /**
     * Запустить анализ сборочной единицы.
     *
     * <p>Шаги:
     * <ol>
     *   <li>Проверка запроса.</li>
     *   <li>Проверка, есть ли уже BOM для проекта (если нет {@code force} — возвращаем существующий).</li>
     *   <li>Извлечение структуры через {@link BomExtractionService}.</li>
     *   <li>Сохранение в БД с версионностью.</li>
     *   <li>Возврат {@link BomResponse}.</li>
     * </ol>
     */
    @Transactional
    public BomResponse analyze(BomAnalyzeRequest request) {
        validateAnalyze(request);

        String projectId = request.getProjectId();
        String geometryId = request.getGeometryId();

        log.info("analyze: projectId={}, geometryId={}, force={}",
                projectId, geometryId, request.isForce());

        // Проверка существующего BOM
        if (!request.isForce()) {
            Optional<AssemblyUnit> existing = assemblyRepository.findRootByProject(projectId);
            if (existing.isPresent()) {
                log.info("analyze: BOM уже существует, projectId={}, assemblyId={}",
                        projectId, existing.get().getId());
                return BomResponse.alreadyExists(
                        existing.get().getId(),
                        projectId,
                        countItems(existing.get().getId()));
            }
        }

        // Извлечение структуры через ExtractionService
        BomExtractionResult extraction = extractionService.extract(
                projectId, geometryId, request.getCadSystem());

        if (extraction == null || extraction.getItems().isEmpty()) {
            log.warn("analyze: структура изделия не извлечена, projectId={}", projectId);
            throw BomException.emptyStructure(projectId);
        }

        // Создание корневой сборочной единицы
        AssemblyUnit root = AssemblyUnit.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .designation(extraction.getRootDesignation())
                .name(extraction.getRootName())
                .geometryId(geometryId)
                .itemCount(extraction.getItems().size())
                .version(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        assemblyRepository.save(root);

        // Сохранение позиций (иерархия)
        List<BomItem> saved = persistItems(root, extraction.getItems());

        // Сохранение связей и сопряжений
        List<AssemblyConnection> connections = persistConnections(
                root, extraction.getConnections());
        connectionRepository.saveAll(connections);

        // Версионность
        versioningService.createVersion(
                "ASSEMBLY",
                UUID.fromString(root.getId()),
                UUID.fromString(projectId),
                mapper.toJsonNode(root, saved, connections),
                VersioningService.ChangeType.CREATE,
                "Извлечён BOM из CAD",
                "system");

        log.info("analyze done: projectId={}, assemblyId={}, items={}, connections={}",
                projectId, root.getId(), saved.size(), connections.size());

        return BomResponse.builder()
                .projectId(projectId)
                .assemblyId(root.getId())
                .bomId(root.getId())
                .status("COMPLETED")
                .positionsCount(saved.size())
                .connectionsCount(connections.size())
                .message("BOM извлечён успешно")
                .build();
    }

    /**
     * Получить статус анализа по ID.
     */
    @Transactional(readOnly = true)
    public BomResponse getAnalysis(String analysisId) {
        // В текущей реализации анализ синхронный — возвращаем результат
        AssemblyUnit unit = assemblyRepository.findById(analysisId)
                .orElseThrow(() -> BomException.notFound(
                        "Анализ не найден: " + analysisId));
        return BomResponse.builder()
                .projectId(unit.getProjectId())
                .assemblyId(unit.getId())
                .bomId(unit.getId())
                .status("COMPLETED")
                .positionsCount(countItems(unit.getId()))
                .message("BOM извлечён успешно")
                .build();
    }

    // ==================================================================
    // 2. Дерево состава изделия
    // ==================================================================

    /**
     * Получить дерево BOM по проекту.
     */
    @Transactional(readOnly = true)
    public BomTree getBomTreeByProject(String projectId) {
        validateProjectId(projectId);

        AssemblyUnit root = assemblyRepository.findRootByProject(projectId)
                .orElseThrow(() -> BomException.notFound(
                        "BOM для проекта не найден: " + projectId));

        return buildTree(root);
    }

    /**
     * Получить дерево BOM по ID сборки.
     */
    @Transactional(readOnly = true)
    public BomTree getBomTree(String bomId) {
        AssemblyUnit root = findAssembly(bomId);
        return buildTree(root);
    }

    /**
     * Получить уплощённый список позиций BOM.
     */
    @Transactional(readOnly = true)
    public List<BomItemDto> getItems(String bomId, String type) {
        findAssembly(bomId);

        List<BomItem> items = (type == null || type.isBlank())
                ? itemRepository.findByAssemblyIdOrderByPosition(bomId)
                : itemRepository.findByAssemblyIdAndTypeOrderByPosition(bomId, type);

        return items.stream()
                .map(mapper::toItemDto)
                .toList();
    }

    // ==================================================================
    // 3. Ручная корректировка BOM
    // ==================================================================

    /**
     * Обновить позиции BOM.
     */
    @Transactional
    public BomTree updateItems(String bomId, BomUpdateRequest request) {
        AssemblyUnit assembly = findAssembly(bomId);

        if (request == null || request.getItems() == null) {
            throw BomException.validation("Не указаны позиции для обновления");
        }

        log.info("updateItems: bomId={}, items={}", bomId, request.getItems().size());

        // Удаляем старые позиции
        itemRepository.deleteByAssemblyId(bomId);

        // Сохраняем новые
        List<BomItem> entities = new ArrayList<>();
        int order = 0;
        for (BomItemDto dto : request.getItems()) {
            BomItem entity = mapper.toItemEntity(dto);
            entity.setId(UUID.randomUUID().toString());
            entity.setAssemblyId(bomId);
            entity.setOrder(order++);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            entities.add(entity);
        }
        itemRepository.saveAll(entities);

        assembly.setItemCount(entities.size());
        assembly.setUpdatedAt(Instant.now());
        assemblyRepository.save(assembly);

        // Версионность
        versioningService.createVersion(
                "ASSEMBLY",
                UUID.fromString(bomId),
                UUID.fromString(assembly.getProjectId()),
                mapper.toJsonNode(assembly, entities, List.of()),
                VersioningService.ChangeType.UPDATE,
                "Ручная корректировка BOM",
                "system");

        log.info("updateItems done: bomId={}, items={}", bomId, entities.size());

        return buildTree(assembly);
    }

    /**
     * Добавить одну позицию BOM.
     */
    @Transactional
    public BomItemDto addItem(String bomId, String parentId, BomItemDto dto) {
        AssemblyUnit assembly = findAssembly(bomId);

        if (dto == null) {
            throw BomException.validation("Пустая позиция");
        }

        BomItem entity = mapper.toItemEntity(dto);
        entity.setId(UUID.randomUUID().toString());
        entity.setAssemblyId(bomId);
        entity.setParentId(parentId);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        if (entity.getOrder() == 0) {
            entity.setOrder((int) itemRepository.countByAssemblyId(bomId) + 1);
        }

        BomItem saved = itemRepository.save(entity);

        assembly.setItemCount(assembly.getItemCount() + 1);
        assembly.setUpdatedAt(Instant.now());
        assemblyRepository.save(assembly);

        log.info("addItem: bomId={}, itemId={}, designation={}",
                bomId, saved.getId(), saved.getDesignation());

        return mapper.toItemDto(saved);
    }

    /**
     * Удалить позицию BOM.
     */
    @Transactional
    public void deleteItem(String bomId, String itemId) {
        findAssembly(bomId);

        BomItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> BomException.notFound("Позиция не найдена: " + itemId));

        if (!bomId.equals(item.getAssemblyId())) {
            throw BomException.validation("Позиция не принадлежит указанной сборке");
        }

        // Удаляем вместе с потомками (рекурсивно)
        List<BomItem> descendants = findDescendants(itemId);
        itemRepository.deleteAll(descendants);
        itemRepository.delete(item);

        log.warn("deleteItem: bomId={}, itemId={}, deletedWithDescendants={}",
                bomId, itemId, descendants.size());
    }

    // ==================================================================
    // 4. Связи и сопряжения сборки
    // ==================================================================

    /**
     * Получить связи и сопряжения сборки.
     */
    @Transactional(readOnly = true)
    public List<AssemblyConnectionDto> getConnections(String bomId) {
        findAssembly(bomId);

        return connectionRepository.findByAssemblyId(bomId)
                .stream()
                .map(mapper::toConnectionDto)
                .toList();
    }

    /**
     * Получить рекомендуемую последовательность установки деталей.
     *
     * <p>Алгоритм: топологическая сортировка по графу связей
     * (детали-основания устанавливаются раньше деталей, которые
     * к ним крепятся).
     */
    @Transactional(readOnly = true)
    public List<BomItemDto> getAssemblySequence(String bomId) {
        AssemblyUnit assembly = findAssembly(bomId);

        List<BomItem> items = itemRepository.findByAssemblyIdOrderByPosition(bomId);
        List<AssemblyConnection> connections = connectionRepository.findByAssemblyId(bomId);

        List<BomItem> ordered = topologicalSort(items, connections);

        log.debug("getAssemblySequence: bomId={}, items={}, connections={}",
                bomId, ordered.size(), connections.size());

        return ordered.stream()
                .map(mapper::toItemDto)
                .toList();
    }

    // ==================================================================
    // 5. Спецификация
    // ==================================================================

    /**
     * Получить спецификацию по ГОСТ 2.106.
     */
    @Transactional(readOnly = true)
    public SpecificationDto getSpecification(String bomId) {
        findAssembly(bomId);

        List<BomItem> items = itemRepository.findByAssemblyIdOrderByPosition(bomId);

        return mapper.toSpecification(items);
    }

    // ==================================================================
    // 6. Экспорт
    // ==================================================================

    /**
     * Экспортировать BOM в указанный формат.
     *
     * @return внутренний URI файла в MinIO
     */
    @Transactional(readOnly = true)
    public String export(String bomId, String format) {
        AssemblyUnit assembly = findAssembly(bomId);

        log.info("export: bomId={}, format={}", bomId, format);

        List<BomItem> items = itemRepository.findByAssemblyIdOrderByPosition(bomId);
        BomTree tree = buildTree(assembly);

        String content;
        String filename;
        String contentType;

        switch (format == null ? "EXCEL" : format.toUpperCase()) {
            case "CSV" -> {
                content = exportCsv(items);
                filename = "bom-" + bomId + ".csv";
                contentType = "text/csv; charset=utf-8";
            }
            case "PDF" -> {
                content = exportPdf(tree);
                filename = "bom-" + bomId + ".pdf";
                contentType = "application/pdf";
            }
            case "EXCEL" -> {
                content = exportExcel(items);
                filename = "bom-" + bomId + ".xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }
            default -> throw BomException.validation(
                    "Неподдерживаемый формат экспорта: " + format);
        }

        return storageClient.uploadText(
                content,
                assembly.getProjectId(),
                "bom",
                filename);
    }

    // ==================================================================
    // 7. Сводки и служебные
    // ==================================================================

    /**
     * Сводка по BOM: количество позиций, уровни, типы.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String bomId) {
        AssemblyUnit assembly = findAssembly(bomId);

        List<BomItem> items = itemRepository.findByAssemblyIdOrderByPosition(bomId);

        long parts = items.stream().filter(i -> "PART".equals(i.getType())).count();
        long assemblies = items.stream().filter(i -> "ASSEMBLY".equals(i.getType())).count();
        long standard = items.stream().filter(i -> "STANDARD".equals(i.getType())).count();
        long materials = items.stream().filter(i -> "MATERIAL".equals(i.getType())).count();

        int maxDepth = items.stream()
                .mapToInt(BomItem::getLevel)
                .max()
                .orElse(0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("bomId", bomId);
        summary.put("projectId", assembly.getProjectId());
        summary.put("designation", assembly.getDesignation());
        summary.put("totalPositions", items.size());
        summary.put("parts", parts);
        summary.put("assemblies", assemblies);
        summary.put("standard", standard);
        summary.put("materials", materials);
        summary.put("maxDepth", maxDepth);
        summary.put("connections", connectionRepository.countByAssemblyId(bomId));
        summary.put("version", assembly.getVersion());
        summary.put("updatedAt", assembly.getUpdatedAt());
        return summary;
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    /**
     * Построить дерево BOM из корневой сборки.
     */
    private BomTree buildTree(AssemblyUnit root) {
        List<BomItem> items = itemRepository.findByAssemblyIdOrderByPosition(root.getId());

        // Индекс по ID и по parentId
        Map<String, BomItemDto> dtoById = new HashMap<>();
        Map<String, List<BomItemDto>> childrenByParent = new HashMap<>();

        for (BomItem item : items) {
            BomItemDto dto = mapper.toItemDto(item);
            dtoById.put(dto.getId(), dto);
            childrenByParent
                    .computeIfAbsent(item.getParentId(), k -> new ArrayList<>())
                    .add(dto);
        }

        // Связываем детей
        for (BomItemDto dto : dtoById.values()) {
            List<BomItemDto> children = childrenByParent.get(dto.getId());
            if (children != null) {
                dto.setChildren(children);
            }
        }

        // Корневые позиции (parentId == null или отсутствует в dtoById)
        List<BomItemDto> rootItems = items.stream()
                .filter(i -> i.getParentId() == null
                        || !dtoById.containsKey(i.getParentId()))
                .map(i -> dtoById.get(i.getId()))
                .toList();

        return BomTree.builder()
                .id(root.getId())
                .projectId(root.getProjectId())
                .rootAssemblyId(root.getId())
                .designation(root.getDesignation())
                .name(root.getName())
                .items(rootItems)
                .totalPositions(items.size())
                .maxDepth(items.stream().mapToInt(BomItem::getLevel).max().orElse(0))
                .build();
    }

    /**
     * Сохранить позиции BOM (иерархически).
     */
    private List<BomItem> persistItems(AssemblyUnit root, List<BomItemDto> dtos) {
        List<BomItem> saved = new ArrayList<>();
        int order = 0;

        for (BomItemDto dto : dtos) {
            BomItem entity = mapper.toItemEntity(dto);
            entity.setId(UUID.randomUUID().toString());
            entity.setAssemblyId(root.getId());
            entity.setOrder(order++);
            entity.setLevel(calculateLevel(dto));
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            saved.add(entity);
        }

        itemRepository.saveAll(saved);
        return saved;
    }

    /**
     * Сохранить связи сборки.
     */
    private List<AssemblyConnection> persistConnections(AssemblyUnit root,
                                                         List<AssemblyConnectionDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }

        List<AssemblyConnection> saved = new ArrayList<>();
        for (AssemblyConnectionDto dto : dtos) {
            AssemblyConnection entity = mapper.toConnectionEntity(dto);
            entity.setId(UUID.randomUUID().toString());
            entity.setAssemblyId(root.getId());
            entity.setCreatedAt(Instant.now());
            saved.add(entity);
        }
        return saved;
    }

    /**
     * Найти всех потомков позиции (рекурсивно).
     */
    private List<BomItem> findDescendants(String itemId) {
        List<BomItem> result = new ArrayList<>();
        List<BomItem> direct = itemRepository.findByParentId(itemId);
        for (BomItem child : direct) {
            result.add(child);
            result.addAll(findDescendants(child.getId()));
        }
        return result;
    }

    /**
     * Топологическая сортировка позиций по графу связей.
     *
     * <p>Простая реализация: детали без входящих связей — первыми;
     * затем — итеративно.
     */
    private List<BomItem> topologicalSort(List<BomItem> items,
                                          List<AssemblyConnection> connections) {
        Map<String, BomItem> byId = items.stream()
                .collect(Collectors.toMap(BomItem::getId, i -> i));

        Map<String, Set<String>> incoming = new HashMap<>();
        for (BomItem item : items) {
            incoming.put(item.getId(), new HashSet<>());
        }
        for (AssemblyConnection conn : connections) {
            if (conn.getPart2Id() != null && incoming.containsKey(conn.getPart2Id())) {
                incoming.get(conn.getPart2Id()).add(conn.getPart1Id());
            }
        }

        List<BomItem> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        boolean progress = true;
        while (progress && result.size() < items.size()) {
            progress = false;
            for (BomItem item : items) {
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

        // Оставшиеся (циклы) — в исходном порядке
        for (BomItem item : items) {
            if (!visited.contains(item.getId())) {
                result.add(item);
            }
        }

        return result;
    }

    private int calculateLevel(BomItemDto dto) {
        int level = 0;
        BomItemDto current = dto;
        Set<String> visited = new HashSet<>();
        while (current.getParentId() != null
                && visited.add(current.getId())
                && level < MAX_TREE_DEPTH) {
            level++;
            current = null; // упрощение: без обхода вверх
            break;
        }
        return level;
    }

    private int countItems(String assemblyId) {
        return (int) itemRepository.countByAssemblyId(assemblyId);
    }

    private AssemblyUnit findAssembly(String assemblyId) {
        if (assemblyId == null || assemblyId.isBlank()) {
            throw BomException.validation("Не указан bomId");
        }
        return assemblyRepository.findById(assemblyId)
                .orElseThrow(() -> BomException.notFound(
                        "Сборка не найдена: " + assemblyId));
    }

    private void validateAnalyze(BomAnalyzeRequest request) {
        if (request == null) {
            throw BomException.validation("Пустой запрос на анализ");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw BomException.validation("Не указан projectId");
        }
    }

    private void validateProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw BomException.validation("Не указан projectId");
        }
    }

    // ==================================================================
    // Экспорт — простые реализации (реальные — через библиотеки)
    // ==================================================================

    private String exportCsv(List<BomItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("Позиция;Обозначение;Наименование;Количество;Ед.;Материал;Тип\n");
        for (BomItem item : items) {
            sb.append(nvl(item.getPosition())).append(';')
                    .append(nvl(item.getDesignation())).append(';')
                    .append(nvl(item.getName())).append(';')
                    .append(item.getQuantity()).append(';')
                    .append(nvl(item.getUnit())).append(';')
                    .append(nvl(item.getMaterial())).append(';')
                    .append(nvl(item.getType())).append('\n');
        }
        return sb.toString();
    }

    private String exportExcel(List<BomItem> items) {
        // Заглушка: реальная реализация — через Apache POI.
        // Возвращаем CSV-совместимый текст, помеченный как Excel.
        log.warn("exportExcel: используется заглушка, реальная реализация — Apache POI");
        return exportCsv(items);
    }

    private String exportPdf(BomTree tree) {
        // Заглушка: реальная реализация — через iText / OpenPDF.
        log.warn("exportPdf: используется заглушка, реальная реализация — iText/OpenPDF");
        return "BOM: " + tree.getDesignation() + " (" + tree.getTotalPositions() + " позиций)";
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    // ==================================================================
    // Вспомогательные типы
    // ==================================================================

    /**
     * Результат извлечения BOM из CAD.
     */
    public static class BomExtractionResult {
        private final String rootDesignation;
        private final String rootName;
        private final List<BomItemDto> items;
        private final List<AssemblyConnectionDto> connections;

        public BomExtractionResult(String rootDesignation, String rootName,
                                   List<BomItemDto> items,
                                   List<AssemblyConnectionDto> connections) {
            this.rootDesignation = rootDesignation;
            this.rootName = rootName;
            this.items = items == null ? List.of() : items;
            this.connections = connections == null ? List.of() : connections;
        }

        public String getRootDesignation() { return rootDesignation; }
        public String getRootName() { return rootName; }
        public List<BomItemDto> getItems() { return items; }
        public List<AssemblyConnectionDto> getConnections() { return connections; }
    }
}