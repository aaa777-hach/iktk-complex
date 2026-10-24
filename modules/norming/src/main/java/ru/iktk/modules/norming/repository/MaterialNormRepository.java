package ru.iktk.modules.norming.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.norming.model.MaterialNorm;

import java.util.List;

@Repository
public interface MaterialNormRepository extends JpaRepository<MaterialNorm, String> {
    List<MaterialNorm> findByProcessId(String processId);
    List<MaterialNorm> findByProjectId(String projectId);
}
