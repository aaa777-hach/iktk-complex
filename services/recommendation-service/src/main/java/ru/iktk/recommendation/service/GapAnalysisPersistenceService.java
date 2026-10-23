package ru.iktk.recommendation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;

/**
 * Сохранение EquipmentGapAnalysis в отдельной транзакции (REQUIRES_NEW).
 *
 * <p>Зачем: {@code GapAnalysisService.run()} — долгая операция (Feign-вызовы к
 * equipment-service, генерация рекомендаций). Если она падает вне {@code try/catch}
 * (например, {@code Error}, OOM, {@code matchRequirement} до try) — транзакция
 * помечается rollback-only, и {@code analysis} со статусом {@code FAILED}
 * <b>не сохраняется</b> — в БД остаётся «зависший» {@code RUNNING}.
 *
 * <p>Решение: каждое сохранение — в собственной транзакции (REQUIRES_NEW).
 * Даже если основная транзакция откатится, {@code analysis} останется
 * в БД с актуальным статусом.
 */
@Service
public class GapAnalysisPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(GapAnalysisPersistenceService.class);

    private final EquipmentGapAnalysisRepository gapRepository;

    public GapAnalysisPersistenceService(EquipmentGapAnalysisRepository gapRepository) {
        this.gapRepository = gapRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EquipmentGapAnalysis save(EquipmentGapAnalysis analysis) {
        EquipmentGapAnalysis saved = gapRepository.save(analysis);
        log.debug("persisted analysis: id={}, status={}", saved.getId(), saved.getStatus());
        return saved;
    }
}