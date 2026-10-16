package ru.iktk.orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.orchestrator.model.DecisionLogEntry;

import java.util.List;
import java.util.Optional;

@Repository
public interface DecisionLogRepository extends JpaRepository<DecisionLogEntry, String> {

    List<DecisionLogEntry> findByProjectIdOrderByTimestampDesc(String projectId);

    List<DecisionLogEntry> findByProjectIdOrderByTimestampAsc(String projectId);

    List<DecisionLogEntry> findByProjectIdAndEventTypeOrderByTimestampDesc(String projectId, String eventType);

    Optional<DecisionLogEntry> findTopByProjectIdOrderByTimestampDesc(String projectId);

    long countByProjectId(String projectId);
}