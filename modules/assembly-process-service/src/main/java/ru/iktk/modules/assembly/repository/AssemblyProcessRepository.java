package ru.iktk.modules.assembly.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.modules.assembly.model.AssemblyProcess;

import java.util.List;

public interface AssemblyProcessRepository extends JpaRepository<AssemblyProcess, String> {
    List<AssemblyProcess> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<AssemblyProcess> findByProjectIdAndStatus(String projectId, String status);
}