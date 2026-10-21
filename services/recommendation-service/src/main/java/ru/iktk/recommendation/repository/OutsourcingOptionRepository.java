package ru.iktk.recommendation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.recommendation.model.OutsourcingOption;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface OutsourcingOptionRepository extends JpaRepository<OutsourcingOption, String> {

    List<OutsourcingOption> findByGapIdOrderByScoreDesc(String gapId);
    List<OutsourcingOption> findByProjectIdOrderByScoreDesc(String projectId);
    List<OutsourcingOption> findByProjectIdAndTypeOrderByScoreDesc(String projectId, String type);
    Optional<OutsourcingOption> findByGapIdAndStatus(String gapId, String status);
    long countByGapId(String gapId);

    @Query(value = "SELECT DISTINCT contractor AS contractor, " +
           "AVG(quality_rating) AS qualityRating, " +
           "MIN(cost) AS minCost, " +
           "MIN(lead_time_days) AS leadTimeDays " +
           "FROM outsourcing_options " +
           "WHERE type = :type AND contractor IS NOT NULL " +
           "GROUP BY contractor", nativeQuery = true)
    List<Map<String, Object>> findContractorsByOperation(@Param("type") String type);
}