package ru.iktk.bom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.bom.client.IntegrationClient;
import ru.iktk.bom.config.BomProperties;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.exception.BomException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Сервис извлечения BOM (Bill of Materials) из CAD-систем ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>выбор CAD-адаптера по системе-источнику
 *       (Siemens, Dassault, T-FLEX, КОМПАС);</li>
 *   <li>запрос структуры изделия (Engineering BOM) через
 *       {@link IntegrationClient};</li>
 *   <li>запрос связей и сопряжений сборки;</li>
 *   <li>нормализацию структуры: позиции, количества, обозначения,
 *       материалы, иерархия;</li>
 *   <li>валидацию данных (циклы, дубликаты, отсутствующие поля);</li>
 *   <li>формирование результата для {@link BomService}.</li>
 * </ul>
 *
 * <p><b>Порядок работы.</b>
 * <ol>
 *   <li>Определение CAD-системы (явно в запросе или по геометрии).</li>
 *   <li>Извлечение BOM через {@link IntegrationClient}.</li>
 *   <li>Извлечение связей и сопряжений.</li>
 *   <li>Нормализация: иерархия, типы, материалы, единицы.</li>
 *   <li>Валидация: циклы, дубликаты, глубина.</li>
 *   <li>Возврат {@link BomService.BomExtractionResult}.</li>
 * </ol>
 *
 * <p><b>Обработка ошибок.</b> При недоступности CAD — retry
 * (согласно {@link BomProperties}); при исчерпании — {@link BomException}.
 */
@Service
public class BomExtractionService {

    private static final Logger log = LoggerFactory.getLogger(BomExtractionService.class);

    /** Поддерживаемые CAD-системы. */
    private static final Set<String> SUPPORTED_CADS = Set.of(
            "SIEMENS", "DASSAULT", "T_FLEX", "KOMPAS", "STEP", "JT", "PARASOLID"
    );

    /** Типы позиций BOM. */
    private static final String TYPE_PART = "PART";
    private static final String TYPE_ASSEMBLY = "ASSEMBLY";
    private static final String TYPE_STANDARD = "STANDARD";
    private static final String TYPE_MATERIAL = "MATERIAL";
    private static final String TYPE_FASTENER = "FASTENER";

    private final IntegrationClient integrationClient;
    private final BomProperties properties;

    public BomExtractionService(IntegrationClient integrationClient,
                                BomProperties properties) {
        this.integrationClient = integrationClient;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Основной сценарий: извлечение BOM
    // ==================================================================

    /**
     * Извлечь структуру изделия из CAD.
     *
     * @param projectId  идентификатор проекта
     * @param geometryId идентификатор геометрии (или {@code null})
     * @param cadSystem  CAD-система (или {@code null} — автоопределение)
     * @return результат извлечения BOM
     */
    @Transactional(readOnly = true)
    public BomService.BomExtractionResult extract(String projectId,
                                                  String geometryId,
                                                  String cadSystem) {
        if (projectId == null || projectId.isBlank()) {
            throw BomException.validation("Не указан projectId");
        }

        String cad = resolveCadSystem(cadSystem, projectId, geometryId);
        log.info("extract: projectId={}, geometryId={}, cadSystem={}",
                projectId, geometryId, cad);

        Instant startedAt = Instant.now();

        try {
            // 1. Извлечение BOM
            RawBom raw = extractRawBom(projectId, geometryId, cad);
            if (raw == null || raw.getItems() == null || raw.getItems().isEmpty()) {
                throw BomException.emptyStructure(projectId);
            }

            // 2. Извлечение связей
            List<RawConnection> rawConnections = extractRawConnections(
                    projectId, geometryId, cad);

            // 3. Нормализация
            List<BomItemDto> items = normalizeItems(raw.getItems(), raw.getRootId());
            List<AssemblyConnectionDto> connections = normalizeConnections(
                    rawConnections, raw.getRootId());

            // 4. Валидация
            validate(items, connections);

            Duration duration = Duration.between(startedAt, Instant.now());
            log.info("extract done: projectId={}, root={}, items={}, connections={}, duration={}ms",
                    projectId, raw.getRootId(), items.size(), connections.size(),
                    duration.toMillis());

            return new BomService.BomExtractionResult(
                    raw.getRootDesignation(),
                    raw.getRootName(),
                    items,
                    connections);

        } catch (BomException e) {
            throw e;
        } catch (Exception e) {
            log.error("extract failed: projectId={}, cadSystem={}", projectId, cad, e);
            throw BomException.extractionFailed(projectId, cad, e);
        }
    }

    // ==================================================================
    // 2. Определение CAD-системы
    // ==================================================================

    /**
     * Определить CAD-систему.
     *
     * <p>Приоритет:
     * <ol>
     *   <li>явно указана в запросе;</li>
     *   <li>по геометрии проекта (через {@code integration});</li>
     *   <li>по умолчанию — {@code STEP}.</li>
     * </ol>
     */
    private String resolveCadSystem(String requested,
                                    String projectId,
                                    String geometryId) {
        if (requested != null && !requested.isBlank()) {
            String upper = requested.toUpperCase();
            if (!SUPPORTED_CADS.contains(upper)) {
                throw BomException.validation(
                        "Неподдерживаемая CAD-система: " + requested);
            }
            return upper;
        }

        try {
            String detected = integrationClient.detectCadSystem(projectId, geometryId);
            if (detected != null && SUPPORTED_CADS.contains(detected.toUpperCase())) {
                log.debug("resolveCadSystem: определена система {} по геометрии", detected);
                return detected.toUpperCase();
            }
        } catch (Exception e) {
            log.warn("resolveCadSystem: не удалось определить CAD, используется STEP: {}",
                    e.getMessage());
        }

        return "STEP";
    }

    // ==================================================================
    // 3. Извлечение raw-данных через IntegrationClient
    // ==================================================================

    /**
     * Извлечь «сырую» структуру BOM через {@link IntegrationClient}.
     */
    private RawBom extractRawBom(String projectId, String geometryId, String cadSystem) {
        // 1. Сначала пробуем /bom/step (многоуровневый STEP-парсер, сессия 28)
        if (geometryId != null && !geometryId.isBlank()) {
            Optional<RawBom> stepBom = tryFetchStepBom(projectId, geometryId);
            if (stepBom.isPresent()) {
                return stepBom.get();
            }
        }
        // 2. Fallback: старый /bom (DB-only)
        int maxAttempts = properties.getExtractionRetry() > 0
                ? properties.getExtractionRetry() : 3;

        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RawBom raw = integrationClient.extractBom(
                        projectId, geometryId, cadSystem);
                return raw;
            } catch (Exception e) {
                lastError = e;
                log.warn("extractRawBom: попытка {} из {} не удалась: {}",
                        attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(properties.getExtractionRetryDelayMs());
                }
            }
        }

        throw BomException.extractionFailed(projectId, cadSystem, lastError);
    }

    /**
     * Извлечь «сырые» связи сборки через {@link IntegrationClient}.
     */
    private List<RawConnection> extractRawConnections(String projectId,
                                                      String geometryId,
                                                      String cadSystem) {
        try {
            List<RawConnection> connections = integrationClient.extractConnections(
                    projectId, geometryId, cadSystem);
            return connections == null ? List.of() : connections;
        } catch (Exception e) {
            log.warn("extractRawConnections: связи недоступны: {}", e.getMessage());
            return List.of();
        }
    }


    /**
     * Попытка получить многоуровневый BOM из реального STEP-файла (сессия 28).
     * Пустой / недоступный STEP -> Optional.empty(), fallback на /bom.
     */
    private Optional<RawBom> tryFetchStepBom(String projectId, String objectRef) {
        try {
            RawBom bom = integrationClient.fetchStepBom(projectId, objectRef);
            if (bom == null || bom.getItems() == null || bom.getItems().isEmpty()) {
                log.info("tryFetchStepBom: /bom/step пуст, objectRef={}, fallback на /bom", objectRef);
                return Optional.empty();
            }
            log.info("tryFetchStepBom: /bom/step OK, objectRef={}, root={}, items={}",
                    objectRef, bom.getRootId(), bom.getItems().size());
            return Optional.of(bom);
        } catch (feign.FeignException.NotFound | feign.FeignException.BadRequest e) {
            log.info("tryFetchStepBom: /bom/step {} для objectRef={}, fallback на /bom",
                    e.status(), objectRef);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("tryFetchStepBom: /bom/step ошибка для objectRef={}, fallback на /bom: {}",
                    objectRef, e.getMessage());
            return Optional.empty();
        }
    }
    // ==================================================================
    // 4. Нормализация
    // ==================================================================

    /**
     * Нормализовать позиции BOM: иерархия, типы, единицы, материалы.
     */
    private List<BomItemDto> normalizeItems(List<RawItem> rawItems, String rootId) {
        Map<String, BomItemDto> dtoById = new LinkedHashMap<>();
        int order = 0;

        for (RawItem raw : rawItems) {
            BomItemDto dto = BomItemDto.builder()
                    .id(raw.getId() == null ? generateId() : raw.getId())
                    .position(raw.getPosition())
                    .designation(raw.getDesignation())
                    .name(raw.getName())
                    .quantity(raw.getQuantity() == null ? 1 : raw.getQuantity())
                    .unit(raw.getUnit() == null ? "шт" : raw.getUnit())
                    .material(raw.getMaterial())
                    .type(normalizeType(raw.getType(), raw))
                    .parentId(raw.getParentId())
                    .order(order++)
                    .build();
            dtoById.put(dto.getId(), dto);
        }

        // Убираем ссылки на несуществующих родителей
        Set<String> validIds = dtoById.keySet();
        for (BomItemDto dto : dtoById.values()) {
            if (dto.getParentId() != null
                    && !validIds.contains(dto.getParentId())
                    && !dto.getParentId().equals(rootId)) {
                dto.setParentId(null);
            }
        }

        return new ArrayList<>(dtoById.values());
    }

    /**
     * Нормализовать связи сборки.
     */
    private List<AssemblyConnectionDto> normalizeConnections(List<RawConnection> rawConnections,
                                                              String rootId) {
        List<AssemblyConnectionDto> result = new ArrayList<>();

        for (RawConnection raw : rawConnections) {
            if (raw.getPart1Id() == null || raw.getPart2Id() == null) {
                continue;
            }
            result.add(AssemblyConnectionDto.builder()
                    .id(raw.getId() == null ? generateId() : raw.getId())
                    .type(raw.getType())
                    .part1Id(raw.getPart1Id())
                    .part2Id(raw.getPart2Id())
                    .parameters(raw.getParameters())
                    .build());
        }

        return result;
    }

    /**
     * Нормализовать тип позиции.
     */
    private String normalizeType(String rawType, RawItem raw) {
        if (rawType != null) {
            String upper = rawType.toUpperCase();
            if (Set.of(TYPE_PART, TYPE_ASSEMBLY, TYPE_STANDARD,
                    TYPE_MATERIAL, TYPE_FASTENER).contains(upper)) {
                return upper;
            }
        }

        // Эвристика
        if (raw.hasChildren()) {
            return TYPE_ASSEMBLY;
        }
        if (raw.getDesignation() != null && raw.getDesignation().matches("^\\d{2}\\.\\d{2}\\.\\d{3}$")) {
            return TYPE_STANDARD;
        }
        if (raw.getMaterial() != null && raw.getName() != null
                && raw.getName().toLowerCase().contains("материал")) {
            return TYPE_MATERIAL;
        }
        if (raw.getDesignation() != null
                && (raw.getDesignation().toLowerCase().contains("болт")
                    || raw.getDesignation().toLowerCase().contains("винт")
                    || raw.getDesignation().toLowerCase().contains("гайка"))) {
            return TYPE_FASTENER;
        }
        return TYPE_PART;
    }

    // ==================================================================
    // 5. Валидация
    // ==================================================================

    /**
     * Проверить структуру BOM.
     *
     * <p>Проверки:
     * <ul>
     *   <li>нет циклов в иерархии;</li>
     *   <li>глубина не превышает {@link BomProperties#getMaxDepth()};</li>
     *   <li>нет дубликатов обозначений с разными позициями;</li>
     *   <li>количества положительные.</li>
     * </ul>
     */
    private void validate(List<BomItemDto> items, List<AssemblyConnectionDto> connections) {
        Map<String, BomItemDto> byId = new LinkedHashMap<>();
        for (BomItemDto item : items) {
            byId.put(item.getId(), item);
        }

        // Циклы
        for (BomItemDto item : items) {
            if (hasCycle(item, byId, new HashSet<>())) {
                throw BomException.validation(
                        "Обнаружен цикл в иерархии BOM: " + item.getDesignation());
            }
        }

        // Глубина
        int maxDepth = properties.getMaxDepth() > 0
                ? properties.getMaxDepth() : 20;
        for (BomItemDto item : items) {
            int depth = calculateDepth(item, byId);
            if (depth > maxDepth) {
                throw BomException.validation(
                        "Глубина BOM превышает " + maxDepth + ": " + item.getDesignation());
            }
        }

        // Количества
        for (BomItemDto item : items) {
            if (item.getQuantity() <= 0) {
                throw BomException.validation(
                        "Некорректное количество для позиции " + item.getDesignation());
            }
        }

        // Дубликаты обозначений (мягкая проверка — предупреждение)
        Map<String, Integer> designationCount = new LinkedHashMap<>();
        for (BomItemDto item : items) {
            if (item.getDesignation() != null) {
                designationCount.merge(item.getDesignation(), 1, Integer::sum);
            }
        }
        designationCount.forEach((designation, count) -> {
            if (count > 1) {
                log.debug("validate: обозначение {} встречается {} раз",
                        designation, count);
            }
        });
    }

    /**
     * Проверка на цикл в иерархии.
     */
    private boolean hasCycle(BomItemDto item,
                             Map<String, BomItemDto> byId,
                             Set<String> visited) {
        if (!visited.add(item.getId())) {
            return true;
        }
        if (item.getParentId() != null) {
            BomItemDto parent = byId.get(item.getParentId());
            if (parent != null && hasCycle(parent, byId, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Глубина позиции в иерархии.
     */
    private int calculateDepth(BomItemDto item, Map<String, BomItemDto> byId) {
        int depth = 0;
        Set<String> visited = new HashSet<>();
        BomItemDto current = item;

        while (current.getParentId() != null && visited.add(current.getId())) {
            current = byId.get(current.getParentId());
            if (current == null) {
                break;
            }
            depth++;
        }
        return depth;
    }

    // ==================================================================
    // 6. Утилиты
    // ==================================================================

    private String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================================================================
    // Вспомогательные типы (внутренние, для IntegrationClient)
    // ==================================================================

    /**
     * «Сырая» структура BOM, полученная от CAD-адаптера.
     */
    public static class RawBom {
        private final String rootId;
        private final String rootDesignation;
        private final String rootName;
        private final List<RawItem> items;

        public RawBom(String rootId, String rootDesignation, String rootName,
                      List<RawItem> items) {
            this.rootId = rootId;
            this.rootDesignation = rootDesignation;
            this.rootName = rootName;
            this.items = items == null ? List.of() : items;
        }

        public String getRootId() { return rootId; }
        public String getRootDesignation() { return rootDesignation; }
        public String getRootName() { return rootName; }
        public List<RawItem> getItems() { return items; }
    }

    /**
     * «Сырая» позиция BOM.
     */
    public static class RawItem {
        private String id;
        private String parentId;
        private String position;
        private String designation;
        private String name;
        private Integer quantity;
        private String unit;
        private String material;
        private String type;
        private boolean hasChildren;

        public RawItem() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }

        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }

        public String getDesignation() { return designation; }
        public void setDesignation(String designation) { this.designation = designation; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean hasChildren() { return hasChildren; }
        public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }
    }

    /**
     * «Сырая» связь сборки.
     */
    public static class RawConnection {
        private String id;
        private String type;
        private String part1Id;
        private String part2Id;
        private Map<String, Object> parameters;

        public RawConnection() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getPart1Id() { return part1Id; }
        public void setPart1Id(String part1Id) { this.part1Id = part1Id; }

        public String getPart2Id() { return part2Id; }
        public void setPart2Id(String part2Id) { this.part2Id = part2Id; }

        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
}
