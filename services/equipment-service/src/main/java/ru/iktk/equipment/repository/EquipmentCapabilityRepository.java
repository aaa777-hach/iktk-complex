package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.equipment.model.EquipmentCapability;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentCapabilityRepository extends JpaRepository<EquipmentCapability, String> {

    List<EquipmentCapability> findByOperationType(String operationType);

    Optional<EquipmentCapability> findByEquipmentId(String equipmentId);
}