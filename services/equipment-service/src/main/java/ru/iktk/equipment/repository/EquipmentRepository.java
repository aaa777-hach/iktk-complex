package ru.iktk.equipment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.equipment.model.Equipment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий оборудования ИКТК.
 *
 * <p>Обеспечивает доступ к таблице {@code equipment} (PostgreSQL),
 * которая хранит реестр оборудования предприятия.
 *
 * <p>Схема таблицы (ориентир, см. {@code V4__equipment.sql}):
 * <pre>
 *   equipment (
 *     id                UUID PRIMARY KEY,
 *     name              VARCHAR(255) NOT NULL,
 *     equipment_type    VARCHAR(64) NOT NULL,
 *     model             VARCHAR(128),
 *     inventory_no      VARCHAR(64) UNIQUE,
 *     status            VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
 *     location          VARCHAR(255),
 *     enterprise_code   VARCHAR(64),
 *     department_code   VARCHAR(64),
 *     parameters        JSONB,
 *     created_at        TIMESTAMPTZ NOT NULL,
 *     updated_at        TIMESTAMPTZ NOT NULL
 *   )
 * </pre>
 *
 * <p>Связи: {@code equipment_capabilities} (OneToMany),
 * {@code equipment_status} (OneToOne), {@code equipment_tooling} (ManyToMany).
 *
 * <p>Ключевые сценарии использования:
 * <ul>
 *   <li>постраничный поиск с фильтрами — для UI и API;</li>
 *   <li>поиск доступного оборудования по требованиям операции
 *       — для matching и gap-анализа;</li>
 *   <li>поиск по инвентарному номеру — для дедупликации при импорте;</li>
 *   <li>агрегация по типу/статусу — для дашборда и сводок.</li>
 * </ul>
 */
@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    // ==================================================================
    // 1. Основные выборки
    // ==================================================================

    /**
     * Найти оборудование по инвентарному номеру.
     */
    Optional<Equipment> findByInventoryNo(String inventoryNo);

    /**
     * Проверить существование по инвентарному номеру.
     */
    boolean existsByInventoryNo(String inventoryNo);

    /**
     * Найти оборудование по имени.
     */
    Optional<Equipment> findByName(String name);

    /**
     * Найти всё оборудование указанного предприятия.
     */
    List<Equipment> findByEnterpriseCode(String enterpriseCode);

    /**
     * Найти всё оборудование указанного подразделения.
     */
    List<Equipment> findByDepartmentCode(String departmentCode);

    /**
     * Найти оборудование по статусу.
     */
    List<Equipment> findByStatus(String status);

    /**
     * Найти оборудование по типу.
     */
    List<Equipment> findByType(String type);

    // ==================================================================
    // 2. Постраничный поиск с фильтрами
    // ==================================================================

    /**
     * Постраничный поиск оборудования с фильтрами.
     *
     * <p>Все параметры опциональны: {@code null} — фильтр не применяется.
     * Поиск по строке — по {@code name}, {@code inventoryNo}, {@code model}.
     */
    @Query("""
            SELECT e FROM Equipment e
             WHERE (:type IS NULL OR e.type = :type)
               AND (:status IS NULL OR e.status = :status)
               AND (:enterpriseCode IS NULL OR e.enterpriseCode = :enterpriseCode)
               AND (:search IS NULL OR :search = ''
                    OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.inventoryNo) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(e.model) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Equipment> search(@Param("type") String type,
                           @Param("status") String status,
                           @Param("enterpriseCode") String enterpriseCode,
                           @Param("search") String search,
                           Pageable pageable);

    // ==================================================================
    // 3. Агрегация и счётчики
    // ==================================================================

    /**
     * Количество оборудования по статусу.
     */
    long countByStatus(String status);

    /**
     * Количество оборудования по типу.
     */
    long countByType(String type);

    /**
     * Количество оборудования предприятия.
     */
    long countByEnterpriseCode(String enterpriseCode);

    /**
     * Распределение оборудования по типам.
     */
    @Query("""
            SELECT e.type, COUNT(e)
              FROM Equipment e
             WHERE e.type IS NOT NULL
             GROUP BY e.type
             ORDER BY e.type
            """)
    List<Object[]> countByTypeGrouped();

    /**
     * Распределение оборудования по статусам.
     */
    @Query("""
            SELECT e.status, COUNT(e)
              FROM Equipment e
             WHERE e.status IS NOT NULL
             GROUP BY e.status
             ORDER BY e.status
            """)
    List<Object[]> countByStatusGrouped();

    /**
     * Распределение оборудования по предприятиям.
     */
    @Query("""
            SELECT e.enterpriseCode, COUNT(e)
              FROM Equipment e
             WHERE e.enterpriseCode IS NOT NULL
             GROUP BY e.enterpriseCode
             ORDER BY e.enterpriseCode
            """)
    List<Object[]> countByEnterpriseGrouped();

    // ==================================================================
    // 4. Выборки для matching и gap-анализа
    // ==================================================================

    /**
     * Найти доступное оборудование, поддерживающее указанную операцию.
     *
     * <p>Использует таблицу возможностей {@code equipment_capabilities}.
     * Возвращает только оборудование со статусом {@code AVAILABLE}.
     *
     * @param operationType тип операции
     * @param cncRequired   требуется ли поддержка ЧПУ
     * @return список оборудования
     */
    @Query("""
            SELECT DISTINCT e FROM Equipment e
              JOIN EquipmentCapability c ON c.equipmentId = e.id
             WHERE c.operationType = :operationType
               AND e.status = 'AVAILABLE'
               AND (:cncRequired = FALSE
                    OR c.operationType LIKE '%CNC%')
             ORDER BY e.name
            """)
    List<Equipment> findAvailableByOperation(@Param("operationType") String operationType,
                                             @Param("cncRequired") boolean cncRequired);

    /**
     * Найти оборудование, поддерживающее указанную операцию
     * с требуемой точностью.
     */
    @Query("""
            SELECT DISTINCT e.id FROM Equipment e
              JOIN EquipmentCapability c ON c.equipmentId = e.id
             WHERE c.operationType = :operationType
               AND (:accuracy IS NULL OR c.tolerance = :accuracy)
               AND e.status = 'AVAILABLE'
             ORDER BY e.id
            """)
    List<String> findIdsByOperationAndAccuracy(
            @Param("operationType") String operationType,
            @Param("accuracy") String accuracy);

    /**
     * Проверить, есть ли на предприятии оборудование,
     * поддерживающее указанную операцию.
     */
    @Query("""
            SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END
              FROM EquipmentCapability c
             WHERE c.operationType = :operationType
            """)
    boolean existsByCapabilityOperation(@Param("operationType") String operationType);

    /**
     * Получить список ID оснастки, совместимой с оборудованием.
     */
    @Query(value = """
            SELECT et.tooling_id
              FROM iktk.equipment_tooling et
             WHERE et.equipment_id = :equipmentId
            """, nativeQuery = true)
    List<String> findCompatibleToolingIds(@Param("equipmentId") String equipmentId);

    // ==================================================================
    // 5. Выборки по состоянию и загрузке
    // ==================================================================

    /**
     * Найти оборудование со статусом AVAILABLE.
     */
    @Query("SELECT e FROM Equipment e WHERE e.status = 'AVAILABLE' ORDER BY e.name")
    List<Equipment> findAvailable();

    /**
     * Найти оборудование, находящееся на обслуживании.
     */
    @Query("SELECT e FROM Equipment e WHERE e.status = 'MAINTENANCE' ORDER BY e.name")
    List<Equipment> findUnderMaintenance();

    /**
     * Найти оборудование, обновлённое после указанного момента.
     */
    List<Equipment> findByUpdatedAtAfter(Instant since);

    /**
     * Найти «устаревшее» оборудование, не обновлявшееся дольше указанного момента.
     */
    @Query("""
            SELECT e FROM Equipment e
             WHERE e.status <> 'DECOMMISSIONED'
               AND e.updatedAt < :before
             ORDER BY e.updatedAt ASC
            """)
    List<Equipment> findStale(@Param("before") Instant before);

    // ==================================================================
    // 6. Обновления
    // ==================================================================

    /**
     * Обновить статус оборудования.
     */
    @Modifying
    @Query("""
            UPDATE Equipment e
               SET e.status = :status,
                   e.updatedAt = :updatedAt
             WHERE e.id = :equipmentId
            """)
    int updateStatus(@Param("equipmentId") String equipmentId,
                     @Param("status") String status,
                     @Param("updatedAt") Instant updatedAt);

    /**
     * Обновить расположение оборудования.
     */
    @Modifying
    @Query("""
            UPDATE Equipment e
               SET e.location = :location,
                   e.updatedAt = :updatedAt
             WHERE e.id = :equipmentId
            """)
    int updateLocation(@Param("equipmentId") String equipmentId,
                       @Param("location") String location,
                       @Param("updatedAt") Instant updatedAt);

    // ==================================================================
    // 7. Проекции (для отчётов и UI)
    // ==================================================================

    /**
     * Список ID всего оборудования предприятия.
     */
    @Query("SELECT e.id FROM Equipment e WHERE e.enterpriseCode = :enterpriseCode")
    List<String> findIdsByEnterprise(@Param("enterpriseCode") String enterpriseCode);

    /**
     * Список наименований оборудования по типу.
     */
    @Query("SELECT e.name FROM Equipment e WHERE e.type = :type ORDER BY e.name")
    List<String> findNamesByType(@Param("type") String type);

    /**
     * Проверка существования оборудования с указанным статусом.
     */
    boolean existsByStatus(String status);
}