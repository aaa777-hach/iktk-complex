package ru.iktk.equipment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.equipment.model.EquipmentGapAnalysis;

import java.util.List;

public interface EquipmentGapAnalysisRepository extends JpaRepository<EquipmentGapAnalysis, String> {
    List<EquipmentGapAnalysis> findByProjectIdOrderByStartedAtDesc(String projectId);
    List<EquipmentGapAnalysis> findByStatusOrderByStartedAtDesc(String status);
}