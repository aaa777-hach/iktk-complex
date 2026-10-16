package ru.iktk.recommendation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.EquipmentRequirement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий требований к оборудованию ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code equipment_requirements}
 * (PostgreSQL, миграция V6), которая хранит требования к оборудованию,
 * вытекающие из операций спроектированных ТП.
 *
 * <p><b>Назначение.</b> Каждое требование описывает, какое оборудование
 * необходимо для выполнения конкретной операции: тип операции,
 * точность, габариты, материал, мощность, усилие. Требования
 * формируются модулями {@code casting}, {@code forging},
 * {@code machining}, {@code assembly-process-service} и используются
 * {@code GapAnalysisService} при сопоставлении с парком.
 *
 * <p>Схема таблицы (ориентир):
 * <pre>
 *   equipment_requirements (
 *     id                    UUID PRIMARY KEY,
 *     analysis_id           UUID,
 *     project_id            UUID NOT NULL,
 *     process_id            UUID,
 *     stage_id              UUID,
 *     operation_id          UUID,
 *     operation_type        VARCHAR(64) NOT NULL,
 *     required_equipment_type VARCHAR(64),
 *     required_accuracy     VARCHAR(32),
 *     max_dimensions        VARCHAR(64),
 *     min_dimensions        VARCHAR(64),
 *     material              VARCHAR(128),
 *     required_power_kw     NUMERIC(10,3),
 *     required_force_kn     NUMERIC(12,3),
 *     required_cnc          BOOLEAN NOT NULL DEFAULT FALSE,
 *     required_axes         INT,
 *     required_temperature_c NUMERIC(8,2),
 *     quantity              INT NOT NULL DEFAULT 1,
 *     priority              INT NOT NULL DEFAULT 0,
 *     notes                 VARCHAR(1000),
 *     parameters            JSONB,
 *     created_at            TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p><b>Ключевые сценарии использования:</b>
 * <ul>
 *   <li>загрузка требований по проекту — для gap-анализа;</li>
 *   <li>загрузка требований по конкретной операции/этапу;</li>
 *   <li>поиск по типу операции и критичным параметрам;</li>
 *   <li>агрегация по типам операций — для сводок;</li>
 *   <li>очистка устаревших требований.</li>
 * </ul>
 */
@Repository
public interface EquipmentRequirementRepository
        extends JpaRepository<EquipmentRequirement, String> {

    // ==================================================================
    // 1. Основные выборки по проекту
    // ==================================================================

    /**
     * Все требования по проекту (сортировка по времени создания).
     *
     * <p>Основной метод для {@code GapAnalysisService.loadRequirements}.
     */
    List<EquipmentRequirement> findByProjectIdOrderByCreatedAt(String projectId);

    /**
     * Требования по проекту с сортировкой по приоритету
     * (сначала более критичные).
     */
    List<EquipmentRequirement> findByProjectIdOrderByPriorityDesc(String projectId);

    /**
     * Постраничный список требований проекта.
     */
    Page<EquipmentRequirement> findByProjectId(String projectId, Pageable pageable);

    /**
     * Количество требований по проекту.
     */
    long countByProjectId(String projectId);

    /**
     * Проверить существование требований для проекта.
     */
    boolean existsByProjectId(String projectId);

    // ==================================================================
    // 2. Поиск по процессу / этапу / операции
    // ==================================================================

    /**
     * Требования по технологическому процессу.
     */
    List<EquipmentRequirement> findByProcessIdOrderByCreatedAt(String processId);

    /**
     * Требования по этапу ТП.
     */
    List<EquipmentRequirement> findByStageIdOrderByCreatedAt(String stageId);

    /**
     * Требования по операции.
     */
    List<EquipmentRequirement> findByOperationIdOrderByCreatedAt(String operationId);

    /**
     * Количество требований по процессу.
     */
    long countByProcessId(String processId);

    /**
     * Количество требований по этапу.
     */
    long countByStageId(String stageId);

    // ==================================================================
    // 3. Поиск по анализу
    // ==================================================================

    /**
     * Требования по идентификатору gap-анализа.
     */
    List<EquipmentRequirement> findByAnalysisIdOrderByCreatedAt(String analysisId);

    /**
     * Количество требований по анализу.
     */
    long countByAnalysisId(String analysisId);

    // ==================================================================
    // 4. Поиск по типу операции и параметрам
    // ==================================================================

    /**
     * Требования по типу операции в рамках проекта.
     */
    @Query("""
            SELECT r FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.operationType = :operationType
             ORDER BY r.priority DESC, r.createdAt
            """)
    List<EquipmentRequirement> findByProjectAndOperationType(
            @Param("projectId") String projectId,
            @Param("operationType") String operationType);

    /**
     * Требования, для которых необходимо оборудование с ЧПУ.
     */
    List<EquipmentRequirement> findByProjectIdAndRequiredCncTrue(String projectId);

    /**
     * Требования с мощностью выше указанной.
     */
    @Query("""
            SELECT r FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.requiredPowerKw >= :minPower
             ORDER BY r.requiredPowerKw DESC
            """)
    List<EquipmentRequirement> findByProjectAndMinPower(
            @Param("projectId") String projectId,
            @Param("minPower") BigDecimal minPower);

    /**
     * Требования с усилием выше указанного (для прессового оборудования).
     */
    @Query("""
            SELECT r FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.requiredForceKn >= :minForce
             ORDER BY r.requiredForceKn DESC
            """)
    List<EquipmentRequirement> findByProjectAndMinForce(
            @Param("projectId") String projectId,
            @Param("minForce") BigDecimal minForce);

    /**
     * Требования с указанным материалом.
     */
    List<EquipmentRequirement> findByProjectIdAndMaterial(String projectId, String material);

    /**
     * Требования по типу требуемого оборудования.
     */
    List<EquipmentRequirement> findByProjectIdAndRequiredEquipmentType(
            String projectId, String requiredEquipmentType);

    /**
     * Требования по критичности (приоритету).
     */
    @Query("""
            SELECT r FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.priority >= :minPriority
             ORDER BY r.priority DESC
            """)
    List<EquipmentRequirement> findByProjectAndMinPriority(
            @Param("projectId") String projectId,
            @Param("minPriority") int minPriority);

    // ==================================================================
    // 5. Агрегация и счётчики
    // ==================================================================

    /**
     * Распределение требований по типам операций в проекте.
     */
    @Query("""
            SELECT r.operationType, COUNT(r)
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
             GROUP BY r.operationType
             ORDER BY r.operationType
            """)
    List<Object[]> countByOperationTypeGrouped(@Param("projectId") String projectId);

    /**
     * Распределение требований по критичности (приоритету).
     */
    @Query("""
            SELECT r.priority, COUNT(r)
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
             GROUP BY r.priority
             ORDER BY r.priority DESC
            """)
    List<Object[]> countByPriorityGrouped(@Param("projectId") String projectId);

    /**
     * Распределение требований по материалам.
     */
    @Query("""
            SELECT r.material, COUNT(r)
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.material IS NOT NULL
             GROUP BY r.material
             ORDER BY r.material
            """)
    List<Object[]> countByMaterialGrouped(@Param("projectId") String projectId);

    /**
     * Количество требований с ЧПУ в проекте.
     */
    @Query("""
            SELECT COUNT(r) FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.requiredCnc = TRUE
            """)
    long countCncRequirements(@Param("projectId") String projectId);

    /**
     * Суммарная требуемая мощность по проекту.
     */
    @Query("""
            SELECT COALESCE(SUM(r.requiredPowerKw), 0)
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
            """)
    BigDecimal sumRequiredPower(@Param("projectId") String projectId);

    // ==================================================================
    // 6. Выборки по времени
    // ==================================================================

    /**
     * Требования, созданные после указанного момента.
     */
    List<EquipmentRequirement> findByCreatedAtAfter(Instant since);

    /**
     * «Устаревшие» требования (без анализа и старше указанного момента).
     *
     * <p>Используется плановой задачей для очистки.
     */
    @Query("""
            SELECT r FROM EquipmentRequirement r
             WHERE r.analysisId IS NULL
               AND r.createdAt < :before
             ORDER BY r.createdAt ASC
            """)
    List<EquipmentRequirement> findOrphanedBefore(@Param("before") Instant before);

    // ==================================================================
    // 7. Обновления
    // ==================================================================

    /**
     * Привязать требования к анализу.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRequirement r
               SET r.analysisId = :analysisId
             WHERE r.projectId = :projectId
               AND r.analysisId IS NULL
            """)
    int assignToAnalysis(@Param("projectId") String projectId,
                         @Param("analysisId") String analysisId);

    /**
     * Обновить приоритет требования.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRequirement r
               SET r.priority = :priority
             WHERE r.id = :requirementId
            """)
    int updatePriority(@Param("requirementId") String requirementId,
                       @Param("priority") int priority);

    /**
     * Обновить заметки требования.
     */
    @Modifying
    @Query("""
            UPDATE EquipmentRequirement r
               SET r.notes = :notes
             WHERE r.id = :requirementId
            """)
    int updateNotes(@Param("requirementId") String requirementId,
                    @Param("notes") String notes);

    // ==================================================================
    // 8. Удаление
    // ==================================================================

    /**
     * Удалить требования по проекту.
     */
    @Modifying
    @Query("DELETE FROM EquipmentRequirement r WHERE r.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") String projectId);

    /**
     * Удалить требования по анализу.
     */
    @Modifying
    @Query("DELETE FROM EquipmentRequirement r WHERE r.analysisId = :analysisId")
    int deleteByAnalysisId(@Param("analysisId") String analysisId);

    /**
     * Удалить требования по этапу.
     */
    @Modifying
    @Query("DELETE FROM EquipmentRequirement r WHERE r.stageId = :stageId")
    int deleteByStageId(@Param("stageId") String stageId);

    // ==================================================================
    // 9. Проекции (для отчётов и UI)
    // ==================================================================

    /**
     * Список ID требований проекта.
     */
    @Query("SELECT r.id FROM EquipmentRequirement r WHERE r.projectId = :projectId")
    List<String> findIdsByProject(@Param("projectId") String projectId);

    /**
     * Уникальные типы операций, требующих оборудования.
     */
    @Query("""
            SELECT DISTINCT r.operationType
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
             ORDER BY r.operationType
            """)
    List<String> findDistinctOperationTypes(@Param("projectId") String projectId);

    /**
     * Уникальные материалы в требованиях.
     */
    @Query("""
            SELECT DISTINCT r.material
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
               AND r.material IS NOT NULL
             ORDER BY r.material
            """)
    List<String> findDistinctMaterials(@Param("projectId") String projectId);

    /**
     * Сводка по проекту: количество требований, с ЧПУ, суммарная мощность.
     */
    @Query("""
            SELECT COUNT(r),
                   COALESCE(SUM(CASE WHEN r.requiredCnc = TRUE THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(r.requiredPowerKw), 0)
              FROM EquipmentRequirement r
             WHERE r.projectId = :projectId
            """)
    Object[] getProjectSummary(@Param("projectId") String projectId);

    /**
     * Найти требование по операции и типу.
     */
    Optional<EquipmentRequirement> findByOperationIdAndOperationType(
            String operationId, String operationType);
}