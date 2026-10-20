package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.equipment.model.EquipmentRequirement;

import java.util.List;

public interface EquipmentRequirementRepository extends JpaRepository<EquipmentRequirement, String> {
    List<EquipmentRequirement> findByProjectIdOrderByPriorityDesc(String projectId);
    List<EquipmentRequirement> findByProjectIdAndOperationType(String projectId, String operationType);
}