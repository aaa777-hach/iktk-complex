package ru.iktk.bom.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.bom.model.BomItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий позиций состава изделия (BOM) ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code bom_items} (PostgreSQL),
 * которая хранит позиции состава изделия с иерархией через
 * {@code parent_id}.
 *
 * <p>Схема таблицы (ориентир, см. {@code V5__bom.sql}):
 * <pre>
 *   bom_items (
 *     id              UUID PRIMARY KEY,
 *     assembly_id     UUID NOT NULL,            -- FK на assembly_units
 *     parent_id       UUID,                     -- FK на bom_items (иерархия)
 *     item_order      INT NOT NULL,             -- порядок в дереве
 *     level           INT NOT NULL DEFAULT 0,   -- уровень вложенности
 *     position        VARCHAR(32),
 *     designation     VARCHAR(128),
 *     name            VARCHAR(255),
 *     quantity        INT NOT NULL DEFAULT 1,
 *     unit            VARCHAR(32),
 *     material        VARCHAR(128),
 *     item_type       VARCHAR(32) NOT NULL,     -- PART, ASSEMBLY, STANDARD, MATERIAL, FASTENER
 *     geometry_id     VARCHAR(64),
 *     mass_kg         NUMERIC(12,4),
 *     parameters      JSONB,
 *     created_at      TIMESTAMPTZ NOT NULL,
 *     updated_at      TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p><b>Ключевые сценарии использования:</b>
 * <ul>
 *   <li>построение дерева BOM — все позиции сборки, упорядоченные
 *       по {@code item_order};</li>
 *   <li>навигация по иерархии — дочерние позиции по {@code parent_id};</li>
 *   <li>фильтрация по типу позиции (PART, ASSEMBLY, STANDARD, ...);</li>
 *   <li>поиск по обозначению или наименованию;</li>
 *   <li>агрегация по типам — для сводок;</li>
 *   <li>массовое удаление при перестроении BOM.</li>
 * </ul>
 */
@Repository
public interface BomItemRepository extends JpaRepository<BomItem, String> {

    // ==================================================================
    // 1. Основные выборки по сборке
    // ==================================================================

    /**
     * Все позиции BOM сборки, упорядоченные по порядку.
     *
     * <p>Основной метод для построения дерева в {@code BomService.buildTree}.
     */
    List<BomItem> findByAssemblyIdOrderByPosition(String assemblyId);

    /**
     * Все позиции BOM сборки (без сортировки).
     */
    List<BomItem> findByAssemblyId(String assemblyId);

    /**
     * Постраничный список позиций сборки.
     */
    Page<BomItem> findByAssemblyId(String assemblyId, Pageable pageable);

    /**
     * Количество позиций в сборке.
     */
    long countByAssemblyId(String assemblyId);

    /**
     * Проверить существование позиции в сборке.
     */
    boolean existsByAssemblyIdAndDesignation(String assemblyId, String designation);

    // ==================================================================
    // 2. Поиск по типу позиции
    // ==================================================================

    /**
     * Позиции сборки указанного типа (PART, ASSEMBLY, STANDARD,
     * MATERIAL, FASTENER).
     */
    List<BomItem> findByAssemblyIdAndTypeOrderByPosition(String assemblyId, String type);

    /**
     * Все позиции указанного типа (по всем сборкам).
     */
    List<BomItem> findByTypeOrderByDesignation(String type);

    /**
     * Количество позиций указанного типа в сборке.
     */
    long countByAssemblyIdAndType(String assemblyId, String type);

    /**
     * Распределение позиций по типам внутри сборки.
     */
    @Query("""
            SELECT i.type, COUNT(i)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
             GROUP BY i.type
             ORDER BY i.type
            """)
    List<Object[]> countByTypeGrouped(@Param("assemblyId") String assemblyId);

    /**
     * Распределение позиций по типам по всему проекту.
     *
     * <p>JOIN с {@code assembly_units} для фильтрации по проекту.
     */
    @Query("""
            SELECT i.type, COUNT(i)
              FROM BomItem i, AssemblyUnit a
             WHERE i.assemblyId = a.id
               AND a.projectId = :projectId
             GROUP BY i.type
             ORDER BY i.type
            """)
    List<Object[]> countByTypeGroupedForProject(@Param("projectId") String projectId);

    // ==================================================================
    // 3. Навигация по иерархии
    // ==================================================================

    /**
     * Дочерние позиции по родителю.
     */
    List<BomItem> findByParentId(String parentId);

    /**
     * Дочерние позиции, упорядоченные по позиции.
     */
    List<BomItem> findByParentIdOrderByPosition(String parentId);

    /**
     * Количество дочерних позиций.
     */
    long countByParentId(String parentId);

    /**
     * Корневые позиции сборки (без родителя).
     */
    @Query("""
            SELECT i FROM BomItem i
             WHERE i.assemblyId = :assemblyId
               AND i.parentId IS NULL
             ORDER BY i.order
            """)
    List<BomItem> findRootsByAssembly(@Param("assemblyId") String assemblyId);

    /**
     * Все позиции сборки на указанном уровне вложенности.
     */
    List<BomItem> findByAssemblyIdAndLevelOrderByPosition(String assemblyId, int level);

    /**
     * Максимальный уровень вложенности в сборке.
     */
    @Query("""
            SELECT COALESCE(MAX(i.level), 0)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
            """)
    int findMaxLevel(@Param("assemblyId") String assemblyId);

    /**
     * Все потомки позиции (рекурсивный CTE).
     *
     * <p>Используется при удалении позиции вместе с поддеревом.
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT bi.* FROM iktk.bom_items bi
                 WHERE bi.parent_id = :itemId
                UNION ALL
                SELECT bi.* FROM iktk.bom_items bi
                  JOIN descendants d ON bi.parent_id = d.id
            )
            SELECT * FROM descendants
            """, nativeQuery = true)
    List<BomItem> findAllDescendants(@Param("itemId") String itemId);

    /**
     * Все предки позиции (рекурсивный CTE).
     */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT bi.* FROM iktk.bom_items bi
                 WHERE bi.id = :itemId
                UNION ALL
                SELECT bi.* FROM iktk.bom_items bi
                  JOIN ancestors a ON bi.id = a.parent_id
            )
            SELECT * FROM ancestors WHERE id <> :itemId
            """, nativeQuery = true)
    List<BomItem> findAllAncestors(@Param("itemId") String itemId);

    // ==================================================================
    // 4. Поиск по обозначению/наименованию/материалу
    // ==================================================================

    /**
     * Поиск позиции по обозначению в сборке.
     */
    Optional<BomItem> findByAssemblyIdAndDesignation(String assemblyId, String designation);

    /**
     * Поиск позиции по обозначению в проекте (по всем сборкам).
     */
    @Query("""
            SELECT i FROM BomItem i, AssemblyUnit a
             WHERE i.assemblyId = a.id
               AND a.projectId = :projectId
               AND i.designation = :designation
            """)
    List<BomItem> findByProjectAndDesignation(
            @Param("projectId") String projectId,
            @Param("designation") String designation);

    /**
     * Поиск по части обозначения или наименования.
     */
    @Query("""
            SELECT i FROM BomItem i
             WHERE i.assemblyId = :assemblyId
               AND (LOWER(i.designation) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')))
             ORDER BY i.order
            """)
    List<BomItem> search(@Param("assemblyId") String assemblyId,
                         @Param("query") String query);

    /**
     * Позиции с указанным материалом.
     */
    List<BomItem> findByAssemblyIdAndMaterial(String assemblyId, String material);

    // ==================================================================
    // 5. Агрегация и счётчики
    // ==================================================================

    /**
     * Суммарное количество по всем позициям сборки.
     */
    @Query("""
            SELECT COALESCE(SUM(i.quantity), 0)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
            """)
    long sumQuantityByAssembly(@Param("assemblyId") String assemblyId);

    /**
     * Суммарная масса по сборке.
     */
    @Query("""
            SELECT COALESCE(SUM(i.massKg), 0)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
            """)
    java.math.BigDecimal sumMassByAssembly(@Param("assemblyId") String assemblyId);

    /**
     * Количество уникальных обозначений в сборке.
     */
    @Query("""
            SELECT COUNT(DISTINCT i.designation)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
               AND i.designation IS NOT NULL
            """)
    long countDistinctDesignations(@Param("assemblyId") String assemblyId);

    /**
     * Список уникальных материалов в сборке.
     */
    @Query("""
            SELECT DISTINCT i.material
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
               AND i.material IS NOT NULL
             ORDER BY i.material
            """)
    List<String> findDistinctMaterials(@Param("assemblyId") String assemblyId);

    // ==================================================================
    // 6. Обновления
    // ==================================================================

    /**
     * Обновить количество для позиции.
     */
    @Modifying
    @Query("""
            UPDATE BomItem i
               SET i.quantity = :quantity,
                   i.updatedAt = :updatedAt
             WHERE i.id = :itemId
            """)
    int updateQuantity(@Param("itemId") String itemId,
                       @Param("quantity") int quantity,
                       @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить уровень для всех позиций сборки (после перестроения).
     */
    @Modifying
    @Query("""
            UPDATE BomItem i
               SET i.level = :level,
                   i.updatedAt = :updatedAt
             WHERE i.id = :itemId
            """)
    int updateLevel(@Param("itemId") String itemId,
                    @Param("level") int level,
                    @Param("updatedAt") Instant updatedAt);

    /**
     * Переместить позицию к новому родителю.
     */
    @Modifying
    @Query("""
            UPDATE BomItem i
               SET i.parentId = :parentId,
                   i.updatedAt = :updatedAt
             WHERE i.id = :itemId
            """)
    int updateParent(@Param("itemId") String itemId,
                     @Param("parentId") String parentId,
                     @Param("updatedAt") Instant updatedAt);

    // ==================================================================
    // 7. Удаление
    // ==================================================================

    /**
     * Удалить все позиции сборки.
     *
     * <p>Используется в {@code BomService.updateItems} при полном
     * замещении набора позиций.
     */
    @Modifying
    @Query("DELETE FROM BomItem i WHERE i.assemblyId = :assemblyId")
    int deleteByAssemblyId(@Param("assemblyId") String assemblyId);

    /**
     * Удалить все дочерние позиции указанного родителя.
     */
    @Modifying
    @Query("DELETE FROM BomItem i WHERE i.parentId = :parentId")
    int deleteByParentId(@Param("parentId") String parentId);

    // ==================================================================
    // 8. Проекции (для отчётов и UI)
    // ==================================================================

    /**
     * Список ID позиций сборки.
     */
    @Query("SELECT i.id FROM BomItem i WHERE i.assemblyId = :assemblyId")
    List<String> findIdsByAssembly(@Param("assemblyId") String assemblyId);

    /**
     * Список обозначений позиций сборки.
     */
    @Query("""
            SELECT i.designation FROM BomItem i
             WHERE i.assemblyId = :assemblyId
               AND i.designation IS NOT NULL
             ORDER BY i.order
            """)
    List<String> findDesignationsByAssembly(@Param("assemblyId") String assemblyId);

    /**
     * Уплощённый список позиций проекта для экспорта.
     */
    @Query("""
            SELECT i FROM BomItem i, AssemblyUnit a
             WHERE i.assemblyId = a.id
               AND a.projectId = :projectId
             ORDER BY a.designation, i.order
            """)
    List<BomItem> findAllByProject(@Param("projectId") String projectId);

    /**
     * Сводка по сборке: количество, глубина, суммарное количество.
     */
    @Query("""
            SELECT COUNT(i),
                   COALESCE(MAX(i.level), 0),
                   COALESCE(SUM(i.quantity), 0)
              FROM BomItem i
             WHERE i.assemblyId = :assemblyId
            """)
    Object[] getAssemblySummary(@Param("assemblyId") String assemblyId);
}