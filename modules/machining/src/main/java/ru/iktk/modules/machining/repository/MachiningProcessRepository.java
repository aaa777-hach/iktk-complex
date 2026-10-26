package ru.iktk.modules.machining.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.machining.model.MachiningProcess;

@Repository
public interface MachiningProcessRepository extends JpaRepository<MachiningProcess, String> {
}