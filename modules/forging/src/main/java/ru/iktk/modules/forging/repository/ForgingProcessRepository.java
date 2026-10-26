package ru.iktk.modules.forging.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.forging.model.ForgingProcess;

import java.util.List;

@Repository
public interface ForgingProcessRepository extends JpaRepository<ForgingProcess, String> {
    List<ForgingProcess> findByProjectId(String projectId);
}
