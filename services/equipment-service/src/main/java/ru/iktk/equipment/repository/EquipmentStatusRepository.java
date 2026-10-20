package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.equipment.model.EquipmentStatus;

import java.util.Optional;

public interface EquipmentStatusRepository extends JpaRepository<EquipmentStatus, String> {
    Optional<EquipmentStatus> findByEquipmentId(String equipmentId);
    boolean existsByEquipmentId(String equipmentId);
}