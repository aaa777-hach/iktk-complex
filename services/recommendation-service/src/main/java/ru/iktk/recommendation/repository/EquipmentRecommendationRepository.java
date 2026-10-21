package ru.iktk.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.EquipmentRecommendation;

import java.util.List;

@Repository
public interface EquipmentRecommendationRepository extends JpaRepository<EquipmentRecommendation, String> {
    List<EquipmentRecommendation> findByProjectIdOrderByScoreDesc(String projectId);
    List<EquipmentRecommendation> findByProjectIdAndTypeOrderByScoreDesc(String projectId, String type);
    List<EquipmentRecommendation> findByProjectIdAndStatusOrderByScoreDesc(String projectId, String status);
    List<EquipmentRecommendation> findByProjectIdAndTypeAndStatusOrderByScoreDesc(String projectId, String type, String status);
    List<EquipmentRecommendation> findByGapIdOrderByScoreDesc(String gapId);
}