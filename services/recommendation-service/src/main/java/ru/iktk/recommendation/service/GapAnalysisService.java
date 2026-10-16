package ru.iktk.recommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.EquipmentRequirement;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRequirementRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
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
 * Сервис gap-анализа ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>сопоставление требований ТП (из спроектированных процессов)
 *       с имеющимся парком оборудования предприятия;</li>
 *   <li>выявление дефицита по критичным параметрам: тип операции,
 *       точность, габариты, материал, мощность, усилие;</li>
 *   <li>классификацию дефицита по степени критичности
 *       (CRITICAL, MAJOR, MINOR);</li>
 *   <li>сохранение результатов gap-анализа в БД;</li>
 *   <li>формирование основы для рекомендаций (через
 *       {@link EquipmentRecommendationService});</li>
 *   <li>предоставление результатов для точки контроля
 *       {@code RECOMMENDATION_REVIEW} оркестратора.</li>
 * </ul>
 *
 * <p><b>Стратегии сопоставления:</b>
 * <ul>
 *   <li>{@code STRICT} — строгое сопоставление по всем параметрам;</li>
 *   <li>{@code FLEXIBLE} — гибкое, допускаются отклонения по
 *       второстепенным параметрам;</li>
 *   <li>{@code HYBRID} — гибридное, приоритет критичным параметрам.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Ключевые таблицы (миграция V6):
 * <ul>
 *   <li>{@code equipment_requirements} — требования к оборудованию
 *       из операций ТП;</li>
 *   <li>{@code equipment_gap_analysis} — результаты gap-анализа.</li>
 * </ul>
 */
@Service
public class GapAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GapAnalysisService.class);

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private static final String SEVERITY_CRITICAL = "CRITICAL";
    private static final String SEVERITY_MAJOR = "MAJOR";
    private static final String SEVERITY_MINOR = "MINOR";

    private final EquipmentGapAnalysisRepository gapRepository;
    private final EquipmentRequirementRepository requirementRepository;
    private final EquipmentRecommendationRepository recommendationRepository;
    private final RecommendationMapper mapper;
    private final EquipmentClient equipmentClient;
    private final EquipmentRecommendationService recommendationService;
    private final VersioningService versioningService;
    private final RecommendationProperties properties;

    public GapAnalysisService(EquipmentGapAnalysisRepository gapRepository,
                              EquipmentRequirementRepository requirementRepository,
                              EquipmentRecommendationRepository recommendationRepository,
                              RecommendationMapper mapper,
                              EquipmentClient equipmentClient,
                              EquipmentRecommendationService recommendationService,
                              VersioningService versioningService,
                              RecommendationProperties properties) {
        this.gapRepository = gapRepository;
        this.requirementRepository = requirementRepository;
        this.recommendationRepository = recommendationRepository;
        this.mapper = mapper;
        this.equipmentClient = equipmentClient;
        this.recommendationService = recommendationService;
        this.versioningService = versioningService;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Запуск gap-анализа
    // ==================================================================

    /**
     * Запустить gap-анализ по проекту.
     *
     * <p>Шаги:
     * <ol>
     *   <li>Загрузка требований к оборудованию из операций ТП.</li>
     *   <li>Для каждого требования — поиск подходящего оборудования
     *       через {@link EquipmentClient}.</li>
     *   <li>Классификация дефицита по критичности.</li>
     *   <li>Сохранение результатов gap-анализа.</li>
     *   <li>Автоматическое формирование рекомендаций
     *       (если включено).</li>
     * </ol>
     */
    @Transactional
    public GapAnalysisResponse run(GapAnalysisRequest request) {
        validate(request);

        String projectId = request.getProjectId();
        String strategy = resolveStrategy(request);

        log.info("run: projectId={}, strategy={}, requirements={}",
                projectId, strategy,
                request.getRequirements() == null ? 0 : request.getRequirements().size());

        String analysisId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        EquipmentGapAnalysis analysis = EquipmentGapAnalysis.builder()
                .id(analysisId)
                .projectId(projectId)
                .strategy(strategy)
                .status(STATUS_RUNNING)
                .startedAt(startedAt)
                .build();

        gapRepository.save(analysis);

        try {
            // 1. Загрузка требований
            List<EquipmentRequirement> requirements =
                    loadRequirements(projectId, request);

            if (requirements.isEmpty()) {
                analysis.setStatus(STATUS_COMPLETED);
                analysis.setGapsCount(0);
                analysis.setRecommendationsCount(0);
                analysis.setCompletedAt(Instant.now());
                gapRepository.save(analysis);

                log.info("run: нет требований для анализа, projectId={}", projectId);
                return mapper.toGapResponse(analysis);
            }

            // 2. Сопоставление каждого требования с парком
            List<EquipmentGapAnalysis.Gap> gaps = new ArrayList<>();

            for (EquipmentRequirement requirement : requirements) {
                EquipmentGapAnalysis.Gap gap = matchRequirement(requirement, strategy);
                if (gap != null) {
                    gaps.add(gap);
                }
            }

            // 3. Обновление записи анализа
            analysis.setGapsCount(gaps.size());
            analysis.setGaps(gaps);
            analysis.setCompletedAt(Instant.now());

            if (gaps.isEmpty()) {
                analysis.setStatus(STATUS_COMPLETED);
                analysis.setRecommendationsCount(0);
                gapRepository.save(analysis);

                log.info("run: дефицита не обнаружено, projectId={}", projectId);
                return mapper.toGapResponse(analysis);
            }

            analysis.setStatus(STATUS_COMPLETED);
            gapRepository.save(analysis);

            // 4. Автоматическое формирование рекомендаций
            List<EquipmentRecommendation> recommendations = new ArrayList<>();
            if (properties.isAutoGenerateRecommendations()) {
                for (EquipmentGapAnalysis.Gap gap : gaps) {
                    try {
                        recommendations.addAll(
                                recommendationService.generateForGap(gap.getId()));
                    } catch (Exception e) {
                        log.warn("run: не удалось сформировать рекомендации для gap={}: {}",
                                gap.getId(), e.getMessage());
                    }
                }
            }

            analysis.setRecommendationsCount(recommendations.size());
            gapRepository.save(analysis);

            versioningService.createVersion(
                    "GAP_ANALYSIS",
                    UUID.fromString(analysisId),
                    UUID.fromString(projectId),
                    mapper.toJsonNode(analysis),
                    VersioningService.ChangeType.CREATE,
                    "Gap-анализ выполнен: " + gaps.size() + " дефицитов",
                    "system");

            log.info("run done: projectId={}, analysisId={}, gaps={}, recommendations={}",
                    projectId, analysisId, gaps.size(), recommendations.size());

            return mapper.toGapResponse(analysis);

        } catch (RecommendationException e) {
            analysis.setStatus(STATUS_FAILED);
            analysis.setError(e.getMessage());
            analysis.setCompletedAt(Instant.now());
            gapRepository.save(analysis);
            throw e;
        } catch (Exception e) {
            analysis.setStatus(STATUS_FAILED);
            analysis.setError(e.getMessage());
            analysis.setCompletedAt(Instant.now());
            gapRepository.save(analysis);
            log.error("run failed: projectId={}", projectId, e);
            throw RecommendationException.gapAnalysisFailed(projectId, e);
        }
    }

    // ==================================================================
    // 2. Сопоставление требования с парком
    // ==================================================================

    /**
     * Сопоставить одно требование с парком оборудования.
     *
     * @return дефицит или {@code null}, если оборудование найдено
     */
    private EquipmentGapAnalysis.Gap matchRequirement(EquipmentRequirement requirement,
                                                       String strategy) {
        log.debug("matchRequirement: operationType={}, strategy={}",
                requirement.getOperationType(), strategy);

        try {
            // 1. Поиск подходящего оборудования
            Optional<Map<String, Object>> matched = equipmentClient.findMatching(
                    requirement.getProjectId(),
                    requirement.getOperationType(),
                    requirement.getRequiredAccuracy(),
                    requirement.getMaxDimensions(),
                    requirement.getMaterial(),
                    requirement.getRequiredPowerKw(),
                    strategy);

            if (matched.isPresent()) {
                // Оборудование найдено
                return null;
            }

            // 2. Дефицит не найден — формируем запись
            String reason = buildGapReason(requirement);
            String severity = classifySeverity(requirement);

            EquipmentGapAnalysis.Gap gap = EquipmentGapAnalysis.Gap.builder()
                    .id(UUID.randomUUID().toString())
                    .analysisId(requirement.getAnalysisId())
                    .projectId(requirement.getProjectId())
                    .operationId(requirement.getOperationId())
                    .operationType(requirement.getOperationType())
                    .stageId(requirement.getStageId())
                    .requiredAccuracy(requirement.getRequiredAccuracy())
                    .maxDimensions(requirement.getMaxDimensions())
                    .material(requirement.getMaterial())
                    .requiredPowerKw(requirement.getRequiredPowerKw())
                    .gapReason(reason)
                    .severity(severity)
                    .strategy(strategy)
                    .createdAt(Instant.now())
                    .build();

            log.debug("matchRequirement: дефицит {}, severity={}, operationType={}",
                    gap.getId(), severity, requirement.getOperationType());

            return gap;

        } catch (Exception e) {
            log.warn("matchRequirement: ошибка сопоставления: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Классифицировать дефицит по критичности.
     *
     * <p>Правила:
     * <ul>
     *   <li>CRITICAL — нет оборудования для ключевой операции
     *       (литьё, штамповка, механообработка, ЧПУ);</li>
     *   <li>MAJOR — нет оборудования для вспомогательной операции
     *       или серьёзное отклонение по параметрам;</li>
     *   <li>MINOR — незначительное отклонение.</li>
     * </ul>
     */
    private String classifySeverity(EquipmentRequirement requirement) {
        String operationType = requirement.getOperationType();
        if (operationType == null) {
            return SEVERITY_MINOR;
        }

        boolean critical = switch (operationType) {
            case "CASTING", "HOT_FORGING", "COLD_FORGING", "SHEET_STAMPING",
                 "MACHINING", "CNC_MACHINING", "ASSEMBLY", "SUBASSEMBLY" -> true;
            default -> false;
        };

        if (critical) {
            return SEVERITY_CRITICAL;
        }

        // Проверка отклонений по параметрам
        if (requirement.getRequiredPowerKw() != null
                && requirement.getRequiredPowerKw().compareTo(BigDecimal.valueOf(100)) > 0) {
            return SEVERITY_MAJOR;
        }

        return SEVERITY_MINOR;
    }

    /**
     * Сформировать текстовое описание дефицита.
     */
    private String buildGapReason(EquipmentRequirement requirement) {
        StringBuilder sb = new StringBuilder();

        sb.append("Нет оборудования для операции ");
        sb.append(requirement.getOperationType() == null
                ? "неизвестного типа"
                : requirement.getOperationType());

        if (requirement.getRequiredAccuracy() != null) {
            sb.append("; точность: ").append(requirement.getRequiredAccuracy());
        }
        if (requirement.getMaxDimensions() != null) {
            sb.append("; габариты: ").append(requirement.getMaxDimensions());
        }
        if (requirement.getMaterial() != null) {
            sb.append("; материал: ").append(requirement.getMaterial());
        }
        if (requirement.getRequiredPowerKw() != null) {
            sb.append("; мощность: ").append(requirement.getRequiredPowerKw()).append(" кВт");
        }

        return sb.toString();
    }

    // ==================================================================
    // 3. Чтение
    // ==================================================================

    /**
     * Получить результаты gap-анализа по ID.
     */
    @Transactional(readOnly = true)
    public GapAnalysisResponse get(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            throw RecommendationException.validation("Не указан analysisId");
        }

        EquipmentGapAnalysis analysis = gapRepository.findById(analysisId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Gap-анализ не найден: " + analysisId));

        return mapper.toGapResponse(analysis);
    }

    /**
     * Получить последний gap-анализ по проекту.
     */
    @Transactional(readOnly = true)
    public GapAnalysisResponse getLatest(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }

        EquipmentGapAnalysis analysis = gapRepository
                .findTopByProjectIdOrderByStartedAtDesc(projectId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Gap-анализ для проекта не найден: " + projectId));

        return mapper.toGapResponse(analysis);
    }

    /**
     * Получить все gap-анализы проекта.
     */
    @Transactional(readOnly = true)
    public List<GapAnalysisResponse> getAllByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }

        return gapRepository.findByProjectIdOrderByStartedAtDesc(projectId)
                .stream()
                .map(mapper::toGapResponse)
                .toList();
    }

    /**
     * Получить дефициты по критичности.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGapsBySeverity(String analysisId, String severity) {
        EquipmentGapAnalysis analysis = gapRepository.findById(analysisId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Gap-анализ не найден: " + analysisId));

        if (analysis.getGaps() == null) {
            return List.of();
        }

        return analysis.getGaps().stream()
                .filter(g -> severity == null || severity.equalsIgnoreCase(g.getSeverity()))
                .map(g -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("gapId", g.getId());
                    m.put("operationType", g.getOperationType());
                    m.put("gapReason", g.getGapReason());
                    m.put("severity", g.getSeverity());
                    m.put("strategy", g.getStrategy());
                    return m;
                })
                .toList();
    }

    // ==================================================================
    // 4. Сводки
    // ==================================================================

    /**
     * Сводка по gap-анализу.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String analysisId) {
        EquipmentGapAnalysis analysis = gapRepository.findById(analysisId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Gap-анализ не найден: " + analysisId));

        Map<String, Integer> bySeverity = new HashMap<>();
        Map<String, Integer> byOperation = new HashMap<>();

        if (analysis.getGaps() != null) {
            for (EquipmentGapAnalysis.Gap gap : analysis.getGaps()) {
                bySeverity.merge(gap.getSeverity(), 1, Integer::sum);
                byOperation.merge(gap.getOperationType(), 1, Integer::sum);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("analysisId", analysisId);
        summary.put("projectId", analysis.getProjectId());
        summary.put("strategy", analysis.getStrategy());
        summary.put("status", analysis.getStatus());
        summary.put("gapsCount", analysis.getGapsCount());
        summary.put("recommendationsCount", analysis.getRecommendationsCount());
        summary.put("bySeverity", bySeverity);
        summary.put("byOperation", byOperation);
        summary.put("startedAt", analysis.getStartedAt());
        summary.put("completedAt", analysis.getCompletedAt());
        return summary;
    }

    /**
     * Сводка по всем gap-анализам проекта.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProjectSummary(String projectId) {
        List<EquipmentGapAnalysis> analyses =
                gapRepository.findByProjectIdOrderByStartedAtDesc(projectId);

        long totalGaps = analyses.stream()
                .mapToLong(EquipmentGapAnalysis::getGapsCount)
                .sum();

        long totalRecommendations = analyses.stream()
                .mapToLong(EquipmentGapAnalysis::getRecommendationsCount)
                .sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", projectId);
        summary.put("analysesCount", analyses.size());
        summary.put("totalGaps", totalGaps);
        summary.put("totalRecommendations", totalRecommendations);
        summary.put("lastAnalysisAt", analyses.isEmpty()
                ? null : analyses.get(0).getStartedAt());
        return summary;
    }

    // ==================================================================
    // 5. Пересчёт
    // ==================================================================

    /**
     * Пересчитать gap-анализ с другой стратегией.
     */
    @Transactional
    public GapAnalysisResponse recalculate(String analysisId, String strategy) {
        EquipmentGapAnalysis existing = gapRepository.findById(analysisId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Gap-анализ не найден: " + analysisId));

        log.info("recalculate: analysisId={}, strategy={}", analysisId, strategy);

        List<EquipmentRequirementDto> requirements = existing.getGaps() == null
                ? List.of()
                : existing.getGaps().stream()
                        .map(g -> EquipmentRequirementDto.builder()
                                .operationType(g.getOperationType())
                                .requiredAccuracy(g.getRequiredAccuracy())
                                .maxDimensions(g.getMaxDimensions())
                                .material(g.getMaterial())
                                .requiredPowerKw(g.getRequiredPowerKw())
                                .build())
                        .toList();

        GapAnalysisRequest request = GapAnalysisRequest.builder()
                .projectId(existing.getProjectId())
                .strategy(strategy)
                .requirements(requirements)
                .build();

        return run(request);
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    /**
     * Загрузить требования к оборудованию.
     */
    private List<EquipmentRequirement> loadRequirements(String projectId,
                                                         GapAnalysisRequest request) {
        // Если требования переданы явно — используем их
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            List<EquipmentRequirement> result = new ArrayList<>();
            String analysisId = UUID.randomUUID().toString();

            for (EquipmentRequirementDto dto : request.getRequirements()) {
                result.add(EquipmentRequirement.builder()
                        .id(UUID.randomUUID().toString())
                        .analysisId(analysisId)
                        .projectId(projectId)
                        .operationId(dto.getOperationId())
                        .operationType(dto.getOperationType())
                        .stageId(dto.getStageId())
                        .requiredAccuracy(dto.getRequiredAccuracy())
                        .maxDimensions(dto.getMaxDimensions())
                        .material(dto.getMaterial())
                        .requiredPowerKw(dto.getRequiredPowerKw())
                        .createdAt(Instant.now())
                        .build());
            }
            return result;
        }

        // Иначе — загружаем из БД
        return requirementRepository.findByProjectIdOrderByCreatedAt(projectId);
    }

    private String resolveStrategy(GapAnalysisRequest request) {
        if (request.getStrategy() != null && !request.getStrategy().isBlank()) {
            return request.getStrategy().toUpperCase();
        }
        String defaultStrategy = properties.getGapStrategy();
        return defaultStrategy == null ? "STRICT" : defaultStrategy.toUpperCase();
    }

    private void validate(GapAnalysisRequest request) {
        if (request == null) {
            throw RecommendationException.validation("Пустой запрос");
        }
        if (request.getProjectId() == null || request.getProjectId().isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }
    }
}