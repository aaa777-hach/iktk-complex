package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.model.EquipmentToolingId;

import java.util.List;

public interface EquipmentToolingRepository extends JpaRepository<EquipmentTooling, EquipmentToolingId> {
    List<EquipmentTooling> findByEquipmentId(String equipmentId);
    List<EquipmentTooling> findByToolingId(String toolingId);
    void deleteByEquipmentIdAndToolingId(String equipmentId, String toolingId);
}