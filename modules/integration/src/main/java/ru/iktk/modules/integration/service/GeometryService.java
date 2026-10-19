package ru.iktk.modules.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.repository.GeometryRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис геометрии.
 *
 * <p>Возвращает геометрию из БД {@code iktk_geometry} по {@code objectRef}.
 * Контракт JSON совпадает с {@code CastingProcessService.GeometryData}
 * (11 полей), чтобы {@code casting} мог десериализовать через Feign.
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
     *
     * @param projectId идентификатор проекта (для контекста; в БД — опционально)
     * @param objectRef идентификатор объекта (ключ поиска в БД)
     * @return Map с 11 полями {@code GeometryData}, либо {@code null},
     *         если {@code objectRef} не найден (контроллер вернёт 404)
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
}