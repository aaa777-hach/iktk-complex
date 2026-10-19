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
}