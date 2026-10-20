package ru.iktk.modules.assembly.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.modules.assembly.model.AssemblyStage;

import java.util.List;

public interface AssemblyStageRepository extends JpaRepository<AssemblyStage, String> {
    List<AssemblyStage> findByAssemblyProcessIdOrderByStageOrderAsc(String assemblyProcessId);
    long countByAssemblyProcessId(String assemblyProcessId);
    void deleteByAssemblyProcessId(String assemblyProcessId);
}