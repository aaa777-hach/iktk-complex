package ru.iktk.modules.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.iktk.modules.integration.model.Geometry;

import java.util.Optional;

/**
 * Репозиторий геометрии.
 */
public interface GeometryRepository extends JpaRepository<Geometry, String> {

    Optional<Geometry> findByObjectRef(String objectRef);
}