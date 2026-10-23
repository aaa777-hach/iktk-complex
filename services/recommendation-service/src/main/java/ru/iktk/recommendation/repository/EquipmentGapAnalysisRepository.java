package ru.iktk.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentGapAnalysisRepository extends JpaRepository<EquipmentGapAnalysis, String> {
    Optional<EquipmentGapAnalysis> findTopByProjectIdOrderByStartedAtDesc(String projectId);
    Optional<EquipmentGapAnalysis> findTopByProjectIdAndStrategyOrderByStartedAtDesc(String projectId, String strategy);
    List<EquipmentGapAnalysis> findByProjectIdOrderByStartedAtDesc(String projectId);
}