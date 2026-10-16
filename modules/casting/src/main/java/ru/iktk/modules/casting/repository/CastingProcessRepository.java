package ru.iktk.modules.casting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.iktk.modules.casting.model.CastingProcess;

@Repository
public interface CastingProcessRepository extends JpaRepository<CastingProcess, String> {
}