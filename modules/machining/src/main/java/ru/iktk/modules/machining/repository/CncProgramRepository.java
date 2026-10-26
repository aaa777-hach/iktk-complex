package ru.iktk.modules.machining.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.machining.model.CncProgram;

import java.util.List;

@Repository
public interface CncProgramRepository extends JpaRepository<CncProgram, String> {

    List<CncProgram> findByProcessId(String processId);
}