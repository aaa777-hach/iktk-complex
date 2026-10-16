package ru.iktk.equipment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.iktk.equipment.dto.EquipmentDto;
import ru.iktk.equipment.dto.EquipmentCapabilityDto;
import ru.iktk.equipment.dto.EquipmentImportResult;
import ru.iktk.equipment.dto.EquipmentStatusDto;
import ru.iktk.equipment.dto.GapAnalysisRequest;
import ru.iktk.equipment.dto.GapAnalysisResponse;
import ru.iktk.equipment.dto.MatchingRequest;
import ru.iktk.equipment.dto.MatchingResponse;
import ru.iktk.equipment.exception.EquipmentException;
import ru.iktk.equipment.service.EquipmentCapabilityService;
import ru.iktk.equipment.service.EquipmentService;

import java.util.List;
import java.util.Map;

/**
 * REST API сервиса оборудования ИКТК.
 *
 * <p>Предоставляет:
 * <ul>
 *   <li>CRUD по оборудованию (реестр предприятия);</li>
 *   <li>управление возможностями оборудования;</li>
 *   <li>получение состояния и загрузки;</li>
 *   <li>массовый импорт оборудования из файлов;</li>
 *   <li>сопоставление требований ТП с парком (matching);</li>
 *   <li>gap-анализ по проекту;</li>
 *   <li>проверку совместимости с оснасткой.</li>
 * </ul>
 *
 * <p>Базовый путь: {@code /api/v1/equipment}.
 *
 * <p>Доступ по ролям:
 * <ul>
 *   <li>чтение — {@code ENGINEER}, {@code MANAGER}, {@code NORMER},
 *       {@code OPERATOR}, {@code ADMIN};</li>
 *   <li>изменение — {@code ENGINEER}, {@code ADMIN};</li>
 *   <li>массовый импорт — {@code ADMIN};</li>
 *   <li>gap-анализ — {@code ENGINEER}, {@code MANAGER}, {@code ADMIN}.</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1/equipment",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Equipment", description = "Реестр оборудования, возможности, gap-анализ")
public class EquipmentController {

    private static final Logger log = LoggerFactory.getLogger(EquipmentController.class);

    private final EquipmentService equipmentService;
    private final EquipmentCapabilityService capabilityService;

    public EquipmentController(EquipmentService equipmentService,
                               EquipmentCapabilityService capabilityService) {
        this.equipmentService = equipmentService;
        this.capabilityService = capabilityService;
    }

    // ==================================================================
    // 1. CRUD — реестр оборудования
    // ==================================================================

    /**
     * Постраничный список оборудования с фильтрами.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Список оборудования",
            description = "Постраничный список с фильтрами по типу, статусу, предприятию")
    public ResponseEntity<Map<String, Object>> list(
            @Parameter(description = "Тип оборудования")
            @RequestParam(required = false) String type,
            @Parameter(description = "Статус: AVAILABLE, BUSY, MAINTENANCE, DECOMMISSIONED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Код предприятия")
            @RequestParam(required = false) String enterpriseCode,
            @Parameter(description = "Поиск по наименованию или инвентарному номеру")
            @RequestParam(required = false) String search,
            @Parameter(description = "Страница (с 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1–100)")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Сортировка: name, type, status, updatedAt")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Направление: asc / desc")
            @RequestParam(defaultValue = "asc") String sortDir) {

        log.debug("list: type={}, status={}, search={}, page={}, size={}",
                type, status, search, page, size);

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        sortBy));

        Page<EquipmentDto> result = equipmentService.list(
                type, status, enterpriseCode, search, pageable);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()
        ));
    }

    /**
     * Получить оборудование по ID.
     */
    @GetMapping("/{equipmentId}")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Получить оборудование по ID")
    public ResponseEntity<EquipmentDto> get(
            @PathVariable String equipmentId) {
        log.debug("get: equipmentId={}", equipmentId);
        return ResponseEntity.ok(equipmentService.getById(equipmentId));
    }

    /**
     * Создать оборудование.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Добавить оборудование")
    public ResponseEntity<EquipmentDto> create(
            @Valid @RequestBody EquipmentDto dto) {
        log.info("create: name={}, inventoryNo={}",
                dto.getName(), dto.getInventoryNo());

        EquipmentDto created = equipmentService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Обновить оборудование.
     */
    @PutMapping(path = "/{equipmentId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Обновить оборудование")
    public ResponseEntity<EquipmentDto> update(
            @PathVariable String equipmentId,
            @Valid @RequestBody EquipmentDto dto) {
        log.info("update: equipmentId={}", equipmentId);
        return ResponseEntity.ok(equipmentService.update(equipmentId, dto));
    }

    /**
     * Удалить оборудование (архивация).
     */
    @DeleteMapping("/{equipmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Удалить (архивировать) оборудование")
    public ResponseEntity<Void> delete(
            @PathVariable String equipmentId) {
        log.warn("delete: equipmentId={}", equipmentId);
        equipmentService.delete(equipmentId);
        return ResponseEntity.noContent().build();
    }

    // ==================================================================
    // 2. Возможности оборудования
    // ==================================================================

    /**
     * Получить возможности оборудования.
     */
    @GetMapping("/{equipmentId}/capabilities")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Возможности оборудования")
    public ResponseEntity<List<EquipmentCapabilityDto>> getCapabilities(
            @PathVariable String equipmentId) {
        log.debug("getCapabilities: equipmentId={}", equipmentId);
        return ResponseEntity.ok(capabilityService.getByEquipment(equipmentId));
    }

    /**
     * Обновить возможности оборудования.
     */
    @PutMapping(path = "/{equipmentId}/capabilities",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Обновить возможности оборудования")
    public ResponseEntity<List<EquipmentCapabilityDto>> updateCapabilities(
            @PathVariable String equipmentId,
            @Valid @RequestBody List<EquipmentCapabilityDto> capabilities) {
        log.info("updateCapabilities: equipmentId={}, count={}",
                equipmentId, capabilities.size());
        return ResponseEntity.ok(
                capabilityService.replace(equipmentId, capabilities));
    }

    // ==================================================================
    // 3. Состояние и загрузка
    // ==================================================================

    /**
     * Получить текущее состояние и загрузку.
     */
    @GetMapping("/{equipmentId}/status")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','NORMER','OPERATOR','ADMIN')")
    @Operation(summary = "Состояние и загрузка оборудования")
    public ResponseEntity<EquipmentStatusDto> getStatus(
            @PathVariable String equipmentId) {
        log.debug("getStatus: equipmentId={}", equipmentId);
        return ResponseEntity.ok(equipmentService.getStatus(equipmentId));
    }

    /**
     * Обновить состояние и загрузку.
     */
    @PutMapping(path = "/{equipmentId}/status",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")
    @Operation(summary = "Обновить состояние и загрузку")
    public ResponseEntity<EquipmentStatusDto> updateStatus(
            @PathVariable String equipmentId,
            @Valid @RequestBody EquipmentStatusDto status) {
        log.info("updateStatus: equipmentId={}, state={}, load={}%",
                equipmentId, status.getState(), status.getLoadPercent());
        return ResponseEntity.ok(equipmentService.updateStatus(equipmentId, status));
    }

    // ==================================================================
    // 4. Массовый импорт
    // ==================================================================

    /**
     * Массовый импорт оборудования из файла (CSV, XLSX).
     */
    @PostMapping(path = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Массовый импорт оборудования")
    public ResponseEntity<EquipmentImportResult> importFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean updateExisting,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("importFromFile: filename={}, size={}, updateExisting={}, dryRun={}",
                file.getOriginalFilename(), file.getSize(), updateExisting, dryRun);

        EquipmentImportResult result = equipmentService.importFromFile(
                file, updateExisting, dryRun);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    // ==================================================================
    // 5. Сопоставление требований с парком (matching)
    // ==================================================================

    /**
     * Сопоставить требования ТП с парком оборудования.
     */
    @PostMapping(path = "/matching",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Сопоставить требования ТП с оборудованием",
            description = "Возвращает назначенное оборудование или дефицит по каждой операции")
    public ResponseEntity<MatchingResponse> match(
            @Valid @RequestBody MatchingRequest request) {
        log.info("match: projectId={}, operations={}",
                request.getProjectId(),
                request.getRequirements() == null ? 0 : request.getRequirements().size());

        return ResponseEntity.ok(equipmentService.match(request));
    }

    // ==================================================================
    // 6. Gap-анализ
    // ==================================================================

    /**
     * Запустить gap-анализ по проекту.
     */
    @PostMapping(path = "/gap-analysis",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Запустить gap-анализ по проекту",
            description = "Выявляет дефицит оборудования и формирует основу для рекомендаций")
    public ResponseEntity<GapAnalysisResponse> runGapAnalysis(
            @Valid @RequestBody GapAnalysisRequest request) {
        log.info("runGapAnalysis: projectId={}, strategy={}",
                request.getProjectId(), request.getStrategy());

        GapAnalysisResponse response = equipmentService.runGapAnalysis(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Получить результаты gap-анализа по проекту.
     */
    @GetMapping("/gap-analysis/{analysisId}")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Получить результаты gap-анализа")
    public ResponseEntity<GapAnalysisResponse> getGapAnalysis(
            @PathVariable String analysisId) {
        log.debug("getGapAnalysis: analysisId={}", analysisId);
        return ResponseEntity.ok(equipmentService.getGapAnalysis(analysisId));
    }

    /**
     * Получить последний gap-анализ по проекту.
     */
    @GetMapping("/projects/{projectId}/gap-analysis")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Последний gap-анализ по проекту")
    public ResponseEntity<GapAnalysisResponse> getLatestGapAnalysis(
            @PathVariable String projectId) {
        log.debug("getLatestGapAnalysis: projectId={}", projectId);
        return ResponseEntity.ok(equipmentService.getLatestGapAnalysis(projectId));
    }

    // ==================================================================
    // 7. Совместимость с оснасткой
    // ==================================================================

    /**
     * Проверить совместимость оборудования с оснасткой.
     */
    @GetMapping("/{equipmentId}/tooling")
    @PreAuthorize("hasAnyRole('ENGINEER','MANAGER','ADMIN')")
    @Operation(summary = "Совместимая оснастка")
    public ResponseEntity<List<String>> getCompatibleTooling(
            @PathVariable String equipmentId) {
        log.debug("getCompatibleTooling: equipmentId={}", equipmentId);
        return ResponseEntity.ok(equipmentService.getCompatibleTooling(equipmentId));
    }

    // ==================================================================
    // 8. Служебные
    // ==================================================================

    /**
     * Health-check сервиса.
     */
    @GetMapping("/health")
    @Operation(summary = "Проверка доступности сервиса")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "equipment-service",
                "timestamp", java.time.Instant.now().toString()));
    }

    // ==================================================================
    // 9. Обработка ошибок
    // ==================================================================

    /**
     * Обработчик ошибок сервиса оборудования.
     */
    @ExceptionHandler(EquipmentException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentException(
            EquipmentException ex) {
        log.error("EquipmentException: code={}, message={}",
                ex.getCode(), ex.getMessage(), ex);

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(Map.of(
                        "error", "EQUIPMENT_ERROR",
                        "code", ex.getCode(),
                        "message", ex.getMessage(),
                        "context", ex.getContext(),
                        "timestamp", java.time.Instant.now().toString()
                ));
    }
}