package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.iktk.equipment.model.Equipment;

/**
 * Репозиторий оборудования ИКТК.
 */
public interface EquipmentRepository extends JpaRepository<Equipment, String> {

    /**
     * Проверить, есть ли хотя бы одна единица оборудования со статусом
     * AVAILABLE, поддерживающая указанную операцию.
     */
    @Query("""
            SELECT COUNT(e) > 0
            FROM Equipment e
            JOIN e.capabilities c
            WHERE c.operationType = :operationType
              AND e.status = 'AVAILABLE'
            """)
    boolean existsAvailableByOperation(@Param("operationType") String operationType);

    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT e FROM Equipment e LEFT JOIN FETCH e.capabilities c " +
            "WHERE e.decommissionedAt IS NULL " +
            "AND (:operationType IS NULL OR c.operationType = :operationType)")
    java.util.List<ru.iktk.equipment.model.Equipment> findActiveWithCapabilities(
            @org.springframework.data.repository.query.Param("operationType") String operationType);
}