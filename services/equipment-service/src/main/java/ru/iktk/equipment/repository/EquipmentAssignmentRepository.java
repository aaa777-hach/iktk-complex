package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.equipment.model.EquipmentAssignment;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, String> {

    List<EquipmentAssignment> findByProjectIdAndActiveTrue(String projectId);

    List<EquipmentAssignment> findByEquipmentIdAndActiveTrue(String equipmentId);

    Optional<EquipmentAssignment> findByProjectIdAndEquipmentIdAndActiveTrue(String projectId, String equipmentId);

    long countByEquipmentIdAndActiveTrue(String equipmentId);
}