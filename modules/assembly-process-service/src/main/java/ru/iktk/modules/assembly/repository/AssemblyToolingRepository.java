package ru.iktk.modules.assembly.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.modules.assembly.model.AssemblyTooling;

import java.util.List;

public interface AssemblyToolingRepository extends JpaRepository<AssemblyTooling, String> {
    List<AssemblyTooling> findByAssemblyProcessId(String assemblyProcessId);
    List<AssemblyTooling> findByToolingId(String toolingId);
    void deleteByAssemblyProcessId(String assemblyProcessId);
}