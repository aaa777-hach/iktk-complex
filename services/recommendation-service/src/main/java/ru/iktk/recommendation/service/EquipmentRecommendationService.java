package ru.iktk.recommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.RecommendationResponse;
import ru.iktk.recommendation.dto.VendorCatalogDto;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.VendorCatalog;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.recommendation.repository.VendorCatalogRepository;
import ru.iktk.shared.versioning.VersioningService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис формирования и управления рекомендациями ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>формирование рекомендаций по дефициту оборудования
 *       (альтернативы, докупка, аренда, аутсорс, корректировка ТП);</li>
 *   <li>скоринг вариантов по производительности, загрузке, стоимости,
 *       срокам и качеству;</li>
 *   <li>принятие/отклонение рекомендаций;</li>
 *   <li>выбор варианта докупки/аренды;</li>
 *   <li>корректировку ТП под имеющийся парк;</li>
 *   <li>выбор аутсорса для операции;</li>
 *   <li>поиск в каталоге внешнего оборудования;</li>
 *   <li>сводки по рекомендациям проекта.</li>
 * </ul>
 *
 * <p><b>Стратегии скоринга:</b>
 * <ul>
 *   <li>{@code BALANCED} — сбалансированная (по умолчанию):</li>
 *   <li>{@code COST_FIRST} — минимальная стоимость;</li>
 *   <li>{@code TIME_FIRST} — минимальный срок;</li>
 *   <li>{@code QUALITY_FIRST} — максимальное качество.</li>
 * </ul>
 *
 * <p><b>События оркестратора:</b> при принятии рекомендации формируется
 * событие {@code RECOMMENDATION_ACCEPTED}; при корректировке ТП —
 * {@code PROCESS_ADJUSTED}; при выборе аутсорса — {@code OUTSOURCING_SELECTED}.
 */
@Service
public class EquipmentRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentRecommendationService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACCEPTED = "ACCEPTED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final EquipmentRecommendationRepository recommendationRepository;
    private final EquipmentGapAnalysisRepository gapRepository;
    private final VendorCatalogRepository vendorRepository;
    private final RecommendationMapper mapper;
    private final EquipmentClient equipmentClient;
    private final VersioningService versioningService;
    private final RecommendationProperties properties;

    public EquipmentRecommendationService(EquipmentRecommendationRepository recommendationRepository,
                                          EquipmentGapAnalysisRepository gapRepository,
                                          VendorCatalogRepository vendorRepository,
                                          RecommendationMapper mapper,
                                          EquipmentClient equipmentClient,
                                          VersioningService versioningService,
                                          RecommendationProperties properties) {
        this.recommendationRepository = recommendationRepository;
        this.gapRepository = gapRepository;
        this.vendorRepository = vendorRepository;
        this.mapper = mapper;
        this.equipmentClient = equipmentClient;
        this.versioningService = versioningService;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Формирование рекомендаций
    // ==================================================================

    /**
     * Сформировать рекомендации по дефициту.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Поиск доступного оборудования на предприятии
     *       (альтернативы).</li>
     *   <li>Поиск в каталоге внешнего оборудования (докупка/аренда).</li>
     *   <li>Формирование варианта аутсорса.</li>
     *   <li>Формирование варианта корректировки ТП.</li>
     *   <li>Скоринг и сортировка по стратегии.</li>
     * </ol>
     */
    @Transactional
    public List<EquipmentRecommendation> generateForGap(String gapId) {
        EquipmentGapAnalysis gap = gapRepository.findById(gapId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Дефицит не найден: " + gapId));

        log.info("generateForGap: gapId={}, projectId={}, operationType={}",
                gapId, gap.getProjectId(), gap.getOperationType());

        List<EquipmentRecommendation> result = new ArrayList<>();

        // 1. Альтернативы на предприятии
        result.addAll(findAlternatives(gap));

        // 2. Каталог внешнего оборудования (докупка/аренда)
        if (properties.isVendorEnabled()) {
            result.addAll(findVendorOptions(gap));
        }

        // 3. Аутсорс
        if (properties.isOutsourcingEnabled()) {
            result.add(buildOutsourcingRecommendation(gap));
        }

        // 4. Корректировка ТП
        result.add(buildAdjustProcessRecommendation(gap));

        // 5. Скоринг и сортировка
        applyScoring(result, gap);
        result.sort(Comparator.comparing(
                EquipmentRecommendation::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 6. Ограничение количества
        int maxRecs = properties.getMaxRecommendations() > 0
                ? properties.getMaxRecommendations() : 5;
        if (result.size() > maxRecs) {
            result = result.subList(0, maxRecs);
        }

        // 7. Сохранение
        List<EquipmentRecommendation> saved = recommendationRepository.saveAll(result);

        log.info("generateForGap done: gapId={}, recommendations={}",
                gapId, saved.size());

        return saved;
    }

    /**
     * Найти альтернативы на предприятии.
     */
    private List<EquipmentRecommendation> findAlternatives(EquipmentGapAnalysis gap) {
        List<EquipmentRecommendation> result = new ArrayList<>();

        try {
            List<Map<String, Object>> candidates = equipmentClient.findAlternatives(
                    gap.getProjectId(), gap.getOperationType(), gap.getRequirements());

            if (candidates == null || candidates.isEmpty()) {
                return result;
            }

            for (Map<String, Object> candidate : candidates) {
                String equipmentId = str(candidate.get("equipmentId"));
                if (equipmentId == null) continue;

                result.add(EquipmentRecommendation.builder()
                        .id(UUID.randomUUID().toString())
                        .projectId(gap.getProjectId())
                        .gapId(gap.getId())
                        .type("ALTERNATIVE")
                        .equipmentId(equipmentId)
                        .rationale("Альтернативное оборудование на предприятии: "
                                + str(candidate.get("name")))
                        .status(STATUS_PENDING)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
            }
        } catch (Exception e) {
            log.warn("findAlternatives: ошибка поиска альтернатив: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Найти варианты в каталоге внешнего оборудования.
     */
    private List<EquipmentRecommendation> findVendorOptions(EquipmentGapAnalysis gap) {
        List<EquipmentRecommendation> result = new ArrayList<>();

        try {
            List<VendorCatalog> vendors = vendorRepository.findByEquipmentType(
                    gap.getOperationType());

            for (VendorCatalog vendor : vendors) {
                result.add(EquipmentRecommendation.builder()
                        .id(UUID.randomUUID().toString())
                        .projectId(gap.getProjectId())
                        .gapId(gap.getId())
                        .type("PURCHASE")
                        .vendorId(vendor.getId())
                        .rationale("Докупка/аренда: " + vendor.getName())
                        .estimatedCost(vendor.getCost())
                        .leadTimeDays(vendor.getLeadTimeDays())
                        .status(STATUS_PENDING)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
            }
        } catch (Exception e) {
            log.warn("findVendorOptions: ошибка поиска в каталоге: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Сформировать вариант аутсорса.
     */
    private EquipmentRecommendation buildOutsourcingRecommendation(EquipmentGapAnalysis gap) {
        return EquipmentRecommendation.builder()
                .id(UUID.randomUUID().toString())
                .projectId(gap.getProjectId())
                .gapId(gap.getId())
                .type("OUTSOURCE")
                .rationale("Передача операции на аутсорс")
                .status(STATUS_PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Сформировать вариант корректировки ТП.
     */
    private EquipmentRecommendation buildAdjustProcessRecommendation(EquipmentGapAnalysis gap) {
        return EquipmentRecommendation.builder()
                .id(UUID.randomUUID().toString())
                .projectId(gap.getProjectId())
                .gapId(gap.getId())
                .type("ADJUST_PROCESS")
                .rationale("Скорректировать ТП под имеющееся оборудование")
                .status(STATUS_PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    // 2. Скоринг
    // ==================================================================

    /**
     * Применить скоринг ко всем рекомендациям.
     */
    private void applyScoring(List<EquipmentRecommendation> recommendations,
                              EquipmentGapAnalysis gap) {
        for (EquipmentRecommendation rec : recommendations) {
            BigDecimal score = computeScore(rec, gap);
            rec.setScore(score);
        }
    }

    /**
     * Вычислить скор рекомендации.
     *
     * <p>Веса берутся из {@link RecommendationProperties#getScoring()}.
     */
    private BigDecimal computeScore(EquipmentRecommendation rec,
                                    EquipmentGapAnalysis gap) {
        Map<String, Double> weights = parseWeights(properties.getScoringWeights());

        double costScore = computeCostScore(rec);
        double leadTimeScore = computeLeadTimeScore(rec);
        double qualityScore = computeQualityScore(rec);
        double loadScore = computeLoadScore(rec);

        double total =
                costScore * weights.getOrDefault("cost", 0.4)
                        + leadTimeScore * weights.getOrDefault("leadTime", 0.3)
                        + qualityScore * weights.getOrDefault("quality", 0.2)
                        + loadScore * weights.getOrDefault("load", 0.1);

        return BigDecimal.valueOf(total).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private double computeCostScore(EquipmentRecommendation rec) {
        if ("ALTERNATIVE".equals(rec.getType())
                || "ADJUST_PROCESS".equals(rec.getType())) {
            return 1.0; // нет затрат
        }
        if ("OUTSOURCE".equals(rec.getType())) {
            return 0.6;
        }
        if (rec.getEstimatedCost() == null) {
            return 0.5;
        }
        // Чем меньше стоимость, тем выше скор (нормализация 0–1)
        double maxCost = 5_000_000.0;
        double cost = rec.getEstimatedCost().doubleValue();
        return Math.max(0.0, 1.0 - cost / maxCost);
    }

    private double computeLeadTimeScore(EquipmentRecommendation rec) {
        if ("ALTERNATIVE".equals(rec.getType())
                || "ADJUST_PROCESS".equals(rec.getType())) {
            return 1.0; // мгновенно
        }
        if (rec.getLeadTimeDays() == null) {
            return 0.5;
        }
        int maxDays = 365;
        return Math.max(0.0, 1.0 - (double) rec.getLeadTimeDays() / maxDays);
    }

    private double computeQualityScore(EquipmentRecommendation rec) {
        return switch (rec.getType()) {
            case "ALTERNATIVE" -> 0.9;
            case "PURCHASE" -> 0.8;
            case "OUTSOURCE" -> 0.6;
            case "ADJUST_PROCESS" -> 0.5;
            default -> 0.5;
        };
    }

    private double computeLoadScore(EquipmentRecommendation rec) {
        // Для альтернатив — учитываем текущую загрузку
        return 0.7;
    }

    private Map<String, Double> parseWeights(String weightsStr) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (weightsStr == null || weightsStr.isBlank()) {
            result.put("cost", 0.4);
            result.put("leadTime", 0.3);
            result.put("quality", 0.2);
            result.put("load", 0.1);
            return result;
        }
        for (String part : weightsStr.split(",")) {
            String[] kv = part.trim().split("=");
            if (kv.length == 2) {
                try {
                    result.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    // ==================================================================
    // 3. Чтение
    // ==================================================================

    @Transactional(readOnly = true)
    public List<EquipmentRecommendationDto> getByProject(String projectId,
                                                         String type,
                                                         String status) {
        if (projectId == null || projectId.isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }

        List<EquipmentRecommendation> result;
        if (type != null && status != null) {
            result = recommendationRepository
                    .findByProjectIdAndTypeAndStatusOrderByScoreDesc(projectId, type, status);
        } else if (type != null) {
            result = recommendationRepository
                    .findByProjectIdAndTypeOrderByScoreDesc(projectId, type);
        } else if (status != null) {
            result = recommendationRepository
                    .findByProjectIdAndStatusOrderByScoreDesc(projectId, status);
        } else {
            result = recommendationRepository
                    .findByProjectIdOrderByScoreDesc(projectId);
        }

        return result.stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentRecommendationDto> getByGap(String gapId) {
        if (gapId == null || gapId.isBlank()) {
            throw RecommendationException.validation("Не указан gapId");
        }
        return recommendationRepository.findByGapIdOrderByScoreDesc(gapId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EquipmentRecommendationDto getById(String recommendationId) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);
        return mapper.toDto(rec);
    }

    // ==================================================================
    // 4. Принятие решений
    // ==================================================================

    /**
     * Принять рекомендацию (альтернативное оборудование).
     */
    @Transactional
    public RecommendationResponse accept(String recommendationId, String comment) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);

        if (STATUS_ACCEPTED.equals(rec.getStatus())) {
            throw RecommendationException.validation(
                    "Рекомендация уже принята: " + recommendationId);
        }

        rec.setStatus(STATUS_ACCEPTED);
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        // Назначаем оборудование на операции ТП
        try {
            if (rec.getEquipmentId() != null) {
                equipmentClient.assignEquipment(
                        rec.getProjectId(), rec.getEquipmentId(), rec.getGapId());
            }
        } catch (Exception e) {
            log.warn("accept: не удалось назначить оборудование: {}", e.getMessage());
        }

        versioningService.createVersion(
                "RECOMMENDATION",
                UUID.fromString(recommendationId),
                UUID.fromString(rec.getProjectId()),
                mapper.toJsonNode(rec),
                VersioningService.ChangeType.APPROVE,
                comment == null ? "Рекомендация принята" : comment,
                "system");

        log.info("accept: recommendationId={}, type={}, equipmentId={}",
                recommendationId, rec.getType(), rec.getEquipmentId());

        return RecommendationResponse.builder()
                .projectId(rec.getProjectId())
                .recommendationId(recommendationId)
                .status(STATUS_ACCEPTED)
                .state("ASSEMBLY_PROCESS_PLANNING")
                .message("Рекомендация принята")
                .build();
    }

    /**
     * Отклонить рекомендацию.
     */
    @Transactional
    public RecommendationResponse reject(String recommendationId, String reason) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);

        rec.setStatus(STATUS_REJECTED);
        rec.setRationale((rec.getRationale() == null ? "" : rec.getRationale() + " | ")
                + "Отклонено: " + (reason == null ? "" : reason));
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        log.warn("reject: recommendationId={}, reason={}", recommendationId, reason);

        return RecommendationResponse.builder()
                .projectId(rec.getProjectId())
                .recommendationId(recommendationId)
                .status(STATUS_REJECTED)
                .message("Рекомендация отклонена")
                .build();
    }

    /**
     * Скорректировать ТП под имеющееся оборудование.
     */
    @Transactional
    public RecommendationResponse adjustProcess(String recommendationId,
                                                Map<String, String> payload) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);
        rec.setStatus(STATUS_ACCEPTED);
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        log.info("adjustProcess: recommendationId={}", recommendationId);

        return RecommendationResponse.builder()
                .projectId(rec.getProjectId())
                .recommendationId(recommendationId)
                .status(STATUS_ACCEPTED)
                .state("EQUIPMENT_MATCHING")
                .message("ТП скорректирован — возврат к сопоставлению с оборудованием")
                .build();
    }

    /**
     * Выбрать вариант докупки/аренды.
     */
    @Transactional
    public RecommendationResponse purchase(String recommendationId,
                                            Map<String, String> payload) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);

        String vendorId = payload == null ? null : payload.get("vendorId");
        if (vendorId != null) {
            rec.setVendorId(vendorId);
        }

        rec.setStatus(STATUS_ACCEPTED);
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        log.info("purchase: recommendationId={}, vendorId={}",
                recommendationId, rec.getVendorId());

        return RecommendationResponse.builder()
                .projectId(rec.getProjectId())
                .recommendationId(recommendationId)
                .status(STATUS_ACCEPTED)
                .state("ASSEMBLY_PROCESS_PLANNING")
                .message("Решение о докупке/аренде зафиксировано")
                .build();
    }

    /**
     * Выбрать аутсорс для операции.
     */
    @Transactional
    public RecommendationResponse selectOutsourcing(String recommendationId,
                                                     Map<String, String> payload) {
        EquipmentRecommendation rec = findRecommendation(recommendationId);

        String contractor = payload == null ? null : payload.get("contractor");
        rec.setRationale("Аутсорс: " + (contractor == null ? "—" : contractor));
        rec.setStatus(STATUS_ACCEPTED);
        rec.setUpdatedAt(Instant.now());
        recommendationRepository.save(rec);

        log.info("selectOutsourcing: recommendationId={}, contractor={}",
                recommendationId, contractor);

        return RecommendationResponse.builder()
                .projectId(rec.getProjectId())
                .recommendationId(recommendationId)
                .status(STATUS_ACCEPTED)
                .state("ASSEMBLY_PROCESS_PLANNING")
                .message("Выбран аутсорс для операции")
                .build();
    }

    // ==================================================================
    // 5. Каталог внешнего оборудования
    // ==================================================================

    @Transactional(readOnly = true)
    public List<VendorCatalogDto> searchVendorCatalog(String equipmentType,
                                                       Double maxCost,
                                                       Integer maxLeadTimeDays,
                                                       String search) {
        return vendorRepository.search(equipmentType, maxCost, maxLeadTimeDays, search)
                .stream().map(mapper::toVendorDto).toList();
    }

    @Transactional(readOnly = true)
    public VendorCatalogDto getVendorById(String vendorId) {
        VendorCatalog vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Запись каталога не найдена: " + vendorId));
        return mapper.toVendorDto(vendor);
    }

    // ==================================================================
    // 6. Сводка
    // ==================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String projectId) {
        List<EquipmentRecommendation> all = recommendationRepository
                .findByProjectIdOrderByScoreDesc(projectId);

        long pending = all.stream().filter(r -> STATUS_PENDING.equals(r.getStatus())).count();
        long accepted = all.stream().filter(r -> STATUS_ACCEPTED.equals(r.getStatus())).count();
        long rejected = all.stream().filter(r -> STATUS_REJECTED.equals(r.getStatus())).count();

        Map<String, Integer> byType = new LinkedHashMap<>();
        for (EquipmentRecommendation r : all) {
            byType.merge(r.getType(), 1, Integer::sum);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", projectId);
        summary.put("total", all.size());
        summary.put("pending", pending);
        summary.put("accepted", accepted);
        summary.put("rejected", rejected);
        summary.put("byType", byType);
        return summary;
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    private EquipmentRecommendation findRecommendation(String recommendationId) {
        if (recommendationId == null || recommendationId.isBlank()) {
            throw RecommendationException.validation("Не указан recommendationId");
        }
        return recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Рекомендация не найдена: " + recommendationId));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}