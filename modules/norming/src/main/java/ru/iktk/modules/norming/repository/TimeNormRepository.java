package ru.iktk.modules.norming.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.norming.model.TimeNorm;

import java.util.List;

@Repository
public interface TimeNormRepository extends JpaRepository<TimeNorm, String> {
    List<TimeNorm> findByProcessId(String processId);
    List<TimeNorm> findByProjectId(String projectId);
}
