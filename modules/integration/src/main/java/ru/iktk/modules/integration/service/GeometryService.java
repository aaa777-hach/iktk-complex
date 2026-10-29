package ru.iktk.modules.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.repository.GeometryRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис геометрии.
 *
 * <p>Возвращает геометрию из БД {@code iktk_geometry} по {@code objectRef}.
 * Контракт JSON совпадает с {@code CastingProcessService.GeometryData}
 * (11 полей), чтобы {@code casting} мог десериализовать через Feign.
 *
 * <p>Сессия 33: {@link #upsertFromStep} сохраняет эвристическую оценку
 * из {@link GeometryEstimateService} в БД.
 */
@Service
public class GeometryService {

    private static final Logger log = LoggerFactory.getLogger(GeometryService.class);

    private final GeometryRepository repository;

    public GeometryService(GeometryRepository repository) {
        this.repository = repository;
    }

    /**
     * Построить геометрию для {@code objectRef}.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildGeometry(String projectId, String objectRef) {
        String ref = (objectRef == null || objectRef.isBlank()) ? "default-object" : objectRef;
        log.info("GeometryService: projectId={}, objectRef={}", projectId, ref);

        Optional<Geometry> found = repository.findByObjectRef(ref);
        if (found.isEmpty()) {
            log.warn("GeometryService: geometry not found for objectRef={}", ref);
            return null;
        }

        Geometry g = found.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectRef", g.getObjectRef());
        m.put("material", g.getMaterial());
        m.put("volume", g.getVolume());
        m.put("mass", g.getMass());
        m.put("minWallThickness", g.getMinWallThickness());
        m.put("minFilletRadius", g.getMinFilletRadius());
        m.put("minDraftAngle", g.getMinDraftAngle());
        m.put("nominalDimensions", g.getNominalDimensions());
        m.put("complexityScore", g.getComplexityScore());
        m.put("undercuts", g.isUndercuts());
        m.put("hotSpots", g.isHotSpots());

        log.info("GeometryService: found geometry objectRef={}, volume={}, complexity={}",
                ref, g.getVolume(), g.getComplexityScore());
        return m;
    }

    /**
     * Сохранить/обновить геометрию из эвристической оценки (сессия 33).
     *
     * @param objectRef уникальный ключ (имя STEP-файла без расширения)
     * @param projectId опциональный проект
     * @param estimate  эвристики из {@link GeometryEstimateService}
     * @param overwrite {@code true} — обновить существующую, {@code false} — {@link AlreadyExistsException}
     * @return сохранённая сущность
     */
    @Transactional
    public Geometry upsertFromStep(String objectRef, String projectId,
                                   GeometryEstimateService.EstimateResult estimate,
                                   boolean overwrite) {
        Optional<Geometry> existing = repository.findByObjectRef(objectRef);
        if (existing.isPresent() && !overwrite) {
            log.warn("upsertFromStep: objectRef={} already exists, overwrite=false", objectRef);
            throw new AlreadyExistsException(objectRef);
        }

        Geometry g;
        if (existing.isPresent()) {
            g = existing.get();
            g.setProjectId(projectId);
            g.setMaterial(estimate.getMaterial());
            g.setVolume(estimate.getVolume());
            g.setMass(estimate.getMass());
            g.setMinWallThickness(estimate.getMinWallThickness());
            g.setMinFilletRadius(estimate.getMinFilletRadius());
            g.setMinDraftAngle(estimate.getMinDraftAngle());
            g.setNominalDimensions(estimate.getNominalDimensions());
            g.setComplexityScore(estimate.getComplexityScore());
            g.setUndercuts(estimate.isUndercuts());
            g.setHotSpots(estimate.isHotSpots());
            log.info("upsertFromStep: update objectRef={}", objectRef);
        } else {
            g = Geometry.builder()
                    .id(UUID.randomUUID().toString())
                    .objectRef(objectRef)
                    .projectId(projectId)
                    .material(estimate.getMaterial())
                    .volume(estimate.getVolume())
                    .mass(estimate.getMass())
                    .minWallThickness(estimate.getMinWallThickness())
                    .minFilletRadius(estimate.getMinFilletRadius())
                    .minDraftAngle(estimate.getMinDraftAngle())
                    .nominalDimensions(estimate.getNominalDimensions())
                    .complexityScore(estimate.getComplexityScore())
                    .undercuts(estimate.isUndercuts())
                    .hotSpots(estimate.isHotSpots())
                    .build();
            log.info("upsertFromStep: insert objectRef={}", objectRef);
        }

        Geometry saved = repository.save(g);
        log.info("upsertFromStep: saved objectRef={}, id={}", saved.getObjectRef(), saved.getId());
        return saved;
    }

    /** objectRef уже существует, overwrite=false. */
    public static class AlreadyExistsException extends RuntimeException {
        public AlreadyExistsException(String objectRef) {
            super("Geometry already exists: " + objectRef);
        }
    }
}