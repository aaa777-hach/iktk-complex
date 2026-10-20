package ru.iktk.modules.assembly.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.modules.assembly.model.AssemblyComponent;

import java.util.List;

public interface AssemblyComponentRepository extends JpaRepository<AssemblyComponent, String> {
    List<AssemblyComponent> findByAssemblyProcessIdOrderByPositionAsc(String assemblyProcessId);
    List<AssemblyComponent> findByAssemblyProcessIdAndParentComponentIdIsNull(String assemblyProcessId);
    List<AssemblyComponent> findByParentComponentId(String parentComponentId);
    long countByAssemblyProcessId(String assemblyProcessId);
    void deleteByAssemblyProcessId(String assemblyProcessId);
}