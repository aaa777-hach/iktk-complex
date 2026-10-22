package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.model.EquipmentToolingId;

import java.util.List;

public interface EquipmentToolingRepository extends JpaRepository<EquipmentTooling, EquipmentToolingId> {

    @Query("SELECT t FROM EquipmentTooling t WHERE t.id.equipmentId = :equipmentId")
    List<EquipmentTooling> findByEquipmentId(@Param("equipmentId") String equipmentId);

    @Query("SELECT t FROM EquipmentTooling t WHERE t.id.toolingId = :toolingId")
    List<EquipmentTooling> findByToolingId(@Param("toolingId") String toolingId);

    @Modifying
    @Transactional
    @Query("DELETE FROM EquipmentTooling t WHERE t.id.equipmentId = :equipmentId AND t.id.toolingId = :toolingId")
    void deleteByEquipmentIdAndToolingId(@Param("equipmentId") String equipmentId,
                                         @Param("toolingId") String toolingId);
}