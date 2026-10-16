package ru.iktk.recommendation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.EquipmentRecommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий рекомендаций по оборудованию ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code equipment_recommendations}
 * (PostgreSQL, миграция V6), которая хранит рекомендации по закрытию
 * дефицита оборудования.
 *
 * <p><b>Типы рекомендаций:</b>
 * <ul>
 *   <li>{@code ALTERNATIVE} — альтернативное имеющееся оборудование;</li>
 *   <li>{@code PURCHASE} — докупка оборудования;</li>
 *   <li>{@code RENT} — аренда оборудования;</li>
 *   <li>{@code OUTSOURCE} — передача операции на аутсорс;</li>
 *   <li>{@code ADJUST_PROCESS} — корректировка ТП.</li>
 * </ul>
 *
 * <p><b>Статусы:</b> {@code PENDING}, {@code ACCEPTED}, {@code REJECTED}.
 *
 * <p>Схема таблицы (ориентир):
 * <pre>
 *   equipment_recommendations (
 *     id              UUID PRIMARY KEY,
 *     project_id      UUID NOT NULL,
 *     gap_id          UUID,
 *     type            VARCHAR(32) NOT NULL,
 *     equipment_id    VARCHAR(64),
 *     vendor_id       UUID,
 *     rationale       VARCHAR(2000),
 *     score           NUMERIC(8,4),
 *     estimated_cost  NUMERIC(14,2),
 *     lead_time_days  INT,
 *     status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 *     accepted_by     VARCHAR(128),
 *     accepted_at     TIMESTAMPTZ,
 *     parameters      JSONB,
 *     created_at      TIMESTAMPTZ NOT NULL,
 *     updated_at      TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 */
@Repository
public interface RecommendationRepository
        extends JpaRepository<EquipmentRecommendation, String> {

    // ==================================================================
    // 1. Выборки по проекту
    // ==================================================================

    /**
     * Все рекомендации проекта, отсортированные по скору (убывание).
     *
     * <p>Основной метод для {@code EquipmentRecommendationService.getByProject}.
     */
    List<EquipmentRecommendation> findByProjectIdOrderByScoreDesc(String projectId);

    /**
     * Постраничный список рекомендаций проекта.
     */
    Page<EquipmentRecommendation> findByProjectId(String projectId, Pageable pageable);

    /**
     * Количество рекомендаций по проекту.
     */
    long countByProjectId(String projectId);

    /**
     * Проверить существование рекомендаций для проекта.
     */
    boolean existsByProjectId(String projectId);

    // ==================================================================
    // 2. Выборки по дефициту
    // ==================================================================

    /**
     * Рекомендации по дефициту, отсортированные по скору.
     *
     * <p>Основной метод для {@code EquipmentRecommendationService.getByGap}.
     */
    List<EquipmentRecommendation> findByGapIdOrderByScoreDesc(String gapId);

    /**
     * Рекомендации по дефициту.
     */
    List<EquipmentRecommendation> findByGapId(String gapId);

    /**
     * Количество рекомендаций по дефициту.
     */
    long countByGapId(String gapId);

    /**
     * Рекомендации по дефициту и статусу.
     */
    List<EquipmentRecommendation> findByGapIdAndStatusOrderByScoreDesc(
            String gapId, String status);

    // ==================================================================
    // 3. Выборки по типу и статусу
    // ==================================================================

    /**
     * Рекомендации проекта по типу.
     */
    List<EquipmentRecommendation> findByProjectIdAndTypeOrderByScoreDesc(
            String projectId, String type);

    /**
     * Рекомендации проекта по статусу.
     */
    List<EquipmentRecommendation> findByProjectIdAndStatusOrderByScoreDesc(
            String projectId, String status);

    /**
     * Рекомендации проекта по типу и статусу.
     */
    List<EquipmentRecommendation> findByProjectIdAndTypeAndStatusOrderByScoreDesc(
            String projectId, String type, String status);

    /**
     * Рекомендации по типу (по всем проектам).
     */
    List<EquipmentRecommendation> findByTypeOrderByCreatedAtDesc(String type);

    /**
     * Рекомендации по статусу (по всем проектам).
     */
    List<EquipmentRecommendation> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Количество рекомендаций по типу.
     */
    long countByType(String type);

    /**
     * Количество рекомендаций по статусу.
     */
    long countByStatus(String status);

    /**
     * Количество рекомендаций проекта в статусе.
     */
    long countByProjectIdAndStatus(String projectId, String status);

    // ==================================================================
    // 4. Поиск по оборудованию и поставщику
    // ==================================================================

    /**
     * Рекомендации, связанные с указанным оборудованием.
     */
    List<EquipmentRecommendation> findByEquipmentIdOrderByCreatedAtDesc(String equipmentId);

    /**
     * Рекомендации, связанные с указанным поставщиком.
     */
    List<EquipmentRecommendation> findByVendorIdOrderByCreatedAtDesc(String vendorId);

    /**
     * Проверить существование рекомендации по оборудованию и дефициту.
     */
    boolean existsByGapIdAndEquipmentId(String gapId, String equipmentId);

    /**
     * Найти принятую рекомендацию по дефициту.
     */
    Optional<EquipmentRecommendation> findByGapIdAndStatus(String gapId, String status);

    // ==================================================================
    // 5. Выборки по скору
    // ==================================================================

    /**
     * Топ-N рекомендаций проекта по скору.
     */
    @Query("""
            SELECT r FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
             ORDER BY r.score DESC
            """)
    List<EquipmentRecommendation> findTopByProject(
            @Param("projectId") String projectId,
            Pageable pageable);

    /**
     * Рекомендации с минимальным скором.
     */
    @Query("""
            SELECT r FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
               AND r.score >= :minScore
             ORDER BY r.score DESC
            """)
    List<EquipmentRecommendation> findByProjectAndMinScore(
            @Param("projectId") String projectId,
            @Param("minScore") BigDecimal minScore);

    /**
     * Средний скор рекомендаций проекта.
     */
    @Query("""
            SELECT AVG(r.score)
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
            """)
    BigDecimal avgScoreByProject(@Param("projectId") String projectId);

    // ==================================================================
    // 6. Агрегация и счётчики
    // ==================================================================

    /**
     * Распределение рекомендаций по типам в проекте.
     */
    @Query("""
            SELECT r.type, COUNT(r)
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
             GROUP BY r.type
             ORDER BY r.type
            """)
    List<Object[]> countByTypeGrouped(@Param("projectId") String projectId);

    /**
     * Распределение рекомендаций по статусам в проекте.
     */
    @Query("""
            SELECT r.status, COUNT(r)
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
             GROUP BY r.status
             ORDER BY r.status
            """)
    List<Object[]> countByStatusGrouped(@Param("projectId") String projectId);

    /**
     * Суммарная оценочная стоимость принятых рекомендаций проекта.
     */
    @Query("""
            SELECT COALESCE(SUM(r.estimatedCost), 0)
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
               AND r.status = 'ACCEPTED'
            """)
    BigDecimal sumAcceptedCost(@Param("projectId") String projectId);

    /**
     * Сводка по проекту: количество, принятые, отклонённые, ожидающие.
     */
    @Query("""
            SELECT COUNT(r),
                   COALESCE(SUM(CASE WHEN r.status = 'ACCEPTED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN r.status = 'REJECTED' THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN r.status = 'PENDING'  THEN 1 ELSE 0 END), 0)
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
            """)
    Object[] getProjectSummary(@Param("projectId") String projectId);

    // ==================================================================
    // 7. Выборки по времени
    // ==================================================================

    /**
     * Рекомендации, созданные после указанного момента.
     */
    List<EquipmentRecommendation> findByCreatedAtAfter(Instant since);

    /**
     * Рекомендации, обновлённые после указанного момента.
     */
    List<EquipmentRecommendation> findByUpdatedAtAfter(Instant since);

    /**
     * Устаревшие ожидающие рекомендации.
     *
     * <p>Используется плановой задачей для авто-архивации.
     */
    @Query("""
            SELECT r FROM EquipmentRecommendation r
             WHERE r.status = 'PENDING'
               AND r.updatedAt < :before
             ORDER BY r.updatedAt ASC
            """)
    List<EquipmentRecommendation> findStalePending(@Param("before") Instant before);

    // ==================================================================
    // 8. Обновления
    // ==================================================================

    /**
     * Обновить статус рекомендации.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRecommendation r
               SET r.status = :status,
                   r.updatedAt = :updatedAt
             WHERE r.id = :recommendationId
            """)
    int updateStatus(@Param("recommendationId") String recommendationId,
                     @Param("status") String status,
                     @Param("updatedAt") Instant updatedAt);

    /**
     * Зафиксировать принятие рекомендации.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRecommendation r
               SET r.status = 'ACCEPTED',
                   r.acceptedBy = :acceptedBy,
                   r.acceptedAt = :acceptedAt,
                   r.updatedAt = :acceptedAt
             WHERE r.id = :recommendationId
            """)
    int markAccepted(@Param("recommendationId") String recommendationId,
                     @Param("acceptedBy") String acceptedBy,
                     @Param("acceptedAt") Instant acceptedAt);

    /**
     * Обновить скор рекомендации.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRecommendation r
               SET r.score = :score,
                   r.updatedAt = :updatedAt
             WHERE r.id = :recommendationId
            """)
    int updateScore(@Param("recommendationId") String recommendationId,
                    @Param("score") BigDecimal score,
                    @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить обоснование рекомендации.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRecommendation r
               SET r.rationale = :rationale,
                   r.updatedAt = :updatedAt
             WHERE r.id = :recommendationId
            """)
    int updateRationale(@Param("recommendationId") String recommendationId,
                        @Param("rationale") String rationale,
                        @Param("updatedAt") Instant updatedAt);

    // ==================================================================
    // 9. Удаление
    // ==================================================================

    /**
     * Удалить рекомендации по проекту.
     */
    @Modifying
    @Query("DELETE FROM EquipmentRecommendation r WHERE r.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);

    /**
     * Удалить рекомендации по дефициту.
     */
    @Modifying
    @Query("DELETE FROM EquipmentRecommendation r WHERE r.gapId = :gapId")
    int deleteByGapId(@Param("gapId") String gapId);

    // ==================================================================
    // 10. Проекции
    // ==================================================================

    /**
     * Список ID рекомендаций проекта.
     */
    @Query("SELECT r.id FROM EquipmentRecommendation r WHERE r.projectId = :projectId")
    List<String> findIdsByProject(@Param("projectId") String projectId);

    /**
     * Список ID рекомендаций дефицита.
     */
    @Query("SELECT r.id FROM EquipmentRecommendation r WHERE r.gapId = :gapId")
    List<String> findIdsByGap(@Param("gapId") String gapId);

    /**
     * Уникальные типы рекомендаций проекта.
     */
    @Query("""
            SELECT DISTINCT r.type
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
             ORDER BY r.type
            """)
    List<String> findDistinctTypes(@Param("projectId") String projectId);

    /**
     * Уникальные статусы рекомендаций проекта.
     */
    @Query("""
            SELECT DISTINCT r.status
              FROM EquipmentRecommendation r
             WHERE r.projectId = :projectId
             ORDER BY r.status
            """)
    List<String> findDistinctStatuses(@Param("projectId") String projectId);
}