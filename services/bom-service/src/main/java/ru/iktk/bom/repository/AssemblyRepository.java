package ru.iktk.bom.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.bom.model.AssemblyUnit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий сборочных единиц ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code assembly_units} (PostgreSQL),
 * которая хранит корневые и вложенные сборочные единицы проекта.
 *
 * <p>Схема таблицы (ориентир, см. {@code V5__bom.sql}):
 * <pre>
 *   assembly_units (
 *     id                UUID PRIMARY KEY,
 *     project_id        UUID NOT NULL,
 *     parent_id         UUID,                    -- для вложенных подсборок
 *     designation       VARCHAR(128),
 *     name              VARCHAR(255),
 *     geometry_id       VARCHAR(64),
 *     item_count        INT NOT NULL DEFAULT 0,
 *     max_depth         INT NOT NULL DEFAULT 0,
 *     version           INT NOT NULL DEFAULT 1,
 *     status            VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
 *     created_at        TIMESTAMPTZ NOT NULL,
 *     updated_at        TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p><b>Ключевые сценарии использования:</b>
 * <ul>
 *   <li>поиск корневой сборочной единицы по проекту — для анализа
 *       и построения дерева BOM;</li>
 *   <li>поиск всех подсборок проекта;</li>
 *   <li>поиск сборок по геометрии (для дедупликации при повторном
 *       анализе);</li>
 *   <li>подсчёт статистики по проекту/предприятию;</li>
 *   <li>обновление статуса и метаданных (itemCount, maxDepth).</li>
 * </ul>
 */
@Repository
public interface AssemblyRepository extends JpaRepository<AssemblyUnit, String> {

    // ==================================================================
    // 1. Поиск корневой сборочной единицы
    // ==================================================================

    /**
     * Найти корневую сборочную единицу проекта.
     *
     * <p>Корневой считается сборка с {@code parent_id IS NULL},
     * относящаяся к указанному проекту.
     */
    @Query("""
            SELECT a FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.parentId IS NULL
             ORDER BY a.createdAt DESC
            """)
    Optional<AssemblyUnit> findRootByProject(@Param("projectId") String projectId);

    /**
     * Найти все корневые сборочные единицы проекта
     * (если в проекте несколько сборок).
     */
    @Query("""
            SELECT a FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.parentId IS NULL
             ORDER BY a.designation
            """)
    List<AssemblyUnit> findAllRootsByProject(@Param("projectId") String projectId);

    /**
     * Проверить существование BOM для проекта.
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
              FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.parentId IS NULL
            """)
    boolean existsRootByProject(@Param("projectId") String projectId);

    // ==================================================================
    // 2. Поиск по проекту и иерархии
    // ==================================================================

    /**
     * Все сборочные единицы проекта.
     */
    List<AssemblyUnit> findByProjectIdOrderByDesignation(String projectId);

    /**
     * Постраничный список сборочных единиц проекта.
     */
    Page<AssemblyUnit> findByProjectId(String projectId, Pageable pageable);

    /**
     * Дочерние сборочные единицы (подсборки) по родителю.
     */
    List<AssemblyUnit> findByParentIdOrderByDesignation(String parentId);

    /**
     * Количество подсборок указанного родителя.
     */
    long countByParentId(String parentId);

    /**
     * Все подсборки (не корневые) проекта.
     */
    @Query("""
            SELECT a FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.parentId IS NOT NULL
             ORDER BY a.designation
            """)
    List<AssemblyUnit> findSubAssembliesByProject(@Param("projectId") String projectId);

    // ==================================================================
    // 3. Поиск по геометрии
    // ==================================================================

    /**
     * Найти сборочную единицу по идентификатору геометрии
     * (для дедупликации при повторном анализе).
     */
    Optional<AssemblyUnit> findByProjectIdAndGeometryId(String projectId,
                                                        String geometryId);

    /**
     * Все сборки, построенные по указанной геометрии.
     */
    List<AssemblyUnit> findByGeometryId(String geometryId);

    /**
     * Проверить существование сборки по геометрии в проекте.
     */
    boolean existsByProjectIdAndGeometryId(String projectId, String geometryId);

    // ==================================================================
    // 4. Поиск по статусу и версии
    // ==================================================================

    /**
     * Сборочные единицы в указанном статусе.
     */
    List<AssemblyUnit> findByStatusOrderByUpdatedAtDesc(String status);

    /**
     * Сборочные единицы проекта в указанном статусе.
     */
    List<AssemblyUnit> findByProjectIdAndStatus(String projectId, String status);

    /**
     * Сборочные единицы с версией больше указанной.
     */
    @Query("""
            SELECT a FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.version > :version
             ORDER BY a.version DESC
            """)
    List<AssemblyUnit> findByProjectIdAndVersionGreaterThan(
            @Param("projectId") String projectId,
            @Param("version") int version);

    /**
     * Количество сборок в статусе.
     */
    long countByStatus(String status);

    // ==================================================================
    // 5. Выборки по времени
    // ==================================================================

    /**
     * Сборочные единицы, обновлённые после указанного момента.
     */
    List<AssemblyUnit> findByUpdatedAtAfter(Instant since);

    /**
     * «Устаревшие» сборки, не обновлявшиеся дольше указанного момента.
     *
     * <p>Используется плановой задачей для архивации.
     */
    @Query("""
            SELECT a FROM AssemblyUnit a
             WHERE a.status <> 'ARCHIVED'
               AND a.updatedAt < :before
             ORDER BY a.updatedAt ASC
            """)
    List<AssemblyUnit> findStale(@Param("before") Instant before);

    // ==================================================================
    // 6. Агрегация и счётчики
    // ==================================================================

    /**
     * Количество сборочных единиц проекта.
     */
    long countByProjectId(String projectId);

    /**
     * Количество корневых сборок проекта.
     */
    @Query("""
            SELECT COUNT(a) FROM AssemblyUnit a
             WHERE a.projectId = :projectId
               AND a.parentId IS NULL
            """)
    long countRootsByProject(@Param("projectId") String projectId);

    /**
     * Распределение сборок по статусам (для дашборда).
     */
    @Query("""
            SELECT a.status, COUNT(a)
              FROM AssemblyUnit a
             GROUP BY a.status
             ORDER BY a.status
            """)
    List<Object[]> countByStatusGrouped();

    /**
     * Распределение сборок по проектам.
     */
    @Query("""
            SELECT a.projectId, COUNT(a)
              FROM AssemblyUnit a
             GROUP BY a.projectId
             ORDER BY a.projectId
            """)
    List<Object[]> countByProjectGrouped();

    // ==================================================================
    // 7. Обновления
    // ==================================================================

    /**
     * Обновить статус сборки.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyUnit a
               SET a.status = :status,
                   a.updatedAt = :updatedAt
             WHERE a.id = :assemblyId
            """)
    int updateStatus(@Param("assemblyId") String assemblyId,
                     @Param("status") String status,
                     @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить количество позиций и глубину дерева.
     *
     * <p>Вызывается после перестроения BOM.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyUnit a
               SET a.itemCount = :itemCount,
                   a.maxDepth = :maxDepth,
                   a.updatedAt = :updatedAt
             WHERE a.id = :assemblyId
            """)
    int updateMetrics(@Param("assemblyId") String assemblyId,
                      @Param("itemCount") int itemCount,
                      @Param("maxDepth") int maxDepth,
                      @Param("updatedAt") Instant updatedAt);

    /**
     * Инкремент версии сборки.
     */
    @Modifying
    @Query("""
            UPDATE AssemblyUnit a
               SET a.version = a.version + 1,
                   a.updatedAt = :updatedAt
             WHERE a.id = :assemblyId
            """)
    int incrementVersion(@Param("assemblyId") String assemblyId,
                         @Param("updatedAt") Instant updatedAt);

    // ==================================================================
    // 8. Проекции (для отчётов и UI)
    // ==================================================================

    /**
     * Список ID сборок проекта.
     */
    @Query("SELECT a.id FROM AssemblyUnit a WHERE a.projectId = :projectId")
    List<String> findIdsByProject(@Param("projectId") String projectId);

    /**
     * Список обозначений сборок проекта.
     */
    @Query("""
            SELECT a.designation FROM AssemblyUnit a
             WHERE a.projectId = :projectId
             ORDER BY a.designation
            """)
    List<String> findDesignationsByProject(@Param("projectId") String projectId);

    /**
     * Сводка по проекту: общее количество, максимальная глубина.
     */
    @Query("""
            SELECT COUNT(a),
                   COALESCE(MAX(a.maxDepth), 0),
                   COALESCE(SUM(a.itemCount), 0)
              FROM AssemblyUnit a
             WHERE a.projectId = :projectId
            """)
    Object[] getProjectSummary(@Param("projectId") String projectId);
}