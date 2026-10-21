package ru.iktk.bom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.bom.model.AssemblyConnection;

import java.util.List;

/**
 * Репозиторий связей и сопряжений сборочных единиц.
 */
@Repository
public interface AssemblyConnectionRepository extends JpaRepository<AssemblyConnection, String> {

    List<AssemblyConnection> findByAssemblyId(String assemblyId);

    List<AssemblyConnection> findByAssemblyIdOrderByOrderAsc(String assemblyId);

    List<AssemblyConnection> findByType(String type);

    long countByAssemblyId(String assemblyId);

    void deleteByAssemblyId(String assemblyId);
}