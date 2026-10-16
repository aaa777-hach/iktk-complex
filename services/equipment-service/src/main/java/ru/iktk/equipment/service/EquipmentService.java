package ru.iktk.equipment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.iktk.equipment.dto.EquipmentDto;
import ru.iktk.equipment.dto.EquipmentImportResult;
import ru.iktk.equipment.dto.EquipmentStatusDto;
import ru.iktk.equipment.dto.GapAnalysisRequest;
import ru.iktk.equipment.dto.GapAnalysisResponse;
import ru.iktk.equipment.dto.MatchingRequest;
import ru.iktk.equipment.dto.MatchingResponse;
import ru.iktk.equipment.exception.EquipmentException;
import ru.iktk.equipment.mapper.EquipmentMapper;
import ru.iktk.equipment.model.Equipment;
import ru.iktk.equipment.model.EquipmentGapAnalysis;
import ru.iktk.equipment.model.EquipmentStatus;
import ru.iktk.equipment.repository.EquipmentGapAnalysisRepository;
import ru.iktk.equipment.repository.EquipmentRepository;
import ru.iktk.equipment.repository.EquipmentStatusRepository;
import ru.iktk.shared.versioning.VersioningService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Основной сервис реестра оборудования ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>CRUD по реестру оборудования предприятия;</li>
 *   <li>состояние и загрузку станков;</li>
 *   <li>массовый импорт из CSV/XLSX;</li>
 *   <li>сопоставление требований ТП с парком (matching);</li>
 *   <li>gap-анализ: выявление дефицита оборудования;</li>
 *   <li>проверку совместимости с оснасткой;</li>
 *   <li>версионирование изменений оборудования.</li>
 * </ul>
 *
 * <p>Сервис вызывается оркестратором на этапе {@code EQUIPMENT_MATCHING}
 * и используется {@code recommendation-service} для формирования
 * рекомендаций при дефиците.
 */
@Service
public class EquipmentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentService.class);

    private final EquipmentRepository equipmentRepository;
    private final EquipmentStatusRepository statusRepository;
    private final EquipmentGapAnalysisRepository gapRepository;
    private final EquipmentMapper mapper;
    private final EquipmentImportService importService;
    private final EquipmentMatchingService matchingService;
    private final VersioningService versioningService;

    public EquipmentService(EquipmentRepository equipmentRepository,
                            EquipmentStatusRepository statusRepository,
                            EquipmentGapAnalysisRepository gapRepository,
                            EquipmentMapper mapper,
                            EquipmentImportService importService,
                            EquipmentMatchingService matchingService,
                            VersioningService versioningService) {
        this.equipmentRepository = equipmentRepository;
        this.statusRepository = statusRepository;
        this.gapRepository = gapRepository;
        this.mapper = mapper;
        this.importService = importService;
        this.matchingService = matchingService;
        this.versioningService = versioningService;
    }

    // ==================================================================
    // 1. CRUD — реестр оборудования
    // ==================================================================

    /**
     * Постраничный список оборудования с фильтрами.
     */
    @Transactional(readOnly = true)
    public Page<EquipmentDto> list(String type,
                                   String status,
                                   String enterpriseCode,
                                   String search,
                                   Pageable pageable) {
        log.debug("list: type={}, status={}, enterprise={}, search={}",
                type, status, enterpriseCode, search);

        return equipmentRepository.search(type, status, enterpriseCode, search, pageable)
                .map(mapper::toDto);
    }

    /**
     * Получить оборудование по ID.
     */
    @Transactional(readOnly = true)
    public EquipmentDto getById(String equipmentId) {
        Equipment equipment = findEquipment(equipmentId);
        return mapper.toDto(equipment);
    }

    /**
     * Создать оборудование.
     */
    @Transactional
    public EquipmentDto create(EquipmentDto dto) {
        validate(dto);

        if (dto.getInventoryNo() != null
                && equipmentRepository.existsByInventoryNo(dto.getInventoryNo())) {
            throw EquipmentException.duplicateInventoryNo(dto.getInventoryNo());
        }

        Equipment equipment = mapper.toEntity(dto);
        equipment.setId(UUID.randomUUID().toString());
        equipment.setCreatedAt(Instant.now());
        equipment.setUpdatedAt(Instant.now());

        Equipment saved = equipmentRepository.save(equipment);

        versioningService.createVersion(
                "EQUIPMENT",
                UUID.fromString(saved.getId()),
                null,
                mapper.toJsonNode(saved),
                VersioningService.ChangeType.CREATE,
                "Создано оборудование",
                "system");

        log.info("create: equipmentId={}, name={}, inventoryNo={}",
                saved.getId(), saved.getName(), saved.getInventoryNo());

        return mapper.toDto(saved);
    }

    /**
     * Обновить оборудование.
     */
    @Transactional
    public EquipmentDto update(String equipmentId, EquipmentDto dto) {
        validate(dto);

        Equipment equipment = findEquipment(equipmentId);

        if (dto.getInventoryNo() != null
                && !dto.getInventoryNo().equals(equipment.getInventoryNo())
                && equipmentRepository.existsByInventoryNo(dto.getInventoryNo())) {
            throw EquipmentException.duplicateInventoryNo(dto.getInventoryNo());
        }

        mapper.updateEntity(equipment, dto);
        equipment.setUpdatedAt(Instant.now());

        Equipment saved = equipmentRepository.save(equipment);

        versioningService.createVersion(
                "EQUIPMENT",
                UUID.fromString(saved.getId()),
                null,
                mapper.toJsonNode(saved),
                VersioningService.ChangeType.UPDATE,
                "Обновлено оборудование",
                "system");

        log.info("update: equipmentId={}", equipmentId);

        return mapper.toDto(saved);
    }

    /**
     * Удалить (архивировать) оборудование.
     */
    @Transactional
    public void delete(String equipmentId) {
        Equipment equipment = findEquipment(equipmentId);
        equipment.setStatus("DECOMMISSIONED");
        equipment.setUpdatedAt(Instant.now());
        equipmentRepository.save(equipment);

        versioningService.createVersion(
                "EQUIPMENT",
                UUID.fromString(equipment.getId()),
                null,
                mapper.toJsonNode(equipment),
                VersioningService.ChangeType.ARCHIVE,
                "Оборудование списано",
                "system");

        log.warn("delete (archive): equipmentId={}", equipmentId);
    }

    // ==================================================================
    // 2. Состояние и загрузка
    // ==================================================================

    /**
     * Получить состояние и загрузку оборудования.
     */
    @Transactional(readOnly = true)
    public EquipmentStatusDto getStatus(String equipmentId) {
        findEquipment(equipmentId);

        return statusRepository.findByEquipmentId(equipmentId)
                .map(mapper::toStatusDto)
                .orElseGet(() -> EquipmentStatusDto.empty(equipmentId));
    }

    /**
     * Обновить состояние и загрузку.
     */
    @Transactional
    public EquipmentStatusDto updateStatus(String equipmentId, EquipmentStatusDto dto) {
        findEquipment(equipmentId);

        EquipmentStatus status = statusRepository.findByEquipmentId(equipmentId)
                .orElseGet(() -> {
                    EquipmentStatus s = new EquipmentStatus();
                    s.setId(UUID.randomUUID().toString());
                    s.setEquipmentId(equipmentId);
                    return s;
                });

        status.setState(dto.getState());
        status.setLoadPercent(dto.getLoadPercent());
        status.setSchedule(dto.getSchedule());
        status.setUpdatedAt(Instant.now());

        EquipmentStatus saved = statusRepository.save(status);
        log.info("updateStatus: equipmentId={}, state={}, load={}%",
                equipmentId, dto.getState(), dto.getLoadPercent());

        return mapper.toStatusDto(saved);
    }

    // ==================================================================
    // 3. Массовый импорт
    // ==================================================================

    /**
     * Массовый импорт оборудования из файла.
     */
    @Transactional
    public EquipmentImportResult importFromFile(MultipartFile file,
                                                boolean updateExisting,
                                                boolean dryRun) {
        log.info("importFromFile: filename={}, size={}, updateExisting={}, dryRun={}",
                file.getOriginalFilename(), file.getSize(), updateExisting, dryRun);

        return importService.importFromFile(file, updateExisting, dryRun);
    }

    // ==================================================================
    // 4. Сопоставление требований с парком (matching)
    // ==================================================================

    /**
     * Сопоставить требования ТП с парком оборудования.
     */
    @Transactional(readOnly = true)
    public MatchingResponse match(MatchingRequest request) {
        if (request == null || request.getProjectId() == null) {
            throw EquipmentException.validation("Не указан projectId");
        }

        log.info("match: projectId={}, requirements={}",
                request.getProjectId(),
                request.getRequirements() == null ? 0 : request.getRequirements().size());

        return matchingService.match(request);
    }

    // ==================================================================
    // 5. Gap-анализ
    // ==================================================================

    /**
     * Запустить gap-анализ по проекту.
     */
    @Transactional
    public GapAnalysisResponse runGapAnalysis(GapAnalysisRequest request) {
        if (request == null || request.getProjectId() == null) {
            throw EquipmentException.validation("Не указан projectId");
        }

        log.info("runGapAnalysis: projectId={}, strategy={}",
                request.getProjectId(), request.getStrategy());

        String analysisId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        EquipmentGapAnalysis analysis = EquipmentGapAnalysis.builder()
                .id(analysisId)
                .projectId(request.getProjectId())
                .strategy(request.getStrategy())
                .status("RUNNING")
                .startedAt(startedAt)
                .build();

        gapRepository.save(analysis);

        try {
            MatchingRequest matchingRequest = MatchingRequest.builder()
                    .projectId(request.getProjectId())
                    .requirements(request.getRequirements())
                    .strategy(request.getStrategy())
                    .build();

            MatchingResponse matching = matchingService.match(matchingRequest);

            analysis.setStatus("COMPLETED");
            analysis.setGapsCount(matching.getGaps() == null ? 0 : matching.getGaps().size());
            analysis.setRecommendationsCount(
                    matching.getGaps() == null ? 0 : matching.getGaps().size());
            analysis.setCompletedAt(Instant.now());
            analysis.setGaps(matching.getGaps());

            gapRepository.save(analysis);

            log.info("runGapAnalysis done: analysisId={}, gaps={}",
                    analysisId, analysis.getGapsCount());

        } catch (Exception e) {
            analysis.setStatus("FAILED");
            analysis.setError(e.getMessage());
            analysis.setCompletedAt(Instant.now());
            gapRepository.save(analysis);
            log.error("runGapAnalysis failed: analysisId={}", analysisId, e);
            throw new EquipmentException(
                    "GAP_ANALYSIS_FAILED",
                    "Ошибка gap-анализа: " + e.getMessage(),
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("analysisId", analysisId),
                    e);
        }

        return mapper.toGapResponse(analysis);
    }

    /**
     * Получить результаты gap-анализа по ID.
     */
    @Transactional(readOnly = true)
    public GapAnalysisResponse getGapAnalysis(String analysisId) {
        EquipmentGapAnalysis analysis = gapRepository.findById(analysisId)
                .orElseThrow(() -> EquipmentException.notFound("Анализ не найден: " + analysisId));
        return mapper.toGapResponse(analysis);
    }

    /**
     * Получить последний gap-анализ по проекту.
     */
    @Transactional(readOnly = true)
    public GapAnalysisResponse getLatestGapAnalysis(String projectId) {
        EquipmentGapAnalysis analysis = gapRepository
                .findTopByProjectIdOrderByStartedAtDesc(projectId)
                .orElseThrow(() -> EquipmentException.notFound(
                        "Gap-анализ для проекта не найден: " + projectId));
        return mapper.toGapResponse(analysis);
    }

    // ==================================================================
    // 6. Совместимость с оснасткой
    // ==================================================================

    /**
     * Получить список оснастки, совместимой с оборудованием.
     */
    @Transactional(readOnly = true)
    public List<String> getCompatibleTooling(String equipmentId) {
        findEquipment(equipmentId);
        return equipmentRepository.findCompatibleToolingIds(equipmentId);
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    /**
     * Найти оборудование или выбросить исключение.
     */
    private Equipment findEquipment(String equipmentId) {
        if (equipmentId == null || equipmentId.isBlank()) {
            throw EquipmentException.validation("Не указан equipmentId");
        }
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> EquipmentException.notFound(
                        "Оборудование не найдено: " + equipmentId));
    }

    /**
     * Валидация DTO оборудования.
     */
    private void validate(EquipmentDto dto) {
        if (dto == null) {
            throw EquipmentException.validation("Пустой запрос");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw EquipmentException.validation("Не указано наименование");
        }
        if (dto.getType() == null || dto.getType().isBlank()) {
            throw EquipmentException.validation("Не указан тип оборудования");
        }
    }

    // ==================================================================
    // Дополнительные сценарии
    // ==================================================================

    /**
     * Найти доступное оборудование по требованиям операции.
     *
     * <p>Используется оркестратором при передаче команды на
     * сопоставление (через Feign-клиент).
     */
    @Transactional(readOnly = true)
    public List<EquipmentDto> findAvailable(String projectId,
                                            String operationType,
                                            boolean cncRequired,
                                            Map<String, Object> requirements) {
        log.debug("findAvailable: projectId={}, operationType={}, cnc={}",
                projectId, operationType, cncRequired);

        List<Equipment> candidates = equipmentRepository.findAvailableByOperation(
                operationType, cncRequired);

        return candidates.stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Проверить доступность метода обработки на парке предприятия.
     *
     * <p>Используется модулями {@code casting}, {@code forging},
     * {@code machining} при выборе способа обработки.
     */
    @Transactional(readOnly = true)
    public boolean isMethodAvailable(String projectId, String method) {
        if (method == null || method.isBlank()) {
            return false;
        }

        boolean available = equipmentRepository.existsByCapabilityOperation(method);
        log.debug("isMethodAvailable: projectId={}, method={}, available={}",
                projectId, method, available);
        return available;
    }

    /**
     * Получить оборудование по ID (для Feign-клиента оркестратора).
     */
    @Transactional(readOnly = true)
    public EquipmentDto getEquipmentForClient(String equipmentId) {
        try {
            return getById(equipmentId);
        } catch (EquipmentException e) {
            log.debug("getEquipmentForClient: не найдено, equipmentId={}", equipmentId);
            return null;
        }
    }

    /**
     * Сводка по парку оборудования.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        long total = equipmentRepository.count();
        long available = equipmentRepository.countByStatus("AVAILABLE");
        long busy = equipmentRepository.countByStatus("BUSY");
        long maintenance = equipmentRepository.countByStatus("MAINTENANCE");

        List<Object[]> byType = equipmentRepository.countByTypeGrouped();

        List<Map<String, Object>> types = new ArrayList<>();
        for (Object[] row : byType) {
            types.add(Map.of(
                    "type", row[0],
                    "count", row[1]
            ));
        }

        return Map.of(
                "total", total,
                "available", available,
                "busy", busy,
                "maintenance", maintenance,
                "byType", types
        );
    }
}