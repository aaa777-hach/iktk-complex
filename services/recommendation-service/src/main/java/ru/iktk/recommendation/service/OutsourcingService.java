package ru.iktk.recommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.OutsourcingOptionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис управления вариантами аутсорса ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>формирование вариантов аутсорса при дефиците оборудования;</li>
 *   <li>поиск подрядчиков по типу операции, стоимости, срокам;</li>
 *   <li>скоринг вариантов аутсорса;</li>
 *   <li>получение вариантов по проекту или по дефициту;</li>
 *   <li>выбор подрядчика и фиксацию решения;</li>
 *   <li>предоставление данных для точки контроля
 *       {@code RECOMMENDATION_REVIEW}.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Таблица {@code outsourcing_options}
 * (миграция V6) с привязкой к дефициту ({@code gap_id}).
 *
 * <p><b>Виды аутсорса:</b>
 * <ul>
 *   <li>{@code CONTRACTOR} — передача операции подрядчику;</li>
 *   <li>{@code PARTNER_PLANT} — передача на завод-партнёр;</li>
 *   <li>{@code RENTAL} — аренда оборудования с оператором;</li>
 *   <li>{@code COOPERATION} — кооперация с другим предприятием.</li>
 * </ul>
 */
@Service
public class OutsourcingService {

    private static final Logger log = LoggerFactory.getLogger(OutsourcingService.class);

    private final OutsourcingOptionRepository outsourcingRepository;
    private final EquipmentGapAnalysisRepository gapRepository;
    private final RecommendationMapper mapper;
    private final RecommendationProperties properties;

    public OutsourcingService(OutsourcingOptionRepository outsourcingRepository,
                              EquipmentGapAnalysisRepository gapRepository,
                              RecommendationMapper mapper,
                              RecommendationProperties properties) {
        this.outsourcingRepository = outsourcingRepository;
        this.gapRepository = gapRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    // ==================================================================
    // 1. Формирование вариантов аутсорса
    // ==================================================================

    /**
     * Сформировать варианты аутсорса по дефициту.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Загрузка дефицита.</li>
     *   <li>Поиск подрядчиков по типу операции (из каталога или БД).</li>
     *   <li>Скоринг по стоимости, срокам, качеству.</li>
     *   <li>Сохранение вариантов.</li>
     * </ol>
     */
    @Transactional
    public List<OutsourcingOption> generateForGap(String gapId) {
        EquipmentGapAnalysis gap = gapRepository.findById(gapId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Дефицит не найден: " + gapId));

        log.info("generateForGap: gapId={}, projectId={}, operationType={}",
                gapId, gap.getProjectId(), gap.getOperationType());

        List<OutsourcingOption> options = new ArrayList<>();

        // 1. Вариант «Стандартный подрядчик» — из каталога (если есть)
        options.addAll(findContractors(gap));

        // 2. Вариант «Завод-партнёр»
        options.add(buildOption(gap, "PARTNER_PLANT",
                "Завод-партнёр (по договору кооперации)",
                BigDecimal.valueOf(250_000),
                30));

        // 3. Вариант «Аренда оборудования с оператором»
        options.add(buildOption(gap, "RENTAL",
                "Аренда оборудования с оператором",
                BigDecimal.valueOf(180_000),
                14));

        // 4. Вариант «Кооперация»
        options.add(buildOption(gap, "COOPERATION",
                "Кооперация с другим предприятием",
                BigDecimal.valueOf(120_000),
                45));

        // 5. Скоринг
        applyScoring(options);
        options.sort(Comparator.comparing(
                OutsourcingOption::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 6. Ограничение количества
        int maxOptions = properties.getMaxOutsourcingOptions() > 0
                ? properties.getMaxOutsourcingOptions() : 5;
        if (options.size() > maxOptions) {
            options = options.subList(0, maxOptions);
        }

        // 7. Сохранение
        List<OutsourcingOption> saved = outsourcingRepository.saveAll(options);

        log.info("generateForGap done: gapId={}, options={}", gapId, saved.size());

        return saved;
    }

    /**
     * Найти подрядчиков по каталогу.
     */
    private List<OutsourcingOption> findContractors(EquipmentGapAnalysis gap) {
        List<OutsourcingOption> result = new ArrayList<>();

        try {
            List<Map<String, Object>> contractors = outsourcingRepository
                    .findContractorsByOperation(gap.getOperationType());

            for (Map<String, Object> c : contractors) {
                String contractor = str(c.get("contractor"));
                if (contractor == null || contractor.isBlank()) {
                    continue;
                }

                result.add(OutsourcingOption.builder()
                        .id(UUID.randomUUID().toString())
                        .gapId(gap.getId())
                        .projectId(gap.getProjectId())
                        .type("CONTRACTOR")
                        .contractor(contractor)
                        .operation(gap.getOperationType())
                        .cost(decimal(c.get("cost")))
                        .leadTimeDays(intVal(c.get("leadTimeDays")))
                        .qualityRating(decimal(c.get("qualityRating")))
                        .status("PENDING")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
            }
        } catch (Exception e) {
            log.warn("findContractors: ошибка поиска подрядчиков: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Построить вариант аутсорса.
     */
    private OutsourcingOption buildOption(EquipmentGapAnalysis gap,
                                           String type,
                                           String contractor,
                                           BigDecimal cost,
                                           int leadTimeDays) {
        return OutsourcingOption.builder()
                .id(UUID.randomUUID().toString())
                .gapId(gap.getId())
                .projectId(gap.getProjectId())
                .type(type)
                .contractor(contractor)
                .operation(gap.getOperationType())
                .cost(cost)
                .leadTimeDays(leadTimeDays)
                .qualityRating(BigDecimal.valueOf(0.7))
                .status("PENDING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    // 2. Скоринг
    // ==================================================================

    /**
     * Применить скоринг к вариантам аутсорса.
     */
    private void applyScoring(List<OutsourcingOption> options) {
        for (OutsourcingOption option : options) {
            option.setScore(computeScore(option));
        }
    }

    /**
     * Вычислить скор варианта аутсорса.
     *
     * <p>Формула: score = costScore * 0.4 + leadTimeScore * 0.3 +
     *                qualityScore * 0.3.
     */
    private BigDecimal computeScore(OutsourcingOption option) {
        double costScore = computeCostScore(option);
        double leadTimeScore = computeLeadTimeScore(option);
        double qualityScore = computeQualityScore(option);

        double total = costScore * 0.4 + leadTimeScore * 0.3 + qualityScore * 0.3;
        return BigDecimal.valueOf(total)
                .setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private double computeCostScore(OutsourcingOption option) {
        if (option.getCost() == null) {
            return 0.5;
        }
        double maxCost = 1_000_000.0;
        double cost = option.getCost().doubleValue();
        return Math.max(0.0, Math.min(1.0, 1.0 - cost / maxCost));
    }

    private double computeLeadTimeScore(OutsourcingOption option) {
        if (option.getLeadTimeDays() == null) {
            return 0.5;
        }
        int maxDays = 180;
        return Math.max(0.0, Math.min(1.0,
                1.0 - (double) option.getLeadTimeDays() / maxDays));
    }

    private double computeQualityScore(OutsourcingOption option) {
        if (option.getQualityRating() == null) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0,
                option.getQualityRating().doubleValue()));
    }

    // ==================================================================
    // 3. Чтение
    // ==================================================================

    /**
     * Получить варианты аутсорса по дефициту.
     */
    @Transactional(readOnly = true)
    public List<OutsourcingOptionDto> getByGap(String gapId) {
        if (gapId == null || gapId.isBlank()) {
            throw RecommendationException.validation("Не указан gapId");
        }

        return outsourcingRepository.findByGapIdOrderByScoreDesc(gapId)
                .stream()
                .map(mapper::toOutsourcingDto)
                .toList();
    }

    /**
     * Получить варианты аутсорса по проекту.
     */
    @Transactional(readOnly = true)
    public List<OutsourcingOptionDto> getByProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }

        return outsourcingRepository.findByProjectIdOrderByScoreDesc(projectId)
                .stream()
                .map(mapper::toOutsourcingDto)
                .toList();
    }

    /**
     * Получить вариант аутсорса по ID.
     */
    @Transactional(readOnly = true)
    public OutsourcingOptionDto getById(String optionId) {
        OutsourcingOption option = findOption(optionId);
        return mapper.toOutsourcingDto(option);
    }

    /**
     * Получить варианты аутсорса по типу.
     */
    @Transactional(readOnly = true)
    public List<OutsourcingOptionDto> getByProjectAndType(String projectId, String type) {
        if (projectId == null || projectId.isBlank()) {
            throw RecommendationException.validation("Не указан projectId");
        }
        if (type == null || type.isBlank()) {
            throw RecommendationException.validation("Не указан тип аутсорса");
        }

        return outsourcingRepository
                .findByProjectIdAndTypeOrderByScoreDesc(projectId, type)
                .stream()
                .map(mapper::toOutsourcingDto)
                .toList();
    }

    // ==================================================================
    // 4. Выбор подрядчика
    // ==================================================================

    /**
     * Выбрать подрядчика (зафиксировать решение).
     */
    @Transactional
    public OutsourcingOptionDto select(String optionId, String comment) {
        OutsourcingOption option = findOption(optionId);

        option.setStatus("SELECTED");
        option.setComment(comment);
        option.setUpdatedAt(Instant.now());

        OutsourcingOption saved = outsourcingRepository.save(option);

        // Отклоняем остальные варианты по этому дефициту
        outsourcingRepository
                .findByGapIdOrderByScoreDesc(option.getGapId())
                .stream()
                .filter(o -> !o.getId().equals(optionId))
                .forEach(o -> {
                    o.setStatus("REJECTED");
                    o.setUpdatedAt(Instant.now());
                    outsourcingRepository.save(o);
                });

        log.info("select: optionId={}, gapId={}, contractor={}, cost={}",
                optionId, option.getGapId(), option.getContractor(), option.getCost());

        return mapper.toOutsourcingDto(saved);
    }

    /**
     * Отклонить вариант аутсорса.
     */
    @Transactional
    public OutsourcingOptionDto reject(String optionId, String reason) {
        OutsourcingOption option = findOption(optionId);

        option.setStatus("REJECTED");
        option.setComment(reason);
        option.setUpdatedAt(Instant.now());

        OutsourcingOption saved = outsourcingRepository.save(option);

        log.warn("reject: optionId={}, reason={}", optionId, reason);
        return mapper.toOutsourcingDto(saved);
    }

    // ==================================================================
    // 5. Ручное управление
    // ==================================================================

    /**
     * Добавить вариант аутсорса вручную.
     */
    @Transactional
    public OutsourcingOptionDto add(String gapId, OutsourcingOptionDto dto) {
        EquipmentGapAnalysis gap = gapRepository.findById(gapId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Дефицит не найден: " + gapId));

        if (dto == null) {
            throw RecommendationException.validation("Пустой вариант аутсорса");
        }

        OutsourcingOption option = mapper.toOutsourcingEntity(dto);
        option.setId(UUID.randomUUID().toString());
        option.setGapId(gapId);
        option.setProjectId(gap.getProjectId());
        option.setStatus(dto.getStatus() == null ? "PENDING" : dto.getStatus());
        option.setCreatedAt(Instant.now());
        option.setUpdatedAt(Instant.now());
        option.setScore(computeScore(option));

        OutsourcingOption saved = outsourcingRepository.save(option);

        log.info("add: gapId={}, optionId={}, contractor={}",
                gapId, saved.getId(), saved.getContractor());

        return mapper.toOutsourcingDto(saved);
    }

    /**
     * Удалить вариант аутсорса.
     */
    @Transactional
    public void delete(String optionId) {
        OutsourcingOption option = findOption(optionId);
        outsourcingRepository.delete(option);
        log.warn("delete: optionId={}", optionId);
    }

    // ==================================================================
    // 6. Сводки
    // ==================================================================

    /**
     * Сводка по аутсорсу проекта.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String projectId) {
        List<OutsourcingOption> options =
                outsourcingRepository.findByProjectIdOrderByScoreDesc(projectId);

        long pending = options.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long selected = options.stream().filter(o -> "SELECTED".equals(o.getStatus())).count();
        long rejected = options.stream().filter(o -> "REJECTED".equals(o.getStatus())).count();

        BigDecimal totalCost = options.stream()
                .filter(o -> "SELECTED".equals(o.getStatus()))
                .map(OutsourcingOption::getCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Integer> byType = new LinkedHashMap<>();
        for (OutsourcingOption o : options) {
            byType.merge(o.getType(), 1, Integer::sum);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectId", projectId);
        summary.put("totalOptions", options.size());
        summary.put("pending", pending);
        summary.put("selected", selected);
        summary.put("rejected", rejected);
        summary.put("totalCost", totalCost);
        summary.put("byType", byType);
        return summary;
    }

    /**
     * Проверить наличие вариантов аутсорса по дефициту.
     */
    @Transactional(readOnly = true)
    public boolean hasOptions(String gapId) {
        return outsourcingRepository.countByGapId(gapId) > 0;
    }

    /**
     * Получить выбранный вариант по дефициту.
     */
    @Transactional(readOnly = true)
    public OutsourcingOptionDto getSelected(String gapId) {
        return outsourcingRepository
                .findByGapIdAndStatus(gapId, "SELECTED")
                .map(mapper::toOutsourcingDto)
                .orElse(null);
    }

    // ==================================================================
    // Вспомогательные методы
    // ==================================================================

    private OutsourcingOption findOption(String optionId) {
        if (optionId == null || optionId.isBlank()) {
            throw RecommendationException.validation("Не указан optionId");
        }
        return outsourcingRepository.findById(optionId)
                .orElseThrow(() -> RecommendationException.notFound(
                        "Вариант аутсорса не найден: " + optionId));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intVal(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}