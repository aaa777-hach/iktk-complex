package ru.iktk.assembly.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.assembly.model.AssemblyProcess;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий технологических процессов сборки ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code assembly_processes} (PostgreSQL),
 * которая хранит ТП сборки сборочных единиц проекта.
 *
 * <p>Схема таблицы (ориентир, см. {@code V5__bom.sql}):
 * <pre>
 *   assembly_processes (
 *     id                UUID PRIMARY KEY,
 *     project_id        UUID NOT NULL,
 *     bom_id            UUID,
 *     designation       VARCHAR(128) NOT NULL,
 *     name              VARCHAR(255),
 *     version           INT NOT NULL DEFAULT 1,
 *     status            VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 *     strategy          VARCHAR(32),
 *     operations_count  INT NOT NULL DEFAULT 0,
 *     tooling_count     INT NOT NULL DEFAULT 0,
 *     created_at        TIMESTAMPTZ NOT NULL,
 *     updated_at        TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p><b>Ключевые сценарии использования:</b>
 * <ul>
 *   <li>поиск последнего ТП сборки по проекту — для UI и API;</li>
 *   <li>поиск всех ТП сборки проекта — при наличии нескольких версий;</li>
 *   <li>поиск ТП по статусу — для точки контроля
 *       {@code ASSEMBLY_PROCESS_PLANNING};</li>
 *   <li>агрегация по проекту — для дашборда и сводок;</li>
 *   <li>обновление счётчиков операций и оснастки.</li>
 * </ul>
 */
@Repository
public interface ProcessPlanRepository extends JpaRepository<AssemblyProcess, String> {

    // ==================================================================
    // 1. Поиск по проекту
    // ==================================================================

    /**
     * Последний ТП сборки по проекту (свежий по времени создания).
     *
     * <p>Основной метод для {@code AssemblyProcessService.getByProject}.
     */
    Optional<AssemblyProcess> findTopByProjectIdOrderByCreatedAtDesc(String projectId);

    /**
     * Все ТП сборки проекта, упорядоченные по времени создания.
     */
    List<AssemblyProcess> findByProjectIdOrderByCreatedAtDesc(String projectId);

    /**
     * Постраничный список ТП сборки проекта.
     */
    Page<AssemblyProcess> findByProjectId(String projectId, Pageable pageable);

    /**
     * Количество ТП сборки проекта.
     */
    long countByProjectId(String projectId);

    // ==================================================================
    // 2. Поиск по BOM
    // ==================================================================

    /**
     * ТП сборки по идентификатору BOM.
     */
    Optional<AssemblyProcess> findByBomId(String bomId);

    /**
     * Все ТП по BOM (если BOM используется в нескольких проектах).
     */
    List<AssemblyProcess> findByBomIdOrderByCreatedAtDesc(String bomId);

    /**
     * ТП сборки по проекту и BOM.
     */
    Optional<AssemblyProcess> findByProjectIdAndBomId(String projectId, String bomId);

    // ==================================================================
    // 3. Поиск по статусу и версии
    // ==================================================================

    /**
     * ТП сборки проекта в указанном статусе.
     */
    List<AssemblyProcess> findByProjectIdAndStatus(String projectId, String status);

    /**
     * Все ТП в указанном статусе.
     */
    List<AssemblyProcess> findByStatusOrderByUpdatedAtDesc(String status);

    /**
     * Последний ТП сборки проекта в указанном статусе.
     */
    Optional<AssemblyProcess> findTopByProjectIdAndStatusOrderByVersionDesc(
            String projectId, String status);

    /**
     * Все версии ТП сборки проекта (для истории).
     */
    List<AssemblyProcess> findByProjectIdAndDesignationOrderByVersionDesc(
            String projectId, String designation);

    /**
     * ТП сборки с версией больше указанной.
     */
    @Query("""
            SELECT p FROM AssemblyProcess p
             WHERE p.projectId = :projectId
               AND p.version > :version
             ORDER BY p.version DESC
            """)
    List<AssemblyProcess> findByProjectIdAndVersionGreaterThan(
            @Param("projectId") String projectId,
            @Param("version") int version);

    /**
     * Количество ТП в статусе.
     */
    long countByStatus(String status);

    // ==================================================================
    // 4. Поиск по стратегии
    // ==================================================================

    /**
     * ТП сборки с указанной стратегией.
     */
    List<AssemblyProcess> findByStrategyOrderByCreatedAtDesc(String strategy);

    /**
     * ТП сборки проекта с указанной стратегией.
     */
    List<AssemblyProcess> findByProjectIdAndStrategy(String projectId, String strategy);

    // ==================================================================
    // 5. Выборки по времени
    // ==================================================================

    /**
     * ТП сборки, созданные после указанного момента.
     */
    List<AssemblyProcess> findByCreatedAtAfter(Instant since);

    /**
     * ТП сборки, обновлённые после указанного момента.
     */
    List<AssemblyProcess> findByUpdatedAtAfter(Instant since);

    /**
     * «Устаревшие» ТП, не обновлявшиеся дольше указанного момента.
     *
     * <p>Используется плановой задачей для архивации черновиков.
     */
    @Query("""
            SELECT p FROM AssemblyProcess p
             WHERE p.status <> 'APPROVED'
               AND p.status <> 'ARCHIVED'
               AND p.updatedAt < :before
             ORDER BY p.updatedAt ASC
            """)
    List<AssemblyProcess> findStaleDrafts(@Param("before") Instant before);

    // ==================================================================
    // 6. Агрегация и счётчики
    // ==================================================================

    /**
     * Распределение ТП по статусам.
     */
    @Query("""
            SELECT p.status, COUNT(p)
              FROM AssemblyProcess p
             GROUP BY p.status
             ORDER BY p.status
            """)
    List<Object[]> countByStatusGrouped();

    /**
     * Распределение ТП по проектам.
     */
    @Query("""
            SELECT p.projectId, COUNT(p)
              FROM AssemblyProcess p
             GROUP BY p.projectId
             ORDER BY p.projectId
            """)
    List<Object[]> countByProjectGrouped();

    /**
     * Распределение ТП по стратегиям.
     */
    @Query("""
            SELECT p.strategy, COUNT(p)
              FROM AssemblyProcess p
             WHERE p.strategy IS NOT NULL
             GROUP BY p.strategy
             ORDER BY p.strategy
            """)
    List<Object[]> countByStrategyGrouped();

    /**
     * Сводка по проекту: количество ТП, суммарно операций и оснастки.
     */
    @Query("""
            SELECT COUNT(p),
                   COALESCE(SUM(p.operationsCount), 0),
                   COALESCE(SUM(p.toolingCount), 0)
              FROM AssemblyProcess p
             WHERE p.projectId = :projectId
            """)
    Object[] getProjectSummary(@Param("projectId") String projectId);

    /**
     * Суммарное количество операций по всем ТП проекта.
     */
    @Query("""
            SELECT COALESCE(SUM(p.operationsCount), 0)
              FROM AssemblyProcess p
             WHERE p.projectId = :projectId
            """)
    long sumOperationsByProject(@Param("projectId") String projectId);

    // ==================================================================
    // 7. Обновления
    // ==================================================================

    /**
     * Обновить статус ТП.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyProcess p
               SET p.status = :status,
                   p.updatedAt = :updatedAt
             WHERE p.id = :processId
            """)
    int updateStatus(@Param("processId") String processId,
                     @Param("status") String status,
                     @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить счётчики операций и оснастки.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyProcess p
               SET p.operationsCount = :operationsCount,
                   p.toolingCount = :toolingCount,
                   p.updatedAt = :updatedAt
             WHERE p.id = :processId
            """)
    int updateCounters(@Param("processId") String processId,
                       @Param("operationsCount") int operationsCount,
                       @Param("toolingCount") int toolingCount,
                       @Param("updatedAt") Instant updatedAt);

    /**
     * Инкремент версии ТП.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyProcess p
               SET p.version = p.version + 1,
                   p.updatedAt = :updatedAt
             WHERE p.id = :processId
            """)
    int incrementVersion(@Param("processId") String processId,
                         @Param("updatedAt") Instant updatedAt);

    // ==================================================================
    // 8. Проекции (для отчётов и UI)
    // ==================================================================

    /**
     * Список ID ТП проекта.
     */
    @Query("SELECT p.id FROM AssemblyProcess p WHERE p.projectId = :projectId")
    List<String> findIdsByProject(@Param("projectId") String projectId);

    /**
     * Список обозначений ТП проекта.
     */
    @Query("""
            SELECT p.designation FROM AssemblyProcess p
             WHERE p.projectId = :projectId
             ORDER BY p.designation
            """)
    List<String> findDesignationsByProject(@Param("projectId") String projectId);

    /**
     * Проверка существования ТП для проекта.
     */
    boolean existsByProjectId(String projectId);

    /**
     * Проверка существования утверждённого ТП для проекта.
     */
    boolean existsByProjectIdAndStatus(String projectId, String status);
}