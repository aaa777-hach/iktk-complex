package ru.iktk.bom.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.iktk.bom.dto.AssemblyConnectionDto;
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.dto.BomTree;
import ru.iktk.bom.dto.BomUpdateRequest;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.service.BomService;

import java.util.List;
import java.util.Map;

/**
 * REST API сервиса BOM (Bill of Materials) ИКТК.
 *
 * <p>Предоставляет:
 * <ul>
 *   <li>запуск анализа сборочной единицы (извлечение BOM из CAD);</li>
 *   <li>получение дерева состава изделия по проекту или по ID сборки;</li>
 *   <li>ручную корректировку позиций BOM;</li>
 *   <li>получение связей и сопряжений сборки;</li>
 *   <li>получение спецификации по ГОСТ 2.106;</li>
 *   <li>экспорт BOM в Excel/CSV/PDF.</li>
 * </ul>
 *
 * <p>Базовый путь: {@code /api/v1/bom}.
 *
 * <p>Доступ по ролям:
 * <ul>
 *   <li>чтение — {@code ENGINEER}, {@code MANAGER}, {@code NORMER},
 *       {@code OPERATOR}, {@code ADMIN};</li>
 *   <li>изменение BOM — {@code ENGINEER}, {@code ADMIN};</li>
 *   <li>запуск анализа — {@code ENGINEER}, {@code ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1/bom",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "BOM", description = "Анализ сборочных единиц и состав изделия")
public class BomController {

    private static final Logger log = LoggerFactory.getLogger(BomController.class);

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    // ==================================================================
    // 1. Анализ сборочной единицы
    // ==================================================================

    /**
     * Запустить анализ сборочной единицы по проекту.
     */
    @PostMapping(path = "/projects/{projectId}/analyze",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Анализ сборочной единицы",
            description = "Извлекает структуру изделия (BOM) из CAD-модели")
    public ResponseEntity<BomResponse> analyze(
            @PathVariable String projectId,
            @Valid @RequestBody(required = false) BomAnalyzeRequest request) {

        BomAnalyzeRequest effectiveRequest = request == null
                ? BomAnalyzeRequest.forProject(projectId)
                : request;

        if (effectiveRequest.getProjectId() == null) {
            effectiveRequest.setProjectId(projectId);
        }

        log.info("analyze: projectId={}, geometryId={}, force={}",
                projectId,
                effectiveRequest.getGeometryId(),
                effectiveRequest.isForce());

        BomResponse response = bomService.analyze(effectiveRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Получить статус анализа по ID.
     */
    @GetMapping("/analyses/{analysisId}")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Статус анализа BOM")
    public ResponseEntity<BomResponse> getAnalysis(
            @Parameter(description = "Идентификатор анализа")
            @PathVariable String analysisId) {
        log.debug("getAnalysis: analysisId={}", analysisId);
        return ResponseEntity.ok(bomService.getAnalysis(analysisId));
    }

    // ==================================================================
    // 2. Дерево состава изделия
    // ==================================================================

    /**
     * Получить дерево состава изделия по проекту.
     */
    @GetMapping("/projects/{projectId}")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Дерево состава изделия по проекту")
    public ResponseEntity<BomTree> getByProject(
            @PathVariable String projectId) {
        log.debug("getByProject: projectId={}", projectId);
        return ResponseEntity.ok(bomService.getBomTreeByProject(projectId));
    }

    /**
     * Получить дерево BOM по ID сборки.
     */
    @GetMapping("/{bomId}/tree")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Дерево BOM по ID сборки")
    public ResponseEntity<BomTree> getTree(
            @Parameter(description = "Идентификатор BOM")
            @PathVariable String bomId) {
        log.debug("getTree: bomId={}", bomId);
        return ResponseEntity.ok(bomService.getBomTree(bomId));
    }

    /**
     * Получить уплощённый список позиций BOM.
     */
    @GetMapping("/{bomId}/items")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Уплощённый список позиций BOM")
    public ResponseEntity<List<BomItemDto>> getItems(
            @PathVariable String bomId,
            @Parameter(description = "Фильтр по типу позиции (PART, ASSEMBLY, MATERIAL, ...)")
            @RequestParam(required = false) String type) {
        log.debug("getItems: bomId={}, type={}", bomId, type);
        return ResponseEntity.ok(bomService.getItems(bomId, type));
    }

    // ==================================================================
    // 3. Ручная корректировка BOM
    // ==================================================================

    /**
     * Обновить позиции BOM (ручная корректировка).
     */
    @PutMapping(path = "/{bomId}/items",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Обновить позиции BOM (ручная корректировка)")
    public ResponseEntity<BomTree> updateItems(
            @PathVariable String bomId,
            @Valid @RequestBody BomUpdateRequest request) {
        log.info("updateItems: bomId={}, items={}",
                bomId, request.getItems() == null ? 0 : request.getItems().size());

        return ResponseEntity.ok(bomService.updateItems(bomId, request));
    }

    /**
     * Добавить одну позицию в BOM.
     */
    @PostMapping(path = "/{bomId}/items",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Добавить позицию в BOM")
    public ResponseEntity<BomItemDto> addItem(
            @PathVariable String bomId,
            @RequestParam(required = false) String parentId,
            @Valid @RequestBody BomItemDto item) {
        log.info("addItem: bomId={}, parentId={}, designation={}",
                bomId, parentId, item.getDesignation());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bomService.addItem(bomId, parentId, item));
    }

    /**
     * Удалить позицию BOM.
     */
    @DeleteMapping("/{bomId}/items/{itemId}")
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Удалить позицию BOM")
    public ResponseEntity<Void> deleteItem(
            @PathVariable String bomId,
            @PathVariable String itemId) {
        log.warn("deleteItem: bomId={}, itemId={}", bomId, itemId);
        bomService.deleteItem(bomId, itemId);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // 4. Связи и сопряжения сборки
    // ==================================================================

    /**
     * Получить связи и сопряжения сборки.
     */
    @GetMapping("/{bomId}/connections")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Связи и сопряжения сборки",
            description = "Используется для проектирования ТП сборки")
    public ResponseEntity<List<AssemblyConnectionDto>> getConnections(
            @PathVariable String bomId) {
        log.debug("getConnections: bomId={}", bomId);
        return ResponseEntity.ok(bomService.getConnections(bomId));
    }

    /**
     * Получить последовательность установки деталей.
     */
    @GetMapping("/{bomId}/assembly-sequence")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Последовательность установки деталей",
            description = "Рекомендуемая последовательность сборки по связям и сопряжениям")
    public ResponseEntity<List<BomItemDto>> getAssemblySequence(
            @PathVariable String bomId) {
        log.debug("getAssemblySequence: bomId={}", bomId);
        return ResponseEntity.ok(bomService.getAssemblySequence(bomId));
    }

    // ==================================================================
    // 5. Спецификация
    // ==================================================================

    /**
     * Получить спецификацию по ГОСТ 2.106.
     */
    @GetMapping("/{bomId}/specification")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','ADMIN')")
    @Operation(summary = "Спецификация по ГОСТ 2.106")
    public ResponseEntity<SpecificationDto> getSpecification(
            @PathVariable String bomId) {
        log.debug("getSpecification: bomId={}", bomId);
        return ResponseEntity.ok(bomService.getSpecification(bomId));
    }

    // ==================================================================
    // 6. Экспорт
    // ==================================================================

    /**
     * Экспортировать BOM в указанный формат.
     */
    @PostMapping(path = "/{bomId}/export",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Экспорт BOM",
            description = "Поддерживаемые форматы: EXCEL, CSV, PDF")
    public ResponseEntity<Map<String, Object>> export(
            @PathVariable String bomId,
            @RequestParam(defaultValue = "EXCEL") String format) {
        log.info("export: bomId={}, format={}", bomId, format);

        String fileUri = bomService.export(bomId, format);

        return ResponseEntity.ok(Map.of(
                "bomId", bomId,
                "format", format,
                "fileUri", fileUri,
                "message", "Экспорт завершён"
        ));
    }

    // ==================================================================
    // 7. Сводки и служебные
    // ==================================================================

    /**
     * Сводка по BOM: количество позиций, уровни, типы.
     */
    @GetMapping("/{bomId}/summary")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Сводка по BOM")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable String bomId) {
        log.debug("getSummary: bomId={}", bomId);
        return ResponseEntity.ok(bomService.getSummary(bomId));
    }

    /**
     * Проверка доступности сервиса.
     */
    @GetMapping("/health")
    @Operation(summary = "Проверка доступности сервиса")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "bom-service",
                "timestamp", java.time.Instant.now().toString()));
    }

    // ==================================================================
    // 8. Обработка ошибок
    // ==================================================================

    /**
     * Обработчик ошибок сервиса BOM.
     */
    @ExceptionHandler(BomException.class)
    public ResponseEntity<Map<String, Object>> handleBomException(
            BomException ex) {
        log.error("BomException: code={}, message={}",
                ex.getCode(), ex.getMessage(), ex);

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(Map.of(
                        "error", "BOM_ERROR",
                        "code", ex.getCode(),
                        "message", ex.getMessage(),
                        "context", ex.getContext(),
                        "timestamp", java.time.Instant.now().toString()
                ));
    }
}